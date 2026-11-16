package dev.catzeyea.thorbro.core.library

import dev.catzeyea.thorbro.core.fs.ThorFileSystem
import dev.catzeyea.thorbro.core.model.GameLibrary

/**
 * Findet die Game-Library-Ordner auf dem Thor automatisch.
 * Wird von beiden Apps genutzt: PC als Schnellzugriffe ("Start"), Thor als Ziele beim Einsortieren.
 *
 * TODO: Erkennungsregeln festlegen, sobald klar ist, wie die Libraries aufgebaut sind
 * (Frontend-Struktur wie ROMs/<system> oder eigene Ordner pro Emulator).
 */
class LibraryFinder(private val fs: ThorFileSystem) {
    suspend fun find(): List<GameLibrary> {
        TODO("Erkennungsregeln noch offen")
    }
}
