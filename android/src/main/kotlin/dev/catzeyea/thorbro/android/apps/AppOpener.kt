package dev.catzeyea.thorbro.android.apps

import android.content.ActivityNotFoundException
import android.content.Intent

/** What happened when we tried to open another app. */
sealed interface OpenAppResult {
    data object Opened : OpenAppResult

    /** Android has no launch intent for the package: the app is not installed (or has no launcher icon). */
    data class NotInstalled(val appName: String) : OpenAppResult {
        val message: String get() = "$appName is not installed"
    }

    data class Failed(val message: String) : OpenAppResult
}

/**
 * Opens another app at its normal start screen, like tapping its icon.
 *
 * Kept out of the UI and free of Context so it can be unit-tested: the caller passes
 * [launchIntentFor] (usually `packageManager::getLaunchIntentForPackage`) and [start]
 * (usually `context::startActivity`).
 *
 * Package visibility (Android 11+): getLaunchIntentForPackage only finds packages listed under
 * <queries> in AndroidManifest.xml. Moonlight and Tailscale are listed there.
 */
object AppOpener {

    fun open(
        packageName: String,
        appName: String,
        launchIntentFor: (String) -> Intent?,
        start: (Intent) -> Unit,
    ): OpenAppResult {
        val intent = launchIntentFor(packageName) ?: return OpenAppResult.NotInstalled(appName)
        return try {
            start(intent)
            OpenAppResult.Opened
        } catch (e: ActivityNotFoundException) {
            // The app was uninstalled or disabled between the lookup and the start.
            OpenAppResult.NotInstalled(appName)
        } catch (e: SecurityException) {
            OpenAppResult.Failed("Android did not allow opening $appName. Details: ${e.message}")
        }
    }
}
