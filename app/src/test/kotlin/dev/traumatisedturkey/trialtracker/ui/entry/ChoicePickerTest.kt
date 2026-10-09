package dev.traumatisedturkey.trialtracker.ui.entry

import dev.traumatisedturkey.trialtracker.questionnaire.Choice
import dev.traumatisedturkey.trialtracker.questionnaire.Coding
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert
import org.junit.Test

class ChoicePickerTest {

    @Test
    fun `numeric-typed code displays as code dash display`() {
        // Coding.code is typed as JsonPrimitive (not String), so this also checks displayText()
        // doesn't care whether the underlying primitive is int- or string-typed.
        val choice = Choice(Coding(code = JsonPrimitive(2), display = "Mild"))
        Assert.assertEquals("2 - Mild", choice.displayText())
    }

    @Test
    fun `string code displays unquoted`() {
        val choice = Choice(Coding(code = JsonPrimitive("unknown"), display = "Prefer not to say"))
        Assert.assertEquals("unknown - Prefer not to say", choice.displayText())
    }

    @Test
    fun `display containing its own separator is not altered`() {
        // The display text is used verbatim; only code/display are joined with " - ", so a
        // display that happens to contain " - " itself should pass through unchanged rather
        // than being split or escaped.
        val choice = Choice(Coding(code = JsonPrimitive("1"), display = "None - no symptoms"))
        Assert.assertEquals("1 - None - no symptoms", choice.displayText())
    }
}
