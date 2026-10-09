package dev.traumatisedturkey.trialtracker.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// Fakes just enough of EntryDao to exercise EntryDateRangeProvider without a real Room instance.
private class FakeEntryDao(private val bounds: DateBounds) : EntryDao {
    override suspend fun upsert(entry: Entry) = error("not used")

    override suspend fun getByDate(trialId: String, date: String): Entry? = error("not used")

    override fun observeByDate(trialId: String, date: String) = error("not used")

    override suspend fun getAll(trialId: String): List<Entry> = error("not used")

    override suspend fun getDateBounds(trialId: String): DateBounds = bounds
}

class EntryDateRangeProviderTest {
    @Test
    fun `returns null when no entries exist`() = runBlocking {
        val provider = EntryDateRangeProvider(FakeEntryDao(DateBounds(minDate = null, maxDate = null)))
        assertNull(provider.getDateRange("trial-1"))
    }

    @Test
    fun `parses min and max dates when entries exist`() = runBlocking {
        val provider =
            EntryDateRangeProvider(
                FakeEntryDao(DateBounds(minDate = "2026-01-15", maxDate = "2026-03-02")),
            )
        val range = provider.getDateRange("trial-1")
        assertEquals(LocalDate.of(2026, 1, 15), range?.start)
        assertEquals(LocalDate.of(2026, 3, 2), range?.end)
    }
}
