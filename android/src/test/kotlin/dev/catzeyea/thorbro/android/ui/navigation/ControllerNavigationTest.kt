package dev.catzeyea.thorbro.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1/R1 tab switching, B, and which keys count as "activate". */
class ControllerNavigationTest {

    // Values of android.view.KeyEvent constants, written out so the test also documents them.
    private val l1 = 102 // KEYCODE_BUTTON_L1
    private val r1 = 103 // KEYCODE_BUTTON_R1
    private val a = 96 // KEYCODE_BUTTON_A
    private val b = 97 // KEYCODE_BUTTON_B
    private val dpadDown = 20 // KEYCODE_DPAD_DOWN
    private val dpadCenter = 23 // KEYCODE_DPAD_CENTER
    private val enter = 66 // KEYCODE_ENTER
    private val back = 4 // KEYCODE_BACK

    @Test
    fun `tabs are Stream, Games, Setup, About and Stream is the start tab`() {
        assertEquals(listOf("Stream", "Games", "Setup", "About"), AppTab.entries.map { it.title })
        assertEquals(AppTab.Stream, AppTab.START)
    }

    @Test
    fun `R1 moves to the next tab`() {
        assertEquals(NavCommand.ShowTab(AppTab.Games), ControllerNavigation.commandFor(r1, AppTab.Stream, false))
        assertEquals(NavCommand.ShowTab(AppTab.Setup), ControllerNavigation.commandFor(r1, AppTab.Games, false))
        assertEquals(NavCommand.ShowTab(AppTab.About), ControllerNavigation.commandFor(r1, AppTab.Setup, false))
    }

    @Test
    fun `L1 moves to the previous tab`() {
        assertEquals(NavCommand.ShowTab(AppTab.Setup), ControllerNavigation.commandFor(l1, AppTab.About, false))
        assertEquals(NavCommand.ShowTab(AppTab.Games), ControllerNavigation.commandFor(l1, AppTab.Setup, false))
        assertEquals(NavCommand.ShowTab(AppTab.Stream), ControllerNavigation.commandFor(l1, AppTab.Games, false))
    }

    @Test
    fun `L1 on the first tab and R1 on the last tab do not wrap around`() {
        assertEquals(NavCommand.Consume, ControllerNavigation.commandFor(l1, AppTab.Stream, false))
        assertEquals(NavCommand.Consume, ControllerNavigation.commandFor(r1, AppTab.About, false))
    }

    @Test
    fun `L1 and R1 do nothing while a sub-screen is open`() {
        assertEquals(NavCommand.Consume, ControllerNavigation.commandFor(l1, AppTab.Games, true))
        assertEquals(NavCommand.Consume, ControllerNavigation.commandFor(r1, AppTab.Games, true))
    }

    @Test
    fun `B closes a sub-screen`() {
        assertEquals(NavCommand.CloseSubScreen, ControllerNavigation.commandFor(b, AppTab.About, true))
    }

    @Test
    fun `B on a main tab is consumed and does nothing`() {
        AppTab.entries.forEach { tab ->
            assertEquals(NavCommand.Consume, ControllerNavigation.commandFor(b, tab, false))
        }
    }

    @Test
    fun `A, D-pad and system back are left to Android and Compose`() {
        listOf(a, dpadDown, dpadCenter, enter, back).forEach { key ->
            assertNull("key $key", ControllerNavigation.commandFor(key, AppTab.Stream, false))
            assertNull("key $key", ControllerNavigation.commandFor(key, AppTab.Stream, true))
        }
    }

    @Test
    fun `A, D-pad center and Enter activate the focused element`() {
        assertTrue(ControllerKeys.isConfirm(a))
        assertTrue(ControllerKeys.isConfirm(dpadCenter))
        assertTrue(ControllerKeys.isConfirm(enter))
        assertFalse(ControllerKeys.isConfirm(b))
        assertFalse(ControllerKeys.isConfirm(dpadDown))
        assertFalse(ControllerKeys.isConfirm(l1))
    }

    @Test
    fun `controller buttons are recognized by key code`() {
        assertEquals(ControllerButton.L1, ControllerKeys.buttonFor(l1))
        assertEquals(ControllerButton.R1, ControllerKeys.buttonFor(r1))
        assertEquals(ControllerButton.A, ControllerKeys.buttonFor(a))
        assertEquals(ControllerButton.B, ControllerKeys.buttonFor(b))
        assertNull(ControllerKeys.buttonFor(dpadDown))
    }

    @Test
    fun `saved tab name is restored, unknown names fall back to Stream`() {
        assertEquals(AppTab.Setup, AppTab.fromName("Setup"))
        assertEquals(AppTab.Stream, AppTab.fromName(null))
        assertEquals(AppTab.Stream, AppTab.fromName("Status"))
    }
}
