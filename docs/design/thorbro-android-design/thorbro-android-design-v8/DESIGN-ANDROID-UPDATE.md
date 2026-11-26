# ThorBro Android – Design Update (restructure)

Android app only (controller first). PC repo is read-only reference. UI text and code comments in English. Do NOT commit or push.
Reference boards are in `ref/` (open in a browser). Example data in the boards (names, numbers, versions) is mock.

## Tab bar (L1 / R1 switch tabs)
Start · Files · Saves · Stream · ThorBro
Setup and About move into a small menu (button Y, top right). The old Games tab moves into Stream.

## Buttons (design assumption, controller-style setting UNVERIFIED)
A select/open · B back · X "Scan this folder" · Y menu · Select = folder options (pin, set as default scan folder) · L1/R1 tabs

## Header (every tab)
ThorBro trio + wordmark left, tabs in the middle, right: status pill ("PC connected" / "No PC"), small ThorBro (awake / asleep), menu button.
"Asleep" = no PC connected (see decisions).

## Boards
- `AndroidStart` – health like the PC Start page (ThorBro mood, battery + charging, internal storage, SD card if present, CPU, temperature; RAM replaces SD when there is no SD card). Values that cannot be read are dropped.
- `AndroidFiles` – storage + pinned folders left (Download is the default scan folder), file list right, button "Scan this folder" (X) scans the OPEN folder. Pin icon next to the path when the folder is pinned.
- `AndroidScan` – scan preview, grouped by type with target, unclear files stay in place (Review). Nothing moves before "Move N files" (A). B cancels.
- `AndroidSaves` – list of saves with backup state, Back up (A), Restore latest (X).
- `AndroidStream2` – PC card, 1 Tailscale, 2 Connect to PC, PC games as a list in the same tab.
- `AndroidThorBro*` – ThorBro tab: idle, copying, alone, problem, update available. NOT part of step 2 (see scope).

## Decisions (from Alek)
1. Storage access: declare all-files access AND ask for it, so the app keeps working if a firmware update closes the open access.
2. Target library: follow the PC rule (library with the most games = main library, today the SD card). Moves across storages are copies: confirm dialog warns about it, progress shown, B stops after the current file. If no SD card, use internal ROMs.
3. Rules for step 2: games, updates, DLC exactly like the PC app. Also accept title IDs between underscores (_0100…_). Saves, cheats, APKs, firmware are NOT guessed yet: they go to Review. Rules for those come later from real files.
4. Archives: look inside .zip only (built in). .7z and .rar go to Review. No extraction on the Thor.
5. Charging: status 2 only (same as the PC rule).
6. "Asleep" mood = no PC connected over USB, if the app can read that reliably. If it cannot, drop the mood.
7. Saves: backup and restore as proposed (ThorBro/SaveBackups/<game>/<time>), restore asks first and backs up the current save. No sync, no import.
8. "No PC": use USB connection state if readable. The PC name comes from the received profile. If USB state is not readable, say so and propose an alternative, do not guess.
9. Undo log: ThorBro/logs on internal storage (visible).
10. Branch: start from the commit that contains release signing + README.

## Scope
Step 2 builds: tab bar, menu, Start, Files with Scan (open folder, default Download), pinning, Saves, Stream with games, focus ring fix.
NOT in step 2: the ThorBro tab and update buttons. They need a channel to the PC app and an update source (GitHub releases). That gets its own investigation first.

## Focus ring (from your report)
Request focus as soon as the new screen exists, ring reads focus at draw time. Goal: ring and new screen in the same frame, 1 frame per switch instead of 3. Verify.
