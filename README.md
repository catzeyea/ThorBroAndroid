<p align="center">
  <img src="docs/logo-256.png" width="128" alt="ThorBro logo">
</p>

# ThorBro for Android

A small companion app for the AYN Thor that works together with [ThorBro](https://github.com/catzeyea/ThorBro) on your PC. It shows how the Thor is doing, sorts downloaded games into your ES-DE library, backs up Switch saves, and starts PC games through [Moonlight](https://moonlight-stream.org/) from a stream profile that ThorBro sends.

> **Status: alpha.** The app builds and the 131 unit tests pass. Stick input and the layout were only checked by hand on a Thor. Expect rough edges. See [Not done yet](#not-done-yet).

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

Download the APK (`thorbro-android-<version>.apk`) from the [GitHub Releases](https://github.com/catzeyea/ThorBroAndroid/releases) of this repository.

**Without adb (on the Thor):** open the APK and allow your file manager or browser to install unknown apps when Android asks. Tab switches stutter at first: a freshly installed app runs uncompiled until Android compiles it in the background, which can take a while (usually when the Thor is idle and charging).

**With adb (from a PC):** install it, start it once, wait about 20 seconds, then compile it, so it is smooth right away:

```
adb install -r thorbro-android-0.2.0.apk
adb shell am start -n dev.catzeyea.thorbro/dev.catzeyea.thorbro.android.MainActivity
# wait about 20 seconds
adb shell cmd package compile -m speed-profile -f dev.catzeyea.thorbro
```

The app has to run once before compiling, so it can hand its built-in profile to Android; compiling right after the install does nothing. Check with `adb shell dumpsys package dexopt | grep -A2 dev.catzeyea.thorbro`: it should say `status=speed-profile`, not `verify`.

**Signing:** the APK is signed with a local debug key. An update only installs over a build signed with the same key. Switching to a real release key later needs a one-time uninstall first (`adb uninstall dev.catzeyea.thorbro`, or uninstall it on the Thor), and that deletes the stored stream profile, so send it again from ThorBro. Debug builds are noticeably slower and only meant for development.

## Usage

1. Install Moonlight on the Thor and pair it with your Sunshine host once, as described by Moonlight.
2. In ThorBro on the PC, open **Stream → Stream PC games to your Thor** and press **Send to Thor app**. ThorBro sends the profile over adb.
3. Open the app on the Thor. A rejected profile never replaces the one you already have; the Stream tab shows why it was rejected.
4. For Files and Saves, allow "All files access" when the app asks (button **Allow**). On the current Thor firmware every app can reach shared storage anyway, but a firmware update could change that.

### Tabs

- **Start:** ThorBro's mood and the Thor's health: battery (charging only when Android reports status "charging"), internal storage, SD card, CPU load and CPU temperature. Without an SD card, RAM takes its place. Values that can't be read are left out. ThorBro sleeps while no PC is connected over USB.
- **Files:** storages and pinned folders on the left, the open folder on the right. **Scan this folder** (X) first explains what Scan does (can be turned off with "Don't show this again"), then looks through the open folder (Download by default) and shows a preview: games, updates and DLC with their target in the main ES-DE library (the `ROMs` folder with the most games). Each file can be switched on or off. Nothing moves before you confirm. Details below.
- **Saves:** Switch saves grouped by emulator (Eden, Eden Duo, Citron, Sudachi, yuzu and Peanuts), each section with "x of y backed up", each game as a cover tile. Covers come from the art the iiSU launcher keeps on the SD card; without art the tile shows the game's initials. A opens a game: its backups (newest first), **Back up now** (A) copies the save to `ThorBro/SaveBackups/<game>/<time>`, **Restore selected** (X) puts the chosen backup back after asking, and backs up the current save first. Peanuts saves can be backed up but not restored yet. No sync with the PC, no import.
- **Stream:** the PC from the profile, step 1 (Open Tailscale) and step 2 (Connect to PC) with the PC's games as buttons, so a game can be started directly. **Connect to PC** starts the profile's `Desktop` entry (or opens Moonlight if there is none). Without a profile the tab points to Setup.
- **Menu** (Y, top right): **Setup** (four steps to connect the PC), **About** (version, installed apps with download links, profile details, font license) and **How it works** (one card per tab).

The header shows "PC connected" while the Thor has a USB data connection to a computer, else "No PC".

### Sorting files (Scan this folder)

- Same rules as the PC app: Switch title ID in the name (`[0100…]`, `(0100…)`, and here also `_0100…_`; ending `000` = game, `800` = update, anything else = DLC; updates and DLC go to `switch/update`), then the file header, then the extension (ES-DE `systeminfo.txt`, or the folder name).
- A file tagged `[BASE]` is a base game even if its title ID ends in `800` (as on the PC).
- Sure guesses start switched on in the preview. An unsure guess (several systems fit) gets its best target but starts switched off; you decide. Files already in the right folder are listed as "Already in place".
- Unknown files, folders, `.7z` and `.rar`, and zips that would need extracting stay where they are (Review). Saves, cheats, apps (APK, `.jar`, `.jad`) and firmware are not sorted.
- Unfinished downloads (`.part`, `.crdownload`, … or files that still grow) are skipped.
- Nothing is ever overwritten. On the same storage a file is moved; to another storage it is copied, checked, and only then deleted. File names are made safe for the SD card (`:` becomes ` -`).
- Every move is written to `ThorBro/logs/moves-<time>.jsonl` on internal storage; **Undo** on the result screen puts the files back.

### Controller

- **L1 / R1:** previous / next tab (no wrap-around)
- **D-pad:** move focus (stick input goes through Compose's focus system)
- **A:** activate the focused element (Saves: open a game; in a game: back up; Scan preview: switch a file on or off, or Move)
- **B:** back: closes a sub-page or the menu, goes one folder up in Files, stops a running move after the current file. On a main tab it never closes the app.
- **X:** Files: scan the open folder. In a game (Saves): restore the selected backup.
- **Y:** menu (Setup, About, How it works)
- **Select:** Files: folder options (pin, set as default scan folder). Scan info: "Don't show this again".

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
- In its private app storage it keeps the stream profile, the pinned folders and small cover thumbnails (cache).
- On shared storage it writes only what you ask for: moved files, the move logs in `ThorBro/logs` and save backups in `ThorBro/SaveBackups`.
- The profile contains no passwords or logins. Credential-like fields are rejected.

## Build

You need JDK 17 and the Android SDK (platform 36 and matching build tools). Set `ANDROID_SDK_ROOT` to your SDK.

```
./gradlew :android:testDebugUnitTest
./gradlew :android:assembleRelease
cp android/build/outputs/apk/release/android-release.apk dist/thorbro-android-0.2.0.apk
```

`dist/` is ignored by git; the APK is published as a GitHub Release.

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
- Moving files and Undo on the real Thor (only tested with unit tests in temporary folders), Restore of a real save (does the emulator read the restored files?), and granting "All files access" in Settings.
- Restore to Peanuts is turned off: Peanuts keeps a second copy of each save (`.work`), and writing a save back hasn't been tested.
- Which physical buttons send X, Y and Select (depends on the Thor's controller-style setting).
- 120 Hz.
- No lint run yet.

Not implemented:

- No automatic download of Moonlight or Tailscale (links only)
- No scan of the PC game library and no shortcuts
- No rules yet for saves, cheats, apps and firmware in Scan (they stay in Review), no looking into `.7z` or folders, no extracting of archives
- No Azahar (3DS) saves yet
- No ThorBro tab and no update buttons yet

## License

MIT, see `LICENSE`. This app does not contain code from Moonlight or Sunshine. It only starts Moonlight through its public shortcut activity.

Credits: [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) (SIL Open Font License 1.1), see `THIRD-PARTY-NOTICES.txt`.
