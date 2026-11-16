package dev.catzeyea.thorbro.core.model

/** Ein Ordner auf dem Thor, in dem die Games für ein System liegen. */
data class GameLibrary(
    val system: GameSystem,
    val path: String,
    /** true = automatisch gefunden, false = vom User in den Einstellungen gesetzt */
    val autoDetected: Boolean,
)

/** Wie sicher die Konsolenerkennung ist. */
enum class Confidence { EXTENSION, HEADER, UNKNOWN }

/** Ein Game, das beim Durchsuchen (z.B. Downloads) gefunden wurde. */
data class DetectedGame(
    val path: String,
    val sizeBytes: Long,
    val system: GameSystem?,
    val confidence: Confidence,
    /** Vorschlag, wohin es verschoben wird. Kann der User pro Durchlauf ändern. */
    val target: GameLibrary?,
)
