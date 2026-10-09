package dev.traumatisedturkey.trialtracker.questionnaire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deliberately not a full FHIR-general "value[x]" polymorphic extension.
 * Only recognises the handful of extension shapes this app actually consumes.
 */
@Serializable
data class Extension(
    val url: String,
    val valueBoolean: Boolean? = null,
    val valueInteger: Int? = null,
    val valueDecimal: Float? = null,
)

private const val HIDDEN_URL = "http://hl7.org/fhir/StructureDefinition/questionnaire-hidden"
private const val MIN_VALUE_URL = "http://hl7.org/fhir/StructureDefinition/minValue"
private const val MAX_VALUE_URL = "http://hl7.org/fhir/StructureDefinition/maxValue"

private fun List<Extension>.intValue(url: String): Int? = firstOrNull { it.url == url }?.valueInteger
private fun List<Extension>.floatValue(url: String): Float? = firstOrNull { it.url == url }?.valueDecimal

@Serializable
sealed class FieldDef {
    abstract val linkId: String
    abstract val text: String?
    abstract val extension: List<Extension>

    val hidden: Boolean
        get() = extension.any { it.url == HIDDEN_URL && it.valueBoolean == true }

    @Serializable
    @SerialName("integer")
    data class IntegerField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef() {
        val min: Int = extension.intValue(MIN_VALUE_URL)
            ?: throw SerializationException("IntegerField '$linkId' is missing required extension $MIN_VALUE_URL")
        val max: Int = extension.intValue(MAX_VALUE_URL)
            ?: throw SerializationException("IntegerField '$linkId' is missing required extension $MAX_VALUE_URL")
    }

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
    }

    @Serializable
    @SerialName("choice")
    data class ChoiceField(
        override val linkId: String,
        override val text: String? = null,
        @SerialName("answerOption")
        val choices: List<Choice>,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef()

    @Serializable
    @SerialName("text")
    data class TextField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef()

    @Serializable
    @SerialName("date")
    data class DateField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef()

    @Serializable
    @SerialName("period")
    data class PeriodField(
        override val linkId: String,
        override val text: String? = null,
        override val extension: List<Extension> = emptyList(),
    ) : FieldDef()
}

// A single answerOption entry, in FHIR's valueCoding shape.
@Serializable
data class Choice(
    val valueCoding: Coding,
)

@Serializable
data class Coding(
    val system: String? = null,
    // JsonPrimitive (not String): real FHIR's Coding.code is always string-typed, so this will
    // always decode as a string primitive in practice.
    // Could possibly be simplified to String?
    val code: JsonPrimitive,
    val display: String,
)
