package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.apps.InstalledApp
import dev.catzeyea.thorbro.android.apps.KnownApps
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusScreen(state: AppUiState, onOpenUrl: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppStatusCard(
            title = "Moonlight",
            app = state.moonlight,
            missingHint = "Required. Moonlight does the actual streaming. Install the official app, " +
                "pair it with your PC inside Moonlight, then open the PC's game list in Moonlight once " +
                "so it knows the game names.",
            installedHint = "Make sure Moonlight is paired with your PC (pairing happens inside Moonlight).",
            links = listOf(
                "moonlight-stream.org" to KnownApps.MOONLIGHT_WEBSITE,
                "Google Play" to KnownApps.MOONLIGHT_PLAY_STORE,
            ),
            onOpenUrl = onOpenUrl,
        )
        AppStatusCard(
            title = "Tailscale",
            app = state.tailscale,
            missingHint = "Optional. Only needed to stream when the Thor is not on the same network as the PC.",
            installedHint = "This app cannot see whether Tailscale is connected. Check that in the Tailscale app.",
            links = listOf("tailscale.com/download/android" to KnownApps.TAILSCALE_DOWNLOAD),
            onOpenUrl = onOpenUrl,
        )
        ProfileCard(state)
    }
}

@Composable
private fun AppStatusCard(
    title: String,
    app: InstalledApp,
    missingHint: String,
    installedHint: String,
    links: List<Pair<String, String>>,
    onOpenUrl: (String) -> Unit,
) {
    StatusCard(title) {
        if (app.installed) {
            Text("Installed" + (app.versionName?.let { " (version $it)" } ?: ""), color = MaterialTheme.colorScheme.primary)
            Text(installedHint, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Not installed", color = MaterialTheme.colorScheme.error)
            Text(missingHint, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { (label, url) ->
                    OutlinedButton(onClick = { onOpenUrl(url) }) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(state: AppUiState) {
    StatusCard("Stream profile") {
        state.importMessage?.let { message ->
            Text(
                message.text,
                color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        val stored = state.storedProfile
        if (stored == null) {
            Text("No profile received yet. Connect the Thor to your PC by USB and send the profile from ThorBro.")
        } else {
            val profile = stored.profile
            val receivedAt = DateFormat.getDateTimeInstance().format(Date(stored.receivedAtMillis))
            Text("Last received: $receivedAt")
            Text("PC: ${profile.host.name ?: "(no name)"}")
            Text("Host UUID: ${profile.host.uuid}", style = MaterialTheme.typography.bodySmall)
            Text("Games: ${profile.games.size}")
            profile.createdBy?.let { Text("Created by: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** A card with a bold title; [content] goes below it. */
@Composable
fun StatusCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
