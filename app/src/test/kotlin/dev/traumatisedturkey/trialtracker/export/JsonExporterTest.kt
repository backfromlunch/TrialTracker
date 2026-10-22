package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.fields.FieldDef
import dev.traumatisedturkey.trialtracker.fixtures.MOOD_SYSTEM_URL
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.exampleServiceRequest
import dev.traumatisedturkey.trialtracker.fixtures.withGroupItems
import dev.traumatisedturkey.trialtracker.questionnaire.COUNTER_EVENT_URL
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.servicerequest.ServiceRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class JsonExporterTest {
    private val trialId = "arbitrary-id"
    private val authored = OffsetDateTime.parse("2026-08-08T08:08:08+08:00")
    private val json = Json { ignoreUnknownKeys = true }

    private val questionnaire = exampleQuestionnaire
    private val serviceRequest = exampleServiceRequest

    // Call JsonExporter.buildJson (which gives a string) and then decode back to JSON for easier comparison.
    private fun buildRoot(
        entries: List<Entry>,
        questionnaire: Questionnaire = this.questionnaire,
        serviceRequest: ServiceRequest = this.serviceRequest,
    ): JsonObject = json.decodeFromString(
        JsonObject.serializer(),
        JsonExporter.buildJson(questionnaire, serviceRequest, entries, authored),
    )

    private fun dayItem(
        root: JsonObject,
        index: Int = 0,
    ): JsonObject = root["item"]!!.jsonArray[index].jsonObject

    private fun nestedItem(
        day: JsonObject,
        linkId: String,
    ): JsonObject? = day["item"]!!.jsonArray
        .map { it.jsonObject }
        .find { it["linkId"]!!.jsonPrimitive.content == linkId }

    @Test
    fun `resourceType status is set correctly`() {
        val root = buildRoot(emptyList())
        assertEquals("QuestionnaireResponse", root["resourceType"]!!.jsonPrimitive.content)
        assertEquals("completed", root["status"]!!.jsonPrimitive.content)
        // TODO: this testcase needs expanding greatly
    }

    @Test
    fun `id is the questionnaire id verbatim`() {
        val root = buildRoot(emptyList())
        assertEquals(exampleQuestionnaire.id, root["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `questionnaire strips spaces from the title and appends the version`() {
        val root = buildRoot(emptyList())
        assertEquals("${exampleQuestionnaire.url}|${exampleQuestionnaire.version}", root["questionnaire"]!!.jsonPrimitive.content)
    }

    @Test
    fun `questionnaire omits the version segment when version is blank`() {
        val questionnaire = exampleQuestionnaire.copy(version = "")
        val root = buildRoot(emptyList(), questionnaire = questionnaire)
        assertEquals(exampleQuestionnaire.url, root["questionnaire"]!!.jsonPrimitive.content)
    }

    @Test
    fun `authored uses the given timestamp in ISO-8601 offset form`() {
        val root = buildRoot(emptyList())
        assertEquals(
            authored.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            root["authored"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `top-level items are ordered by date even when entries are supplied out of order`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-26", answersJson = "{}"),
                Entry(trialId = trialId, date = "2026-07-24", answersJson = "{}"),
                Entry(trialId = trialId, date = "2026-07-25", answersJson = "{}"),
            )
        val root = buildRoot(entries)
        val dates =
            root["item"]!!.jsonArray.map { entryItem ->
                nestedItem(entryItem.jsonObject, "Date")!!["answer"]!!
                    .jsonArray[0].jsonObject["valueDate"]!!.jsonPrimitive.content
            }
        assertEquals(listOf("2026-07-24", "2026-07-25", "2026-07-26"), dates)
    }

    @Test
    fun `each entry's nested items start with a Date item carrying the entry's date`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = "{}"))
        val root = buildRoot(entries)
        val day = dayItem(root)
        assertEquals("DiaryEntry", day["linkId"]!!.jsonPrimitive.content)
        val firstNested = day["item"]!!.jsonArray[0].jsonObject
        assertEquals("Date", firstNested["linkId"]!!.jsonPrimitive.content)
        assertEquals(
            "2026-07-24",
            firstNested["answer"]!!.jsonArray[0].jsonObject["valueDate"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `integer field maps to valueInteger`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":7}"""))
        val root = buildRoot(entries)
        val sleep = nestedItem(dayItem(root), "sleep")!!
        assertEquals(7, sleep["answer"]!!.jsonArray[0].jsonObject["valueInteger"]!!.jsonPrimitive.int)
    }

    @Test
    fun `text field maps to valueString`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"notes":"fine"}"""))
        val root = buildRoot(entries)
        val notes = nestedItem(dayItem(root), "notes")!!
        assertEquals("fine", notes["answer"]!!.jsonArray[0].jsonObject["valueString"]!!.jsonPrimitive.content)
    }

    @Test
    fun `choice field maps to valueCoding using the questionnaire's system for that choice`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"mood":1}"""))
        val root = buildRoot(entries)
        val mood = nestedItem(dayItem(root), "mood")!!
        val coding = mood["answer"]!!.jsonArray[0].jsonObject["valueCoding"]!!.jsonObject
        assertEquals(MOOD_SYSTEM_URL, coding["system"]!!.jsonPrimitive.content)
        assertEquals("1", coding["code"]!!.jsonPrimitive.content)
        assertEquals("Low", coding["display"]!!.jsonPrimitive.content)
    }

    // Covers older/malformed questionnaire data where a choice's answerOption.valueCoding has no `system`
    @Test
    fun `choice field falls back to a placeholder system when the questionnaire doesn't specify one`() {
        val questionnaire = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map { field ->
                if (field is FieldDef.ChoiceField && field.linkId == "mood") {
                    field.copy(
                        choices = field.choices.map { choice ->
                            choice.copy(valueCoding = choice.valueCoding.copy(system = null))
                        },
                    )
                } else {
                    field
                }
            }
        }
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"mood":1}"""))
        val root = buildRoot(entries, questionnaire = questionnaire)
        val mood = nestedItem(dayItem(root), "mood")!!
        val coding = mood["answer"]!!.jsonArray[0].jsonObject["valueCoding"]!!.jsonObject
        assertEquals("https://your-domain.example/fhir/CodeSystem/mood", coding["system"]!!.jsonPrimitive.content)
    }

    @Test
    fun `period field appends seconds to produce a valid FHIR time`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"bedtime":"22:15"}"""))
        val root = buildRoot(entries)
        val bedtime = nestedItem(dayItem(root), "bedtime")!!
        assertEquals("22:15:00", bedtime["answer"]!!.jsonArray[0].jsonObject["valueTime"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a missing field is omitted from that day's items`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":7}"""))
        val root = buildRoot(entries)
        assertNull(nestedItem(dayItem(root), "notes"))
    }

    @Test
    fun `an explicitly blanked (null) answer is omitted from that day's items`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":null,"notes":"x"}"""),
            )
        val root = buildRoot(entries)
        assertNull(nestedItem(dayItem(root), "sleep"))
    }

    @Test
    fun `an empty string text answer is omitted from that day's items`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":7,"notes":""}"""),
            )
        val root = buildRoot(entries)
        assertNull(nestedItem(dayItem(root), "notes"))
    }

    @Test
    fun `malformed answersJson falls back to the Date item and a zero counter instead of crashing`() {
        val entries = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = "not valid json"))
        val root = buildRoot(entries)
        val items = dayItem(root)["item"]!!.jsonArray.map { it.jsonObject["linkId"]!!.jsonPrimitive.content }
        assertEquals(listOf("Date", "coffee"), items) // counter is always exported, as zero
    }

    // --- counters ---

    private fun coffeeEntry(answersJson: String) = listOf(Entry(trialId = trialId, date = "2026-07-24", answersJson = answersJson))

    // The counter's single answer object.
    private fun coffeeAnswer(answersJson: String): JsonObject {
        val coffee = nestedItem(dayItem(buildRoot(coffeeEntry(answersJson))), "coffee")!!
        return coffee["answer"]!!.jsonArray.single().jsonObject
    }

    @Test
    fun `counter exports its count with one counter-event extension per timestamp, in stored order`() {
        val answer = coffeeAnswer(
            """{"coffee":["2026-07-24T08:12:03+01:00","2026-07-24T11:40:51+01:00","2026-07-24T15:05:27+01:00"]}""",
        )
        assertEquals(3, answer["valueInteger"]!!.jsonPrimitive.int)
        val events = answer["extension"]!!.jsonArray.map { it.jsonObject }
        assertEquals(List(3) { COUNTER_EVENT_URL }, events.map { it["url"]!!.jsonPrimitive.content })
        assertEquals(
            listOf("2026-07-24T08:12:03+01:00", "2026-07-24T11:40:51+01:00", "2026-07-24T15:05:27+01:00"),
            events.map { it["valueDateTime"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `counter with one timestamp exports count 1 and one extension`() {
        val answer = coffeeAnswer("""{"coffee":["2026-07-24T08:12:03Z"]}""")
        assertEquals(1, answer["valueInteger"]!!.jsonPrimitive.int)
        assertEquals(1, answer["extension"]!!.jsonArray.size)
    }

    @Test
    fun `counter with an empty array exports valueInteger 0 and no extension`() {
        val answer = coffeeAnswer("""{"coffee":[]}""")
        assertEquals(0, answer["valueInteger"]!!.jsonPrimitive.int)
        assertNull(answer["extension"])
    }

    @Test
    fun `a missing, null or non-array counter also exports as zero`() {
        for (json in listOf("""{"sleep":7}""", """{"coffee":null}""", """{"coffee":"oops"}""")) {
            val answer = coffeeAnswer(json)
            assertEquals(json, 0, answer["valueInteger"]!!.jsonPrimitive.int)
            assertNull(json, answer["extension"])
        }
    }

    @Test
    fun `a counter is exported as a single answer however many timestamps it has`() {
        val coffee = nestedItem(
            dayItem(buildRoot(coffeeEntry("""{"coffee":["2026-07-24T08:12:03Z","2026-07-24T09:00:00Z"]}"""))),
            "coffee",
        )!!
        assertEquals(1, coffee["answer"]!!.jsonArray.size)
    }

    @Test
    fun `a counter does not disturb the other fields in the same entry`() {
        val root = buildRoot(coffeeEntry("""{"sleep":7,"coffee":["2026-07-24T08:12:03+01:00"]}"""))
        val day = dayItem(root)
        assertEquals(7, nestedItem(day, "sleep")!!["answer"]!!.jsonArray[0].jsonObject["valueInteger"]!!.jsonPrimitive.int)
        assertEquals(1, nestedItem(day, "coffee")!!["answer"]!!.jsonArray[0].jsonObject["valueInteger"]!!.jsonPrimitive.int)
    }
}
