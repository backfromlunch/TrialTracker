package dev.traumatisedturkey.trialtracker.ui

import dev.traumatisedturkey.trialtracker.data.DataDateRange
import dev.traumatisedturkey.trialtracker.data.DateRangeProvider
import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.data.EntryDao
import dev.traumatisedturkey.trialtracker.data.TrialDateRange
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.summary.DayStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

private class FakeEntryDao(
    private val entries: List<Entry>,
) : EntryDao {
    override suspend fun upsert(entry: Entry) = error("not used")

    override suspend fun getByDate(trialId: String, date: String): Entry? = error("not used")

    override fun observeByDate(trialId: String, date: String) = error("not used")

    override suspend fun getAll(trialId: String): List<Entry> = entries

    override suspend fun getDateBounds(trialId: String) = error("not used - test supplies its own DateRangeProvider")
}

private class FixedDateRangeProvider(
    private val range: DataDateRange?,
) : DateRangeProvider {
    override suspend fun getDateRange(trialId: String): DataDateRange? = range
}

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no date range yields NoData`() = runTest {
        val viewModel =
            SummaryViewModel(
                entryDao = FakeEntryDao(emptyList()),
                questionnaire = exampleQuestionnaire,
                trialId = "trial-1",
                trialDateRange = TrialDateRange(start = null, end = null),
                dateRangeProvider = FixedDateRangeProvider(null),
            )
        dispatcher.scheduler.advanceUntilIdle()
        Assert.assertEquals(SummaryState.NoData, viewModel.state.value)
    }

    @Test
    fun `calendar bounds expand to whole months and cover every month in range`() = runTest {
        val entries =
            listOf(
                Entry(trialId = "trial-1", date = "2026-01-15", answersJson = """{"sleep":3}"""),
                Entry(trialId = "trial-1", date = "2026-03-02", answersJson = """{}"""),
            )
        val viewModel =
            SummaryViewModel(
                entryDao = FakeEntryDao(entries),
                questionnaire = exampleQuestionnaire,
                trialId = "trial-1",
                trialDateRange = TrialDateRange(start = null, end = null),
                dateRangeProvider =
                FixedDateRangeProvider(
                    DataDateRange(
                        start = LocalDate.of(2026, 1, 15),
                        end = LocalDate.of(2026, 3, 2),
                    ),
                ),
            )
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        Assert.assertTrue(state is SummaryState.Loaded)
        state as SummaryState.Loaded
        Assert.assertEquals(LocalDate.of(2026, 1, 1), state.calendarStart)
        Assert.assertEquals(LocalDate.of(2026, 3, 31), state.calendarEnd)
        Assert.assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            state.months,
        )
        Assert.assertEquals(DayStatus.PARTIAL, state.statusByDate[LocalDate.of(2026, 1, 15)])
        Assert.assertEquals(DayStatus.EMPTY, state.statusByDate[LocalDate.of(2026, 3, 2)])
        // A day with no Entry row at all (e.g. Feb 10) is simply absent from the map.
        Assert.assertEquals(null, state.statusByDate[LocalDate.of(2026, 2, 10)])
    }
}
