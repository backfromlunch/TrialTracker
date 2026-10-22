package dev.traumatisedturkey.trialtracker.ui.entry

import dev.traumatisedturkey.trialtracker.fixtures.counterField
import dev.traumatisedturkey.trialtracker.fixtures.integerField
import dev.traumatisedturkey.trialtracker.fixtures.sliderField
import org.junit.Assert.assertEquals
import org.junit.Test

class SliderFieldLabelTest {
    @Test
    fun `slider label shows the linkId with no numeric range`() {
        assertEquals("mood", fieldLabel(sliderField("mood")))
    }

    @Test
    fun `slider label prefers text over linkId, still with no numeric range`() {
        assertEquals("Current Mood", fieldLabel(sliderField("mood").copy(text = "Current Mood")))
    }

    @Test
    fun `slider label has no range even when min and max are given explicitly`() {
        assertEquals("mood", fieldLabel(sliderField("mood", min = 1, max = 10)))
    }

    @Test
    fun `plain integer label still shows its range`() {
        assertEquals("sleep [1-9]", fieldLabel(integerField("sleep", min = 1, max = 9)))
    }

    @Test
    fun `counter field-type shows bare id`() {
        assertEquals("coffee", fieldLabel(counterField(linkId = "coffee")))
    }
}
