package dev.catzeyea.thorbro.core.model

/**
 * Konsolen, die ThorBro kennt.
 *
 * [extensions] sind nur Endungen, die eindeutig zu genau diesem System gehören.
 * Mehrdeutige Endungen (siehe [AMBIGUOUS_EXTENSIONS]) brauchen eine Header-Prüfung
 * oder eine Rückfrage beim User.
 *
 * Liste ist ein erster Wurf, wird erweitert, sobald klar ist, welche Systeme
 * auf dem Thor wirklich genutzt werden.
 */
enum class GameSystem(val displayName: String, val extensions: Set<String>) {
    SWITCH("Nintendo Switch", setOf("nsp", "xci", "nsz", "xcz")),
    N3DS("Nintendo 3DS", setOf("3ds", "cia")),
    NDS("Nintendo DS", setOf("nds")),
    GBA("Game Boy Advance", setOf("gba")),
    GBC("Game Boy Color", setOf("gbc")),
    GB("Game Boy", setOf("gb")),
    N64("Nintendo 64", setOf("z64", "n64", "v64")),
    SNES("Super Nintendo", setOf("sfc", "smc")),
    NES("NES", setOf("nes")),
    WII("Wii", setOf("wbfs")),
    GAMECUBE_WII("GameCube / Wii", setOf("rvz")),
    PS2("PlayStation 2", emptySet()),
    PSP("PSP", emptySet()),
    PS1("PlayStation", emptySet());

    companion object {
        /** Endungen, die mehreren Systemen gehören können. */
        val AMBIGUOUS_EXTENSIONS = setOf("iso", "chd", "cso", "bin", "cue", "pbp", "zip", "7z")

        /** Eindeutiges System zur Endung, sonst null (unbekannt oder mehrdeutig). */
        fun forExtension(ext: String): GameSystem? {
            val e = ext.lowercase()
            if (e in AMBIGUOUS_EXTENSIONS) return null
            return entries.firstOrNull { e in it.extensions }
        }
    }
}
