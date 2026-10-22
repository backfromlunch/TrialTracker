package dev.traumatisedturkey.trialtracker.summary

import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class DayStatusTest {

    @Test
    fun `no answers is EMPTY`() {
        val status = computeDayStatus(exampleQuestionnaire, JsonObject(emptyMap()))
        assertEquals(DayStatus.EMPTY, status)
    }

    @Test
    fun `all fields of interest filled in is COMPLETE regardless of Comments`() {
        val answers =
            JsonObject(
                mapOf(
                    "sleep" to JsonPrimitive(3),
                    "weight" to JsonPrimitive(42.0f),
                    "sleep_score" to JsonPrimitive(43),
                    "notes" to JsonPrimitive("foo"),
                    "mood" to JsonPrimitive("Low"),
                    "logged_at" to JsonPrimitive("2022-04-03T14:57"),
                    "bedtime" to JsonPrimitive("22:50"),
                    "coffee" to JsonArray(listOf(JsonPrimitive("2026-07-24T08:12:03+01:00"))),
                ),
            )
        assertEquals(DayStatus.COMPLETE, computeDayStatus(exampleQuestionnaire, answers))
    }

    @Test
    fun `subset of fields of interest is PARTIAL`() {
        val answers =
            JsonObject(
                mapOf(
                    "sleep" to JsonPrimitive(3),
                    "notes" to JsonPrimitive("foo"),
                    // OMITTED FOR TEST "mood" to JsonPrimitive("Low"),
                    "bedtime" to JsonPrimitive("22:50"),
                ),
            )
        assertEquals(DayStatus.PARTIAL, computeDayStatus(exampleQuestionnaire, answers))
    }

    @Test
    fun `blank text answer does not count as completed`() {
        val answers =
            JsonObject(
                mapOf(
                    "sleep" to JsonPrimitive(3),
                    "notes" to JsonPrimitive("foo"),
                    "mood" to JsonPrimitive("   "),
                    "bedtime" to JsonPrimitive("22:50"),
                ),
            )
        assertEquals(DayStatus.PARTIAL, computeDayStatus(exampleQuestionnaire, answers))
    }

    @Test
    fun `only Comments filled in is still EMPTY since Comments is excluded`() {
        val answers = JsonObject(mapOf("Comments" to JsonPrimitive("looks fine")))
        assertEquals(DayStatus.EMPTY, computeDayStatus(exampleQuestionnaire, answers))
    }

    // ---- counters -------------------------------------------------------------------------

    // The app always saves counters as `[]` (see withZeroCounters), so the missing/null/non-array cases
    // below are only reachable from hand-built answers. They pin computeDayStatus's strictness.

    private val allButCounter: Map<String, JsonElement> = mapOf(
        "sleep" to JsonPrimitive(3),
        "weight" to JsonPrimitive(42.0f),
        "notes" to JsonPrimitive("foo"),
        "mood" to JsonPrimitive("Low"),
        "logged_at" to JsonPrimitive("2022-04-03T14:57"),
        "bedtime" to JsonPrimitive("22:50"),
    )

    @Test
    fun `a counter with an empty array counts as completed (zero)`() {
        val answers = JsonObject(allButCounter + ("coffee" to JsonArray(emptyList())))
        assertEquals(DayStatus.COMPLETE, computeDayStatus(exampleQuestionnaire, answers))
    }

    @Test
    fun `a missing counter leaves the day PARTIAL`() {
        assertEquals(DayStatus.PARTIAL, computeDayStatus(exampleQuestionnaire, JsonObject(allButCounter)))
    }

    @Test
    fun `a null counter leaves the day PARTIAL`() {
        val answers = JsonObject(allButCounter + ("coffee" to JsonNull))
        assertEquals(DayStatus.PARTIAL, computeDayStatus(exampleQuestionnaire, answers))
    }

    @Test
    fun `a non-array counter answer leaves the day PARTIAL`() {
        val answers = JsonObject(allButCounter + ("coffee" to JsonPrimitive("oops")))
        assertEquals(DayStatus.PARTIAL, computeDayStatus(exampleQuestionnaire, answers))
    }

    @Test
    fun `only an empty counter is PARTIAL, not EMPTY`() {
        val answers = JsonObject(mapOf("coffee" to JsonArray(emptyList())))
        assertEquals(DayStatus.PARTIAL, computeDayStatus(exampleQuestionnaire, answers))
    }
}
