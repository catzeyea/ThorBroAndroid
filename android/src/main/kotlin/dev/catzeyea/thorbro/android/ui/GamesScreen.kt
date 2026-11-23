package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.profile.StoredProfile
import dev.catzeyea.thorbro.android.profile.StreamGame

@Composable
fun GamesScreen(
    storedProfile: StoredProfile?,
    moonlightInstalled: Boolean,
    onLaunchGame: (StreamGame) -> Unit,
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
                "Streaming from ${profile.host.name ?: profile.host.uuid}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!moonlightInstalled) {
                Text("Moonlight is not installed, see the Status tab.", color = MaterialTheme.colorScheme.error)
            }
        }
        itemsIndexed(profile.games) { _, game ->
            GameRow(game, onPlay = { onLaunchGame(game) })
        }
    }
}

@Composable
private fun GameRow(game: StreamGame, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(game.name, style = MaterialTheme.typography.titleMedium)
                if (game.hdr) Text("HDR", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onPlay) { Text("Play") }
        }
    }
}
