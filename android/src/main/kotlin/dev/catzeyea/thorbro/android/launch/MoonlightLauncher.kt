package dev.catzeyea.thorbro.android.launch

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.catzeyea.thorbro.android.apps.KnownApps
import dev.catzeyea.thorbro.android.profile.StreamGame
import dev.catzeyea.thorbro.android.profile.StreamHost

/**
 * Names that Moonlight's code expects. Source: moonlight-android, ShortcutTrampoline.java and
 * ServerHelper.java (see ~/System/ThorBro/docs/PC-STREAMING-RESEARCH.md, section 1).
 */
object Moonlight {
    const val PACKAGE = KnownApps.MOONLIGHT_PACKAGE

    /** The only Moonlight activity that other apps may start ("exported"). */
    const val TRAMPOLINE_CLASS = "com.limelight.ShortcutTrampoline"

    const val EXTRA_UUID = "UUID"
    const val EXTRA_APP_NAME = "AppName"
    const val EXTRA_APP_ID = "AppId"
    const val EXTRA_HDR = "HDR"
}

/**
 * Everything needed to build the launch Intent, as plain data.
 *
 * android.content.Intent cannot be used in local unit tests (on the PC its methods are empty stubs),
 * so the tests check this class, and [toIntent] only copies the values over.
 */
data class LaunchRequest(
    val packageName: String,
    val className: String,
    val action: String,
    val flags: Int,
    val stringExtras: Map<String, String>,
    val booleanExtras: Map<String, Boolean>,
) {
    /**
     * An *explicit* Intent: it names the exact target component (package + class), so Android
     * delivers it to that activity only and does not ask "open with which app?".
     */
    fun toIntent(): Intent = Intent(action).apply {
        component = ComponentName(packageName, className)
        addFlags(flags)
        stringExtras.forEach { (key, value) -> putExtra(key, value) }
        booleanExtras.forEach { (key, value) -> putExtra(key, value) }
    }
}

/** What happened when we asked Android to start Moonlight. */
sealed interface LaunchOutcome {
    data object Started : LaunchOutcome
    data class Failed(val message: String) : LaunchOutcome
}

object MoonlightLauncher {

    /** Builds the same Intent shape that Moonlight's own pinned shortcuts use. */
    fun buildRequest(host: StreamHost, game: StreamGame): LaunchRequest {
        val strings = linkedMapOf(
            Moonlight.EXTRA_UUID to host.uuid,
            Moonlight.EXTRA_APP_NAME to game.name,
        )
        // AppId must be a String: Moonlight reads it with getStringExtra(). An Int extra would be ignored.
        game.appId?.let { strings[Moonlight.EXTRA_APP_ID] = it }

        // Moonlight reads HDR with getBooleanExtra("HDR", false), so it is only sent when true.
        val booleans = if (game.hdr) mapOf(Moonlight.EXTRA_HDR to true) else emptyMap()

        return LaunchRequest(
            packageName = Moonlight.PACKAGE,
            className = Moonlight.TRAMPOLINE_CLASS,
            // Intent.ACTION_VIEW = "android.intent.action.VIEW", the action Moonlight's shortcuts use.
            action = Intent.ACTION_VIEW,
            // Start Moonlight in its own task, like a home-screen shortcut does. Then the stream is a
            // separate entry in "recent apps" and Back inside Moonlight does not land in our app's history.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK,
            stringExtras = strings,
            booleanExtras = booleans,
        )
    }

    /**
     * Asks Android to open Moonlight's trampoline. Call this only from a visible activity (a button tap):
     * since Android 10 apps in the background are not allowed to start activities.
     *
     * "Started" only means Android accepted the Intent. Moonlight itself may then show its own dialogs,
     * for example: the PC is not paired, the PC is offline, or another game is already running on the PC
     * (Moonlight asks whether to quit it). The stream also stops as soon as Moonlight is no longer visible.
     */
    fun launch(context: Context, host: StreamHost, game: StreamGame): LaunchOutcome {
        val intent = buildRequest(host, game).toIntent()
        return try {
            context.startActivity(intent)
            LaunchOutcome.Started
        } catch (e: ActivityNotFoundException) {
            // Thrown when no app has this package + class: Moonlight is missing, or it is a build
            // without ShortcutTrampoline (for example the root flavor "com.limelight.root").
            LaunchOutcome.Failed(
                "Moonlight could not be started. Install the official Moonlight app (com.limelight) " +
                    "and open it once. Details: ${e.message}",
            )
        } catch (e: SecurityException) {
            LaunchOutcome.Failed("Android did not allow starting Moonlight. Details: ${e.message}")
        }
    }
}
