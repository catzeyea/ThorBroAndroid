package dev.catzeyea.thorbro.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1/R1 tab switching, B, X, Y, Select, and which keys count as "activate". */
class ControllerNavigationTest {

    // Values of android.view.KeyEvent constants, written out so the test also documents them.
    private val l1 = 102 // KEYCODE_BUTTON_L1
    private val r1 = 103 // KEYCODE_BUTTON_R1
    private val a = 96 // KEYCODE_BUTTON_A
    private val b = 97 // KEYCODE_BUTTON_B
    private val x = 99 // KEYCODE_BUTTON_X
    private val y = 100 // KEYCODE_BUTTON_Y
    private val select = 109 // KEYCODE_BUTTON_SELECT
    private val dpadDown = 20 // KEYCODE_DPAD_DOWN
    private val dpadCenter = 23 // KEYCODE_DPAD_CENTER
    private val enter = 66 // KEYCODE_ENTER
    private val back = 4 // KEYCODE_BACK

    private fun cmd(key: Int, tab: AppTab = AppTab.Files, open: SubScreen? = null) = ControllerNavigation.commandFor(key, tab, open)

    @Test
    fun `tabs are Start, Files, Saves, Stream and Start is the start tab`() {
        assertEquals(listOf("Start", "Files", "Saves", "Stream"), AppTab.entries.map { it.title })
        assertEquals(AppTab.Start, AppTab.START)
    }

    @Test
    fun `R1 moves to the next tab`() {
        assertEquals(NavCommand.ShowTab(AppTab.Files), cmd(r1, AppTab.Start))
        assertEquals(NavCommand.ShowTab(AppTab.Saves), cmd(r1, AppTab.Files))
        assertEquals(NavCommand.ShowTab(AppTab.Stream), cmd(r1, AppTab.Saves))
    }

    @Test
    fun `L1 moves to the previous tab`() {
        assertEquals(NavCommand.ShowTab(AppTab.Saves), cmd(l1, AppTab.Stream))
        assertEquals(NavCommand.ShowTab(AppTab.Files), cmd(l1, AppTab.Saves))
        assertEquals(NavCommand.ShowTab(AppTab.Start), cmd(l1, AppTab.Files))
    }

    @Test
    fun `L1 on the first tab and R1 on the last tab do not wrap around`() {
        assertEquals(NavCommand.Consume, cmd(l1, AppTab.Start))
        assertEquals(NavCommand.Consume, cmd(r1, AppTab.Stream))
    }

    @Test
    fun `L1 and R1 do nothing while a sub-screen or the menu is open`() {
        listOf(SubScreen.Menu, SubScreen.Setup, SubScreen.Scan).forEach { open ->
            assertEquals(NavCommand.Consume, cmd(l1, AppTab.Saves, open))
            assertEquals(NavCommand.Consume, cmd(r1, AppTab.Saves, open))
        }
    }

    @Test
    fun `B on a sub-screen is Back, on a tab it goes to the screen`() {
        assertEquals(NavCommand.Back, cmd(b, open = SubScreen.About))
        assertEquals(NavCommand.Back, cmd(b, open = SubScreen.Scan))
        AppTab.entries.forEach { tab -> assertEquals(NavCommand.ScreenKey(ControllerButton.B), cmd(b, tab)) }
    }

    @Test
    fun `Y opens the menu on a tab and closes it again`() {
        AppTab.entries.forEach { tab -> assertEquals(NavCommand.OpenMenu, cmd(y, tab)) }
        assertEquals(NavCommand.CloseMenu, cmd(y, open = SubScreen.Menu))
        assertEquals(NavCommand.Consume, cmd(y, open = SubScreen.Scan))
    }

    @Test
    fun `X and Select go to the current screen`() {
        assertEquals(NavCommand.ScreenKey(ControllerButton.X), cmd(x))
        assertEquals(NavCommand.ScreenKey(ControllerButton.Select), cmd(select))
        assertEquals(NavCommand.ScreenKey(ControllerButton.X), cmd(x, open = SubScreen.Scan))
    }

    @Test
    fun `A, D-pad and system back are left to Android and Compose`() {
        listOf(a, dpadDown, dpadCenter, enter, back).forEach { key ->
            assertNull("key $key", cmd(key))
            assertNull("key $key", cmd(key, open = SubScreen.Setup))
        }
    }

    @Test
    fun `A, D-pad center and Enter activate the focused element`() {
        assertTrue(ControllerKeys.isConfirm(a))
        assertTrue(ControllerKeys.isConfirm(dpadCenter))
        assertTrue(ControllerKeys.isConfirm(enter))
        assertFalse(ControllerKeys.isConfirm(b))
        assertFalse(ControllerKeys.isConfirm(x))
        assertFalse(ControllerKeys.isConfirm(dpadDown))
    }

    @Test
    fun `controller buttons are recognized by key code`() {
        assertEquals(ControllerButton.L1, ControllerKeys.buttonFor(l1))
        assertEquals(ControllerButton.R1, ControllerKeys.buttonFor(r1))
        assertEquals(ControllerButton.A, ControllerKeys.buttonFor(a))
        assertEquals(ControllerButton.B, ControllerKeys.buttonFor(b))
        assertEquals(ControllerButton.X, ControllerKeys.buttonFor(x))
        assertEquals(ControllerButton.Y, ControllerKeys.buttonFor(y))
        assertEquals(ControllerButton.Select, ControllerKeys.buttonFor(select))
        assertNull(ControllerKeys.buttonFor(dpadDown))
    }

    @Test
    fun `saved tab name is restored, unknown names fall back to Start`() {
        assertEquals(AppTab.Saves, AppTab.fromName("Saves"))
        assertEquals(AppTab.Start, AppTab.fromName(null))
        assertEquals(AppTab.Start, AppTab.fromName("Games"))
    }

    @Test
    fun `the key router gives a button to the screen registered last`() {
        val router = KeyRouter()
        val seen = mutableListOf<String>()
        val tab: (ControllerButton) -> Boolean = { seen += "tab"; true }
        val dialog: (ControllerButton) -> Boolean = { seen += "dialog"; false }
        router.push(tab)
        router.push(dialog)
        assertFalse(router.handle(ControllerButton.X))
        router.remove(dialog)
        assertTrue(router.handle(ControllerButton.X))
        assertEquals(listOf("dialog", "tab"), seen)
    }
}
