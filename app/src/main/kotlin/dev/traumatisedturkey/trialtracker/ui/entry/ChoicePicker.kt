package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.traumatisedturkey.trialtracker.questionnaire.Choice
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * For 'choice'
 * Collapsed view shows just the selected label, full-width, large tap target.
 * Bottom sheet opens with a scrollable vertical list
 * - handles 7–9 options with long labels fine
 * - text wraps naturally within the Row - not constrained to single line.
 * - Tapping anywhere in the row (not just the radio dot) selects and immediately closes the sheet + autosaves
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoicePicker(
    selectedChoice: Choice?,
    choices: List<Choice>,
    focusRequester: FocusRequester,
    onChoiceSelected: (JsonPrimitive) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Note: OutlinedButton needed an explicit .focusable() alongside .focusRequester() — Material3
    // buttons don't reliably expose their internal focus target to an externally-attached
    // FocusRequester the way OutlinedTextField does.
    OutlinedButton(
        onClick = { sheetOpen = true },
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
        Text(text = selectedChoice?.displayText() ?: "Tap to select", style = MaterialTheme.typography.bodyLarge)
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
        ) {
            ChoiceList(
                choices = choices,
                selectedValue = selectedChoice?.valueCoding?.code,
                onChoiceSelected = {
                    onChoiceSelected(it)
                    sheetOpen = false
                },
            )
        }
    }
}

@Composable
private fun ChoiceList(
    choices: List<Choice>,
    selectedValue: JsonPrimitive?,
    onChoiceSelected: (JsonPrimitive) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(choices) { choice ->
            val isSelected = choice.valueCoding.code == selectedValue
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onChoiceSelected(choice.valueCoding.code) },
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = { onChoiceSelected(choice.valueCoding.code) })
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = choice.displayText(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// "<code> - <display>" so the raw code being saved to the DB is visible alongside its prompt text.
// .content gives the unquoted string form of the JsonPrimitive code.
internal fun Choice.displayText(): String = "${valueCoding.code.content} - ${valueCoding.display}"
