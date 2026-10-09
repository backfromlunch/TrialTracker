package dev.traumatisedturkey.trialtracker.data

import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.questionnaire.parseQuestionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import dev.traumatisedturkey.trialtracker.servicerequest.parseServiceRequest

/**
 * Represents the live, running trial.
 *
 * Built once (at startup, or whenever a ServiceRequest or Questionnaire is imported/replaced); then handed out.
 *
 * This consists principally of the ServiceRequest and Questionnaire, but also has some derived columns.
 */
data class LiveTrial(
    val id: String,
    val importedAt: Long,
    val questionnaire: Questionnaire,
    val serviceRequest: ServiceRequest,
) {
    // From the questionnaire's "title" field.
    val title: String get() = questionnaire.title

    // Org-declared id from the questionnaire; only unique *within* that org's own questionnaires.
    val questionnaireId: String? get() = questionnaire.id

    // Org-declared version from the questionnaire, if present.
    val questionnaireVersion: String? get() = questionnaire.version

    // Sourced from the ServiceRequest.
    val trialDateRange: TrialDateRange get() = serviceRequest.trialDateRange

    companion object {
        // Returns null unless BOTH serviceRequestJson and questionnaireJson are present on the row.
        fun from(record: TrialRecord): LiveTrial? {
            val questionnaireJson = record.questionnaireJson ?: return null
            val serviceRequestJson = record.serviceRequestJson ?: return null
            return LiveTrial(
                id = record.id,
                importedAt = record.importedAt,
                questionnaire = parseQuestionnaire(questionnaireJson),
                serviceRequest = parseServiceRequest(serviceRequestJson),
            )
        }
    }
}
