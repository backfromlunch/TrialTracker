package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

/**
 * All text fields are single-line by design.
 * All changes are handled as immediate commit to storage
 */

@Composable
internal fun NumberTextField(
    initialText: String,
    keyboardType: KeyboardType,
    allowDecimal: Boolean,
    min: Double,
    max: Double,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    onValueCommitted: (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val isEmpty = text.isEmpty()
    val isValid = NumericInputFilter.isValid(text, min, max)

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = NumericInputFilter.filter(raw, allowDecimal)
            text = filtered
            if (filtered.isEmpty() || NumericInputFilter.isValid(filtered, min, max)) {
                onValueCommitted(filtered)
            }
        },
        singleLine = true,
        isError = !isEmpty && !isValid,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = if (isEmpty || isValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                } else if (!isEmpty && !isValid) {
                    text = initialText
                }
            },
    )
}

@Composable
internal fun FreeTextField(
    initialText: String,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    onValueCommitted: (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueCommitted(it)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
    )
}
