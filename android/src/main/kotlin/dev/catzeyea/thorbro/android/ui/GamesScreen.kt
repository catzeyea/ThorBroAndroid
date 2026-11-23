package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.profile.StoredProfile

@Composable
fun GamesScreen(
    storedProfile: StoredProfile?,
    moonlightInstalled: Boolean,
    onLaunchGame: (gameName: String) -> Unit,
) {
    val profile = storedProfile?.profile
    if (profile == null || profile.games.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                if (profile == null) {
                    "No games yet. Send a stream profile from ThorBro on your PC (see the Status tab)."
                } else {
                    "The received profile has no games. Add apps in Sunshine and send the profile again."
                },
            )
        }
        return
    }

    // LazyColumn only builds the rows that are on screen, like a RecyclerView.
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Streaming from ${profile.pc.displayName}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!moonlightInstalled) {
                Text("Moonlight is not installed, see the Status tab.", color = MaterialTheme.colorScheme.error)
            }
        }
        // No item keys: Sunshine could have two apps with the same name, and keys must be unique.
        items(profile.games) { gameName ->
            GameRow(gameName, onPlay = { onLaunchGame(gameName) })
        }
    }
}

@Composable
private fun GameRow(gameName: String, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(gameName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onPlay) { Text("Play") }
        }
    }
}
