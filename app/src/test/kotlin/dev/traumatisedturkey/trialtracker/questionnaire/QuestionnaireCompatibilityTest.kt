package dev.traumatisedturkey.trialtracker.questionnaire

import dev.traumatisedturkey.trialtracker.fixtures.decimalField
import dev.traumatisedturkey.trialtracker.fixtures.exampleQuestionnaire
import dev.traumatisedturkey.trialtracker.fixtures.integerField
import dev.traumatisedturkey.trialtracker.fixtures.withGroupItems
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireCompatibilityTest {

    private fun reasons(result: QuestionnaireCompatibility): List<String> = (result as QuestionnaireCompatibility.Incompatible).reasons

    @Test
    fun `identical questionnaire is compatible`() {
        val candidate = exampleQuestionnaire.copy()
        assertEquals(
            QuestionnaireCompatibility.Compatible,
            checkSuperset(exampleQuestionnaire, candidate),
        )
    }

    @Test
    fun `adding a brand-new field is compatible`() {
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields + FieldDef.DateField(linkId = "newField")
        }
        assertEquals(
            QuestionnaireCompatibility.Compatible,
            checkSuperset(exampleQuestionnaire, candidate),
        )
    }

    @Test
    fun `removing an existing field is incompatible`() {
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.filterNot { it.linkId == "bedtime" }
        }
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("bedtime") && it.contains("missing") })
    }

    @Test
    fun `narrowing an existing field's range is incompatible`() {
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map {
                if (it.linkId == "sleep") integerField(linkId = "sleep", min = 2, max = 8) else it
            }
        }
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("sleep") })
    }

    @Test
    fun `widening an existing field's range is also incompatible`() {
        // Any change to an existing field is rejected - including widening.
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map {
                if (it.linkId == "sleep") integerField(linkId = "sleep", min = 0, max = 10) else it
            }
        }
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("sleep") })
    }

    @Test
    fun `removing a choice value from an existing choice field is incompatible`() {
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map {
                if (it.linkId == "mood") {
                    FieldDef.ChoiceField(linkId = "mood", choices = listOf(Choice(Coding(code = JsonPrimitive(3), display = "High"))))
                } else {
                    it
                }
            }
        }
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("mood") })
    }

    @Test
    fun `reordering an existing choice field's choices is incompatible`() {
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map {
                if (it.linkId == "mood") {
                    FieldDef.ChoiceField(linkId = "mood", choices = it.let { f -> (f as FieldDef.ChoiceField).choices.reversed() })
                } else {
                    it
                }
            }
        }
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("mood") })
    }

    @Test
    fun `changing an existing field's type is incompatible`() {
        // change sleep integer to decimal
        val candidate = exampleQuestionnaire.withGroupItems("LogEntry") { fields ->
            fields.map {
                if (it.linkId == "sleep") decimalField(linkId = "sleep", min = 1f, max = 10f) else it
            }
        }
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("sleep") })
    }

    @Test
    fun `mismatched id when existing id is present is incompatible`() {
        val candidate = exampleQuestionnaire.copy(id = "trial2")
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("id") })
    }

    @Test
    fun `version is unconstrained`() {
        val candidate = exampleQuestionnaire.copy(version = "9.9")
        assertEquals(
            QuestionnaireCompatibility.Compatible,
            checkSuperset(exampleQuestionnaire, candidate),
        )
    }

    @Test
    fun `blank title is incompatible`() {
        val candidate = exampleQuestionnaire.copy(title = "   ")
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertTrue(reasons(result).any { it.contains("title") })
    }

    @Test
    fun `non-blank title change is compatible`() {
        val candidate = exampleQuestionnaire.copy(title = "Renamed Trial")
        assertEquals(
            QuestionnaireCompatibility.Compatible,
            checkSuperset(exampleQuestionnaire, candidate),
        )
    }

    @Test
    fun `multiple violations are all reported together`() {
        val candidate = exampleQuestionnaire.copy(
            title = "",
            id = "trial2",
            item = listOf(
                exampleQuestionnaire.item[0].copy(
                    item = exampleQuestionnaire.item[0].item.filterNot { it.linkId == "notes" },
                ),
            ),
        )
        val result = checkSuperset(exampleQuestionnaire, candidate)
        assertTrue(result is QuestionnaireCompatibility.Incompatible)
        assertEquals(3, reasons(result).size)
    }
}
