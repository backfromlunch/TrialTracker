package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.questionnaire.FieldDef
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The statically-known outer envelope of a QuestionnaireResponse.
 * `item` is left as a raw JsonObject list and built dynamically.
 */
@Serializable
data class QuestionnaireResponseDto(
    val resourceType: String = "QuestionnaireResponse",
    val id: String,
    val text: Text,
    val questionnaire: String,
    val status: String = "completed",
    val basedOn: List<BasedOn>,
    val authored: String,
    val item: List<JsonObject>,
) {
    @Serializable
    data class Text(
        val status: String = "generated",
        val div: String = "<div xmlns=\"http://w3.org\"><p>Form response data details contained in structured fields.</p></div>",
    )

    @Serializable
    data class BasedOn(
        val identifier: Identifier,
    )

    @Serializable
    data class Identifier(
        val system: String,
        val value: String,
    )
}

object JsonExporter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val authoredFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    // Fallback only: used when a choice's answerOption.valueCoding omits `system` (older/malformed
    // questionnaire data). Real questionnaires provide their own system per choice.
    private const val FALLBACK_CODE_SYSTEM_BASE = "https://your-domain.example/fhir/CodeSystem"

    fun buildJson(
        questionnaire: Questionnaire,
        serviceRequest: ServiceRequest,
        entries: List<Entry>,
        authored: OffsetDateTime,
    ): String {
        val dto =
            QuestionnaireResponseDto(
                // KNOWN CAVEAT: per spec, `id` is the study title verbatim. FHIR's `id` element only
                // allows [A-Za-z0-9\-\.]{1,64}. Not sanitized here since the title is used as-is
                // elsewhere. Perhaps revisit.
                id = questionnaire.id,
                text = QuestionnaireResponseDto.Text(),
                questionnaire = questionnaire.questionnaireCanonical(),
                // where the service request came from
                basedOn =
                listOf(
                    QuestionnaireResponseDto.BasedOn(
                        QuestionnaireResponseDto.Identifier(
                            system = serviceRequest.serviceRequestSystem,
                            value = serviceRequest.serviceRequestValue,
                        ),
                    ),
                ),
                authored = authored.format(authoredFormatter),
                item = entries.sortedBy { it.date }.map { entry -> buildDiaryEntryItem(questionnaire, entry) },
            )

        return json.encodeToString(QuestionnaireResponseDto.serializer(), dto)
    }

    private fun buildDiaryEntryItem(
        questionnaire: Questionnaire,
        entry: Entry,
    ): JsonObject {
        val answers =
            try {
                json.decodeFromString(JsonObject.serializer(), entry.answersJson)
            } catch (e: Exception) {
                JsonObject(emptyMap())
            }

        return buildJsonObject {
            put("linkId", "DiaryEntry")

            put(
                "item",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("linkId", "Date")
                            put(
                                "answer",
                                buildJsonArray {
                                    add(buildJsonObject { put("valueDate", entry.date) })
                                },
                            )
                        },
                    )

                    questionnaire.itemGroup().forEach { field ->
                        val rawAnswer = answers[field.linkId]
                        val valueObject = fieldAnswerValue(field, rawAnswer as? JsonPrimitive)
                        if (valueObject != null) {
                            add(
                                buildJsonObject {
                                    put("linkId", field.linkId)
                                    put("answer", buildJsonArray { add(valueObject) })
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    /**
     * Returns null when there's no usable answer - either the key is missing, or it was explicitly blanked to JsonNull.
     */
    private fun fieldAnswerValue(
        field: FieldDef,
        answer: JsonPrimitive?,
    ): JsonObject? {
        if (answer == null || (answer.isString && answer.content.isEmpty())) return null

        return when (field) {
            is FieldDef.IntegerField -> {
                val value = answer.content.toIntOrNull() ?: return null
                buildJsonObject { put("valueInteger", value) }
            }

            is FieldDef.DecimalField -> {
                val value = answer.content.toDoubleOrNull() ?: return null
                buildJsonObject { put("valueDecimal", value) }
            }

            is FieldDef.TextField -> {
                buildJsonObject { put("valueString", answer.content) }
            }

            is FieldDef.DateField -> {
                buildJsonObject { put("valueDate", answer.content) }
            }

            is FieldDef.PeriodField -> {
                // Stored as "HH:MM"; FHIR's `time` type requires seconds.
                buildJsonObject { put("valueTime", "${answer.content}:00") }
            }

            is FieldDef.ChoiceField -> {
                // TODO
                // Currently confirm value *without* type.
                // When the single live trial has finished and we can wipe the DB of legacy values,
                // then this line can be strengthened to value-and-type checking with:
                // it.valueCoding.code == answer
                val choice = field.choices.find { it.valueCoding.code.content == answer.content } ?: return null
                val system = choice.valueCoding.system ?: "$FALLBACK_CODE_SYSTEM_BASE/${field.linkId}"
                buildJsonObject {
                    put(
                        "valueCoding",
                        buildJsonObject {
                            put("system", system)
                            put("code", choice.valueCoding.code.content)
                            put("display", choice.valueCoding.display)
                        },
                    )
                }
            }
        }
    }
}
