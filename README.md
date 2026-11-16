# ThorBro

Macht den Umgang mit dem AYN Thor so einfach wie möglich.

- **PC-App** (Windows + Linux): voller Dateizugriff auf den Thor per USB, Games reindroppen, Game-Libraries als Schnellzugriffe, Save-Sync, Bildschirm-Stream
- **Thor-App** (APK): Game-Libraries finden, Downloads durchsuchen und Games automatisch in die richtigen Ordner einsortieren

Status: Grundgerüst, noch keine Funktion.

## Aufbau

```
ThorBro/
├── core/       gemeinsame Logik (Kotlin Multiplatform)
│   ├── commonMain   Modelle, Konsolenerkennung, Library-Suche, Einsortieren
│   ├── jvmMain      adb-Transport für die PC-App
│   └── androidMain  lokaler Dateizugriff für die Thor-App
├── desktop/    PC-App (Compose Desktop)
├── android/    Thor-App (Compose, APK)
└── docs/       Planung und Architektur
```

Mehr dazu in [docs/ARCHITEKTUR.md](docs/ARCHITEKTUR.md).

## Bauen

Voraussetzung: JDK 17 oder neuer, für Android das Android SDK (am einfachsten über Android Studio).

```sh
./gradlew :desktop:run                  # PC-App starten
./gradlew :desktop:packageDistributionForCurrentOS   # Installer bauen (.deb/.rpm/.msi)
./gradlew :android:assembleDebug        # APK bauen -> android/build/outputs/apk/debug/
./gradlew :core:allTests                # Tests
```

Hinweis: Das Gerüst wurde noch nicht gebaut. Die Versionen in `gradle/libs.versions.toml` sind eine bekannte Kombi (Kotlin 2.3.20, Compose Multiplatform 1.10.3, AGP 8.13.2), aber beim ersten Sync kann noch was hängen.

## Herkunft

Basiert auf den Scripts `thorBro`, `thor-drop`, `thor-save`, `thor-save-gui` und `thor-stream` aus `~/System/Scripts/thor`.
