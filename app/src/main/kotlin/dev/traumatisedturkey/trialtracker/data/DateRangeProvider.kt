package dev.traumatisedturkey.trialtracker.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// DB-derived
data class DataDateRange(
    val start: LocalDate,
    val end: LocalDate,
)

// Derived from recorded Entry rows
fun interface DateRangeProvider {
    suspend fun getDateRange(trialId: String): DataDateRange?
}

class EntryDateRangeProvider(
    private val entryDao: EntryDao,
) : DateRangeProvider {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Get earliest/latest dates in the 'Entry' table.
     *
     * Returns null if there are no entries yet.
     */
    override suspend fun getDateRange(trialId: String): DataDateRange? {
        val bounds = entryDao.getDateBounds(trialId)
        val minDate = bounds.minDate ?: return null
        val maxDate = bounds.maxDate ?: return null
        return DataDateRange(
            start = LocalDate.parse(minDate, formatter),
            end = LocalDate.parse(maxDate, formatter),
        )
    }
}
