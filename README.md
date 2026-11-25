<p align="center">
  <img src="docs/logo-256.png" width="128" alt="ThorBro logo">
</p>

# ThorBro for Android

A small launcher app for the AYN Thor that works together with [ThorBro](https://github.com/catzeyea/ThorBro) on your PC. You send a stream profile from ThorBro to this app, pick a game, and the app starts [Moonlight](https://moonlight-stream.org/) with the right PC and game.

> **Status: alpha.** The app builds and the 74 unit tests pass. Stick input and the layout were only checked by hand on a Thor. Expect rough edges. See [Not done yet](#not-done-yet).

## How it works

ThorBro does not stream anything itself. It controls tools that already do the job:

- **PC:** [Sunshine](https://github.com/LizardByte/Sunshine) captures and streams the game.
- **Thor:** [Moonlight](https://moonlight-stream.org/) plays the stream. This app starts Moonlight with the right PC and game.
- **Network:** [Tailscale](https://tailscale.com/) connects both devices when they are not on the same network (optional).

ThorBro on the PC sends a small stream profile (PC id, addresses, game list) to this app. The app validates and stores it, lists the games, and starts Moonlight through its public shortcut activity (`com.limelight/.ShortcutTrampoline`).

## Requirements

- On the PC: ThorBro and Sunshine, optionally Tailscale
- On the Thor: Moonlight (paired once with your Sunshine host), optionally Tailscale
- A connection between PC and Thor (USB debugging or wireless adb) to send the profile

The app only checks whether Moonlight and Tailscale are installed and links to the official download pages. It does not install anything for you.

## Install

Build the release APK (see below), then install it and compile it right away:

```
adb install -r android/build/outputs/apk/release/android-release.apk
adb shell cmd package compile -m speed-profile -f dev.catzeyea.thorbro
```

Run the compile command after every install: a freshly installed app runs uncompiled, and tab switches stutter until Android compiles it, which otherwise only happens later in the background.

Release builds are signed with the local debug key, like debug builds, so the release APK installs over a debug install and keeps the stored profile. Switching to a real release key later needs a one-time `adb uninstall dev.catzeyea.thorbro` first (this deletes the stored profile, so send it again from ThorBro). Debug builds are noticeably slower and only meant for development.

## Usage

1. Install Moonlight on the Thor and pair it with your Sunshine host once, as described by Moonlight.
2. In ThorBro on the PC, open **Stream → Stream PC games to your Thor** and press **Send to Thor app**. ThorBro sends the profile over adb.
3. Open the app on the Thor. A rejected profile never replaces the one you already have; the Stream tab shows why it was rejected.

### Tabs

- **Stream:** the PC from the profile (name, Tailscale and home address, game count) and two steps.
  - **Open Tailscale** opens the Tailscale app.
  - **Connect to PC** (shown once a profile exists) starts the profile's `Desktop` entry through Moonlight. If the profile has no `Desktop` entry, it only opens Moonlight.
  - **Open Moonlight only** opens Moonlight without starting anything.
- **Games:** the games from the profile. Select one to start it through Moonlight.
- **Setup:** four static steps (Tailscale, Sunshine, send the profile, pair Moonlight).
- **About:** app version, whether Moonlight and Tailscale are installed (with download links), profile details and PC addresses for Moonlight's "Add PC", notes, and the font license.

### Controller

- **L1 / R1:** previous / next tab (no wrap-around)
- **D-pad:** move focus (stick input goes through Compose's focus system)
- **A:** activate the focused element
- **B:** closes a sub-page (start error, font license). On a main tab it does nothing, so it never closes the app.

Elements are also focusable and tappable in touch mode. Moonlight only streams while it is visible on screen.

## Profile format

The app reads the stream profile that ThorBro writes. The single source of truth is [`docs/STREAM-PROFILE.md`](https://github.com/catzeyea/ThorBro/blob/main/docs/STREAM-PROFILE.md) in the ThorBro repository. In short:

- `"format": "thorbro.stream-profile"` and a known `version` are required
- `pc` holds the PC id (`uniqueid`), an optional name, addresses and the Sunshine port
- `games` is a list of game names

Unknown versions are rejected, unknown fields are ignored, and profiles that contain credential-like fields are rejected. Profiles in the old format are not loaded.

## Handoff from the command line

ThorBro sends the profile like this. You can do the same by hand for testing:

```
adb shell am start -n dev.catzeyea.thorbro/dev.catzeyea.thorbro.android.MainActivity --es profile_b64 '<base64 of the profile JSON>'
```

ThorBro limits the encoded profile to 24,576 characters (roughly 830 games). The app itself accepts up to 256 KiB.

## Privacy

- The app has no `INTERNET` permission and never uses the network.
- It stores only the stream profile, in its private app storage.
- The profile contains no passwords or logins. Credential-like fields are rejected.

## Build

You need JDK 17 and the Android SDK (platform 36 and matching build tools). Set `ANDROID_SDK_ROOT` to your SDK.

```
./gradlew :android:testDebugUnitTest
./gradlew :android:assembleRelease
```

`./gradlew :android:assembleDebug` builds the slower debug APK for development.

- Application id: `dev.catzeyea.thorbro`
- minSdk 33 (Android 13), compileSdk and targetSdk 36
- Gradle 8.14.3 (wrapper), Android Gradle Plugin 8.13.2, Kotlin 2.3.20

The repository started as a Kotlin Multiplatform skeleton (`core`, `desktop`, `android`). Only the `android` module is used so far.

## Not done yet

Not verified:

- Stick input and the layout were only tested on the Thor, by hand. Other screen sizes, large font settings and touch-only use are untested.
- The handoff from ThorBro (`adb install`, `am start` with `profile_b64`) and starting Moonlight with a real pairing are not verified end to end, including a PC id with upper-case letters.
- Whether `Connect to PC` really lands on the desktop in Moonlight (it depends on a `Desktop` app in Sunshine).
- Whether the launcher icon, including the monochrome layer, shows correctly in iiSU and other launchers.
- No lint run yet.

Not implemented:

- No automatic download of Moonlight or Tailscale (links only)
- No scan of the PC game library and no shortcuts

## License

MIT, see `LICENSE`. This app does not contain code from Moonlight or Sunshine. It only starts Moonlight through its public shortcut activity.

Credits: [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (SIL Open Font License 1.1), see `THIRD-PARTY-NOTICES.txt`.
