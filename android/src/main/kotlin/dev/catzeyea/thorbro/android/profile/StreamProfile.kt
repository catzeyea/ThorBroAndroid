package dev.catzeyea.thorbro.android.profile

// The stream profile that ThorBro (PC) builds and sends to this app.
// Format (the only source of truth): docs/STREAM-PROFILE.md in the ThorBro repo,
// format "thorbro.stream-profile", version 1.

data class StreamProfile(
    val version: Int,
    val pc: StreamPc,
    /**
     * Sunshine's app names, in Sunshine's order, exactly as Sunshine has them. Each one is sent to
     * Moonlight as the `AppName` extra, which Moonlight matches exactly (case and spaces).
     */
    val games: List<String>,
)

data class StreamPc(
    /** The PC name Moonlight shows (Sunshine's `hostname`). May be empty. */
    val name: String,
    /**
     * Sunshine's `uniqueid`, Moonlight's `UUID` extra. Kept exactly as received (upper or lower case),
     * because Moonlight stores Sunshine's value unchanged as the host UUID.
     */
    val uniqueid: String,
    /** The PC's recommended LAN IPv4 address. Only a hint for adding the PC in Moonlight. */
    val lanAddress: String?,
    /** The PC's Tailscale IPv4 address, if Tailscale is connected on the PC. */
    val tailscaleAddress: String?,
    /** The PC's Tailscale MagicDNS name, if MagicDNS is on. */
    val tailscaleName: String?,
    /** Sunshine's GameStream HTTP port, or null if the profile has none (then the default applies). */
    val sunshinePort: Int?,
) {
    /** What the UI shows for this PC: the name, or the uniqueid if the name is empty. */
    val displayName: String get() = name.ifBlank { uniqueid }

    /**
     * The text to type into Moonlight's "Add PC" for [address]: the address alone, or `address:port`
     * if Sunshine does not use the default port (rule from the format doc).
     */
    fun addressForMoonlight(address: String): String =
        if (sunshinePort == null || sunshinePort == DEFAULT_SUNSHINE_PORT) address else "$address:$sunshinePort"

    companion object {
        const val DEFAULT_SUNSHINE_PORT = 47989
    }
}
