package dev.traumatisedturkey.trialtracker.questionnaire

import dev.traumatisedturkey.trialtracker.fields.FieldDef
import dev.traumatisedturkey.trialtracker.fixtures.COUNTER_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.HIDDEN_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.ITEM_CONTROL_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.ITEM_CONTROL_SYSTEM_URL
import dev.traumatisedturkey.trialtracker.fixtures.MAX_VALUE_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.MIN_VALUE_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.SLIDER_HIGH_LABEL_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.SLIDER_LOW_LABEL_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.counterField
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.integerField
import dev.traumatisedturkey.trialtracker.fixtures.withGroupItems
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterFieldParsingTest {
    // Mirrors the itemJson config in FhirQuestionnaireDto.kt.
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // ---- JSON building blocks -------------------------------------------------------------

    private val counterExtension = """{ "url": "$COUNTER_EXTENSION_URL", "valueBoolean": true }"""

    private val sliderControl = """{ "url": "$ITEM_CONTROL_EXTENSION_URL", "valueCodeableConcept": { "coding": [ { "system": "$ITEM_CONTROL_SYSTEM_URL", "code": "slider" } ] } }"""
    private val sliderLow = """{ "url": "$SLIDER_LOW_LABEL_EXTENSION_URL", "valueString": "Low" }"""
    private val sliderHigh = """{ "url": "$SLIDER_HIGH_LABEL_EXTENSION_URL", "valueString": "High" }"""

    private fun fieldJson(
        type: String,
        extensions: List<String>,
        extra: String = "",
    ): String = """{ "linkId": "coffee", "text": "Cups of coffee", "type": "$type"$extra, "extension": [ ${extensions.joinToString(", ")} ] }"""

    private fun decode(fieldJson: String): FieldDef = json.decodeFromString(FieldDef.serializer(), fieldJson)

    // Checks the message too, so a test can't pass because of some *other* SerializationException.
    private fun assertRejected(fieldJson: String, messagePart: String) {
        val e = assertThrows(SerializationException::class.java) { decode(fieldJson) }
        assertTrue("unexpected message: ${e.message}", e.message.orEmpty().contains(messagePart))
    }

    // ---- well-formed counters -------------------------------------------------------------

    @Test
    fun `integer item with the counter extension parses as a CounterField`() {
        val field = decode(fieldJson("integer", listOf(counterExtension)))
        assertTrue(field is FieldDef.CounterField)
        assertEquals("coffee", field.linkId)
        assertEquals("Cups of coffee", field.text)
    }

    @Test
    fun `parsed counter equals the counterField fixture`() {
        val field = decode(fieldJson("integer", listOf(counterExtension)))
        assertEquals(counterField("coffee", text = "Cups of coffee"), field)
    }

    @Test
    fun `integer item without the counter extension is still an IntegerField`() {
        val bounds = listOf(
            """{ "url": "$MIN_VALUE_EXTENSION_URL", "valueInteger": 1 }""",
            """{ "url": "$MAX_VALUE_EXTENSION_URL", "valueInteger": 9 }""",
        )
        assertTrue(decode(fieldJson("integer", bounds)) is FieldDef.IntegerField)
    }

    @Test
    fun `counter tolerates unrelated extensions such as hidden`() {
        val hidden = """{ "url": "$HIDDEN_EXTENSION_URL", "valueBoolean": true }"""
        val field = decode(fieldJson("integer", listOf(counterExtension, hidden)))
        assertTrue(field.hidden)
    }

    @Test
    fun `counter parses through the public parseQuestionnaire entry point`() {
        val questionnaire = parseQuestionnaire(questionnaireJson(fieldJson("integer", listOf(counterExtension))))
        assertEquals(counterField("coffee", text = "Cups of coffee"), questionnaire.itemGroup().single())
    }

    // ---- malformed counters fail fast -----------------------------------------------------

    @Test
    fun `dateTime items are rejected, with a pointer to the counter format`() {
        assertRejected(fieldJson("dateTime", listOf(counterExtension), """, "repeats": true"""), "'integer' item")
    }

    @Test
    fun `unknown item type is rejected`() {
        assertRejected(fieldJson("banana", emptyList()), "Unsupported item type")
    }

    @Test
    fun `counter extension with valueBoolean false is rejected`() {
        val off = """{ "url": "$COUNTER_EXTENSION_URL", "valueBoolean": false }"""
        assertRejected(fieldJson("integer", listOf(off)), "valueBoolean")
    }

    @Test
    fun `counter extension with no valueBoolean is rejected`() {
        val bare = """{ "url": "$COUNTER_EXTENSION_URL" }"""
        assertRejected(fieldJson("integer", listOf(bare)), "valueBoolean")
    }

    @Test
    fun `slider extensions on a counter are rejected`() {
        assertRejected(
            fieldJson("integer", listOf(counterExtension, sliderControl, sliderLow, sliderHigh)),
            "slider extensions",
        )
    }

    @Test
    fun `counter extension on a non-integer type is rejected`() {
        val decimalBounds = listOf(
            """{ "url": "$MIN_VALUE_EXTENSION_URL", "valueDecimal": 0.0 }""",
            """{ "url": "$MAX_VALUE_EXTENSION_URL", "valueDecimal": 100.0 }""",
        )
        val message = "only valid on integer items"

        // Each has otherwise-valid config, so the only thing wrong is the counter extension.
        assertRejected(fieldJson("decimal", decimalBounds + counterExtension), message)
        assertRejected(fieldJson("text", listOf(counterExtension)), message)
        assertRejected(fieldJson("date", listOf(counterExtension)), message)
        assertRejected(fieldJson("period", listOf(counterExtension)), message)
        assertRejected(fieldJson("choice", listOf(counterExtension), extra = """, "answerOption": []"""), message)
    }

    @Test
    fun `malformed counter is rejected through the public parseQuestionnaire entry point`() {
        assertThrows(SerializationException::class.java) {
            parseQuestionnaire(questionnaireJson(fieldJson("integer", listOf("""{ "url": "$COUNTER_EXTENSION_URL" }"""))))
        }
    }

    // ---- compatibility --------------------------------------------------------------------

    @Test
    fun `identical counter is compatible`() {
        val existing = exampleQuestionnaire.withGroupItems("LogEntry") { it + counterField("coffee") }
        val same = exampleQuestionnaire.withGroupItems("LogEntry") { it + counterField("coffee") }

        assertEquals(QuestionnaireCompatibility.Compatible, checkSuperset(existing, same))
    }

    @Test
    fun `changing a counter into another type is incompatible`() {
        val changed = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map { if (it.linkId == "coffee") integerField("coffee", min = 0, max = 20) else it }
        }
        assertTrue(checkSuperset(exampleQuestionnaire, changed) is QuestionnaireCompatibility.Incompatible)
    }

    // ---- helper for full-questionnaire tests ----------------------------------------------

    private fun questionnaireJson(fieldJson: String): String = """
        {
          "resourceType": "Questionnaire",
          "id": "q1",
          "url": "https://foo.com/q",
          "version": "1.0.0",
          "status": "active",
          "title": "Counter Test",
          "item": [
            { "linkId": "LogEntry", "text": "Log entry", "type": "group", "repeats": true,
              "item": [ $fieldJson ] }
          ]
        }
    """.trimIndent()
}
