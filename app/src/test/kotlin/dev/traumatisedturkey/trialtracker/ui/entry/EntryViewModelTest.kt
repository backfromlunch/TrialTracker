package dev.traumatisedturkey.trialtracker.ui.entry

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.data.EntryDao
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fake just enough to exercise EntryViewModel without a real Room instance.
 *
 * delaysMsByDate lets a specific date's getByDate() take (virtual) time, needed to reproduce
 * out-of-order completion under StandardTestDispatcher.
 */
private class FakeEntryDao(
    private val entriesByKey: Map<String, Entry> = emptyMap(),
    private val delaysMsByDate: Map<String, Long> = emptyMap(),
) : EntryDao {
    val upserts = mutableListOf<Entry>()
    val getByDateCalls = mutableListOf<String>()

    override suspend fun upsert(entry: Entry) {
        upserts += entry
    }

    override suspend fun getByDate(trialId: String, date: String): Entry? {
        getByDateCalls += date
        delaysMsByDate[date]?.let { delay(it) }
        return entriesByKey[date]
    }

    override fun observeByDate(trialId: String, date: String) = error("not used")

    override suspend fun getAll(trialId: String): List<Entry> = error("not used")

    override suspend fun getDateBounds(trialId: String) = error("not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val trialId = "trial-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(date: LocalDate, answersJson: String) = Entry(trialId = trialId, date = date.format(dateFormatter), answersJson = answersJson)

    // --- initial load ---

    @Test
    fun `no stored entry for today loads empty answers`() = runTest {
        val viewModel = EntryViewModel(FakeEntryDao(), exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(JsonObject(emptyMap()), viewModel.answers.value)
    }

    @Test
    fun `existing entry for today is decoded into answers on load`() = runTest {
        val today = LocalDate.now()
        val dao = FakeEntryDao(entriesByKey = mapOf(today.format(dateFormatter) to entry(today, """{"sleep":5}""")))
        val viewModel = EntryViewModel(dao, exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
    }

    // --- updateAnswer ---

    @Test
    fun `updateAnswer applies to the StateFlow immediately, before the save completes`() = runTest {
        val viewModel = EntryViewModel(FakeEntryDao(), exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAnswer("sleep", JsonPrimitive(6))
        // No advanceUntilIdle() here - this asserts the update is visible synchronously.
        assertEquals(6, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
    }

    @Test
    fun `updateAnswer persists via upsert, keyed by trialId and the current date`() = runTest {
        val dao = FakeEntryDao()
        val viewModel = EntryViewModel(dao, exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAnswer("sleep", JsonPrimitive(6))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, dao.upserts.size)
        val saved = dao.upserts.single()
        assertEquals(trialId, saved.trialId)
        assertEquals(LocalDate.now().format(dateFormatter), saved.date)
        assertEquals("""{"sleep":6}""", saved.answersJson)
    }

    @Test
    fun `updateAnswer merges into existing answers rather than replacing them`() = runTest {
        val today = LocalDate.now()
        val dao = FakeEntryDao(
            entriesByKey = mapOf(today.format(dateFormatter) to entry(today, """{"sleep":5,"notes":"ok"}""")),
        )
        val viewModel = EntryViewModel(dao, exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateAnswer("sleep", JsonPrimitive(7))

        assertEquals(7, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
        assertEquals("ok", viewModel.answers.value["notes"]!!.jsonPrimitive.content)
    }

    // --- day navigation ---

    @Test
    fun `goToPreviousDay moves back one day and loads that day's answers`() = runTest {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dao = FakeEntryDao(
            entriesByKey = mapOf(yesterday.format(dateFormatter) to entry(yesterday, """{"sleep":3}""")),
        )
        val viewModel = EntryViewModel(dao, exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.goToPreviousDay()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(yesterday, viewModel.currentDate.value)
        assertEquals(3, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
    }

    @Test
    fun `goToNextDay is blocked when already on today`() = runTest {
        val viewModel = EntryViewModel(FakeEntryDao(), exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.goToNextDay()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LocalDate.now(), viewModel.currentDate.value)
    }

    @Test
    fun `goToDate blocks travel into the future`() = runTest {
        val viewModel = EntryViewModel(FakeEntryDao(), exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.goToDate(LocalDate.now().plusDays(1))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(LocalDate.now(), viewModel.currentDate.value)
    }

    @Test
    fun `goToDate with the current date is a no-op - no reload is triggered`() = runTest {
        val dao = FakeEntryDao()
        val viewModel = EntryViewModel(dao, exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        val callsAfterInitialLoad = dao.getByDateCalls.size

        viewModel.goToDate(LocalDate.now())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(callsAfterInitialLoad, dao.getByDateCalls.size)
    }

    // --- stale-load guard ---

    @Test
    fun `an out-of-order load result is discarded if the date changed before it completed`() = runTest {
        val today = LocalDate.now()
        val slowDate = today.minusDays(2)
        val fastDate = today.minusDays(1)
        val dao = FakeEntryDao(
            entriesByKey = mapOf(
                slowDate.format(dateFormatter) to entry(slowDate, """{"sleep":1}"""),
                fastDate.format(dateFormatter) to entry(fastDate, """{"sleep":2}"""),
            ),
            // slowDate's query resolves after fastDate's, even though it's requested first -
            // reproducing the out-of-order completion loadDate()'s guard exists to handle.
            delaysMsByDate = mapOf(slowDate.format(dateFormatter) to 100L),
        )
        val viewModel = EntryViewModel(dao, exampleQuestionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.goToDate(slowDate)
        viewModel.goToDate(fastDate)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(fastDate, viewModel.currentDate.value)
        assertEquals(2, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
    }
}
