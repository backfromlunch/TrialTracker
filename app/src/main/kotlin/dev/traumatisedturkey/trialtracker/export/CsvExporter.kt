package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import kotlinx.serialization.json.Json
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
        val fieldIds = questionnaire.itemGroup().map { it.linkId }
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
                        fieldIds.map { id ->
                            (answers[id] as? JsonPrimitive)?.content ?: ""
                        }
                cells.joinToString(",") { escapeCell(it) }
            }

        return (titleComments + listOf(header) + rows).joinToString("\n")
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
