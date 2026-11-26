package dev.catzeyea.thorbro.android.ui.navigation

import android.view.KeyEvent

// Controller navigation as plain logic, so it can be unit-tested without a device.
// MainActivity feeds key codes in and applies the returned NavCommand.

/** The main tabs, in screen order. */
enum class AppTab(val title: String) {
    Start("Start"),
    Files("Files"),
    Saves("Saves"),
    Stream("Stream");

    /** The tab to the left; the first tab stays where it is (no wrap-around). */
    fun previous(): AppTab = entries[(ordinal - 1).coerceAtLeast(0)]

    /** The tab to the right; the last tab stays where it is (no wrap-around). */
    fun next(): AppTab = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]

    companion object {
        val START = Start

        /** For restoring the tab after Android re-creates the activity. Unknown or null names give [START]. */
        fun fromName(name: String?): AppTab = entries.firstOrNull { it.name == name } ?: START
    }
}

/** A screen shown on top of the tabs. B (or the system back gesture) closes it. */
sealed interface SubScreen {
    data class LaunchError(val message: String) : SubScreen

    data class FontLicense(val text: String) : SubScreen

    /** The small menu behind Y (top right): Setup, About. Drawn over the current tab. */
    data object Menu : SubScreen

    data object Setup : SubScreen

    data object About : SubScreen

    /** "How it works": one card per tab. */
    data object Help : SubScreen

    /** "What does Scan do?" before a scan (until "Don't show this again"). */
    data class ScanInfo(val folderName: String) : SubScreen

    /** Select in Files: pin / unpin a folder, make it the default scan folder. */
    data class FolderOptions(val path: String) : SubScreen

    /** Scan preview, moving progress and result of "Scan this folder". */
    data object Scan : SubScreen

    /** One game's save: cover, backups, Back up now, Restore selected. */
    data class SaveGame(val saveKey: String) : SubScreen

    /** "Restore selected" asks first. [backupPath]: the backup folder to put back. */
    data class ConfirmRestore(val saveKey: String, val backupPath: String) : SubScreen
}

/** Game controller buttons the app reacts to. D-pad and stick are handled by Compose's focus system. */
enum class ControllerButton { L1, R1, A, B, X, Y, Select }

object ControllerKeys {

    fun buttonFor(keyCode: Int): ControllerButton? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerButton.L1
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerButton.R1
        KeyEvent.KEYCODE_BUTTON_A -> ControllerButton.A
        KeyEvent.KEYCODE_BUTTON_B -> ControllerButton.B
        KeyEvent.KEYCODE_BUTTON_X -> ControllerButton.X
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerButton.Y
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerButton.Select
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

    /** B while a sub-screen is open: the sub-screen may handle it itself (e.g. stop moving), else it closes. */
    data object Back : NavCommand

    data object OpenMenu : NavCommand

    data object CloseMenu : NavCommand

    /** A button for the current screen (X, Select, or B on a tab). The screen decides what it does. */
    data class ScreenKey(val button: ControllerButton) : NavCommand

    /** The key belongs to navigation but there is nothing to do (L1 on the first tab, ...). */
    data object Consume : NavCommand
}

object ControllerNavigation {

    /**
     * Maps a key to a navigation command. [openSubScreen] is the top sub-screen, or null on a tab.
     *
     * Returns null for A, D-pad and every other key, so the caller passes them on (D-pad moves
     * focus, A is handled by the focused element). B is always consumed, so it never falls back to
     * Android's Back key and closes the app by accident.
     */
    fun commandFor(keyCode: Int, currentTab: AppTab, openSubScreen: SubScreen?): NavCommand? {
        val button = ControllerKeys.buttonFor(keyCode) ?: return null
        val open = openSubScreen != null
        return when (button) {
            ControllerButton.L1 -> if (open) NavCommand.Consume else switchTo(currentTab, currentTab.previous())
            ControllerButton.R1 -> if (open) NavCommand.Consume else switchTo(currentTab, currentTab.next())
            ControllerButton.B -> if (open) NavCommand.Back else NavCommand.ScreenKey(ControllerButton.B)
            ControllerButton.Y -> when (openSubScreen) {
                null -> NavCommand.OpenMenu
                SubScreen.Menu -> NavCommand.CloseMenu
                else -> NavCommand.Consume
            }
            ControllerButton.X, ControllerButton.Select -> NavCommand.ScreenKey(button)
            ControllerButton.A -> null
        }
    }

    private fun switchTo(current: AppTab, target: AppTab): NavCommand =
        if (target == current) NavCommand.Consume else NavCommand.ShowTab(target)
}
