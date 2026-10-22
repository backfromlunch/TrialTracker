package dev.traumatisedturkey.trialtracker.ui.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Look-and-feel knobs.
private val TOUCH_HEIGHT = 48.dp // height of the whole touchable area
private val TRACK_THICKNESS = 6.dp
private val THUMB_RADIUS = 11.dp // also the horizontal inset of the track, so the thumb never clips
private val CLEAR_SLOT_WIDTH = 72.dp // fixed, so Clear appearing doesn't re-wrap the end labels
private val FOCUS_RING_CORNER = 8.dp

/**
 * For an 'integer' field declared as a slider.
 *
 * Deliberately shows NO number, and, while unanswered, NO thumb and no progress fill, so the
 * starting position can't bias the answer. The thumb appears at the touched position on the
 * first tap or horizontal drag. A small "Clear" button (shown only once a value exists) returns
 * the field to unanswered.
 *
 * Hand-rolled rather than Material3's Slider, which always draws a thumb at some value.
 * Only a completed tap or a horizontal drag sets a value, so a vertical scroll that happens to
 * start on the slider does not.
 *
 * Does NOT advance focus on change (unlike the pickers): a drag produces many changes.
 */
@Composable
internal fun SliderInput(
    lowLabel: String,
    highLabel: String,
    min: Int,
    max: Int,
    currentValue: Int?, // null if unanswered or cleared
    focusRequester: FocusRequester,
    onValueChange: (Int?) -> Unit, // null means "cleared"
) {
    val isSet = currentValue != null
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }

    // The pointerInput block below only restarts when min/max change, so it must read the
    // *latest* callback and value rather than the ones captured when it started.
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(currentValue)
    val emit: (Int) -> Unit = { v -> if (v != latestValue) latestOnValueChange(v) }

    // Taking focus dismisses any open text-field keyboard, but it is only a nicety, so any
    // failure is swallowed rather than allowed to break the gesture handling.
    val takeFocus: () -> Unit = { runCatching { focusRequester.requestFocus() } }

    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val focusColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        // NOTE: this modifier chain is deliberately constant. Nothing in it depends on state.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .focusRequester(focusRequester)
                .focusable()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }.semantics {
                    stateDescription = if (isSet) "$currentValue" else "Not set"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (currentValue ?: min).toFloat(),
                        range = min.toFloat()..max.toFloat(),
                    )
                    setProgress { target ->
                        emit(target.roundToInt().coerceIn(min, max))
                        true
                    }
                }.pointerInput(min, max) {
                    val inset = THUMB_RADIUS.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // Finger lifted: it's a tap if it never turned into a drag.
                                if (!dragging) {
                                    emit(valueForX(change.position.x, size.width.toFloat(), inset, min, max))
                                }
                                break
                            }
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!dragging) {
                                // Mostly vertical: it's a page scroll. Don't claim it, don't set anything.
                                if (abs(dy) > slop && abs(dy) > abs(dx)) break
                                if (abs(dx) > slop) dragging = true
                            }
                            if (dragging) {
                                change.consume()
                                emit(valueForX(change.position.x, size.width.toFloat(), inset, min, max))
                            }
                        }
                        takeFocus()
                    }
                },
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val inset = THUMB_RADIUS.toPx()
                val thickness = TRACK_THICKNESS.toPx()
                val centreY = size.height / 2f
                val start = Offset(inset, centreY)
                val end = Offset(size.width - inset, centreY)

                // Focus ring is drawn here (not via a modifier) so focus changes never alter
                // the gesture-handling modifier chain.
                if (isFocused) {
                    val ringWidth = 2.dp.toPx()
                    drawRoundRect(
                        color = focusColor,
                        topLeft = Offset(ringWidth / 2f, ringWidth / 2f),
                        size = Size(size.width - ringWidth, size.height - ringWidth),
                        cornerRadius = CornerRadius(FOCUS_RING_CORNER.toPx()),
                        style = Stroke(width = ringWidth),
                    )
                }

                drawLine(trackColor, start, end, strokeWidth = thickness, cap = StrokeCap.Round)

                // Nothing else is drawn until a value exists. Then just the thumb: deliberately no
                // filled "progress" segment, which would make the two sides of the track look
                // different and suggest "more is better".
                if (currentValue != null) {
                    val fraction = (currentValue - min).toFloat() / (max - min).toFloat()
                    val thumb = Offset(start.x + fraction * (end.x - start.x), centreY)
                    drawCircle(activeColor, radius = inset, center = thumb)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lowLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            // Fixed-width slot, so Clear appearing/disappearing doesn't shift anything.
            Box(modifier = Modifier.width(CLEAR_SLOT_WIDTH), contentAlignment = Alignment.Center) {
                if (isSet) {
                    FilledTonalButton(
                        onClick = { onValueChange(null) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(
                text = highLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Maps a touch x-position (px) to an integer in [min, max]. The track runs from `inset` to
 * `width - inset`; touches beyond either end clamp to min/max.
 */
internal fun valueForX(
    x: Float,
    width: Float,
    inset: Float,
    min: Int,
    max: Int,
): Int {
    val usable = (width - 2f * inset).coerceAtLeast(1f)
    val fraction = ((x - inset) / usable).coerceIn(0f, 1f)
    return (min + fraction * (max - min)).roundToInt()
}

// Run this in Android Studio's *Interactive* preview mode (or deploy it) to try the behaviour.
// The "stored value" line exists only in the preview, to show what would be saved.
@Preview(showBackground = true)
@Composable
private fun SliderInputPreview() {
    MaterialTheme {
        var value by remember { mutableStateOf<Int?>(null) }
        Column(modifier = Modifier.padding(16.dp)) {
            SliderInput(
                lowLabel = "Terrible",
                highLabel = "Excellent",
                min = 0,
                max = 100,
                currentValue = value,
                focusRequester = remember { FocusRequester() },
                onValueChange = { value = it },
            )
            Text("(preview only) stored value: ${value ?: "none"}")
        }
    }
}
