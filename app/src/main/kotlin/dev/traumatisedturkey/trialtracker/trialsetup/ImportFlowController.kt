package dev.traumatisedturkey.trialtracker.trialsetup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns the state transitions shared by every import flow: Reading, Idle-on-cancel,
 * Failed-on-read-error, and the two dismiss actions.
 */
class ImportFlowController<T>(
    private val readFailedMessagePrefix: String,
) {
    var flow: ImportFlow<T> by mutableStateOf(ImportFlow.Idle)
        private set

    fun onImportStarted() {
        flow = ImportFlow.Reading
    }

    fun onImportCancelled() {
        flow = ImportFlow.Idle
    }

    fun onReadFailed(message: String?) {
        flow = ImportFlow.Failed("$readFailedMessagePrefix: $message")
    }

    fun dismissPendingImport() {
        flow = ImportFlow.Idle
    }

    fun dismissImportError() {
        flow = ImportFlow.Idle
    }

    // Called by each artifact's own onTextRead once parsing/validation finishes.
    fun showPendingConfirm(pending: ImportFlow.PendingConfirm<T>) {
        flow = pending
    }

    fun showFailed(message: String) {
        flow = ImportFlow.Failed(message)
    }

    // Called by each artifact's own confirm at the start and end of persisting.
    fun beginCommit(pending: ImportFlow.PendingConfirm<T>) {
        flow = pending.copy(committing = true)
    }

    fun finishCommit() {
        flow = ImportFlow.Idle
    }
}
