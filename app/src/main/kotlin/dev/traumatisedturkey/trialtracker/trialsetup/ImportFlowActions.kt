package dev.traumatisedturkey.trialtracker.trialsetup

/**
 * Bundles one artifact's import-related UI wiring - the ImportFlow<T> state itself, plus the
 * four callbacks that drive it (pick/confirm/dismiss-pending/dismiss-error).
 */
data class ImportFlowActions<T>(
    val flow: ImportFlow<T>,
    val onPick: () -> Unit,
    val onConfirm: (ImportFlow.PendingConfirm<T>) -> Unit,
    val onDismissPendingConfirm: () -> Unit,
    val onDismissError: () -> Unit,
)
