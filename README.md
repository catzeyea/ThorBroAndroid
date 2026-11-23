<p align="center">
  <img src="docs/logo-256.png" width="128" alt="ThorBro logo">
</p>

# ThorBro for Android

A small launcher app for the AYN Thor that works together with [ThorBro](https://github.com/catzeyea/ThorBro) on your PC. You send a stream profile from ThorBro to this app, pick a game, and the app starts [Moonlight](https://moonlight-stream.org/) with the right PC and game.

> **Status: early alpha.** The app builds and all 48 unit tests pass, but it has not been tested on a real Thor yet. Expect rough edges.

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

Build the debug APK (see below), then:

```
adb install -r dist/thorbro-launcher-0.1.0-debug.apk
```

Debug builds are signed with a local debug key. If you build with a different key later, `adb install -r` fails and you have to uninstall the app first.

## Usage

1. Install Moonlight on the Thor and pair it with your Sunshine host once, as described by Moonlight.
2. In ThorBro on the PC, open **Stream → Stream PC games to your Thor**.
3. Press **Send to Thor app**. ThorBro sends the profile over adb.
4. Open the app on the Thor. The **Status** tab shows whether the profile was received or rejected. A rejected profile never replaces the one you already have.
5. Pick a game in the **Games** tab, or start it from the PC with **Play on Thor**.

The **Status** tab also shows the addresses of your PC (LAN and Tailscale). Use them in Moonlight's "Add PC" if the PC is not found automatically.

Moonlight only streams while it is visible on screen. It cannot stream in the background.

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
./gradlew :android:assembleDebug
```

- Application id: `dev.catzeyea.thorbro`
- minSdk 33 (Android 13), compileSdk and targetSdk 36
- Gradle 8.14.3 (wrapper), Android Gradle Plugin 8.13.2, Kotlin 2.3.20

The repository started as a Kotlin Multiplatform skeleton (`core`, `desktop`, `android`). Only the `android` module is used so far.

## Not done yet

- Not tested on a real Thor and not with a real Moonlight pairing
- No automatic download of Moonlight or Tailscale
- No scan of the PC game library and no shortcuts
- No controller (D-pad) navigation testing
- No lint run yet

## License

MIT, see `LICENSE`. This app does not contain code from Moonlight or Sunshine. It only starts Moonlight through its public shortcut activity.
