package dev.traumatisedturkey.trialtracker.export

import dev.traumatisedturkey.trialtracker.data.Entry
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.exampleServiceRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

const val N_COMMENT_LINES = 3
const val N_NON_DATA_LINES = N_COMMENT_LINES + 1

class CsvExporterTest {
    private val trialId = "arbitrary-id"
    private val authored = OffsetDateTime.parse("2026-08-08T08:08:08+08:00")

    @Test
    fun `comment param row is correctly formatted`() {
        val csv = CsvExporter.buildCsv(exampleQuestionnaire, exampleServiceRequest, emptyList(), authored)
        assertEquals("# questionnaire: ${exampleQuestionnaire.questionnaireCanonical()}", csv.lines()[0])
        assertEquals("# serviceRequest: ${exampleServiceRequest.serviceRequestValue}", csv.lines()[1])
    }

    @Test
    fun `header row uses date plus field ids in order`() {
        val csv = CsvExporter.buildCsv(exampleQuestionnaire, exampleServiceRequest, emptyList(), authored)
        assertEquals("date,sleep,weight,notes,mood,logged_at,bedtime", csv.lines()[N_COMMENT_LINES])
    }

    @Test
    fun `simple values are rendered without quoting`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":7,"notes":"fine"}"""),
            )
        val csv = CsvExporter.buildCsv(exampleQuestionnaire, exampleServiceRequest, entries, authored)
        val lines = csv.lines().drop(N_NON_DATA_LINES)
        assertEquals("2026-07-24,7,,fine,,,", lines[0])
    }

    @Test
    fun `commas quotes and newlines trigger RFC 4180 quoting`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":7,"notes":"has, a comma"}"""),
                Entry(trialId = trialId, date = "2026-07-25", answersJson = """{"sleep":7,"notes":"has \"quotes\""}"""),
                Entry(trialId = trialId, date = "2026-07-26", answersJson = "{\"sleep\":7,\"notes\":\"line1\\nline2\"}"),
            )
        val csv = CsvExporter.buildCsv(exampleQuestionnaire, exampleServiceRequest, entries, authored)
        val lines = csv.lines().drop(N_NON_DATA_LINES)
        assertEquals("2026-07-24,7,,\"has, a comma\",,,", lines[0])
        assertEquals("2026-07-25,7,,\"has \"\"quotes\"\"\",,,", lines[1])
        // the quoted newline keeps the value on the same logical row but adds a physical line break
        assertEquals("2026-07-26,7,,\"line1", lines[2])
        assertEquals("line2\",,,", lines[3])
    }

    @Test
    fun `missing field renders as blank cell`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-24", answersJson = """{"sleep":7}"""),
            )
        val csv = CsvExporter.buildCsv(exampleQuestionnaire, exampleServiceRequest, entries, authored)
        val lines = csv.lines().drop(N_NON_DATA_LINES)
        assertEquals("2026-07-24,7,,,,,", lines[0])
    }

    @Test
    fun `malformed json falls back to blank row instead of crashing`() {
        val entries =
            listOf(
                Entry(trialId = trialId, date = "2026-07-24", answersJson = "not valid json"),
            )
        val csv = CsvExporter.buildCsv(exampleQuestionnaire, exampleServiceRequest, entries, authored)
        val lines = csv.lines().drop(N_NON_DATA_LINES)
        assertEquals("2026-07-24,,,,,,", lines[0])
    }
}
