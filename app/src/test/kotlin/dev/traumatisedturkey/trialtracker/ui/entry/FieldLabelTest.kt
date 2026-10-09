package dev.traumatisedturkey.trialtracker.ui.entry

import dev.traumatisedturkey.trialtracker.fixtures.decimalField
import dev.traumatisedturkey.trialtracker.fixtures.integerField
import dev.traumatisedturkey.trialtracker.questionnaire.Choice
import dev.traumatisedturkey.trialtracker.questionnaire.Coding
import dev.traumatisedturkey.trialtracker.questionnaire.FieldDef
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert
import org.junit.Test

class FieldLabelTest {

    @Test
    fun `integer field-type shows id with min-max range`() {
        val field = integerField(linkId = "sleep", min = 1, max = 9)
        Assert.assertEquals("sleep [1-9]", fieldLabel(field))
    }

    @Test
    fun `decimal field-type shows id with min-max range`() {
        val field = decimalField(linkId = "weight", min = 40f, max = 200f)
        Assert.assertEquals("weight [40.0-200.0]", fieldLabel(field))
    }

    @Test
    fun `text field-type shows bare id`() {
        val field = FieldDef.TextField(linkId = "notes")
        Assert.assertEquals("notes", fieldLabel(field))
    }

    @Test
    fun `choice field-type shows bare id`() {
        val field = FieldDef.ChoiceField(
            linkId = "pain",
            choices = listOf(
                Choice(
                    Coding(
                        code = JsonPrimitive(
                            1,
                        ),
                        display = "None",
                    ),
                ),
            ),
        )
        Assert.assertEquals("pain", fieldLabel(field))
    }

    @Test
    fun `date field-type shows bare id`() {
        val field = FieldDef.DateField(linkId = "logged_at")
        Assert.assertEquals("logged_at", fieldLabel(field))
    }

    @Test
    fun `arbitrary field-type displays text value in preference to linkId value`() {
        val field = FieldDef.DateField(linkId = "linkid42", text = "text42")
        Assert.assertEquals("text42", fieldLabel(field))
    }
}
