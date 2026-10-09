package dev.traumatisedturkey.trialtracker.trialsetup

import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.questionnaire.QuestionnaireCompatibility
import dev.traumatisedturkey.trialtracker.questionnaire.checkSuperset
import dev.traumatisedturkey.trialtracker.questionnaire.parseQuestionnaire

/**
 * Candidate / Existing Questionnaire compatibility result.
 */
sealed interface QuestionnaireImportResult {
    data class Success(val questionnaire: Questionnaire) : QuestionnaireImportResult

    data class Incompatible(val reasons: List<String>) : QuestionnaireImportResult
}

/**
 * Parse a candidate Questionnaire and determine compability with existing questionnaire.
 * Throws if `rawJson` doesn't parse as a valid questionnaire.
 */
fun parseAndValidateQuestionnaire(rawJson: String, existingQuestionnaire: Questionnaire?): QuestionnaireImportResult {
    val candidateQuestionnaire = parseQuestionnaire(rawJson)

    // Only checked on replace (an existing Trial is being overwritten) - the very first
    // questionnaire load has nothing to be a superset of.
    if (existingQuestionnaire != null) {
        when (val result = checkSuperset(existingQuestionnaire, candidateQuestionnaire)) {
            is QuestionnaireCompatibility.Incompatible -> return QuestionnaireImportResult.Incompatible(result.reasons)
            QuestionnaireCompatibility.Compatible -> Unit
        }
    }

    return QuestionnaireImportResult.Success(candidateQuestionnaire)
}
