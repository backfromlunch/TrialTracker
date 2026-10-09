package dev.traumatisedturkey.trialtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.traumatisedturkey.trialtracker.data.DateRangeProvider
import dev.traumatisedturkey.trialtracker.data.EntryDao
import dev.traumatisedturkey.trialtracker.data.EntryDateRangeProvider
import dev.traumatisedturkey.trialtracker.data.TrialDateRange
import dev.traumatisedturkey.trialtracker.questionnaire.DefaultFieldOfInterestPolicy
import dev.traumatisedturkey.trialtracker.questionnaire.FieldOfInterestPolicy
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.summary.DayStatus
import dev.traumatisedturkey.trialtracker.summary.computeDayStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.YearMonth

sealed class SummaryState {
    object Loading : SummaryState()

    // No entries recorded yet, so there's no meaningful range to show a calendar for.
    object NoData : SummaryState()

    data class Loaded(
        // Whole calendar months, not just the range returned by DateRangeProvider.
        val calendarStart: LocalDate,
        val calendarEnd: LocalDate,
        // Dates with no Entry row at all are simply absent from this map.
        // The UI should treat a missing key the same as DayStatus.EMPTY.
        val statusByDate: Map<LocalDate, DayStatus>,
        // Config-declared bounds.
        val trialDateRange: TrialDateRange,
    ) : SummaryState() {
        val months: List<YearMonth> by lazy {
            val startMonth = YearMonth.from(calendarStart)
            val endMonth = YearMonth.from(calendarEnd)
            generateSequence(startMonth) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(endMonth) }
                .toList()
        }
    }
}

class SummaryViewModel(
    private val entryDao: EntryDao,
    private val questionnaire: Questionnaire,
    private val trialId: String,
    private val trialDateRange: TrialDateRange,
    private val dateRangeProvider: DateRangeProvider = EntryDateRangeProvider(entryDao),
    private val fieldOfInterestPolicy: FieldOfInterestPolicy = DefaultFieldOfInterestPolicy,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state: StateFlow<SummaryState> = _state.asStateFlow()

    init {
        refresh()
    }

    // Public so the screen can trigger a reload every time it's navigated to
    fun refresh() {
        viewModelScope.launch {
            val dataRange = dateRangeProvider.getDateRange(trialId)
            // trialDateRange's bound wins where declared; else fall back to the DB-derived bound.
            val start = trialDateRange.start ?: dataRange?.start
            val end = trialDateRange.end ?: dataRange?.end
            if (start == null || end == null) {
                _state.value = SummaryState.NoData
                return@launch
            }

            val entries = entryDao.getAll(trialId)
            val statusByDate =
                entries.associate { entry ->
                    val answers = json.decodeFromString(JsonObject.serializer(), entry.answersJson)
                    LocalDate.parse(entry.date) to computeDayStatus(
                        questionnaire,
                        answers,
                        fieldOfInterestPolicy,
                    )
                }

            _state.value =
                SummaryState.Loaded(
                    calendarStart = start.withDayOfMonth(1),
                    calendarEnd = end.withDayOfMonth(end.lengthOfMonth()),
                    statusByDate = statusByDate,
                    trialDateRange = trialDateRange,
                )
        }
    }

    class Factory(
        private val entryDao: EntryDao,
        private val questionnaire: Questionnaire,
        private val trialId: String,
        private val trialDateRange: TrialDateRange,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SummaryViewModel(entryDao, questionnaire, trialId, trialDateRange) as T
        }
    }
}
