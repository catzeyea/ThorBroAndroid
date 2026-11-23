package dev.catzeyea.thorbro.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import dev.catzeyea.thorbro.android.apps.AppOpener
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.apps.KnownApps
import dev.catzeyea.thorbro.android.apps.OpenAppResult
import dev.catzeyea.thorbro.android.apps.checkInstalled
import dev.catzeyea.thorbro.android.launch.LaunchOutcome
import dev.catzeyea.thorbro.android.launch.MoonlightLauncher
import dev.catzeyea.thorbro.android.profile.ParseResult
import dev.catzeyea.thorbro.android.profile.ProfileStore
import dev.catzeyea.thorbro.android.ui.AppActions
import dev.catzeyea.thorbro.android.ui.AppUiState
import dev.catzeyea.thorbro.android.ui.ConnectAction
import dev.catzeyea.thorbro.android.ui.ImportMessage
import dev.catzeyea.thorbro.android.ui.StreamModel
import dev.catzeyea.thorbro.android.ui.StreamOverview
import dev.catzeyea.thorbro.android.ui.ThorBroApp
import dev.catzeyea.thorbro.android.ui.components.Notice
import dev.catzeyea.thorbro.android.ui.navigation.AppTab
import dev.catzeyea.thorbro.android.ui.navigation.ControllerNavigation
import dev.catzeyea.thorbro.android.ui.navigation.NavCommand
import dev.catzeyea.thorbro.android.ui.navigation.SubScreen
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
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

        /** Key for the selected tab in the saved instance state. */
        private const val STATE_TAB = "selected_tab"

        /** The font license shipped in the APK (src/main/assets), shown from the About tab. */
        private const val FONT_LICENSE_ASSET = "licenses/JetBrainsMono-OFL.txt"
    }

    private lateinit var profileStore: ProfileStore

    // `mutableStateOf` makes this value observable: when it changes, Compose redraws the UI.
    private var uiState by mutableStateOf(
        AppUiState(
            moonlight = InstalledApp(false, null),
            tailscale = InstalledApp(false, null),
            storedProfile = null,
            importMessage = null,
            appVersion = "",
        ),
    )

    private var selectedTab by mutableStateOf(AppTab.START)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind transparent system bars in the app's background color (the UI pads itself).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(ThorColors.Bg.toArgb()))

        // filesDir = this app's private folder, readable by no other app.
        profileStore = ProfileStore(filesDir)
        uiState = uiState.copy(storedProfile = profileStore.load(), appVersion = readAppVersion())
        selectedTab = AppTab.fromName(savedInstanceState?.getString(STATE_TAB))

        // savedInstanceState != null means Android re-created the activity (e.g. after rotation) and
        // hands us the same Intent again. Import only on the first start, so it is not imported twice.
        if (savedInstanceState == null) handleIncomingIntent(intent)

        val actions = AppActions(
            onConnect = ::connectToPc,
            onLaunchGame = ::launchGame,
            onOpenTailscale = { openApp(KnownApps.TAILSCALE_PACKAGE, "Tailscale") },
            onOpenMoonlight = { openApp(KnownApps.MOONLIGHT_PACKAGE, "Moonlight") },
            onOpenUrl = ::openUrl,
            onShowFontLicense = ::showFontLicense,
            onCloseSubScreen = ::closeSubScreen,
            onNoticeExpired = { uiState = uiState.copy(notice = null) },
        )
        setContent {
            ThorBroApp(
                state = uiState,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                actions = actions,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, selectedTab.name)
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

    /**
     * Controller shoulder buttons and B, before any view sees them: L1/R1 switch tabs, B closes a
     * sub-screen (and otherwise does nothing, so it never closes the app). Everything else (D-pad,
     * stick, A) goes on to Compose, where focus moves and the focused element handles A.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = ControllerNavigation.commandFor(
            keyCode = event.keyCode,
            currentTab = selectedTab,
            subScreenOpen = uiState.subScreen != null,
        ) ?: return super.dispatchKeyEvent(event)

        // Act once per press; the matching key-up and auto-repeats are swallowed too.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (command) {
                is NavCommand.ShowTab -> selectedTab = command.tab
                NavCommand.CloseSubScreen -> closeSubScreen()
                NavCommand.Consume -> Unit
            }
        }
        return true
    }

    /** If the Intent carries a profile, validate it and save it. A normal app start has no extra. */
    private fun handleIncomingIntent(intent: Intent?) {
        val encoded = intent?.getStringExtra(EXTRA_PROFILE_B64) ?: return
        // Remove the extra, so the same profile is not imported again later from this Intent.
        intent.removeExtra(EXTRA_PROFILE_B64)

        // importBase64 saves only a valid profile; a rejected one leaves the saved profile untouched.
        val message = try {
            when (val result = profileStore.importBase64(encoded)) {
                is ParseResult.Failure ->
                    ImportMessage(isError = true, text = "Profile rejected: ${result.message} The previous profile was kept.")
                is ParseResult.Success -> {
                    val profile = result.profile
                    ImportMessage(
                        isError = false,
                        text = "Profile received: ${profile.games.size} games from ${profile.pc.displayName}.",
                    )
                }
            }
        } catch (e: IOException) {
            ImportMessage(isError = true, text = "The profile was valid but could not be saved: ${e.message}")
        }
        uiState = uiState.copy(storedProfile = profileStore.load(), importMessage = message)
    }

    /**
     * "Connect to PC": starts the profile's Desktop entry through Moonlight (same path as "Play on Thor"),
     * or opens Moonlight if the profile has no Desktop entry. See [StreamModel.connectAction].
     */
    private fun connectToPc() {
        when (val overview = StreamModel.overview(uiState.storedProfile)) {
            // The button is not shown without a profile; just in case, lead the user to Setup.
            StreamOverview.NoProfile -> selectedTab = AppTab.Setup
            is StreamOverview.Ready -> when (val action = overview.connect) {
                is ConnectAction.StartApp -> launchGame(action.appName)
                ConnectAction.OpenMoonlight -> openApp(KnownApps.MOONLIGHT_PACKAGE, "Moonlight")
            }
        }
    }

    private fun launchGame(gameName: String) {
        val pc = uiState.storedProfile?.profile?.pc ?: return
        when (val outcome = MoonlightLauncher.launch(this, pc, gameName)) {
            LaunchOutcome.Started -> Unit // Moonlight takes over the screen from here.
            is LaunchOutcome.Failed -> uiState = uiState.copy(subScreen = SubScreen.LaunchError(outcome.message))
        }
    }

    /** Opens another app (Tailscale, Moonlight) at its start screen, like tapping its icon. */
    private fun openApp(packageName: String, appName: String) {
        val message = when (
            val result = AppOpener.open(packageName, appName, packageManager::getLaunchIntentForPackage, ::startActivity)
        ) {
            OpenAppResult.Opened -> return
            is OpenAppResult.NotInstalled -> result.message
            is OpenAppResult.Failed -> result.message
        }
        showNotice(message, isError = true)
    }

    /** Opens a web link in the browser (an *implicit* Intent: Android picks the app that handles http links). */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            showNotice("No browser found. Open $url manually.", isError = true)
        }
    }

    private fun showFontLicense() {
        val text = try {
            assets.open(FONT_LICENSE_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            "The license file is missing from this build. JetBrains Mono is licensed under the " +
                "SIL Open Font License, Version 1.1: https://openfontlicense.org"
        }
        uiState = uiState.copy(subScreen = SubScreen.FontLicense(text))
    }

    private fun closeSubScreen() {
        uiState = uiState.copy(subScreen = null)
    }

    /** A short message in the hint bar, in the app's own font (instead of a system Toast). */
    private fun showNotice(text: String, isError: Boolean) {
        uiState = uiState.copy(notice = Notice(text, isError, id = SystemClock.uptimeMillis()))
    }

    private fun readAppVersion(): String = try {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }
}
