package dev.traumatisedturkey.trialtracker.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * A date range declared by the trial ServiceRequest.
 *
 * Each bound is independently nullable; the object itself is never null.
*/
@Serializable
data class TrialDateRange(
    @Serializable(with = LocalDateSerializer::class) val start: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val end: LocalDate? = null,
) {
    fun contains(date: LocalDate): Boolean = (start == null || date >= start) &&
        (end == null || date <= end)

    // Human-readable summary of the range.
    fun display(): String = when {
        start == null && end == null -> "no fixed dates"
        start == null -> "open – $end"
        end == null -> "$start – open"
        else -> "$start – $end"
    }
}
