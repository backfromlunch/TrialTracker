package dev.traumatisedturkey.trialtracker.questionnaire

import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses a Questionnaire from a raw Questionnaire JSON string, whatever its origin:
 * an imported file, or a Trial row's stored questionnaire Json column.
 *
 * Decodes into FhirQuestionnaireDto first, then flattens via toQuestionnaire().
 */
fun parseQuestionnaire(questionnaireJson: String): Questionnaire = json.decodeFromString<FhirQuestionnaireDto>(questionnaireJson).toQuestionnaire()
