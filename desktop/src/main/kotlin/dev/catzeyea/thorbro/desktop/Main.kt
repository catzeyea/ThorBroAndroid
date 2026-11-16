package dev.catzeyea.thorbro.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// PC-App: Dateimanager (thorBro + thor-drop), Library-Schnellzugriffe, Save-Sync, Stream.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "ThorBro") {
        MaterialTheme {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ThorBro PC")
            }
        }
    }
}
