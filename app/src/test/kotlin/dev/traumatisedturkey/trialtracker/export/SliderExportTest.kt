package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.exampleServiceRequest
import dev.traumatisedturkey.trialtracker.fixtures.sliderField
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.questionnaire.SingleItem
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

/**
 * A slider's stored answer is an ordinary integer, so both exporters should treat it exactly like
 * any other integer field. These tests pin that, and the "cleared" (JsonNull) case, which matters
 * far more for sliders than for text-entry numbers.
 */
class SliderExportTest {
    private val trialId = "arbitrary-id"
    private val authored = OffsetDateTime.parse("2026-08-08T08:08:08+08:00")
    private val json = Json { ignoreUnknownKeys = true }

    // A questionnaire whose only field is a slider, so expected CSV rows stay short and obvious.
    private val sliderOnly: Questionnaire = exampleQuestionnaire.copy(
        item = listOf(
            SingleItem(
                linkId = "LogEntry",
                text = "Log entry",
                type = "group",
                repeats = true,
                item = listOf(sliderField("energy", low = "Exhausted", high = "Energetic")),
            ),
        ),
    )

    private fun entry(answersJson: String) = Entry(trialId = trialId, date = "2026-07-24", answersJson = answersJson)

    // ---- CSV ------------------------------------------------------------------------------

    private fun csvDataLines(questionnaire: Questionnaire, vararg entries: Entry): List<String> = CsvExporter
        .buildCsv(questionnaire, exampleServiceRequest, entries.toList(), authored)
        .lines()
        .drop(N_NON_DATA_LINES)

    @Test
    fun `csv header uses the slider's linkId, not its labels`() {
        val csv = CsvExporter.buildCsv(sliderOnly, exampleServiceRequest, emptyList(), authored)
        assertEquals("date,energy", csv.lines()[N_COMMENT_LINES])
    }

    @Test
    fun `csv renders a slider value as a plain integer`() {
        assertEquals(listOf("2026-07-24,42"), csvDataLines(sliderOnly, entry("""{"energy":42}""")))
    }

    @Test
    fun `csv renders a slider value of zero as 0, not blank`() {
        assertEquals(listOf("2026-07-24,0"), csvDataLines(sliderOnly, entry("""{"energy":0}""")))
    }

    @Test
    fun `csv renders a never-set slider as a blank cell`() {
        assertEquals(listOf("2026-07-24,"), csvDataLines(sliderOnly, entry("{}")))
    }

    @Test
    fun `csv renders a cleared slider (JsonNull) as a blank cell`() {
        assertEquals(listOf("2026-07-24,"), csvDataLines(sliderOnly, entry("""{"energy":null}""")))
    }

    // Pre-existing behaviour for ordinary number fields: blanking one stores JsonNull too.
    @Test
    fun `csv renders a blanked plain integer field (JsonNull) as a blank cell`() {
        assertEquals(
            listOf("2026-07-24,,,,,,,0"),
            csvDataLines(exampleQuestionnaire, entry("""{"sleep":null}""")),
        )
    }

    // ---- JSON -----------------------------------------------------------------------------

    private fun buildRoot(
        questionnaire: Questionnaire,
        vararg entries: Entry,
    ): JsonObject = json.decodeFromString(
        JsonObject.serializer(),
        JsonExporter.buildJson(questionnaire, exampleServiceRequest, entries.toList(), authored),
    )

    private fun nestedItem(
        root: JsonObject,
        linkId: String,
    ): JsonObject? = root["item"]!!.jsonArray[0].jsonObject["item"]!!.jsonArray
        .map { it.jsonObject }
        .find { it["linkId"]!!.jsonPrimitive.content == linkId }

    @Test
    fun `json maps a slider value to valueInteger`() {
        val item = nestedItem(buildRoot(sliderOnly, entry("""{"energy":42}""")), "energy")!!
        assertEquals(42, item["answer"]!!.jsonArray[0].jsonObject["valueInteger"]!!.jsonPrimitive.int)
    }

    @Test
    fun `json keeps a slider value of zero`() {
        val item = nestedItem(buildRoot(sliderOnly, entry("""{"energy":0}""")), "energy")!!
        assertEquals(0, item["answer"]!!.jsonArray[0].jsonObject["valueInteger"]!!.jsonPrimitive.int)
    }

    @Test
    fun `json carries no slider labels or extensions`() {
        val item = nestedItem(buildRoot(sliderOnly, entry("""{"energy":42}""")), "energy")!!
        assertEquals(setOf("linkId", "answer"), item.keys)
    }

    @Test
    fun `json omits a never-set slider`() {
        assertNull(nestedItem(buildRoot(sliderOnly, entry("{}")), "energy"))
    }

    @Test
    fun `json omits a cleared slider (JsonNull)`() {
        assertNull(nestedItem(buildRoot(sliderOnly, entry("""{"energy":null}""")), "energy"))
    }

    // JsonNull used to leak through as the literal string "null" for these types.
    @Test
    fun `json omits a JsonNull answer for text and period fields`() {
        val root = buildRoot(exampleQuestionnaire, entry("""{"notes":null,"bedtime":null}"""))
        assertNull(nestedItem(root, "notes"))
        assertNull(nestedItem(root, "bedtime"))
    }
}
