package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.profile.StoredProfile
import dev.catzeyea.thorbro.android.profile.StreamGame

/** A short message about the last profile handoff (success or error). */
data class ImportMessage(val isError: Boolean, val text: String)

/** Everything the screens show. MainActivity owns it; the UI only reads it. */
data class AppUiState(
    val moonlight: InstalledApp,
    val tailscale: InstalledApp,
    val storedProfile: StoredProfile?,
    val importMessage: ImportMessage?,
    val launchError: String?,
    val appVersion: String,
)

private enum class AppTab(val title: String) { Status("Status"), Games("Games"), About("About") }

/**
 * Root of the UI. In Jetpack Compose the UI is a function of the state: whenever [state] changes,
 * Compose calls this again and redraws what changed.
 */
@Composable
fun ThorBroApp(
    state: AppUiState,
    onLaunchGame: (StreamGame) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismissLaunchError: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        // rememberSaveable keeps the selected tab when Android recreates the activity (e.g. rotation).
        var selectedTab by rememberSaveable { mutableIntStateOf(AppTab.Status.ordinal) }

        Scaffold { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    AppTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab.ordinal,
                            onClick = { selectedTab = tab.ordinal },
                            text = { Text(tab.title) },
                        )
                    }
                }
                when (AppTab.entries[selectedTab]) {
                    AppTab.Status -> StatusScreen(state, onOpenUrl)
                    AppTab.Games -> GamesScreen(state.storedProfile, state.moonlight.installed, onLaunchGame)
                    AppTab.About -> AboutScreen(state.appVersion, onOpenUrl)
                }
            }
        }

        state.launchError?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissLaunchError,
                confirmButton = { TextButton(onClick = onDismissLaunchError) { Text("OK") } },
                title = { Text("Could not start Moonlight") },
                text = { Text(message) },
            )
        }
    }
}
