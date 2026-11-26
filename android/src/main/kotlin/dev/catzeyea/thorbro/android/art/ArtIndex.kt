package dev.catzeyea.thorbro.android.art

import dev.catzeyea.thorbro.android.files.Classifier
import dev.catzeyea.thorbro.android.saves.SwitchSaves
import java.io.File

/**
 * Game covers from the art the iiSU launcher keeps on the Thor (ThorBro ships no art). Same layout
 * and matching as the PC app (`art.rs`):
 *
 * `<storage>/Android/media/com.iisulauncher/iiSULauncher/assets/media/roms/consoles/<system>/<ROM name without extension>/icon.(jpg|png)`
 * (square). For a game folder iiSU nests one more level: `<system>/<folder>/<ROM stem>/`.
 *
 * Checked on a real Thor (SD card): 7 system folders, 42 icons, 1024×1024, 9 JPEG + 33 PNG,
 * about 1 MB each. Readable by the app.
 */
class ArtIndex private constructor(
    private val byRel: Map<Pair<String, String>, File>,
    private val byName: Map<Pair<String, String>, File>,
    private val byTitleId: Map<String, File>,
    /** Display names by base title ID, from iiSU's folder names (for games without a ROM file). */
    val namesByTitleId: Map<String, String>,
) {
    val size: Int get() = byRel.size

    /**
     * The icon of a game. [systems]: folder names to try (iiSU uses ES-DE ids), [romRel]: the ROM's
     * path inside the system folder (`Game.nsp`, `Folder/Game.3ds`), [titleId]: base title ID.
     */
    fun find(systems: List<String>, romRel: String?, titleId: String?): File? {
        for (system in systems.map { it.lowercase() }) {
            if (romRel != null) {
                val stem = stripExt(romRel)
                byRel[system to stem.lowercase()]?.let { return it }
                byName[system to stem.substringAfterLast('/').lowercase()]?.let { return it }
                if ('/' in romRel) byName[system to romRel.substringBefore('/').lowercase()]?.let { return it }
            }
        }
        return titleId?.let { byTitleId[it.uppercase()] }
    }

    companion object {
        const val CONSOLES = "Android/media/com.iisulauncher/iiSULauncher/assets/media/roms/consoles"
        private val ICON = Regex("(?i)^icon\\.(jpg|jpeg|png|webp)$")
        private val EXT = Regex("^[A-Za-z0-9]{1,5}$")

        val EMPTY = ArtIndex(emptyMap(), emptyMap(), emptyMap(), emptyMap())

        /** `Folder/Game.3ds` -> `Folder/Game`; a folder name without extension stays. */
        fun stripExt(rel: String): String {
            val dot = rel.lastIndexOf('.')
            if (dot <= 0) return rel
            val ext = rel.substring(dot + 1)
            return if ('/' !in ext && EXT.matches(ext)) rel.substring(0, dot) else rel
        }

        /** Walks the iiSU folders of all [storageRoots] (system/folder[/stem]/icon.*). */
        fun build(storageRoots: List<File>): ArtIndex {
            val icons = mutableListOf<Pair<File, File>>() // (consoles dir, icon)
            for (root in storageRoots) {
                val consoles = File(root, CONSOLES)
                for (system in consoles.listFiles().orEmpty().filter { it.isDirectory }) {
                    for (folder in system.listFiles().orEmpty().filter { it.isDirectory }) {
                        for (entry in folder.listFiles().orEmpty()) {
                            if (entry.isFile && ICON.matches(entry.name)) icons += consoles to entry
                            if (entry.isDirectory) entry.listFiles().orEmpty().filter { it.isFile && ICON.matches(it.name) }.forEach { icons += consoles to it }
                        }
                    }
                }
            }
            return fromIcons(icons)
        }

        fun fromIcons(icons: List<Pair<File, File>>): ArtIndex {
            val byRel = mutableMapOf<Pair<String, String>, File>()
            val byName = mutableMapOf<Pair<String, String>, File>()
            val byTitleId = mutableMapOf<String, File>()
            val names = mutableMapOf<String, String>()
            for ((consoles, icon) in icons) {
                val rel = icon.relativeTo(consoles).path.split('/')
                if (rel.size < 3) continue
                val system = rel[0].lowercase()
                val folder = rel.subList(1, rel.size - 1).joinToString("/")
                val name = rel[rel.size - 2]
                byRel.putIfAbsent(system to folder.lowercase(), icon)
                byName.putIfAbsent(system to name.lowercase(), icon)
                if (system == "switch") {
                    val id = Classifier.switchId(name) ?: continue
                    val (base, rank) = SwitchSaves.baseTitleId(id, name) ?: continue
                    // Only base games (or [BASE]-tagged files) give a game its cover; updates never do.
                    if (rank == 0) {
                        byTitleId.putIfAbsent(base, icon)
                        SwitchSaves.namesFromFileNames(listOf(name))[base]?.let { names.putIfAbsent(base, it) }
                    }
                }
            }
            return ArtIndex(byRel, byName, byTitleId, names)
        }
    }
}
