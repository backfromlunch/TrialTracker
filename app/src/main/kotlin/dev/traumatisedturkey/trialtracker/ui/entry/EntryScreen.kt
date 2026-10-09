package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.traumatisedturkey.trialtracker.questionnaire.FieldDef
import dev.traumatisedturkey.trialtracker.ui.SettingsViewModel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

@Composable
fun EntryScreen(
    viewModel: EntryViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val selectedDate by viewModel.currentDate.collectAsState()
    val answers by viewModel.answers.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()

    // 'today' must be kept fresh in case the device a sits idle for a long time (e.g. overnight).
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                today = LocalDate.now()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Disable editing for 'before today' if lockHistory requests that.
    val isReadOnly = settings.lockHistory && selectedDate.isBefore(today)

    // Define the 'focus order' as a list of FocusRequester in questionnaire order.
    // This allows "Next" on fields to follows the questionnaire's declared order.
    val focusRequesters = remember(viewModel.questionnaire.itemGroup()) {
        List(viewModel.questionnaire.itemGroup().size) { FocusRequester() }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp)) {
        DateHeader(
            date = selectedDate,
            isToday = selectedDate == today,
            onPrevious = viewModel::goToPreviousDay,
            onNext = viewModel::goToNextDay,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isReadOnly) {
            // Graying the fields alone can look like a glitch or a loading state on first
            // encounter - this banner makes the reason explicit.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Past entries are locked for editing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Plain scrolling Column, not LazyColumn: the FocusRequester chain needs every field
        // to already be composed, regardless of scroll position, or requestFocus() on an
        // off-screen field silently does nothing.
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            viewModel.questionnaire.itemGroup().forEachIndexed { index, field ->
                FieldRow(
                    field = field,
                    answers = answers,
                    onAnswerChange = viewModel::updateAnswer,
                    focusRequester = focusRequesters[index],
                    nextFocusRequester = focusRequesters.getOrNull(index + 1),
                    isReadOnly = isReadOnly,
                )
            }
        }
    }
}

@Composable
private fun FieldRow(
    field: FieldDef,
    answers: JsonObject,
    onAnswerChange: (String, JsonElement) -> Unit,
    focusRequester: FocusRequester,
    nextFocusRequester: FocusRequester?,
    isReadOnly: Boolean,
) {
    // isReadOnly is deliberately handled here rather than inside each field renderer.

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isReadOnly) Modifier.alpha(0.55f) else Modifier),
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = fieldLabel(field), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                when (field) {
                    is FieldDef.IntegerField -> {
                        val current = answers[field.linkId]?.let { (it as? JsonPrimitive)?.intOrNull }
                        NumberTextField(
                            initialText = current?.toString() ?: "",
                            keyboardType = KeyboardType.Number,
                            allowDecimal = false,
                            min = field.min.toDouble(),
                            max = field.max.toDouble(),
                            focusRequester = focusRequester,
                            onNext = { nextFocusRequester?.requestFocus() },
                            onValueCommitted = { text ->
                                // allowing blanking of field
                                val newValue = if (text.isEmpty()) {
                                    JsonNull
                                } else {
                                    text.toIntOrNull()
                                        ?.let { JsonPrimitive(it) }
                                }
                                newValue?.let { onAnswerChange(field.linkId, it) }
                            },
                        )
                    }

                    is FieldDef.DecimalField -> {
                        val current = answers[field.linkId]?.let { (it as? JsonPrimitive)?.floatOrNull }
                        NumberTextField(
                            initialText = current?.toString() ?: "",
                            keyboardType = KeyboardType.Decimal,
                            allowDecimal = true,
                            min = field.min.toDouble(),
                            max = field.max.toDouble(),
                            focusRequester = focusRequester,
                            onNext = { nextFocusRequester?.requestFocus() },
                            onValueCommitted = { text ->
                                // allowing blanking of field
                                val newValue =
                                    if (text.isEmpty()) {
                                        JsonNull
                                    } else {
                                        text.toFloatOrNull()
                                            ?.let { JsonPrimitive(it) }
                                    }
                                newValue?.let { onAnswerChange(field.linkId, it) }
                            },
                        )
                    }

                    is FieldDef.TextField -> {
                        val current = answers[field.linkId]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
                        FreeTextField(
                            initialText = current,
                            focusRequester = focusRequester,
                            onNext = { nextFocusRequester?.requestFocus() },
                            onValueCommitted = { text ->
                                onAnswerChange(
                                    field.linkId,
                                    JsonPrimitive(text),
                                )
                            },
                        )
                    }

                    is FieldDef.ChoiceField -> {
                        // When focus lands on a 'choice' the collapsed button becomes selected.
                        // The user must tap/select to proceed into the choice.
                        val currentValue = answers[field.linkId] as? JsonPrimitive
                        // Confirm value *without* type. When the single live trial has finished
                        // and we can wipe the DB, then we can go back to value-and-type checking
                        // with it.valueCoding.code == currentValue.
                        val selectedChoice = field.choices.find { it.valueCoding.code.content == currentValue?.content }
                        ChoicePicker(
                            selectedChoice = selectedChoice,
                            choices = field.choices,
                            focusRequester = focusRequester,
                            onChoiceSelected = {
                                onAnswerChange(field.linkId, it)
                                nextFocusRequester?.requestFocus()
                            },
                        )
                    }

                    is FieldDef.PeriodField -> {
                        val current = answers[field.linkId]?.let { (it as? JsonPrimitive)?.contentOrNull }
                        PeriodPicker(
                            currentValue = current,
                            focusRequester = focusRequester,
                            onTimeSelected = { time ->
                                onAnswerChange(field.linkId, JsonPrimitive(time))
                                nextFocusRequester?.requestFocus()
                            },
                        )
                    }

                    else -> {
                        Text(
                            text = "(${field::class.simpleName} — not yet implemented)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (isReadOnly) {
                // Transparent scrim, same size as the card content: swallows taps (via a no-op
                // clickable) so the field underneath can't gain focus or be edited, without
                // needing any changes to the field renderers themselves.
                //
                // Trade-off: this also blocks long-press text selection/copy, since the scrim
                // consumes touch events before OutlinedTextField's own gesture detection sees
                // them.
                //
                // If copy is ever needed, swap the plain clickable for
                // detectTapGestures(onLongPress = { ...copy value to clipboard... }) with no
                // onTap, so short taps still do nothing but long-press triggers an explicit copy.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
        }
    }
}

internal fun fieldLabel(field: FieldDef): String {
    // Prefer 'text' if we have it
    val baseText = field.text ?: field.linkId
    // Numeric fields show their valid range too
    return when (field) {
        is FieldDef.IntegerField -> "$baseText [${field.min}-${field.max}]"
        is FieldDef.DecimalField -> "$baseText [${field.min}-${field.max}]"
        else -> baseText
    }
}
