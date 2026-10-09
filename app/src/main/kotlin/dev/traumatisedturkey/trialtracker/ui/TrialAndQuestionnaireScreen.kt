package dev.traumatisedturkey.trialtracker.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import dev.traumatisedturkey.trialtracker.BuildConfig
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import dev.traumatisedturkey.trialtracker.trialsetup.ImportFlow
import dev.traumatisedturkey.trialtracker.trialsetup.ImportFlowActions
import dev.traumatisedturkey.trialtracker.trialsetup.isBusy

/**
 * A Trial requires two artifacts configured: ServiceRequest and Questionnaire.
 * This module owns display and load of both.
 * Each is read-only once loaded and each showing its own "Load"/"Load, Replace" prompt while
 * it isn't (or is being replaced)
*/

private fun formatPeriod(period: Int, unit: String): String {
    // "1" + "d" -> "1 day". Falls back to the raw value + unit for anything not recognised,
    // so an unexpected unit still shows *something* rather than silently vanishing.
    val word = when (unit) {
        "d" -> "day"
        "w" -> "week"
        "mo" -> "month"
        else -> return "$period$unit"
    }
    return "$period $word" + if (period == 1) "" else "s"
}

/**
 * A single label/value fact within a section card, with a small leading icon.
 * Label is muted/secondary so the value (what the user actually scans for) carries more weight.
 */
@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportLoadingCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            CircularProgressIndicator()
            Text(label)
        }
    }
}

@Composable
private fun ImportFailedDialog(failed: ImportFlow.Failed, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import failed") },
        text = { Text(failed.message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun <T> ImportPendingConfirmDialog(
    pending: ImportFlow.PendingConfirm<T>,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: (ImportFlow.PendingConfirm<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        // No dismissal once a commit is underway - closing the dialog mid-write
        // would let the user reopen the picker while the previous import is still
        // being saved.
        onDismissRequest = { if (!pending.committing) onDismiss() },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pending) },
                enabled = !pending.committing,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !pending.committing,
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImportEmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    buttonLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message)
            Button(onClick = onClick, enabled = enabled) { Text(buttonLabel) }
        }
    }
}

@Composable
fun TrialAndQuestionnaireScreen(
    importedServiceRequest: ServiceRequest?,
    serviceRequestImport: ImportFlowActions<ServiceRequest>,
    importedQuestionnaire: Questionnaire?,
    questionnaireImport: ImportFlowActions<Questionnaire>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (serviceRequestImport.flow.isBusy) {
                ImportLoadingCard(Icons.AutoMirrored.Filled.Assignment, "Loading service request...")
            } else if (importedServiceRequest != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(importedServiceRequest.codeText, style = MaterialTheme.typography.titleMedium)
                        InfoRow(Icons.Filled.Business, "Organiser", importedServiceRequest.requesterDisplay)
                        InfoRow(Icons.Filled.Tag, "Trial ID", importedServiceRequest.serviceRequestValue)
                        InfoRow(
                            Icons.Filled.Repeat,
                            "Period",
                            formatPeriod(importedServiceRequest.period, importedServiceRequest.periodUnit),
                        )
                        InfoRow(
                            Icons.Filled.CalendarMonth,
                            "Dates",
                            importedServiceRequest.trialDateRange.display(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = serviceRequestImport.onPick) { Text("Replace") }
                        }
                    }
                }
            } else {
                // Single entry point for picking a service request file
                ImportEmptyCard(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    message = "No service request loaded yet.",
                    buttonLabel = "Load Service Request",
                    onClick = serviceRequestImport.onPick,
                )
            }
        }

        // Keep the 'Questionnaire' section greyed until we have got the service request.
        // Keep the buttons greyed out too.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (importedServiceRequest != null) 1f else 0.38f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (questionnaireImport.flow.isBusy) {
                    ImportLoadingCard(Icons.Filled.Description, "Loading questionnaire...")
                } else if (importedQuestionnaire != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(importedQuestionnaire.title, style = MaterialTheme.typography.titleMedium)
                            InfoRow(Icons.Filled.Business, "Publisher", importedQuestionnaire.publisher ?: "—")
                            InfoRow(Icons.Filled.Tag, "Version", importedQuestionnaire.version)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = questionnaireImport.onPick,
                                    enabled = importedServiceRequest != null,
                                ) { Text("Replace") }
                            }
                        }
                    }
                } else {
                    // Single entry point for picking a questionnaire file
                    ImportEmptyCard(
                        icon = Icons.Filled.Description,
                        message = "No questionnaire loaded yet.",
                        buttonLabel = "Load Questionnaire",
                        onClick = questionnaireImport.onPick,
                        enabled = importedServiceRequest != null,
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    InfoRow(Icons.Filled.Info, "App version", BuildConfig.GIT_VERSION)
                }
            }
        }
    }

    (questionnaireImport.flow as? ImportFlow.PendingConfirm<Questionnaire>)?.let { pending ->
        ImportPendingConfirmDialog(
            pending = pending,
            title = if (pending.isReplacing) "Replace questionnaire?" else "Load questionnaire?",
            body = if (pending.isReplacing) {
                "Replace the current questionnaire with '${pending.parsed.title}'? Existing " +
                    "daily entries are kept, but may not line up correctly if the new " +
                    "questionnaire's fields differ from the old one's (dev-only action, not " +
                    "guaranteed to be safe)."
            } else {
                "Load '${pending.parsed.title}' as your trial questionnaire?"
            },
            confirmLabel = if (pending.isReplacing) "Replace" else "Load",
            onConfirm = questionnaireImport.onConfirm,
            onDismiss = questionnaireImport.onDismissPendingConfirm,
        )
    }

    (questionnaireImport.flow as? ImportFlow.Failed)?.let { failed ->
        ImportFailedDialog(failed, onDismiss = questionnaireImport.onDismissError)
    }

    (serviceRequestImport.flow as? ImportFlow.PendingConfirm<ServiceRequest>)?.let { pending ->
        ImportPendingConfirmDialog(
            pending = pending,
            title = if (pending.isReplacing) "Replace service request?" else "Load service request?",
            body = if (pending.isReplacing) {
                "Replace the current service request with '${pending.parsed.codeText}'? Existing " +
                    "daily entries are unaffected."
            } else {
                "Load '${pending.parsed.codeText}' as your service request?"
            },
            confirmLabel = if (pending.isReplacing) "Replace" else "Load",
            onConfirm = serviceRequestImport.onConfirm,
            onDismiss = serviceRequestImport.onDismissPendingConfirm,
        )
    }

    (serviceRequestImport.flow as? ImportFlow.Failed)?.let { failed ->
        ImportFailedDialog(failed, onDismiss = serviceRequestImport.onDismissError)
    }
}
