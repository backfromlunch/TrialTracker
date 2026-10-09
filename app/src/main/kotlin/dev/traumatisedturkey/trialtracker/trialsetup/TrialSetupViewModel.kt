package dev.traumatisedturkey.trialtracker.trialsetup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.traumatisedturkey.trialtracker.data.LiveTrial
import dev.traumatisedturkey.trialtracker.data.TrialDao
import dev.traumatisedturkey.trialtracker.data.TrialRecord
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.questionnaire.parseQuestionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import dev.traumatisedturkey.trialtracker.servicerequest.parseServiceRequest
import kotlinx.coroutines.launch

/**
 * Owns "what trial do we have, and how do we get one".
 * Should be unit testable without an Activity.
 */
class TrialSetupViewModel(
    private val trialDao: TrialDao,
) : ViewModel() {
    // Distinguishes "DB not yet checked" from "DB checked, and no Trial row".
    var startupCheckComplete: Boolean by mutableStateOf(false)
        private set

    // ARCHITECTURE VS IMPLEMENTATION: Trial/Entry's schema supports any number of trials, but the
    // app currently only tracks a single "active" one. (FUTURE: multi-trial list and switcher UI).
    // switcher UI yet.
    //
    // Non-null now means "questionnaire AND service request are both in".
    var activeTrial: LiveTrial? by mutableStateOf(null)
        private set

    // Owns the questionnaire import state machine.
    val questionnaireImportController = ImportFlowController<Questionnaire>(
        readFailedMessagePrefix = "Could not read that file as a questionnaire",
    )

    // Owns the service-request import state machine.
    val serviceRequestImportController = ImportFlowController<ServiceRequest>(
        readFailedMessagePrefix = "Could not read that file as a service request",
    )

    // The persisted-and-parsed Questionnaire, if one has been imported.
    var importedQuestionnaire: Questionnaire? by mutableStateOf(null)
        private set

    // The persisted-and-parsed ServiceRequest, if one has been imported.
    var importedServiceRequest: ServiceRequest? by mutableStateOf(null)
        private set

    // True exactly once: the moment activeTrial first goes from null to non-null.
    // Never re-set on a later replace of either artifact, since activeTrial can't go back to null once set.
    var showConfigCompleteDialog: Boolean by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // A clean install has zero Trial rows: the caller is expected to prompt for a real ServiceRequest and Questionnaire files.
            val record = trialDao.getActiveTrial()
            importedQuestionnaire = record?.questionnaireJson?.let { parseQuestionnaire(it) }
            importedServiceRequest = record?.serviceRequestJson?.let { parseServiceRequest(it) }
            activeTrial = record?.let { LiveTrial.from(it) }
            startupCheckComplete = true
        }
    }

    /**
     * Parses and validates a questionnaire's raw JSON text once the caller has read it from disk.
     *
     * Order enforcement lives here: a Questionnaire can't be imported before a ServiceRequest exists.
     * This is a stepping stone towards validating a Questionnaire against its ServiceRequest (FUTURE work).
     */
    fun onQuestionnaireTextRead(text: String) {
        if (importedServiceRequest == null) {
            questionnaireImportController.showFailed(
                "A service request must be loaded before a questionnaire can be imported.",
            )
            return
        }
        try {
            when (val result = parseAndValidateQuestionnaire(text, importedQuestionnaire)) {
                is QuestionnaireImportResult.Incompatible ->
                    questionnaireImportController.showFailed(
                        "The new questionnaire isn't compatible with the current one:\n" +
                            result.reasons.joinToString("\n") { "• $it" },
                    )

                is QuestionnaireImportResult.Success ->
                    questionnaireImportController.showPendingConfirm(
                        ImportFlow.PendingConfirm(
                            rawText = text,
                            parsed = result.questionnaire,
                            isReplacing = importedQuestionnaire != null,
                        ),
                    )
            }
        } catch (e: Exception) {
            questionnaireImportController.showFailed("Could not read that file as a questionnaire: ${e.message}")
        }
    }

    fun dismissConfigCompleteDialog() {
        showConfigCompleteDialog = false
    }

    /**
     * Always upserts into the one singleton row, so existing Entry rows (FK'd to that id)
     * stay attached across replacements automatically.
     */
    fun confirmQuestionnaireImport(pending: ImportFlow.PendingConfirm<Questionnaire>) {
        questionnaireImportController.beginCommit(pending)
        viewModelScope.launch {
            val wasTrialNull = activeTrial == null
            val record =
                TrialRecord(
                    id = TrialRecord.ACTIVE_ID,
                    questionnaireJson = pending.rawText,
                    serviceRequestJson = trialDao.getActiveTrial()?.serviceRequestJson,
                    importedAt = System.currentTimeMillis(),
                )
            trialDao.upsert(record)
            importedQuestionnaire = pending.parsed
            // This is the point where activeTrial can first go from null to non-null on a fresh install.
            // onQuestionnaireTextRead's guard means a questionnaire is never imported before a ServiceRequest,
            // so questionnaire is the second (completing) artifact on first-time setup.
            activeTrial = LiveTrial.from(record)
            // Fires the one-shot "configuration complete" dialog exactly on that null->non-null transition.
            // activeTrial can't go back to null once set, so it's a single firing.
            if (wasTrialNull && activeTrial != null) {
                showConfigCompleteDialog = true
            }
            questionnaireImportController.finishCommit()
        }
    }

    fun onServiceRequestTextRead(text: String) {
        // Unlike the questionnaire flow, no guard here since a ServiceRequest can always be imported,
        // whether or not a questionnaire exists yet.
        try {
            serviceRequestImportController.showPendingConfirm(
                ImportFlow.PendingConfirm(
                    rawText = text,
                    parsed = parseServiceRequest(text),
                    isReplacing = importedServiceRequest != null,
                ),
            )
        } catch (e: Exception) {
            serviceRequestImportController.showFailed("Could not read that file as a service request: ${e.message}")
        }
    }

    /**
     * confirmServiceRequestImport
     * Re-reads the current row so it's carried over unchanged when present.
     * If no row exists yet, creates one, with questionnaireJson left null until a questionnaire is imported.
     */
    fun confirmServiceRequestImport(pending: ImportFlow.PendingConfirm<ServiceRequest>) {
        serviceRequestImportController.beginCommit(pending)
        viewModelScope.launch {
            val current = trialDao.getActiveTrial()
            val record =
                current?.copy(serviceRequestJson = pending.rawText)
                    ?: TrialRecord(
                        id = TrialRecord.ACTIVE_ID,
                        questionnaireJson = null,
                        serviceRequestJson = pending.rawText,
                        importedAt = System.currentTimeMillis(),
                    )
            trialDao.upsert(record)
            importedServiceRequest = pending.parsed
            // NOTE: activeTrial can NOT go null->non-null here.
            activeTrial = LiveTrial.from(record)
            serviceRequestImportController.finishCommit()
        }
    }

    class Factory(
        private val trialDao: TrialDao,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrialSetupViewModel(trialDao) as T
    }
}
