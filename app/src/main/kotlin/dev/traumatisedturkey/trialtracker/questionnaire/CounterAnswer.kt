package dev.traumatisedturkey.trialtracker.questionnaire

import dev.traumatisedturkey.trialtracker.fields.FieldDef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/*
 * Helpers for a CounterField's stored answer: a JSON array of ISO-8601 offset date-times,
 * oldest first. The count is the array size. No events is always stored as `[]`; readers also
 * treat a missing key, JsonNull or a non-array as zero. See FieldDef.CounterField.
 *
 * Why full date-times: FHIR `dateTime` requires a date and FHIR `time` can't carry an offset, so each
 * timestamp is a full ISO-8601 offset date-time (2026-10-17T08:12:03+01:00) whose DATE IS IGNORED.
 * The date is just the entry's date, stamped on to make the value valid FHIR; it is not the date of
 * the press (they differ when a past day is edited). Only the time and offset mean anything.
 *
 * Export deliberately differs by format:
 * - JSON: always exactly one answer: `valueInteger` is the count, with one `counter-event` extension
 *   (`valueDateTime`) per timestamp, none when the count is zero. Zero is an explicit `valueInteger: 0`.
 * - CSV: only the count, so timestamps are lost. No events is "0".
 */

/** URL of the answer extension carrying one press time (`valueDateTime`) in the JSON export. */
const val COUNTER_EVENT_URL = "https://traumatisedturkey.dev/fhir/StructureDefinition/counter-event"

/**
 * Fills every counter in [fields] that isn't an array (missing, null or malformed) with `[]`.
 * Other answers are untouched. Returns the same instance if nothing needed filling.
 */
fun JsonObject.withZeroCounters(fields: List<FieldDef>): JsonObject {
    val unset = fields.filterIsInstance<FieldDef.CounterField>().map { it.linkId }.filter { this[it] !is JsonArray }
    if (unset.isEmpty()) return this
    return JsonObject(toMutableMap().apply { unset.forEach { put(it, counterAnswer(emptyList())) } })
}

/**
 * Formats a press time as stored and exported, e.g. `2026-10-17T08:12:03+01:00`.
 * Truncated to whole seconds. A zero offset is written as `Z` (still valid FHIR).
 */
fun formatCounterTimestamp(time: OffsetDateTime): String = time.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

/**
 * A new press: the time and offset of [now], stamped onto [entryDate] (the day the entry belongs to).
 * The offset is now's, not recomputed for [entryDate].
 */
fun newCounterTimestamp(
    entryDate: LocalDate,
    now: OffsetDateTime,
): String = formatCounterTimestamp(now.with(entryDate))

/**
 * Reads the timestamps out of a stored answer. Tolerant: a missing key, JsonNull or non-array value
 * gives an empty list, and non-string array elements are skipped. Strings are not format-checked.
 */
fun JsonElement?.counterTimestamps(): List<String> = (this as? JsonArray)
    ?.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
    ?: emptyList()

/** Builds the stored answer from a list of timestamps. An empty list gives an empty array. */
fun counterAnswer(timestamps: List<String>): JsonArray = JsonArray(timestamps.map { JsonPrimitive(it) })

private val timeOfDayFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The time of day exactly as recorded (in its own offset, not converted), e.g. "08:12". Null if unparseable. */
fun counterTimeOfDay(timestamp: String): String? = try {
    OffsetDateTime.parse(timestamp).format(timeOfDayFormatter)
} catch (e: DateTimeParseException) {
    null
}
