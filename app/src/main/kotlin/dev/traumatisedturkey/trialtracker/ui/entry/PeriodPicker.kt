package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * For 'period' (HH:MM, 24h clock, full day 00:00-23:59 is the valid range).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodPicker(
    currentValue: String?, // "HH:MM" or null if unanswered
    focusRequester: FocusRequester,
    onTimeSelected: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    OutlinedButton(
        onClick = { dialogOpen = true },
        interactionSource = interactionSource,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
    ) {
        Text(text = currentValue ?: "Tap to select", style = MaterialTheme.typography.bodyLarge)
    }

    if (dialogOpen) {
        val (initialHour, initialMinute) = parseHourMinute(currentValue)
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )

        Dialog(onDismissRequest = { dialogOpen = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { dialogOpen = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                onTimeSelected("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                                dialogOpen = false
                            },
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Defaults to midnight (00:00) if unanswered or malformed.
 */
private fun parseHourMinute(value: String?): Pair<Int, Int> {
    val parts = value?.split(":")
    val hour = parts?.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: 0
    val minute = parts?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: 0
    return hour to minute
}
