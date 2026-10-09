package dev.traumatisedturkey.trialtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.traumatisedturkey.trialtracker.summary.DayStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel,
    onDateSelected: (LocalDate) -> Unit,
) {
    // Re-fetches every time this screen is navigated to.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // 'today' must be kept fresh in case the device sits idle on this screen for a long time (e.g. overnight)
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

    val state by viewModel.state.collectAsState()

    when (val current = state) {
        SummaryState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        SummaryState.NoData -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No entries recorded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is SummaryState.Loaded -> SummaryCalendar(current, today, onDateSelected)
    }
}

@Composable
private fun SummaryCalendar(
    state: SummaryState.Loaded,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentMonth = remember(state.months) { YearMonth.now() }

    LaunchedEffect(state.months) {
        val index = state.months.indexOf(currentMonth).takeIf { it >= 0 } ?: 0
        listState.scrollToItem(index)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(state.months) { month ->
            MonthGrid(
                month = month,
                statusByDate = state.statusByDate,
                trialDateRange = state.trialDateRange,
                today = today,
                onDateSelected = onDateSelected,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    statusByDate: Map<LocalDate, DayStatus>,
    trialDateRange: dev.traumatisedturkey.trialtracker.data.TrialDateRange,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val weekDays = remember(firstDayOfWeek) { (0..6).map { firstDayOfWeek.plus(it.toLong()) } }

    Column {
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { dayOfWeek ->
                Text(
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        val firstOfMonth = month.atDay(1)
        val lastOfMonth = month.atEndOfMonth()
        val leadingBlanks = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val cells: List<LocalDate?> =
            List(leadingBlanks) { null } + (1..lastOfMonth.dayOfMonth).map { month.atDay(it) }
        val trailingBlanks = (7 - cells.size % 7) % 7
        val paddedCells = cells + List(trailingBlanks) { null }

        paddedCells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        status = date?.let { statusByDate[it] ?: DayStatus.EMPTY },
                        isToday = date == today,
                        isInBounds = date == null || trialDateRange.contains(date),
                        onDateSelected = onDateSelected,
                    )
                }
            }
        }
    }
}

// Ring for "today": drawn as a separate, slightly larger circle with a gap from the status fill,
// so it always sits against the page background and never needs to contrast against the fill colour itself.
private val TodayRingWidth = 2.dp

@Composable
private fun RowScope.DayCell(
    date: LocalDate?,
    status: DayStatus?,
    isToday: Boolean,
    isInBounds: Boolean,
    onDateSelected: (LocalDate) -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            // Config wins over DB data: a date outside the trial's configured bounds is always
            // treated as EMPTY for display, even if an Entry row exists for it.
            val effectiveStatus = if (isInBounds) status else DayStatus.EMPTY

            val backgroundColor =
                when (effectiveStatus) {
                    DayStatus.COMPLETE -> MaterialTheme.colorScheme.primary
                    DayStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
                    else -> Color.Transparent
                }
            val textColor =
                when {
                    !isInBounds -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    effectiveStatus == DayStatus.COMPLETE -> MaterialTheme.colorScheme.onPrimary
                    effectiveStatus == DayStatus.PARTIAL -> MaterialTheme.colorScheme.onTertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }

            if (isToday) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize(0.95f)
                        .border(TodayRingWidth, MaterialTheme.colorScheme.primary, CircleShape),
                )
            }

            Box(
                modifier =
                Modifier
                    .fillMaxSize(0.8f)
                    .clip(CircleShape)
                    .then(
                        // Out-of-bounds dates are not jumpable to
                        if (isInBounds) {
                            Modifier.clickable { onDateSelected(date) }
                        } else {
                            Modifier
                        },
                    )
                    .background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = date.dayOfMonth.toString(), color = textColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
