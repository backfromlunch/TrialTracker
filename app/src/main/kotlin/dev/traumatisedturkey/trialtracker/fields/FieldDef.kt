package dev.traumatisedturkey.trialtracker.fields

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A FHIR extension, limited to the value types this app reads. */
@Serializable
data class Extension(
    val url: String,
    val valueBoolean: Boolean? = null,
    val valueInteger: Int? = null,
    val valueDecimal: Float? = null,
    val valueString: String? = null,
    val valueCodeableConcept: CodeableConcept? = null,
)

/** FHIR CodeableConcept, as used by an extension's `valueCodeableConcept`. */
@Serializable
data class CodeableConcept(
    val coding: List<ExtensionCoding> = emptyList(),
)

/** A coding inside an extension. Not [Coding]: that is the answerOption shape. */
@Serializable
data class ExtensionCoding(
    val system: String? = null,
    val code: String? = null,
)

/** A slider's end labels. A non-null [FieldDef.IntegerField.slider] means "render as a slider". */
data class SliderSpec(
    val lowLabel: String,
    val highLabel: String,
)

private const val HIDDEN_URL = "http://hl7.org/fhir/StructureDefinition/questionnaire-hidden"
private const val MIN_VALUE_URL = "http://hl7.org/fhir/StructureDefinition/minValue"
private const val MAX_VALUE_URL = "http://hl7.org/fhir/StructureDefinition/maxValue"
private const val ITEM_CONTROL_URL = "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl"
private const val ITEM_CONTROL_SLIDER_CODE = "slider"
private const val SLIDER_LOW_LABEL_URL = "https://traumatisedturkey.dev/fhir/StructureDefinition/slider-low-label"
private const val SLIDER_HIGH_LABEL_URL = "https://traumatisedturkey.dev/fhir/StructureDefinition/slider-high-label"
private const val SLIDER_DEFAULT_MIN = 0
private const val SLIDER_DEFAULT_MAX = 100
private const val COUNTER_URL = "https://traumatisedturkey.dev/fhir/StructureDefinition/questionnaire-counter"

private fun List<Extension>.intValue(url: String): Int? = firstOrNull { it.url == url }?.valueInteger
private fun List<Extension>.floatValue(url: String): Float? = firstOrNull { it.url == url }?.valueDecimal
private fun List<Extension>.stringValue(url: String): String? = firstOrNull { it.url == url }?.valueString

// Counter extension means nothing on non-integer items: refuse it rather than silently ignore it.
// (An integer item with the extension never reaches IntegerField: FieldDefSerializer makes it a CounterField.)
private fun rejectCounterExtension(
    typeName: String,
    linkId: String,
    extension: List<Extension>,
) {
    if (extension.any { it.url == COUNTER_URL }) {
        throw SerializationException(
            "$typeName '$linkId' uses the counter extension, which is only valid on integer items",
        )
    }
}

// Checks the code only, not the system, so a typo'd system still gives a slider, not a silent text box.
private fun List<Extension>.hasSliderControl(): Boolean = any { ext ->
    ext.url == ITEM_CONTROL_URL &&
        ext.valueCodeableConcept?.coding.orEmpty().any { it.code == ITEM_CONTROL_SLIDER_CODE }
}

private fun List<Extension>.hasSliderLabels(): Boolean = any { it.url == SLIDER_LOW_LABEL_URL || it.url == SLIDER_HIGH_LABEL_URL }

// Null for a plain field. Throws for a malformed slider (missing or blank label, or labels without the control).
private fun sliderSpecOrNull(
    linkId: String,
    extension: List<Extension>,
): SliderSpec? {
    if (!extension.hasSliderControl()) {
        if (extension.hasSliderLabels()) {
            throw SerializationException(
                "Field '$linkId' has slider label extensions but no 'slider' itemControl extension",
            )
        }
        return null
    }
    val low = extension.stringValue(SLIDER_LOW_LABEL_URL)
    val high = extension.stringValue(SLIDER_HIGH_LABEL_URL)
    if (low.isNullOrBlank()) {
        throw SerializationException("Slider '$linkId' is missing a non-blank $SLIDER_LOW_LABEL_URL extension")
    }
    if (high.isNullOrBlank()) {
        throw SerializationException("Slider '$linkId' is missing a non-blank $SLIDER_HIGH_LABEL_URL extension")
    }
    return SliderSpec(lowLabel = low, highLabel = high)
}

// Slider extensions mean nothing on non-integer items: refuse them rather than silently ignore them.
private fun rejectSliderExtensions(
    typeName: String,
    linkId: String,
    extension: List<Extension>,
) {
    if (extension.hasSliderControl() || extension.hasSliderLabels()) {
        throw SerializationException(
            "$typeName '$linkId' uses slider extensions, which are only valid on integer items",
        )
    }
}

// Raw-JSON check, made before the item is decoded (so before any Extension objects exist).
private fun JsonObject.hasCounterExtension(): Boolean = (this["extension"] as? JsonArray).orEmpty().any {
    ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull == COUNTER_URL
}

/**
 * Chooses the FieldDef subclass from the item's FHIR `type`. An `integer` with the counter
 * extension is a CounterField; any other `integer` is an IntegerField. Decode only.
 */
object FieldDefSerializer : JsonContentPolymorphicSerializer<FieldDef>(FieldDef::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<FieldDef> {
        val item = element as? JsonObject ?: throw SerializationException("Questionnaire item must be a JSON object")
        return when (val type = (item["type"] as? JsonPrimitive)?.contentOrNull) {
            "integer" -> if (item.hasCounterExtension()) {
                FieldDef.CounterField.serializer()
            } else {
                FieldDef.IntegerField.serializer()
            }

            "decimal" -> FieldDef.DecimalField.serializer()

            "choice" -> FieldDef.ChoiceField.serializer()

            "text" -> FieldDef.TextField.serializer()

            "date" -> FieldDef.DateField.serializer()

            "period" -> FieldDef.PeriodField.serializer()

            "dateTime" -> throw SerializationException(
                "Item type 'dateTime' is not supported; a counter is an 'integer' item with the $COUNTER_URL extension",
            )

            else -> throw SerializationException("Unsupported item type '$type'")
        }
    }
}

/**
 * One questionnaire item, chosen by its FHIR `type` (and, for a counter, its extension).
 * Bad configuration throws [SerializationException] at parse time, so it fails on import.
 *
 * The subclass is chosen by [FieldDefSerializer], not by a class discriminator.
 *
 * Each subclass says how its answer is stored. A missing or null answer means unanswered/cleared
 * (except a counter, where it means zero).
 *
 * To add a type: add a subclass here, add it to FieldDefSerializer, then fix the compile errors.
 * (Every `when` over FieldDef should be exhaustive).
 */
@Serializable(with = FieldDefSerializer::class)
sealed class FieldDef {
    abstract val linkId: String
    abstract val text: String?
    abstract val extension: List<Extension>

    /** True if flagged `questionnaire-hidden`. */
    val hidden: Boolean
        get() = extension.any { it.url == HIDDEN_URL && it.valueBoolean == true }

    /**
     * `integer`: needs `minValue` and `maxValue` extensions.
     *
     * A slider if it also has `questionnaire-itemControl` = `slider` plus `slider-low-label` and
     * `slider-high-label` (both non-blank). A slider's min/max default to 0/100 and need min < max.
     * Answer: a JSON integer, either way.
     *
     * (An integer with the counter extension is a [CounterField], not this.)
     */
    @Serializable
    @SerialName("integer")
    data class IntegerField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        // Derived from `extension`; not serialised.
        @Transient
        val slider: SliderSpec? = sliderSpecOrNull(linkId, extension)

        // Required, except that a slider defaults to 0..100.
        val min: Int = extension.intValue(MIN_VALUE_URL)
            ?: if (extension.hasSliderControl()) {
                SLIDER_DEFAULT_MIN
            } else {
                throw SerializationException("IntegerField '$linkId' is missing required extension $MIN_VALUE_URL")
            }
        val max: Int = extension.intValue(MAX_VALUE_URL)
            ?: if (extension.hasSliderControl()) {
                SLIDER_DEFAULT_MAX
            } else {
                throw SerializationException("IntegerField '$linkId' is missing required extension $MAX_VALUE_URL")
            }

        init {
            if (slider != null && min >= max) {
                throw SerializationException(
                    "Slider '$linkId': minValue must be less than maxValue (got min=$min, max=$max)",
                )
            }
        }
    }

    /** `decimal`: needs `minValue` and `maxValue` extensions. Answer: a JSON number. */
    @Serializable
    @SerialName("decimal")
    data class DecimalField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        val min: Float = extension.floatValue(MIN_VALUE_URL)
            ?: throw SerializationException("DecimalField '$linkId' is missing required extension $MIN_VALUE_URL")
        val max: Float = extension.floatValue(MAX_VALUE_URL)
            ?: throw SerializationException("DecimalField '$linkId' is missing required extension $MAX_VALUE_URL")

        init {
            rejectSliderExtensions("DecimalField", linkId, extension)
            rejectCounterExtension("DecimalField", linkId, extension)
        }
    }

    /** `choice`: one `answerOption` per `valueCoding`. Answer: the chosen coding's `code`. */
    @Serializable
    @SerialName("choice")
    data class ChoiceField(
        override val linkId: String,
        override val text: String? = null,
        @SerialName("answerOption")
        val choices: List<Choice>,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        init {
            rejectSliderExtensions("ChoiceField", linkId, extension)
            rejectCounterExtension("ChoiceField", linkId, extension)
        }
    }

    /** `text`: free text. Answer: a JSON string. */
    @Serializable
    @SerialName("text")
    data class TextField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        init {
            rejectSliderExtensions("TextField", linkId, extension)
            rejectCounterExtension("TextField", linkId, extension)
        }
    }

    /** `date`: an ISO date. Answer: an ISO date string (no UI yet). */
    @Serializable
    @SerialName("date")
    data class DateField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        init {
            rejectSliderExtensions("DateField", linkId, extension)
            rejectCounterExtension("DateField", linkId, extension)
        }
    }

    /** `period`: a time of day. Answer: a string "HH:MM". */
    @Serializable
    @SerialName("period")
    data class PeriodField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        init {
            rejectSliderExtensions("PeriodField", linkId, extension)
            rejectCounterExtension("PeriodField", linkId, extension)
        }
    }

    /**
     * `integer` + the `questionnaire-counter` extension (valueBoolean true): a counter.
     * No min/max: it is 0 or more.
     *
     * The date part of each stored timestamp is ignored: see CounterAnswer.kt
     *
     * Stored answer: a JSON array of ISO-8601 offset date-times, one per press, oldest first. The count is the
     * array size. No events is an empty array; a missing key or null reads as the same.
     */
    @Serializable
    @SerialName("counter")
    data class CounterField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        init {
            val counter = extension.firstOrNull { it.url == COUNTER_URL }
                ?: throw SerializationException("CounterField '$linkId' needs the $COUNTER_URL extension")
            if (counter.valueBoolean != true) {
                throw SerializationException("Counter '$linkId': extension $COUNTER_URL must have valueBoolean true")
            }
            rejectSliderExtensions("CounterField", linkId, extension)
        }
    }
}

/** One `answerOption` entry, in FHIR's `valueCoding` shape. */
@Serializable
data class Choice(
    val valueCoding: Coding,
)

@Serializable
data class Coding(
    val system: String? = null,
    // FHIR codes are always strings, so this could probably be a plain String.
    val code: JsonPrimitive,
    val display: String? = null,
)
