package dev.catzeyea.thorbro.android.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The undo log: one JSON-lines file per move batch in `ThorBro/logs` on internal storage, so it is
 * visible and can be read by hand. Every move is appended right after it happened, so even an
 * interrupted batch is fully recorded.
 *
 * Lines: `{"type":"batch",...}`, then `{"type":"move","from":..,"to":..,"size":..,"copied":..}` per
 * file, and `{"type":"undo","time":..}` once the batch was undone.
 */
class MoveLog(val file: File) {

    data class Entry(val from: File, val to: File, val size: Long, val copied: Boolean)

    fun begin(folder: File, time: Long) = append(
        JsonObject(mapOf("type" to JsonPrimitive("batch"), "time" to JsonPrimitive(time), "folder" to JsonPrimitive(folder.path))),
    )

    fun moved(entry: Entry) = append(
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("move"),
                "from" to JsonPrimitive(entry.from.path),
                "to" to JsonPrimitive(entry.to.path),
                "size" to JsonPrimitive(entry.size),
                "copied" to JsonPrimitive(entry.copied),
            ),
        ),
    )

    fun undone(time: Long) = append(JsonObject(mapOf("type" to JsonPrimitive("undo"), "time" to JsonPrimitive(time))))

    private fun append(line: JsonObject) {
        file.parentFile?.mkdirs()
        file.appendText(line.toString() + "\n")
    }

    private fun lines(): List<JsonObject> =
        if (!file.isFile) emptyList() else file.readLines().filter { it.isNotBlank() }.mapNotNull { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    fun entries(): List<Entry> = lines().filter { it.str("type") == "move" }.mapNotNull { line ->
        val from = line.str("from") ?: return@mapNotNull null
        val to = line.str("to") ?: return@mapNotNull null
        Entry(File(from), File(to), line["size"]?.jsonPrimitive?.longOrNull ?: 0, line["copied"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    val isUndone: Boolean get() = lines().any { it.str("type") == "undo" }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    companion object {
        private const val PREFIX = "moves-"
        private const val SUFFIX = ".jsonl"

        fun create(logDir: File, time: Long): MoveLog {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date(time))
            var file = File(logDir, "$PREFIX$stamp$SUFFIX")
            var n = 2
            while (file.exists()) file = File(logDir, "$PREFIX$stamp.${n++}$SUFFIX")
            return MoveLog(file)
        }

        /** The newest batch that moved something and was not undone yet. */
        fun lastUndoable(logDir: File): MoveLog? =
            logDir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
                ?.sortedByDescending { it.name }
                ?.map(::MoveLog)
                ?.firstOrNull { !it.isUndone && it.entries().isNotEmpty() }
    }
}

/** Undoes a batch: every moved file goes back to its original path, newest first, never overwriting. */
object Undo {
    data class Result(val restored: Int, val problems: List<String>)

    fun run(log: MoveLog, mover: FileMover, time: Long = System.currentTimeMillis()): Result {
        var restored = 0
        val problems = mutableListOf<String>()
        for (entry in log.entries().asReversed()) {
            when (val outcome = mover.moveTo(entry.to, entry.from)) {
                is MoveOutcome.Moved -> restored++
                is MoveOutcome.Skipped -> problems += "${entry.to.name}: ${outcome.reason}"
                is MoveOutcome.Failed -> problems += "${entry.to.name}: ${outcome.reason}"
            }
        }
        log.undone(time)
        return Result(restored, problems)
    }
}
