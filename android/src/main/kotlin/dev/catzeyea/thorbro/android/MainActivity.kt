package dev.catzeyea.thorbro.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.apps.KnownApps
import dev.catzeyea.thorbro.android.apps.checkInstalled
import dev.catzeyea.thorbro.android.launch.LaunchOutcome
import dev.catzeyea.thorbro.android.launch.MoonlightLauncher
import dev.catzeyea.thorbro.android.profile.ParseResult
import dev.catzeyea.thorbro.android.profile.ProfileParser
import dev.catzeyea.thorbro.android.profile.ProfileStore
import dev.catzeyea.thorbro.android.profile.StreamGame
import dev.catzeyea.thorbro.android.ui.AppUiState
import dev.catzeyea.thorbro.android.ui.ImportMessage
import dev.catzeyea.thorbro.android.ui.ThorBroApp
import java.io.IOException

/**
 * The app's only screen. An Activity is one "window" of an Android app; Android creates it, and calls
 * methods like onCreate / onResume / onNewIntent when things happen.
 *
 * It also receives the stream profile from ThorBro: an Intent is a message that starts an activity,
 * and it can carry "extras" (key/value pairs). ThorBro sends the profile as the extra [EXTRA_PROFILE_B64].
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Name of the string extra that carries the base64-encoded profile JSON. */
        const val EXTRA_PROFILE_B64 = "profile_b64"

        /** Value for `adb shell am start -n ...`: applicationId/full class name. */
        const val COMPONENT_FOR_ADB = "dev.catzeyea.thorbro/dev.catzeyea.thorbro.android.MainActivity"
    }

    private lateinit var profileStore: ProfileStore

    // `mutableStateOf` makes this value observable: when it changes, Compose redraws the UI.
    private var uiState by mutableStateOf(
        AppUiState(
            moonlight = InstalledApp(false, null),
            tailscale = InstalledApp(false, null),
            storedProfile = null,
            importMessage = null,
            launchError = null,
            appVersion = "",
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // filesDir = this app's private folder, readable by no other app.
        profileStore = ProfileStore(filesDir)
        uiState = uiState.copy(storedProfile = profileStore.load(), appVersion = readAppVersion())

        // savedInstanceState != null means Android re-created the activity (e.g. after rotation) and
        // hands us the same Intent again. Import only on the first start, so it is not imported twice.
        if (savedInstanceState == null) handleIncomingIntent(intent)

        setContent {
            ThorBroApp(
                state = uiState,
                onLaunchGame = ::launchGame,
                onOpenUrl = ::openUrl,
                onDismissLaunchError = { uiState = uiState.copy(launchError = null) },
            )
        }
    }

    /** Called instead of onCreate when the app is already open and a new Intent arrives (launchMode singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Called every time the app comes to the front, so the status updates after installing an app. */
    override fun onResume() {
        super.onResume()
        uiState = uiState.copy(
            moonlight = packageManager.checkInstalled(KnownApps.MOONLIGHT_PACKAGE),
            tailscale = packageManager.checkInstalled(KnownApps.TAILSCALE_PACKAGE),
        )
    }

    /** If the Intent carries a profile, validate it and save it. A normal app start has no extra. */
    private fun handleIncomingIntent(intent: Intent?) {
        val encoded = intent?.getStringExtra(EXTRA_PROFILE_B64) ?: return
        // Remove the extra, so the same profile is not imported again later from this Intent.
        intent.removeExtra(EXTRA_PROFILE_B64)

        val message = when (val result = ProfileParser.parseBase64(encoded)) {
            is ParseResult.Failure ->
                ImportMessage(isError = true, text = "Profile rejected: ${result.message} The previous profile was kept.")
            is ParseResult.Success -> try {
                profileStore.save(result.json)
                val profile = result.profile
                ImportMessage(
                    isError = false,
                    text = "Profile received: ${profile.games.size} games from ${profile.host.name ?: profile.host.uuid}.",
                )
            } catch (e: IOException) {
                ImportMessage(isError = true, text = "The profile was valid but could not be saved: ${e.message}")
            }
        }
        uiState = uiState.copy(storedProfile = profileStore.load(), importMessage = message)
    }

    private fun launchGame(game: StreamGame) {
        val host = uiState.storedProfile?.profile?.host ?: return
        when (val outcome = MoonlightLauncher.launch(this, host, game)) {
            LaunchOutcome.Started -> Unit // Moonlight takes over the screen from here.
            is LaunchOutcome.Failed -> uiState = uiState.copy(launchError = outcome.message)
        }
    }

    /** Opens a web link in the browser (an *implicit* Intent: Android picks the app that handles http links). */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found. Open $url manually.", Toast.LENGTH_LONG).show()
        }
    }

    private fun readAppVersion(): String = try {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }
}
