package dev.traumatisedturkey.trialtracker.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.traumatisedturkey.trialtracker.data.LiveTrial
import dev.traumatisedturkey.trialtracker.data.TrialDatabase
import dev.traumatisedturkey.trialtracker.export.ExportFormat
import dev.traumatisedturkey.trialtracker.export.TrialExportCoordinator
import dev.traumatisedturkey.trialtracker.export.formatForFilename
import dev.traumatisedturkey.trialtracker.trialsetup.ImportFlowActions
import dev.traumatisedturkey.trialtracker.trialsetup.TrialSetupViewModel
import dev.traumatisedturkey.trialtracker.ui.entry.EntryScreen
import dev.traumatisedturkey.trialtracker.ui.entry.EntryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Screens
 *
 * Navigation-Compose is overkill for our navigation needs.
 * Instead just use a sealed class for screens and a 'when' to dispatch.
 */
sealed class Screen {
    object Entry : Screen()
    object Summary : Screen()
    object TrialAndQuestionnaire : Screen()
    object Help : Screen()
    object Settings : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialTrackerApp(
    db: TrialDatabase,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Viewmodels are retrieved in the same way as is done in MainActivity therefore they resolve
    // to the same instances.
    val loaderViewModel =
        viewModel<TrialSetupViewModel>(factory = TrialSetupViewModel.Factory(db.trialDao()))
    val trial = loaderViewModel.activeTrial
    val importedQuestionnaire = loaderViewModel.importedQuestionnaire
    val importedServiceRequest = loaderViewModel.importedServiceRequest
    val showConfigCompleteDialog = loaderViewModel.showConfigCompleteDialog

    // Export goes straight to the system's "Save" picker with a suggested "trial_results.json" name.
    // Format is inferred from whichever extension the user actually saves with. If they save with
    // neither ".json" nor ".csv", the created (empty) file is deleted and an error toast explains why.

    val exportScope = rememberCoroutineScope()
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("*/*"),
        ) { uri: Uri? ->
            if (uri != null) {
                writeExport(context, db, loaderViewModel.activeTrial, uri, exportScope)
            }
        }

    val questionnaireController = loaderViewModel.questionnaireImportController

    val questionnaireImportLauncher =
        rememberImportLauncher(
            onTextRead = loaderViewModel::onQuestionnaireTextRead,
            onReadFailed = questionnaireController::onReadFailed,
            onCancelled = questionnaireController::onImportCancelled,
        )

    val questionnaireImport =
        ImportFlowActions(
            flow = questionnaireController.flow,
            onPick = {
                questionnaireController.onImportStarted()
                questionnaireImportLauncher.launch(arrayOf("application/json"))
            },
            onConfirm = { loaderViewModel.confirmQuestionnaireImport(it) },
            onDismissPendingConfirm = questionnaireController::dismissPendingImport,
            onDismissError = questionnaireController::dismissImportError,
        )

    val serviceRequestController = loaderViewModel.serviceRequestImportController

    val serviceRequestImportLauncher =
        rememberImportLauncher(
            onTextRead = loaderViewModel::onServiceRequestTextRead,
            onReadFailed = serviceRequestController::onReadFailed,
            onCancelled = serviceRequestController::onImportCancelled,
        )

    val serviceRequestImport =
        ImportFlowActions(
            flow = serviceRequestController.flow,
            onPick = {
                serviceRequestController.onImportStarted()
                serviceRequestImportLauncher.launch(arrayOf("application/json"))
            },
            onConfirm = { loaderViewModel.confirmServiceRequestImport(it) },
            onDismissPendingConfirm = serviceRequestController::dismissPendingImport,
            onDismissError = serviceRequestController::dismissImportError,
        )

    // On a fresh install (no trial exists) land on TrialAndQuestionnaire.
    // On returning user (trial non-null) land on Entry.
    var currentScreen by remember { mutableStateOf<Screen>(if (trial != null) Screen.Entry else Screen.TrialAndQuestionnaire) }

    // IMPORTANT: ViewModelStore keys on a string. We build a 'composite key' string. This
    // must include the 'type' to distinguish different models.
    //
    // We currently (dev only) support servicerequest/questionnaire *replacement* so in several cases the
    // key involves both trial.id and also trial.importedAt (not very pretty). importedAt
    // is enough to distinguish a replace-questionnaire event from the previous trial state, and is
    // far cheaper to build a key string from.
    //
    // Storing in ViewModelStore -> survives recomposition (e.g. rotation, dark mode, etc).
    //
    // TO CONFIRM: Apparently this does NOT evict the old key's instance from the store; for this
    // app's single-trial, dev-only replace flow that's an acceptable leak, not a correctness issue.
    //
    // Both are null until a trial exists - the Entry/Summary drawer items no-op their taps until
    // then, so the null case is never actually rendered; it only has to be handled here so this
    // composable can be called at all before that point.
    val entryViewModel =
        trial?.let { liveTrial ->
            viewModel<EntryViewModel>(
                key = "entry:${liveTrial.id}:${liveTrial.importedAt}",
                factory = EntryViewModel.Factory(db.entryDao(), liveTrial.questionnaire, liveTrial.id),
            )
        }

    val summaryViewModel =
        trial?.let { liveTrial ->
            viewModel<SummaryViewModel>(
                key = "summary:${liveTrial.id}:${liveTrial.importedAt}",
                factory = SummaryViewModel.Factory(db.entryDao(), liveTrial.questionnaire, liveTrial.id, liveTrial.trialDateRange),
            )
        }

    // No key needed - one instance per activity.
    val settingsViewModel =
        viewModel<SettingsViewModel>(factory = SettingsViewModel.Factory(db.settingsDao()))

    // Only ever called from Summary, so the early return here is just defensive.
    fun openEntryForDate(date: LocalDate) {
        val liveTrial = trial ?: return
        val today = LocalDate.now()
        if (date > today || !liveTrial.trialDateRange.contains(date)) return

        entryViewModel?.goToDate(date)
        currentScreen = Screen.Entry
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            // System back (button or gesture) closes the drawer when open.
            BackHandler(enabled = drawerState.isOpen) {
                drawerScope.launch { drawerState.close() }
            }
            ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                // Back arrow to close the drawer.
                IconButton(onClick = { drawerScope.launch { drawerState.close() } }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close menu")
                }
                NavigationDrawerItem(
                    label = { Text("Daily Entry") },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    selected = currentScreen is Screen.Entry,
                    // NavigationDrawerItem (the non-TV Material3 one) has no `enabled` param, so
                    // "disabled while there's no trial yet" is faked here: dim to the standard
                    // Material disabled-content alpha, and no-op the tap instead of navigating.
                    modifier = Modifier.alpha(if (trial != null) 1f else 0.38f),
                    onClick = {
                        if (trial != null) {
                            currentScreen = Screen.Entry
                            drawerScope.launch { drawerState.close() }
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Summary") },
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    selected = currentScreen is Screen.Summary,
                    modifier = Modifier.alpha(if (trial != null) 1f else 0.38f),
                    onClick = {
                        if (trial != null) {
                            currentScreen = Screen.Summary
                            drawerScope.launch { drawerState.close() }
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Trial & Questionnaire") },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    selected = currentScreen is Screen.TrialAndQuestionnaire,
                    onClick = {
                        currentScreen = Screen.TrialAndQuestionnaire
                        drawerScope.launch { drawerState.close() }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Export results") },
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                    // Not tied to `currentScreen` - this is a one-off action, not a
                    // destination, so it's never shown as "selected".
                    selected = false,
                    modifier = Modifier.alpha(if (trial != null) 1f else 0.38f),
                    onClick = {
                        if (trial != null) {
                            Toast.makeText(
                                context,
                                "Save with a \".json\" or \".csv\" name to choose the export format",
                                Toast.LENGTH_LONG,
                            ).show()
                            exportLauncher.launch("trial_results.json")
                            drawerScope.launch { drawerState.close() }
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = currentScreen is Screen.Settings,
                    modifier = Modifier.alpha(if (trial != null) 1f else 0.38f),
                    onClick = {
                        if (trial != null) {
                            currentScreen = Screen.Settings
                            drawerScope.launch { drawerState.close() }
                        }
                    },
                )
                NavigationDrawerItem(
                    label = { Text("Help") },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                    selected = currentScreen is Screen.Help,
                    onClick = {
                        currentScreen = Screen.Help
                        drawerScope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(text = importedServiceRequest?.let { it.codeText } ?: "TrialTracker") },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentScreen) {
                    // entryViewModel/summaryViewModel are only null when trial is - and the
                    // drawer items above are disabled in that case, so these `?.let` blocks are
                    // just defensive.
                    is Screen.Entry -> entryViewModel?.let {
                        EntryScreen(viewModel = it, settingsViewModel = settingsViewModel)
                    }

                    is Screen.Summary -> summaryViewModel?.let {
                        SummaryScreen(viewModel = it, onDateSelected = ::openEntryForDate)
                    }

                    is Screen.TrialAndQuestionnaire -> TrialAndQuestionnaireScreen(
                        importedServiceRequest = importedServiceRequest,
                        serviceRequestImport = serviceRequestImport,
                        importedQuestionnaire = importedQuestionnaire,
                        questionnaireImport = questionnaireImport,
                    )

                    is Screen.Help -> HelpScreen()

                    is Screen.Settings -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    // One-shot: shown exactly when TrialSetupViewModel.showConfigCompleteDialog first flips true.
    // "Jump to Entry" navigates immediately.
    if (showConfigCompleteDialog) {
        AlertDialog(
            onDismissRequest = loaderViewModel::dismissConfigCompleteDialog,
            title = { Text("Congratulations!") },
            text = { Text("You have now finished configuration. Let's get started.") },
            confirmButton = {
                TextButton(onClick = {
                    currentScreen = Screen.Entry
                    loaderViewModel.dismissConfigCompleteDialog()
                }) { Text("Jump to Entry") }
            },
        )
    }
}

private fun writeExport(
    context: Context,
    db: TrialDatabase,
    trial: LiveTrial?,
    uri: Uri,
    scope: CoroutineScope,
) {
    val liveTrial = trial ?: return
    scope.launch {
        try {
            val name =
                withContext(Dispatchers.IO) { queryDisplayName(context, uri) }
            val format =
                name?.let(::formatForFilename)
                    ?: run {
                        // Best-effort cleanup of the empty file the picker already created - a
                        // failure here shouldn't hide the "wrong extension" message below, which
                        // is the one the user actually needs to see.
                        withContext(Dispatchers.IO) {
                            try {
                                DocumentsContract.deleteDocument(context.contentResolver, uri)
                            } catch (deleteError: Exception) {
                                // Ignored - see comment above.
                            }
                        }
                        error("Filename must end in \".json\" or \".csv\" - nothing was saved.")
                    }
            val content =
                when (format) {
                    ExportFormat.CSV -> TrialExportCoordinator.buildCsvExport(db, liveTrial)
                    ExportFormat.JSON -> TrialExportCoordinator.buildJsonExport(db, liveTrial)
                }
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: error("Could not open the destination file for writing")
            Toast.makeText(context, "Export saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = context.contentResolver
    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }
