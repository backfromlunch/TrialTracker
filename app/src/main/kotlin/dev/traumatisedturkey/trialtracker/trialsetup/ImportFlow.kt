package dev.traumatisedturkey.trialtracker.trialsetup

/**
 * State machine for a "pick a file -> read & parse -> confirm -> persist" import flow.
 * Generic over T so useable over ServiceRequest and Questionnaire.
*/
sealed interface ImportFlow<out T> {
    // Nothing in progress: no file picked, no pending confirmation, no error showing.
    data object Idle : ImportFlow<Nothing>

    // A file was picked and its bytes are being read + parsed.
    data object Reading : ImportFlow<Nothing>

    // Parsed successfully; awaiting user confirmation before it's persisted.
    data class PendingConfirm<T>(
        val rawText: String,
        val parsed: T,
        val isReplacing: Boolean,
        val committing: Boolean = false,
    ) : ImportFlow<T>

    // Read, parse, or validation failure. Dismissing this should return the flow to [Idle].
    data class Failed(val message: String) : ImportFlow<Nothing>
}

/**
 * True while a picked file's bytes are being read/parsed, or while a confirmed import is being
 * persisted.
*/
val ImportFlow<*>.isBusy: Boolean
    get() = this is ImportFlow.Reading || (this as? ImportFlow.PendingConfirm<*>)?.committing == true
