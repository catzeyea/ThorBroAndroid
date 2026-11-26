package dev.catzeyea.thorbro.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SavesUiTest {

    @Test
    fun `initials are the first letters of the first two words`() {
        assertEquals("CO", initials("Cult of the Lamb"))
        assertEquals("H", initials("Hades"))
        assertEquals("SM", initials("Super Mario Odyssey"))
        assertEquals("0X", initials("01007EF00011E000 x"))
        assertEquals("?", initials(" "))
    }

    @Test
    fun `backups from today say Today`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 26, 17, 0) }.timeInMillis
        val earlier = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 26, 16, 52) }.timeInMillis
        val older = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 22, 21, 10) }.timeInMillis
        assertEquals("Today, 16:52", backupLabel(earlier, now))
        assertEquals("22.11.2026, 21:10", backupLabel(older, now))
    }
}
