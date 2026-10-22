package dev.traumatisedturkey.trialtracker.questionnaire

import dev.traumatisedturkey.trialtracker.fields.FieldDef
import dev.traumatisedturkey.trialtracker.fields.SliderSpec
import dev.traumatisedturkey.trialtracker.fixtures.ITEM_CONTROL_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.ITEM_CONTROL_SYSTEM_URL
import dev.traumatisedturkey.trialtracker.fixtures.MAX_VALUE_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.MIN_VALUE_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.SLIDER_HIGH_LABEL_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.SLIDER_LOW_LABEL_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.sliderField
import dev.traumatisedturkey.trialtracker.fixtures.withGroupItems
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderFieldParsingTest {
    // Mirrors the itemJson config in FhirQuestionnaireDto.kt.
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    // ---- JSON building blocks -------------------------------------------------------------

    private fun itemControl(code: String) = """{ "url": "$ITEM_CONTROL_EXTENSION_URL", "valueCodeableConcept": { "coding": [ { "system": "$ITEM_CONTROL_SYSTEM_URL", "code": "$code" } ] } }"""
    private fun label(url: String, text: String) = """{ "url": "$url", "valueString": "$text" }"""
    private fun bound(url: String, value: Int) = """{ "url": "$url", "valueInteger": $value }"""

    private val sliderControl = itemControl("slider")
    private val lowLabel = label(SLIDER_LOW_LABEL_EXTENSION_URL, "Terrible")
    private val highLabel = label(SLIDER_HIGH_LABEL_EXTENSION_URL, "Excellent")

    private fun fieldJson(
        type: String,
        extensions: List<String>,
        extra: String = "",
    ): String = """{ "linkId": "energy", "type": "$type"$extra, "extension": [ ${extensions.joinToString(", ")} ] }"""

    private fun decode(fieldJson: String): FieldDef = json.decodeFromString(FieldDef.serializer(), fieldJson)

    // Checks the message too, so a test can't pass because of some *other* SerializationException.
    private fun assertRejected(fieldJson: String, messagePart: String) {
        val e = assertThrows(SerializationException::class.java) { decode(fieldJson) }
        assertTrue("unexpected message: ${e.message}", e.message.orEmpty().contains(messagePart))
    }

    // ---- well-formed sliders --------------------------------------------------------------

    @Test
    fun `slider with labels only parses, with default range 0 to 100`() {
        val field = decode(fieldJson("integer", listOf(sliderControl, lowLabel, highLabel))) as FieldDef.IntegerField
        assertEquals(SliderSpec(lowLabel = "Terrible", highLabel = "Excellent"), field.slider)
        assertEquals(0, field.min)
        assertEquals(100, field.max)
    }

    @Test
    fun `parsed slider equals the sliderField fixture`() {
        val field = decode(fieldJson("integer", listOf(sliderControl, lowLabel, highLabel)))
        assertEquals(sliderField("energy", low = "Terrible", high = "Excellent"), field)
    }

    @Test
    fun `slider honours explicit minValue and maxValue`() {
        val field = decode(
            fieldJson(
                "integer",
                listOf(
                    sliderControl,
                    lowLabel,
                    highLabel,
                    bound(MIN_VALUE_EXTENSION_URL, 1),
                    bound(MAX_VALUE_EXTENSION_URL, 10),
                ),
            ),
        ) as FieldDef.IntegerField
        assertEquals(1, field.min)
        assertEquals(10, field.max)
    }

    @Test
    fun `slider with only minValue given uses default maxValue`() {
        val field = decode(
            fieldJson("integer", listOf(sliderControl, lowLabel, highLabel, bound(MIN_VALUE_EXTENSION_URL, 10))),
        ) as FieldDef.IntegerField
        assertEquals(10, field.min)
        assertEquals(100, field.max)
    }

    @Test
    fun `slider parses through the public parseQuestionnaire entry point`() {
        val questionnaire = parseQuestionnaire(
            questionnaireJson(fieldJson("integer", listOf(sliderControl, lowLabel, highLabel))),
        )
        assertEquals(
            sliderField("energy", low = "Terrible", high = "Excellent"),
            questionnaire.itemGroup().single(),
        )
    }

    // ---- plain integer fields are unaffected ----------------------------------------------

    @Test
    fun `plain integer field has no slider`() {
        val field = decode(
            fieldJson("integer", listOf(bound(MIN_VALUE_EXTENSION_URL, 1), bound(MAX_VALUE_EXTENSION_URL, 9))),
        ) as FieldDef.IntegerField
        assertNull(field.slider)
        assertEquals(1, field.min)
        assertEquals(9, field.max)
    }

    @Test
    fun `plain integer field still requires minValue and maxValue`() {
        assertRejected(fieldJson("integer", emptyList()), MIN_VALUE_EXTENSION_URL)
    }

    @Test
    fun `itemControl with a code other than slider is ignored`() {
        val integer = decode(
            fieldJson(
                "integer",
                listOf(itemControl("drop-down"), bound(MIN_VALUE_EXTENSION_URL, 1), bound(MAX_VALUE_EXTENSION_URL, 9)),
            ),
        ) as FieldDef.IntegerField
        assertNull(integer.slider)

        // And on a non-integer type it is still accepted, since it isn't a slider.
        val choice = decode(fieldJson("choice", listOf(itemControl("drop-down")), extra = ""","answerOption": []"""))
        assertTrue(choice is FieldDef.ChoiceField)
    }

    // ---- malformed sliders fail fast ------------------------------------------------------

    @Test
    fun `slider without a low label is rejected`() {
        assertRejected(fieldJson("integer", listOf(sliderControl, highLabel)), "slider-low-label")
    }

    @Test
    fun `slider without a high label is rejected`() {
        assertRejected(fieldJson("integer", listOf(sliderControl, lowLabel)), "slider-high-label")
    }

    @Test
    fun `slider with a blank label is rejected`() {
        val blankLow = label(SLIDER_LOW_LABEL_EXTENSION_URL, "   ")
        assertRejected(fieldJson("integer", listOf(sliderControl, blankLow, highLabel)), "slider-low-label")
    }

    @Test
    fun `slider with minValue equal to maxValue is rejected`() {
        assertRejected(
            fieldJson(
                "integer",
                listOf(
                    sliderControl,
                    lowLabel,
                    highLabel,
                    bound(MIN_VALUE_EXTENSION_URL, 5),
                    bound(MAX_VALUE_EXTENSION_URL, 5),
                ),
            ),
            "minValue must be less than maxValue",
        )
    }

    @Test
    fun `slider with minValue above maxValue is rejected`() {
        assertRejected(
            fieldJson(
                "integer",
                listOf(
                    sliderControl,
                    lowLabel,
                    highLabel,
                    bound(MIN_VALUE_EXTENSION_URL, 50),
                    bound(MAX_VALUE_EXTENSION_URL, 10),
                ),
            ),
            "minValue must be less than maxValue",
        )
    }

    @Test
    fun `slider with only minValue above the default maxValue is rejected`() {
        assertRejected(
            fieldJson("integer", listOf(sliderControl, lowLabel, highLabel, bound(MIN_VALUE_EXTENSION_URL, 150))),
            "minValue must be less than maxValue",
        )
    }

    @Test
    fun `slider labels without the slider itemControl are rejected`() {
        assertRejected(
            fieldJson(
                "integer",
                listOf(bound(MIN_VALUE_EXTENSION_URL, 0), bound(MAX_VALUE_EXTENSION_URL, 100), lowLabel, highLabel),
            ),
            "no 'slider' itemControl",
        )
    }

    @Test
    fun `slider itemControl on a non-integer type is rejected`() {
        val decimalBounds = listOf(
            """{ "url": "$MIN_VALUE_EXTENSION_URL", "valueDecimal": 0.0 }""",
            """{ "url": "$MAX_VALUE_EXTENSION_URL", "valueDecimal": 100.0 }""",
        )
        val slider = listOf(sliderControl, lowLabel, highLabel)

        // Decimal has valid bounds, so the only thing wrong with it is the slider extensions.
        assertRejected(fieldJson("decimal", decimalBounds + slider), "only valid on integer items")
        assertRejected(fieldJson("text", slider), "only valid on integer items")
        assertRejected(fieldJson("date", slider), "only valid on integer items")
        assertRejected(fieldJson("period", slider), "only valid on integer items")
        assertRejected(fieldJson("choice", slider, extra = ""","answerOption": []"""), "only valid on integer items")
    }

    @Test
    fun `slider labels on a non-integer type are rejected`() {
        assertRejected(fieldJson("text", listOf(lowLabel, highLabel)), "only valid on integer items")
    }

    @Test
    fun `malformed slider is rejected through the public parseQuestionnaire entry point`() {
        assertThrows(SerializationException::class.java) {
            parseQuestionnaire(questionnaireJson(fieldJson("integer", listOf(sliderControl, lowLabel))))
        }
    }

    // ---- compatibility --------------------------------------------------------------------

    @Test
    fun `changing a slider label makes a replacement questionnaire incompatible`() {
        val existing = exampleQuestionnaire.withGroupItems("LogEntry") { it + sliderField("energy", "Bad", "Good") }
        val relabelled = exampleQuestionnaire.withGroupItems("LogEntry") { it + sliderField("energy", "Awful", "Good") }

        assertTrue(checkSuperset(existing, relabelled) is QuestionnaireCompatibility.Incompatible)
    }

    @Test
    fun `identical slider is compatible`() {
        val existing = exampleQuestionnaire.withGroupItems("LogEntry") { it + sliderField("energy", "Bad", "Good") }
        val same = exampleQuestionnaire.withGroupItems("LogEntry") { it + sliderField("energy", "Bad", "Good") }

        assertEquals(QuestionnaireCompatibility.Compatible, checkSuperset(existing, same))
    }

    // ---- helper for full-questionnaire tests ----------------------------------------------

    private fun questionnaireJson(fieldJson: String): String = """
        {
          "resourceType": "Questionnaire",
          "id": "q1",
          "url": "https://foo.com/q",
          "version": "1.0.0",
          "status": "active",
          "title": "Slider Test",
          "item": [
            { "linkId": "LogEntry", "text": "Log entry", "type": "group", "repeats": true,
              "item": [ $fieldJson ] }
          ]
        }
    """.trimIndent()
}
