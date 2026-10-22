package dev.traumatisedturkey.trialtracker.ui.entry

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.data.EntryDao
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.sliderField
import dev.traumatisedturkey.trialtracker.fixtures.withGroupItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Deliberately not named FakeEntryDao: that name is already used (privately) in EntryViewModelTest.kt,
// and two top-level classes with the same name in one package would clash.
private class RecordingEntryDao(private val stored: Entry? = null) : EntryDao {
    val upserts = mutableListOf<Entry>()

    override suspend fun upsert(entry: Entry) {
        upserts += entry
    }

    override suspend fun getByDate(trialId: String, date: String): Entry? = stored

    override fun observeByDate(trialId: String, date: String) = error("not used")

    override suspend fun getAll(trialId: String): List<Entry> = error("not used")

    override suspend fun getDateBounds(trialId: String) = error("not used")
}

/**
 * A slider's answer is an ordinary integer; clearing it stores JsonNull (as blanking a number
 * field does). EntryViewModel has no slider-specific code, so these pin the contract that
 * SliderInput and the exporters rely on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SliderEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val trialId = "trial-1"
    private val questionnaire = exampleQuestionnaire.withGroupItems("LogEntry") { it + sliderField("energy") }
    private val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setting a slider stores a plain integer`() = runTest {
        val dao = RecordingEntryDao()
        val viewModel = EntryViewModel(dao, questionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateAnswer("energy", JsonPrimitive(42))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(42, viewModel.answers.value["energy"]!!.jsonPrimitive.int)
        assertEquals("""{"energy":42,"coffee":[]}""", dao.upserts.last().answersJson)
    }

    @Test
    fun `a slider value of zero is stored, not treated as unset`() = runTest {
        val dao = RecordingEntryDao()
        val viewModel = EntryViewModel(dao, questionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.updateAnswer("energy", JsonPrimitive(0))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("""{"energy":0,"coffee":[]}""", dao.upserts.last().answersJson)
    }

    @Test
    fun `clearing a slider stores JsonNull and keeps the key`() = runTest {
        val dao = RecordingEntryDao()
        val viewModel = EntryViewModel(dao, questionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAnswer("energy", JsonPrimitive(42))

        viewModel.updateAnswer("energy", JsonNull)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(JsonNull, viewModel.answers.value["energy"])
        assertEquals("""{"energy":null,"coffee":[]}""", dao.upserts.last().answersJson)
    }

    @Test
    fun `a slider can be set again after being cleared`() = runTest {
        val dao = RecordingEntryDao()
        val viewModel = EntryViewModel(dao, questionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAnswer("energy", JsonPrimitive(42))
        viewModel.updateAnswer("energy", JsonNull)

        viewModel.updateAnswer("energy", JsonPrimitive(7))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(7, viewModel.answers.value["energy"]!!.jsonPrimitive.int)
        assertEquals("""{"energy":7,"coffee":[]}""", dao.upserts.last().answersJson)
    }

    @Test
    fun `clearing a slider leaves other answers untouched`() = runTest {
        val dao = RecordingEntryDao()
        val viewModel = EntryViewModel(dao, questionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAnswer("sleep", JsonPrimitive(6))
        viewModel.updateAnswer("energy", JsonPrimitive(42))

        viewModel.updateAnswer("energy", JsonNull)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a stored cleared slider reloads as JsonNull`() = runTest {
        val stored = Entry(trialId = trialId, date = today, answersJson = """{"energy":null,"sleep":5}""")
        val viewModel = EntryViewModel(RecordingEntryDao(stored), questionnaire, trialId)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.answers.value["energy"] is JsonNull)
        assertEquals(5, viewModel.answers.value["sleep"]!!.jsonPrimitive.int)
    }
}
