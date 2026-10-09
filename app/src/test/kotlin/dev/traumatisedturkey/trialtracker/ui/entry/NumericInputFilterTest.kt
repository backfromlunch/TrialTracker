package dev.traumatisedturkey.trialtracker.ui.entry

import org.junit.Assert
import org.junit.Test

class NumericInputFilterTest {

    // --- filter() ---

    @Test
    fun `integer filter strips letters`() {
        Assert.assertEquals("72", NumericInputFilter.filter("7a2b", allowDecimal = false))
    }

    @Test
    fun `integer filter strips dots`() {
        Assert.assertEquals("72", NumericInputFilter.filter("7.2", allowDecimal = false))
    }

    @Test
    fun `integer filter strips newlines from paste`() {
        Assert.assertEquals("72", NumericInputFilter.filter("72\n", allowDecimal = false))
    }

    @Test
    fun `decimal filter allows one dot`() {
        Assert.assertEquals("7.2", NumericInputFilter.filter("7.2", allowDecimal = true))
    }

    @Test
    fun `decimal filter drops second dot but keeps trailing digits`() {
        Assert.assertEquals("7.25", NumericInputFilter.filter("7.2.5", allowDecimal = true))
    }

    @Test
    fun `decimal filter strips letters but keeps dot`() {
        Assert.assertEquals("7.2", NumericInputFilter.filter("7a.2b", allowDecimal = true))
    }

    // --- isValid() ---

    @Test
    fun `value within inclusive range is valid`() {
        Assert.assertTrue(NumericInputFilter.isValid("5", min = 1.0, max = 9.0))
    }

    @Test
    fun `value at lower bound is valid`() {
        Assert.assertTrue(NumericInputFilter.isValid("1", min = 1.0, max = 9.0))
    }

    @Test
    fun `value at upper bound is valid`() {
        Assert.assertTrue(NumericInputFilter.isValid("9", min = 1.0, max = 9.0))
    }

    @Test
    fun `value below range is invalid`() {
        Assert.assertFalse(NumericInputFilter.isValid("0", min = 1.0, max = 9.0))
    }

    @Test
    fun `value above range is invalid`() {
        Assert.assertFalse(NumericInputFilter.isValid("10", min = 1.0, max = 9.0))
    }

    @Test
    fun `unparseable text is invalid`() {
        Assert.assertFalse(NumericInputFilter.isValid("", min = 1.0, max = 9.0))
        Assert.assertFalse(NumericInputFilter.isValid(".", min = 1.0, max = 9.0))
    }

    @Test
    fun `partial value matching README example is invalid until complete`() {
        // README scenario: range 10-20, typing "1" then "2" to arrive at "12".
        Assert.assertFalse(NumericInputFilter.isValid("1", min = 10.0, max = 20.0))
        Assert.assertTrue(NumericInputFilter.isValid("12", min = 10.0, max = 20.0))
    }

    // --- normalize() ---

    @Test
    fun `normalize collapses trailing zeros on decimal`() {
        Assert.assertEquals("75.0", NumericInputFilter.normalize("75.00", allowDecimal = true))
    }

    @Test
    fun `normalize on integer field returns plain int string`() {
        Assert.assertEquals("7", NumericInputFilter.normalize("7", allowDecimal = false))
    }

    @Test
    fun `normalize on unparseable text returns null`() {
        Assert.assertNull(NumericInputFilter.normalize("", allowDecimal = true))
    }
}
