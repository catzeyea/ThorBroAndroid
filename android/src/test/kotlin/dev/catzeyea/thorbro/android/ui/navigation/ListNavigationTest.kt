package dev.catzeyea.thorbro.android.ui.navigation

import dev.catzeyea.thorbro.android.ui.navigation.ListNavigation.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListNavigationTest {

    @Test
    fun `up and down move one row`() {
        assertEquals(Step.To(6), ListNavigation.step(focused = 5, count = 40, down = true))
        assertEquals(Step.To(4), ListNavigation.step(focused = 5, count = 40, down = false))
    }

    @Test
    fun `only up on the first row leaves the list`() {
        assertEquals(Step.LeaveUp, ListNavigation.step(focused = 0, count = 40, down = false))
        assertEquals(Step.To(0), ListNavigation.step(focused = 1, count = 40, down = false))
    }

    @Test
    fun `down on the last row stays`() {
        assertEquals(Step.Stay, ListNavigation.step(focused = 39, count = 40, down = true))
    }

    @Test
    fun `an index out of range is treated as the nearest row`() {
        assertEquals(Step.Stay, ListNavigation.step(focused = 99, count = 40, down = true))
        assertEquals(Step.To(38), ListNavigation.step(focused = 99, count = 40, down = false))
        assertEquals(Step.LeaveUp, ListNavigation.step(focused = 0, count = 0, down = false))
        assertEquals(Step.Stay, ListNavigation.step(focused = 0, count = 0, down = true))
    }

    @Test
    fun `25 fast ups from the last row end on row 14 and never leave`() {
        var focused = 39
        repeat(25) {
            val step = ListNavigation.step(focused, 40, down = false)
            focused = (step as Step.To).index
        }
        assertEquals(14, focused)
    }

    @Test
    fun `scroll only when the target is not fully on screen`() {
        // Rows 10..17 fully visible.
        assertNull(ListNavigation.scrollFor(target = 12, firstFull = 10, lastFull = 17))
        // Up past the top: the target becomes the top row.
        assertEquals(9, ListNavigation.scrollFor(target = 9, firstFull = 10, lastFull = 17))
        // Down past the bottom: the target becomes the bottom row (top = 11).
        assertEquals(11, ListNavigation.scrollFor(target = 18, firstFull = 10, lastFull = 17))
        // Nothing laid out yet: scroll to the target.
        assertEquals(5, ListNavigation.scrollFor(target = 5, firstFull = -1, lastFull = -1))
        // Never a negative index.
        assertEquals(0, ListNavigation.scrollFor(target = 0, firstFull = 3, lastFull = 1))
    }
}
