package dev.traumatisedturkey.trialtracker.questionnaire

import dev.traumatisedturkey.trialtracker.fixtures.counterField
import dev.traumatisedturkey.trialtracker.fixtures.integerField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class CounterAnswerTest {

    // ---- formatCounterTimestamp -----------------------------------------------------------

    @Test
    fun `format keeps the offset and whole seconds`() {
        val time = OffsetDateTime.parse("2026-10-17T08:12:03+01:00")
        assertEquals("2026-10-17T08:12:03+01:00", formatCounterTimestamp(time))
    }

    @Test
    fun `format truncates fractional seconds`() {
        val time = OffsetDateTime.parse("2026-10-17T08:12:03.456789+01:00")
        assertEquals("2026-10-17T08:12:03+01:00", formatCounterTimestamp(time))
    }

    @Test
    fun `format still writes seconds when they are zero`() {
        val time = OffsetDateTime.parse("2026-10-17T08:12:00+01:00")
        assertEquals("2026-10-17T08:12:00+01:00", formatCounterTimestamp(time))
    }

    @Test
    fun `format preserves a negative offset`() {
        val time = OffsetDateTime.parse("2026-10-17T08:00:00-04:00")
        assertEquals("2026-10-17T08:00:00-04:00", formatCounterTimestamp(time))
    }

    @Test
    fun `format writes a zero offset as Z`() {
        val time = OffsetDateTime.parse("2026-10-17T08:12:03+00:00")
        assertEquals("2026-10-17T08:12:03Z", formatCounterTimestamp(time))
    }

    // ---- counterTimestamps / counterAnswer ------------------------------------------------

    @Test
    fun `answer round-trips through counterAnswer and counterTimestamps`() {
        val timestamps = listOf("2026-10-17T08:12:03+01:00", "2026-10-17T11:40:51+01:00")
        assertEquals(timestamps, counterAnswer(timestamps).counterTimestamps())
    }

    @Test
    fun `an empty list gives an empty array, which reads back as empty`() {
        assertEquals(JsonArray(emptyList()), counterAnswer(emptyList()))
        assertEquals(emptyList<String>(), counterAnswer(emptyList()).counterTimestamps())
    }

    @Test
    fun `a missing answer reads as empty`() {
        assertEquals(emptyList<String>(), null.counterTimestamps())
    }

    @Test
    fun `a JsonNull answer reads as empty`() {
        assertEquals(emptyList<String>(), JsonNull.counterTimestamps())
    }

    @Test
    fun `a non-array answer reads as empty`() {
        assertEquals(emptyList<String>(), JsonPrimitive("2026-10-17T08:12:03+01:00").counterTimestamps())
    }

    @Test
    fun `non-string array elements are skipped`() {
        val mixed = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(5), JsonNull, JsonPrimitive("b")))
        assertEquals(listOf("a", "b"), mixed.counterTimestamps())
    }

    // ---- newCounterTimestamp --------------------------------------------------------------

    @Test
    fun `new timestamp takes time and offset from now, and the date from the entry`() {
        val now = OffsetDateTime.parse("2026-10-17T08:12:03+01:00")
        assertEquals("2026-10-10T08:12:03+01:00", newCounterTimestamp(LocalDate.parse("2026-10-10"), now))
    }

    @Test
    fun `new timestamp on the same date is just now`() {
        val now = OffsetDateTime.parse("2026-10-17T08:12:03+01:00")
        assertEquals("2026-10-17T08:12:03+01:00", newCounterTimestamp(LocalDate.parse("2026-10-17"), now))
    }

    @Test
    fun `new timestamp truncates fractional seconds`() {
        val now = OffsetDateTime.parse("2026-10-17T08:12:03.987+01:00")
        assertEquals("2026-10-10T08:12:03+01:00", newCounterTimestamp(LocalDate.parse("2026-10-10"), now))
    }

    @Test
    fun `new timestamp keeps now's offset rather than recomputing it for the entry's date`() {
        val now = OffsetDateTime.parse("2026-10-17T08:12:03+01:00") // summer time
        assertEquals("2026-01-10T08:12:03+01:00", newCounterTimestamp(LocalDate.parse("2026-01-10"), now))
    }

    // ---- counterTimeOfDay -----------------------------------------------------------------

    @Test
    fun `time of day is HH-mm`() {
        assertEquals("08:12", counterTimeOfDay("2026-10-17T08:12:03+01:00"))
    }

    @Test
    fun `time of day is shown in the recorded offset, not converted`() {
        assertEquals("23:05", counterTimeOfDay("2026-10-17T23:05:00-04:00"))
    }

    @Test
    fun `time of day handles a Z offset`() {
        assertEquals("08:12", counterTimeOfDay("2026-10-17T08:12:03Z"))
    }

    @Test
    fun `time of day is null for unparseable input`() {
        assertNull(counterTimeOfDay("garbage"))
        assertNull(counterTimeOfDay(""))
    }

    // ---- withZeroCounters -----------------------------------------------------------------

    private val fields =
        listOf(integerField("sleep", 1, 9), counterField("coffee"), counterField("tea"))

    @Test
    fun `unset counters are filled with an empty array`() {
        val result = JsonObject(mapOf("sleep" to JsonPrimitive(7))).withZeroCounters(fields)
        assertEquals(JsonArray(emptyList()), result["coffee"])
        assertEquals(JsonArray(emptyList()), result["tea"])
        assertEquals(JsonPrimitive(7), result["sleep"])
    }

    @Test
    fun `null and non-array counters are replaced with an empty array`() {
        val result =
            JsonObject(mapOf("coffee" to JsonNull, "tea" to JsonPrimitive("oops")))
                .withZeroCounters(fields)
        assertEquals(JsonArray(emptyList()), result["coffee"])
        assertEquals(JsonArray(emptyList()), result["tea"])
    }

    @Test
    fun `existing counter arrays are kept`() {
        val coffee = counterAnswer(listOf("2026-10-17T08:12:03+01:00"))
        val result = JsonObject(mapOf("coffee" to coffee)).withZeroCounters(fields)
        assertEquals(coffee, result["coffee"])
        assertEquals(JsonArray(emptyList()), result["tea"])
    }

    @Test
    fun `nothing to fill returns the same instance`() {
        val full =
            JsonObject(
                mapOf(
                    "coffee" to counterAnswer(emptyList()),
                    "tea" to counterAnswer(emptyList()),
                ),
            )
        assertSame(full, full.withZeroCounters(fields))
    }
}
