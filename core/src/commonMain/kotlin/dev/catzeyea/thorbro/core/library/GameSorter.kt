package dev.catzeyea.thorbro.core.library

import dev.catzeyea.thorbro.core.fs.ThorFileSystem
import dev.catzeyea.thorbro.core.model.Confidence
import dev.catzeyea.thorbro.core.model.DetectedGame
import dev.catzeyea.thorbro.core.model.GameLibrary
import dev.catzeyea.thorbro.core.model.GameSystem

/**
 * Feature 1 "Search Downloads":
 * Ordner durchsuchen, Games erkennen, Ziel-Library vorschlagen, auf Wunsch alles verschieben.
 */
class GameSorter(private val fs: ThorFileSystem) {

    /** Durchsucht [folder] (Standard: Download-Ordner) nach Games. */
    suspend fun scan(folder: String, libraries: List<GameLibrary>): List<DetectedGame> {
        // TODO: rekursiv? Archive? Header-Prüfung für mehrdeutige Endungen? (offene Fragen)
        return fs.list(folder)
            .filter { !it.isDirectory }
            .mapNotNull { entry ->
                val ext = entry.name.substringAfterLast('.', "")
                val system = GameSystem.forExtension(ext)
                val ambiguous = ext.lowercase() in GameSystem.AMBIGUOUS_EXTENSIONS
                if (system == null && !ambiguous) return@mapNotNull null
                DetectedGame(
                    path = entry.path,
                    sizeBytes = entry.sizeBytes,
                    system = system,
                    confidence = if (system != null) Confidence.EXTENSION else Confidence.UNKNOWN,
                    target = libraries.firstOrNull { it.system == system },
                )
            }
    }

    /** "Alles verschieben": jedes Game mit Ziel in seine Library schieben. */
    suspend fun moveAll(games: List<DetectedGame>) {
        for (game in games) {
            val target = game.target ?: continue
            val name = game.path.substringAfterLast('/')
            fs.mkdirs(target.path)
            fs.move(game.path, "${target.path}/$name")
        }
    }
}
