package dev.catzeyea.thorbro.android.ui.navigation

/**
 * D-pad Up/Down inside a lazy list, as plain logic (unit-tested). The list moves focus itself
 * instead of leaving it to Compose's geometric search: with fast presses (a held D-pad) the next
 * row is often not composed yet, and the search then jumps out of the list (measured on the Thor:
 * 25 fast Up presses ended on the "Stream" tab).
 */
object ListNavigation {

    sealed interface Step {
        /** Focus row [index]. */
        data class To(val index: Int) : Step

        /** Up on the first row: focus may leave the list (toolbar, tab bar). */
        data object LeaveUp : Step

        /** Down on the last row: nothing below, stay. */
        data object Stay : Step
    }

    fun step(focused: Int, count: Int, down: Boolean): Step {
        if (count <= 0) return if (down) Step.Stay else Step.LeaveUp
        val current = focused.coerceIn(0, count - 1)
        return when {
            down && current >= count - 1 -> Step.Stay
            down -> Step.To(current + 1)
            current <= 0 -> Step.LeaveUp
            else -> Step.To(current - 1)
        }
    }

    /**
     * The row to scroll to the top so that [target] is fully on screen, or null if it already is.
     * [firstFull] / [lastFull]: the fully visible rows (-1 = none known yet). Moving down puts the
     * target at the bottom, moving up at the top, so the list scrolls one row at a time.
     */
    fun scrollFor(target: Int, firstFull: Int, lastFull: Int): Int? = when {
        firstFull < 0 || lastFull < firstFull -> target
        target < firstFull -> target
        target > lastFull -> (target - (lastFull - firstFull)).coerceAtLeast(0)
        else -> null
    }
}
