package dev.catzeyea.thorbro.android.files

/**
 * Built-in list of known systems, for system folders that have no ES-DE `systeminfo.txt`.
 * Same table as the PC app (ThorBro `src/systems.rs`); keep both in sync.
 *
 * A folder name is matched case-insensitively against the aliases, ignoring spaces, dashes etc.
 */
data class KnownSystem(
    /** Canonical ES-DE folder name, e.g. "snes". */
    val id: String,
    val full: String,
    /** Normalized folder names that mean this system (see [KnownSystems.normalize]). */
    val aliases: List<String>,
    /** Supported extensions, lowercase with dot. */
    val exts: Set<String>,
)

object KnownSystems {

    private fun sys(id: String, full: String, aliases: List<String>, exts: String) =
        KnownSystem(id, full, aliases, exts.split(' ').filter { it.isNotEmpty() }.toSet())

    val ALL: List<KnownSystem> = listOf(
        sys("nes", "Nintendo Entertainment System", listOf("nes", "famicom", "fc"), ".nes .unf .unif .fds .zip .7z"),
        sys("snes", "Super Nintendo", listOf("snes", "superfamicom", "sfc", "supernintendo"), ".sfc .smc .fig .swc .bs .zip .7z"),
        sys("n64", "Nintendo 64", listOf("n64", "nintendo64"), ".n64 .z64 .v64 .ndd .zip .7z"),
        sys("gc", "Nintendo GameCube", listOf("gc", "gamecube", "ngc", "nintendogamecube"), ".iso .gcm .gcz .ciso .rvz .wia .dol .elf"),
        sys("wii", "Nintendo Wii", listOf("wii", "nintendowii"), ".iso .wbfs .rvz .wia .gcz .ciso .wad .dol .elf"),
        sys("wiiu", "Nintendo Wii U", listOf("wiiu", "nintendowiiu"), ".wua .wud .wux .rpx .iso"),
        sys("switch", "Nintendo Switch", listOf("switch", "nintendoswitch", "ns"), ".nsp .xci .nsz .xcz .nro"),
        sys("gb", "Nintendo Game Boy", listOf("gb", "gameboy"), ".gb .zip .7z"),
        sys("gbc", "Nintendo Game Boy Color", listOf("gbc", "gameboycolor"), ".gbc .gb .zip .7z"),
        sys("gba", "Nintendo Game Boy Advance", listOf("gba", "gameboyadvance"), ".gba .zip .7z"),
        sys("nds", "Nintendo DS", listOf("nds", "ds", "nintendods"), ".nds .zip .7z"),
        sys("n3ds", "Nintendo 3DS", listOf("n3ds", "3ds", "nintendo3ds"), ".3ds .3dsx .cci .cia .cxi .app .zcci .zcxi .z3dsx"),
        sys("virtualboy", "Nintendo Virtual Boy", listOf("virtualboy", "vb"), ".vb .zip .7z"),
        sys("psx", "Sony PlayStation", listOf("psx", "ps1", "psone", "playstation", "playstation1"), ".cue .bin .chd .pbp .iso .img .m3u .ecm .ccd .zip .7z"),
        sys("ps2", "Sony PlayStation 2", listOf("ps2", "playstation2"), ".iso .chd .cso .zso .bin .cue .gz .m3u"),
        sys("ps3", "Sony PlayStation 3", listOf("ps3", "playstation3"), ".ps3 .ps3dir .iso"),
        sys("psp", "Sony PlayStation Portable", listOf("psp", "playstationportable"), ".iso .cso .pbp .chd"),
        sys("psvita", "Sony PlayStation Vita", listOf("psvita", "vita", "playstationvita"), ".vpk .psvita"),
        sys("mastersystem", "Sega Master System", listOf("mastersystem", "sms", "segamastersystem"), ".sms .bin .zip .7z"),
        sys("gamegear", "Sega Game Gear", listOf("gamegear", "gg", "segagamegear"), ".gg .bin .zip .7z"),
        sys("megadrive", "Sega Mega Drive", listOf("megadrive", "md", "segamegadrive"), ".md .gen .smd .bin .zip .7z"),
        sys("genesis", "Sega Genesis", listOf("genesis", "segagenesis"), ".md .gen .smd .bin .zip .7z"),
        sys("segacd", "Sega CD", listOf("segacd", "megacd", "segamegacd"), ".cue .chd .iso .m3u"),
        sys("sega32x", "Sega 32X", listOf("sega32x", "32x"), ".32x .bin .zip .7z"),
        sys("saturn", "Sega Saturn", listOf("saturn", "segasaturn"), ".cue .chd .iso .ccd .m3u .zip .7z"),
        sys("dreamcast", "Sega Dreamcast", listOf("dreamcast", "dc", "segadreamcast"), ".cdi .chd .gdi .cue .m3u .zip .7z"),
        sys("pcengine", "NEC PC Engine", listOf("pcengine", "pce", "turbografx16", "turbografx", "tg16"), ".pce .cue .chd .zip .7z"),
        sys("ngpc", "SNK Neo Geo Pocket Color", listOf("ngpc", "neogeopocketcolor", "ngp", "neogeopocket"), ".ngc .ngp .zip .7z"),
        sys("neogeo", "SNK Neo Geo", listOf("neogeo"), ".zip .7z"),
        sys("wonderswan", "Bandai WonderSwan", listOf("wonderswan", "wswan", "wonderswancolor", "wswanc"), ".ws .wsc .zip .7z"),
        sys("atari2600", "Atari 2600", listOf("atari2600", "2600"), ".a26 .bin .zip .7z"),
        sys("arcade", "Arcade", listOf("arcade"), ".zip .7z"),
        sys("mame", "MAME", listOf("mame"), ".zip .7z"),
        sys("fbneo", "FinalBurn Neo", listOf("fbneo", "finalburnneo", "fba"), ".zip .7z"),
        sys("dos", "DOS", listOf("dos", "msdos"), ".bat .com .exe .dosz .zip .7z"),
        sys("scummvm", "ScummVM", listOf("scummvm"), ".scummvm .svm"),
    )

    /** Lowercase letters and digits only: `"Game Boy Advance"` -> `"gameboyadvance"`. */
    fun normalize(name: String): String =
        name.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.lowercase()

    fun byId(id: String): KnownSystem? = ALL.firstOrNull { it.id == id }

    /** The system a folder name stands for (`"Wii"`, `"PS1"`, `"Game Boy Advance"`), if any. */
    fun byFolderName(name: String): KnownSystem? {
        val normalized = normalize(name)
        return ALL.firstOrNull { normalized in it.aliases }
    }
}
