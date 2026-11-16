# Architektur

## Grundidee

Die alten Scripts laufen alle über adb vom PC aus. Auf dem Thor selbst gibt es kein adb, die APK greift direkt aufs Dateisystem zu. Deshalb:

- **Gemeinsamer Kern** (`core/commonMain`): alles, was nicht davon abhängt, wie man an die Dateien kommt. Konsolenerkennung, Library-Suche, Einsortieren, später Save-Sync (Snapshot, md5-Vergleich, Sync-Plan, Backups).
- **Austauschbarer Transport** über das Interface `ThorFileSystem`:
  - `AdbFileSystem` (`core/jvmMain`): PC per USB, wie bisher
  - `LocalFileSystem` (`core/androidMain`): APK direkt auf dem Thor
  - später evtl. WLAN zwischen PC-App und Thor-App

Die UIs sind bewusst getrennt (`desktop/`, `android/`), weil sie sich stark unterscheiden: PC ist ein Dateimanager mit Drag & Drop, Thor ist ein Einsortier-Assistent für den Touchscreen. Geteilte UI-Bausteine können später in ein eigenes Modul.

## Module

| Modul | Plugin | Inhalt |
|---|---|---|
| `core` | Kotlin Multiplatform + Android Library | Logik, Targets `jvm` und `android` |
| `desktop` | Kotlin JVM + Compose Multiplatform | PC-App, Installer für Linux und Windows |
| `android` | Android Application + Compose | APK |

## Features

### PC
- Dateimanager mit vollem Zugriff (Ersatz für `thorBro` und `thor-drop`)
- Game-Libraries als Schnellzugriffe, Games direkt reindroppen
- Save-Sync (Port von `thor-save`)
- Stream (scrcpy, wie `thor-stream`)

### Thor
- Game-Libraries automatisch finden, Standardpfade in den Einstellungen änderbar
- "Search Downloads": Ordner (Standard Download, änderbar) durchsuchen, Games mit Ziel-Library auflisten, Ziel pro Durchlauf änderbar, "Alles verschieben"

## Offene Entscheidungen

- Aufbau der Libraries auf dem Thor (bestimmt `LibraryFinder`)
- Mehrdeutige Endungen (`.iso`, `.chd`, `.zip`): Header prüfen oder nachfragen
- Archive entpacken oder nicht
- adb auf dem PC: Binary mitliefern oder Lib wie `dadb`
- Braucht die APK Shizuku (für `Android/data` und private App-Ordner)?
- Upgrade auf AGP 9 (dann `core` auf `com.android.kotlin.multiplatform.library` umstellen)
