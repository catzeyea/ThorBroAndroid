package dev.catzeyea.thorbro.android.apps

import android.content.pm.PackageManager

/** Other apps this app works with, and where to get them (official sources only). */
object KnownApps {
    /** Package names must also be listed under <queries> in AndroidManifest.xml, see the comment there. */
    const val MOONLIGHT_PACKAGE = "com.limelight"
    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    // Official download pages, as linked from moonlight-stream.org and tailscale.com/download/android.
    const val MOONLIGHT_WEBSITE = "https://moonlight-stream.org/"
    const val MOONLIGHT_PLAY_STORE = "https://play.google.com/store/apps/details?id=com.limelight"
    const val TAILSCALE_DOWNLOAD = "https://tailscale.com/download/android"
}

/** Whether an app is installed, and its version text if it is. */
data class InstalledApp(val installed: Boolean, val versionName: String?)

/**
 * Asks Android whether [packageName] is installed.
 *
 * Because of package visibility (Android 11+), this only works for packages declared in the
 * manifest's <queries> block. For any other package it always says "not installed".
 */
fun PackageManager.checkInstalled(packageName: String): InstalledApp = try {
    val info = getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    InstalledApp(installed = true, versionName = info.versionName)
} catch (e: PackageManager.NameNotFoundException) {
    InstalledApp(installed = false, versionName = null)
}
