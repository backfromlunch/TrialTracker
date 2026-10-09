package dev.traumatisedturkey.trialtracker.fixtures

import dev.traumatisedturkey.trialtracker.questionnaire.Choice
import dev.traumatisedturkey.trialtracker.questionnaire.Coding
import dev.traumatisedturkey.trialtracker.questionnaire.Extension
import dev.traumatisedturkey.trialtracker.questionnaire.FieldDef
import dev.traumatisedturkey.trialtracker.questionnaire.Questionnaire
import dev.traumatisedturkey.trialtracker.questionnaire.SingleItem
import kotlinx.serialization.json.JsonPrimitive

const val HIDDEN_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/questionnaire-hidden"
const val MIN_VALUE_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/minValue"
const val MAX_VALUE_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/maxValue"
const val MOOD_SYSTEM_URL = "https://example.com/fhir/CodeSystem/mood"

// This sample questionnaire should cover every kind of FieldDef.
val exampleQuestionnaire = Questionnaire(
    title = "Test Trial",
    version = "1.2.3",
    id = "id001",
    url = "https://foo.com/bar",
    status = "active",
    item = listOf(
        SingleItem(
            linkId = "LogEntry",
            text = "Log entry",
            type = "group",
            repeats = true,
            item = listOf(
                FieldDef.IntegerField(
                    linkId = "sleep",
                    extension = listOf(
                        Extension(url = MIN_VALUE_EXTENSION_URL, valueInteger = 1),
                        Extension(url = MAX_VALUE_EXTENSION_URL, valueInteger = 9),
                    ),
                ),
                FieldDef.DecimalField(
                    linkId = "weight",
                    extension = listOf(
                        Extension(url = MIN_VALUE_EXTENSION_URL, valueDecimal = 40.0f),
                        Extension(url = MAX_VALUE_EXTENSION_URL, valueDecimal = 200.0f),
                    ),
                ),
                FieldDef.TextField(linkId = "notes"),
                FieldDef.ChoiceField(
                    linkId = "mood",
                    choices = listOf(
                        Choice(Coding(system = MOOD_SYSTEM_URL, code = JsonPrimitive("1"), display = "Low")),
                        Choice(Coding(system = MOOD_SYSTEM_URL, code = JsonPrimitive("2"), display = "Med")),
                        Choice(Coding(system = MOOD_SYSTEM_URL, code = JsonPrimitive("3"), display = "High")),
                        Choice(Coding(system = MOOD_SYSTEM_URL, code = JsonPrimitive("other"), display = "Other")),
                    ),
                ),
                FieldDef.DateField(
                    linkId = "logged_at",
                    extension = listOf(Extension(url = HIDDEN_EXTENSION_URL, valueBoolean = true)),
                ),
                FieldDef.PeriodField(linkId = "bedtime"),
            ),
        ),
    ),
)

// This JSON example should parse to exactly the questionnaire above.
val exampleQuestionnaireJson =
    """
        {
          "resourceType": "Questionnaire",
          "title": "Test Trial",
          "version": "1.2.3",
          "id": "id001",
          "url": "https://foo.com/bar",
          "status": "active",
          "item": [
            { "linkId": "LogEntry",
              "text": "Log entry",
              "type": "group",
              "repeats": true,
              "item": [
              { "linkId": "sleep", "type": "integer", "extension": [
                  { "url": "$MIN_VALUE_EXTENSION_URL", "valueInteger": 1 },
                  { "url": "$MAX_VALUE_EXTENSION_URL", "valueInteger": 9 } ] },
              { "linkId": "weight", "type": "decimal", "extension": [
                  { "url": "$MIN_VALUE_EXTENSION_URL", "valueDecimal": 40.0 },
                  { "url": "$MAX_VALUE_EXTENSION_URL", "valueDecimal": 200.0 } ] },
              { "linkId": "notes", "type": "text" },
              { "linkId": "mood", "type": "choice", "answerOption": [
                  { "valueCoding": { "system": "$MOOD_SYSTEM_URL", "code": "1", "display": "Low" } },
                  { "valueCoding": { "system": "$MOOD_SYSTEM_URL", "code": "2", "display": "Med" } },
                  { "valueCoding": { "system": "$MOOD_SYSTEM_URL", "code": "3", "display": "High" } },
                  { "valueCoding": { "system": "$MOOD_SYSTEM_URL", "code": "other", "display": "Other" } } ] },
              { "linkId": "logged_at", "type": "date", "extension": [
                  { "url": "$HIDDEN_EXTENSION_URL", "valueBoolean": true } ] },
              { "linkId": "bedtime", "type": "period" }
              ]
            }
          ]
        }
    """.trimIndent()

fun Questionnaire.withGroupItems(linkId: String, transform: (List<FieldDef>) -> List<FieldDef>): Questionnaire = copy(
    item = item.map { group ->
        if (group.linkId == linkId) group.copy(item = transform(group.item)) else group
    },
)
fun integerField(linkId: String, min: Int? = null, max: Int? = null): FieldDef.IntegerField = FieldDef.IntegerField(
    linkId = linkId,
    extension = listOfNotNull(
        min?.let { Extension(url = MIN_VALUE_EXTENSION_URL, valueInteger = it) },
        max?.let { Extension(url = MAX_VALUE_EXTENSION_URL, valueInteger = it) },
    ),
)

fun decimalField(linkId: String, min: Float? = null, max: Float? = null): FieldDef.DecimalField = FieldDef.DecimalField(
    linkId = linkId,
    extension = listOfNotNull(
        min?.let { Extension(url = MIN_VALUE_EXTENSION_URL, valueDecimal = it) },
        max?.let { Extension(url = MAX_VALUE_EXTENSION_URL, valueDecimal = it) },
    ),
)
