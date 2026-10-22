package dev.traumatisedturkey.trialtracker.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderValueMappingTest {
    // width 120 with inset 10 => the track runs from x=10 to x=110, i.e. 100px usable for 0..100.
    private val width = 120f
    private val inset = 10f

    private fun at(x: Float, min: Int = 0, max: Int = 100) = valueForX(x, width, inset, min, max)

    @Test
    fun `start of track maps to min and end of track maps to max`() {
        assertEquals(0, at(10f))
        assertEquals(100, at(110f))
    }

    @Test
    fun `positions along the track map linearly`() {
        assertEquals(25, at(35f))
        assertEquals(50, at(60f))
        assertEquals(75, at(85f))
    }

    @Test
    fun `touches beyond either end clamp to min and max`() {
        assertEquals(0, at(0f))
        assertEquals(0, at(-50f))
        assertEquals(100, at(120f))
        assertEquals(100, at(500f))
    }

    @Test
    fun `fractional positions round to the nearest integer`() {
        assertEquals(0, at(10.4f))
        assertEquals(1, at(10.6f))
    }

    @Test
    fun `a non-default range maps its own endpoints`() {
        assertEquals(1, at(10f, min = 1, max = 10))
        assertEquals(10, at(110f, min = 1, max = 10))
    }

    @Test
    fun `a degenerate zero-width track does not crash and stays in range`() {
        val v = valueForX(5f, 20f, 10f, 0, 100)
        assertEquals(true, v in 0..100)
    }
}
