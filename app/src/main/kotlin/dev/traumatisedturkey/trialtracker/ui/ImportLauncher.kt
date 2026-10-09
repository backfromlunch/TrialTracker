package dev.traumatisedturkey.trialtracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Shared launcher for the "pick a JSON file -> read its text" step of an import flow.
 */
@Composable
fun rememberImportLauncher(
    onTextRead: (String) -> Unit,
    onReadFailed: (String?) -> Unit,
    onCancelled: () -> Unit,
): ActivityResultLauncher<Array<String>> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val text =
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not open the selected file")
                    onTextRead(text)
                } catch (e: Exception) {
                    onReadFailed(e.message)
                }
            }
        } else {
            onCancelled()
        }
    }
}
