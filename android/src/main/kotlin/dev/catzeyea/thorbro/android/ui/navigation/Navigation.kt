package dev.catzeyea.thorbro.android.ui.navigation

import android.view.KeyEvent

// Controller navigation as plain logic, so it can be unit-tested without a device.
// MainActivity feeds key codes in and applies the returned NavCommand.

/** The main tabs, in screen order. */
enum class AppTab(val title: String) {
    Stream("Stream"),
    Games("Games"),
    Setup("Setup"),
    About("About");

    /** The tab to the left; the first tab stays where it is (no wrap-around). */
    fun previous(): AppTab = entries[(ordinal - 1).coerceAtLeast(0)]

    /** The tab to the right; the last tab stays where it is (no wrap-around). */
    fun next(): AppTab = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]

    companion object {
        val START = Stream

        /** For restoring the tab after Android re-creates the activity. Unknown or null names give [START]. */
        fun fromName(name: String?): AppTab = entries.firstOrNull { it.name == name } ?: START
    }
}

/** A screen shown on top of the tabs. B (or the system back gesture) closes it. */
sealed interface SubScreen {
    data class LaunchError(val message: String) : SubScreen

    data class FontLicense(val text: String) : SubScreen
}

/** Game controller buttons the app reacts to. D-pad and stick are handled by Compose's focus system. */
enum class ControllerButton { L1, R1, A, B }

object ControllerKeys {

    fun buttonFor(keyCode: Int): ControllerButton? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerButton.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerButton.R1
        KeyEvent.KEYCODE_BUTTON_A -> ControllerButton.A
        KeyEvent.KEYCODE_BUTTON_B -> ControllerButton.B
        else -> null
    }

    /**
     * Keys that activate the focused element: the controller's A button, plus D-pad center and Enter
     * (keyboards, TV remotes, and controllers that report A as D-pad center).
     */
    fun isConfirm(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> true
        else -> false
    }
}

/** What a navigation key should do. */
sealed interface NavCommand {
    data class ShowTab(val tab: AppTab) : NavCommand

    data object CloseSubScreen : NavCommand

    /** The key belongs to navigation but there is nothing to do (L1 on the first tab, B on a main tab). */
    data object Consume : NavCommand
}

object ControllerNavigation {

    /**
     * Maps a key to a navigation command.
     *
     * Returns null for every key that is not L1, R1 or B, so the caller passes it on (D-pad moves focus,
     * A is handled by the focused element). B is always consumed: on a main tab it does nothing, so it
     * never falls back to Android's Back key and closes the app by accident.
     */
    fun commandFor(keyCode: Int, currentTab: AppTab, subScreenOpen: Boolean): NavCommand? =
        when (ControllerKeys.buttonFor(keyCode)) {
            ControllerButton.L1 -> if (subScreenOpen) NavCommand.Consume else switchTo(currentTab, currentTab.previous())
            ControllerButton.R1 -> if (subScreenOpen) NavCommand.Consume else switchTo(currentTab, currentTab.next())
            ControllerButton.B -> if (subScreenOpen) NavCommand.CloseSubScreen else NavCommand.Consume
            ControllerButton.A, null -> null
        }

    private fun switchTo(current: AppTab, target: AppTab): NavCommand =
        if (target == current) NavCommand.Consume else NavCommand.ShowTab(target)
}
