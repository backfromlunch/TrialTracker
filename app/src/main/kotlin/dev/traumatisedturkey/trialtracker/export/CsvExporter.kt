package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.fields.FieldDef
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.questionnaire.counterTimestamps
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val json = Json { ignoreUnknownKeys = true }
    private val authoredFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun buildCsv(
        questionnaire: Questionnaire,
        serviceRequest: ServiceRequest,
        entries: List<Entry>,
        authored: OffsetDateTime,
    ): String {
        val fields = questionnaire.itemGroup()
        val fieldIds = fields.map { it.linkId }
        val titleComments = listOf(
            "# questionnaire: ${questionnaire.questionnaireCanonical()}",
            "# serviceRequest: ${serviceRequest.serviceRequestValue}",
            "# authored: ${authored.format(authoredFormatter)}",
        )
        val header = (listOf("date") + fieldIds).joinToString(",") { escapeCell(it) }

        val rows =
            entries.map { entry ->
                val answers =
                    try {
                        json.decodeFromString(JsonObject.serializer(), entry.answersJson)
                    } catch (e: Exception) {
                        JsonObject(emptyMap())
                    }
                val cells =
                    listOf(entry.date) +
                        fields.map { field -> cellValue(field, answers[field.linkId]) }
                cells.joinToString(",") { escapeCell(it) }
            }

        return (titleComments + listOf(header) + rows).joinToString("\n")
    }

    private fun cellValue(
        field: FieldDef,
        answer: JsonElement?,
    ): String = when (field) {
        // See CounterAnswer.kt: CSV exports only the count. No events (missing, null or []) is 0.
        is FieldDef.CounterField -> answer.counterTimestamps().size.toString()

        // JsonNull is a JsonPrimitive whose content is the string "null", so it must be
        // excluded explicitly: a blanked/cleared answer is an empty cell.
        else -> (answer as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: ""
    }

    private fun escapeCell(value: String): String {
        val needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
        return if (needsQuoting) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
