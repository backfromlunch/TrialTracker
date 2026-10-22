package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.traumatisedturkey.trialtracker.questionnaire.counterTimeOfDay
import kotlinx.coroutines.launch

private val BUTTON_SIZE = 56.dp // large touch targets; also the fixed width of the "-" slot

/**
 * For a counter field (see FieldDef.CounterField).
 *
 * Layout: [ - ]  count / "Last: HH:mm"  [ + ]. "+" appends the current time. "-" removes the most
 * recent one and is only shown while the count is above zero, in a fixed-size slot so it appearing
 * doesn't shift anything. The second line is blank at zero but still occupies a line, so the card doesn't
 * change height on the first press.
 *
 * Does NOT advance focus on press (unlike the pickers): the user may press many times.
 * The "last" time is shown as recorded, in the offset it was recorded with.
 */
@Composable
internal fun CounterInput(
    timestamps: List<String>,
    focusRequester: FocusRequester,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val count = timestamps.size
    val interactionSource = remember { MutableInteractionSource() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Taking focus dismisses any open text-field keyboard (as SliderInput does), but it is only a
    // nicety, so any failure is swallowed rather than allowed to break the press.
    val takeFocus: () -> Unit = { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(BUTTON_SIZE), contentAlignment = Alignment.Center) {
            if (count > 0) {
                FilledTonalButton(
                    onClick = {
                        takeFocus()
                        onDecrement()
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(BUTTON_SIZE),
                ) {
                    Text(
                        text = "\u2212",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { contentDescription = "Remove the most recent" },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                // Zero events: a blank line (not nothing) keeps the card height fixed.
                text = if (count == 0) " " else counterTimeOfDay(timestamps.last())?.let { "Last: $it" } ?: "\u2014",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // As in ChoicePicker: a Material3 button needs an explicit .focusable() alongside
        // .focusRequester() to reliably take part in the externally-driven focus chain.
        Button(
            onClick = {
                takeFocus()
                onIncrement()
            },
            interactionSource = interactionSource,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(BUTTON_SIZE)
                .focusRequester(focusRequester)
                .focusable(interactionSource = interactionSource)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                },
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { contentDescription = "Add one now" },
            )
        }
    }
}
