package dev.catzeyea.thorbro.core

import dev.catzeyea.thorbro.core.model.GameSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameSystemTest {
    @Test
    fun eindeutigeEndungen() {
        assertEquals(GameSystem.SWITCH, GameSystem.forExtension("nsp"))
        assertEquals(GameSystem.SWITCH, GameSystem.forExtension("XCI"))
        assertEquals(GameSystem.GBA, GameSystem.forExtension("gba"))
    }

    @Test
    fun mehrdeutigeEndungenGebenNull() {
        assertNull(GameSystem.forExtension("iso"))
        assertNull(GameSystem.forExtension("chd"))
    }

    @Test
    fun unbekannteEndungGibtNull() {
        assertNull(GameSystem.forExtension("txt"))
    }
}
