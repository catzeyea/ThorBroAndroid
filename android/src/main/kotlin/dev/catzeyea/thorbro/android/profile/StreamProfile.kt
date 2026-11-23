package dev.catzeyea.thorbro.android.profile

// The stream profile that ThorBro (PC) sends to this app.
// Format description: docs/STREAM-PROFILE-ANDROID.md

data class StreamProfile(
    val version: Int,
    val host: StreamHost,
    val games: List<StreamGame>,
    /** Free text like "ThorBro 0.5.0", only for display/debugging. */
    val createdBy: String?,
)

data class StreamHost(
    /** Sunshine's `uniqueid` from /serverinfo. Moonlight's `UUID` extra. */
    val uuid: String,
    /** Display name for the UI only. */
    val name: String?,
)

data class StreamGame(
    /** App name exactly as in Sunshine. Moonlight's `AppName` extra. */
    val name: String,
    /** Optional app ID, always kept as a String of digits. Moonlight's `AppId` extra. */
    val appId: String?,
    /** Ask Moonlight for HDR. */
    val hdr: Boolean,
)
