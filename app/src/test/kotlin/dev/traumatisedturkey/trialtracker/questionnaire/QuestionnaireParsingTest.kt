package dev.traumatisedturkey.trialtracker.questionnaire

import dev.traumatisedturkey.trialtracker.fixtures.MAX_VALUE_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.MIN_VALUE_EXTENSION_URL
import dev.traumatisedturkey.trialtracker.fixtures.MOOD_SYSTEM_URL
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaireJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireParsingTest {
    // Mirrors the itemJson config in FhirQuestionnaireDto.kt (classDiscriminator = "type"),
    // used there for item's second-stage polymorphic decode.
    private val json =
        Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

    @Test
    fun `sample questionnaire json should parse to exactly the sample questionnaire`() {
        val questionnaire = json.decodeFromString(Questionnaire.serializer(), exampleQuestionnaireJson)
        assertEquals(exampleQuestionnaire, questionnaire)
    }

    // The above test renders many of those below technically redundant.
    // But it is good to retain smaller more specific tests.

    // The next two tests exercise FhirQuestionnaireDto.toQuestionnaire()'s resourceType check,
    // via the public parseQuestionnaire entry point.

    @Test
    fun `throws if 'resourceType' is not present`() {
        val obj = Json.parseToJsonElement(exampleQuestionnaireJson).jsonObject
        val questionnareJsonMissingResourceType = Json.encodeToString<JsonObject>(JsonObject(obj - "resourceType"))
        assertThrows(SerializationException::class.java) {
            parseQuestionnaire(questionnareJsonMissingResourceType)
        }
    }

    @Test
    fun `throws if 'resourceType' does not have value 'Questionnaire'`() {
        val obj = Json.parseToJsonElement(exampleQuestionnaireJson).jsonObject
        val questionnaireJsonWrongResourceType =
            Json.encodeToString<JsonObject>(JsonObject(obj + ("resourceType" to JsonPrimitive("WrongType"))))
        assertThrows(SerializationException::class.java) {
            parseQuestionnaire(questionnaireJsonWrongResourceType)
        }
    }

    @Test
    fun `dispatches each field to its correct sealed subtype`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val byId = questionnaire.itemGroup().associateBy { it.linkId }

        assertTrue(byId["mood"] is FieldDef.ChoiceField)
        assertTrue(byId["sleep"] is FieldDef.IntegerField)
        assertTrue(byId["weight"] is FieldDef.DecimalField)
        assertTrue(byId["notes"] is FieldDef.TextField)
        assertTrue(byId["logged_at"] is FieldDef.DateField)
        assertTrue(byId["bedtime"] is FieldDef.PeriodField)
    }

    @Test
    fun `choice field parses its choices in order`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val pain = questionnaire.itemGroup().first { it.linkId == "mood" } as FieldDef.ChoiceField
        assertEquals(
            listOf(
                Choice(Coding(MOOD_SYSTEM_URL, JsonPrimitive("1"), "Low")),
                Choice(Coding(MOOD_SYSTEM_URL, JsonPrimitive("2"), "Med")),
                Choice(Coding(MOOD_SYSTEM_URL, JsonPrimitive("3"), "High")),
                Choice(Coding(MOOD_SYSTEM_URL, JsonPrimitive("other"), "Other")),
            ),
            pain.choices,
        )
    }

    @Test
    fun `choice code and system come from the valueCoding entry`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val mood = questionnaire.itemGroup().first { it.linkId == "mood" } as FieldDef.ChoiceField

        // FHIR's Coding.code is always string-typed, so both are strings here. Coding.code is
        // still typed as JsonPrimitive rather than String for now (see FieldDef.kt), but nothing
        // currently exercises a non-string code now that the legacy shape is no longer accepted.
        val numericLookingChoice = mood.choices[0].valueCoding
        assertEquals(true, numericLookingChoice.code.isString)
        assertEquals("1", numericLookingChoice.code.content)
        assertEquals(MOOD_SYSTEM_URL, numericLookingChoice.system)

        val wordChoice = mood.choices[3].valueCoding
        assertEquals(true, wordChoice.code.isString)
        assertEquals("other", wordChoice.code.content)
        assertEquals(MOOD_SYSTEM_URL, wordChoice.system)
    }

    @Test
    fun `choice field parses 'valueCoding' shape, including its 'system'`() {
        val choiceFieldJson =
            """
                { "linkId": "mood", "type": "choice", "answerOption": [
                    { "valueCoding": { "system": "https://example.com/fhir/CodeSystem/mood",
                                        "code": "1", "display": "Low" } },
                    { "valueCoding": { "system": "https://example.com/fhir/CodeSystem/mood",
                                        "code": "other", "display": "Other" } } ] }
            """.trimIndent()
        val field = json.decodeFromString(FieldDef.serializer(), choiceFieldJson) as FieldDef.ChoiceField
        assertEquals(
            listOf(
                Choice(Coding("https://example.com/fhir/CodeSystem/mood", JsonPrimitive("1"), "Low")),
                Choice(Coding("https://example.com/fhir/CodeSystem/mood", JsonPrimitive("other"), "Other")),
            ),
            field.choices,
        )
    }

    @Test
    fun `choice field throws if 'valueCoding' is missing (legacy 'value' 'label' shape no longer supported)`() {
        val choiceFieldJson =
            """
                { "linkId": "mood", "type": "choice", "answerOption": [
                    { "value": 1, "label": "Low" } ] }
            """.trimIndent()
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(FieldDef.serializer(), choiceFieldJson)
        }
    }

    @Test
    fun `integer field parses min and max`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val hrv = questionnaire.itemGroup().first { it.linkId == "sleep" } as FieldDef.IntegerField
        assertEquals(1, hrv.min)
        assertEquals(9, hrv.max)
    }

    @Test
    fun `integer field throws if minValue extension is missing`() {
        assertThrows(SerializationException::class.java) {
            FieldDef.IntegerField(
                linkId = "no_min",
                extension = listOf(Extension(url = MAX_VALUE_EXTENSION_URL, valueInteger = 9)),
            )
        }
    }

    @Test
    fun `integer field throws if maxValue extension is missing`() {
        assertThrows(SerializationException::class.java) {
            FieldDef.IntegerField(
                linkId = "no_max",
                extension = listOf(Extension(url = MIN_VALUE_EXTENSION_URL, valueInteger = 1)),
            )
        }
    }

    @Test
    fun `Decimal field throws if minValue or maxValue extension is missing`() {
        assertThrows(SerializationException::class.java) {
            FieldDef.DecimalField(linkId = "no_bounds")
        }
    }

    @Test
    fun `period field has an id but no min or max`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val bedTime = questionnaire.itemGroup().first { it.linkId == "bedtime" } as FieldDef.PeriodField
        assertEquals("bedtime", bedTime.linkId)
    }

    @Test
    fun `field with a questionnaire-hidden extension is reported as hidden`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val loggedAt = questionnaire.itemGroup().first { it.linkId == "logged_at" }
        assertTrue(loggedAt.hidden)
    }

    @Test
    fun `field with no extension is not hidden and has an empty extension list`() {
        val questionnaire = parseQuestionnaire(exampleQuestionnaireJson)
        val bedTime = questionnaire.itemGroup().first { it.linkId == "bedtime" }
        assertEquals(false, bedTime.hidden)
        assertEquals(emptyList<Extension>(), bedTime.extension)
    }
}
