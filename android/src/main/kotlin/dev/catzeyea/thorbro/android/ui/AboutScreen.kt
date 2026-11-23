package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.catzeyea.thorbro.android.MainActivity
import dev.catzeyea.thorbro.android.apps.KnownApps

@Composable
fun AboutScreen(appVersion: String, onOpenUrl: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard("ThorBro stream launcher") {
            Text("Version $appVersion")
            Text(
                "Starts PC games on the Thor through Moonlight. This app does not stream, pair or talk to the " +
                    "PC itself: ThorBro on the PC sends the game list, Moonlight does the streaming.",
            )
        }
        StatusCard("How the game list arrives") {
            Text("ThorBro sends it over USB (adb). The command it runs looks like this:")
            Text(
                "adb shell am start -n ${MainActivity.COMPONENT_FOR_ADB} --es ${MainActivity.EXTRA_PROFILE_B64} <base64 JSON>",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "The profile never contains passwords or keys. It is stored only inside this app.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        StatusCard("Good to know") {
            Text("The stream stops when Moonlight is not on screen.")
            Text("Moonlight may show its own messages: PC not paired, PC offline, or another game running.")
        }
        StatusCard("Official downloads") {
            OutlinedButton(onClick = { onOpenUrl(KnownApps.MOONLIGHT_WEBSITE) }) { Text("Moonlight") }
            OutlinedButton(onClick = { onOpenUrl(KnownApps.TAILSCALE_DOWNLOAD) }) { Text("Tailscale") }
        }
    }
}
