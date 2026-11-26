# ThorBro Android – Design Update 2 (reference for Claude Code)

Boards in boards/ (.png screenshot, .dc.html source). Mock data only.

## 1. Scan info card (AndroidScanInfo)
Pressing X on a folder first shows "What does Scan do?" with three points (wording verified against the Android code and tests, keep it true):
- What it looks at: only files directly in this folder; folders, unfinished downloads and files that look like apps, saves, cheats or firmware stay where they are.
- What gets sorted: only when the Switch title ID, the file header or the file extension points to exactly one system in the biggest ROMs folder.
- Safe by default: nothing moves until A, nothing is overwritten, every move is written to ThorBro/logs, Undo on the result screen puts the files back. (Do NOT claim "undo any time": there is no later undo.)
Buttons: A Scan, B Cancel. Checkbox "Don't show this again" (Select), remembered.

## 2. How it works (AndroidHelp)
New entry in the Y menu next to Setup and About. One card per tab (Start, Files incl. the same Scan explanation, Saves, Stream) with short text and button hints.

## 3. Saves by emulator (AndroidSaves2, AndroidSavesGame, AndroidSavesEmpty)
- Grouped per emulator: heading chip with "x of y backed up", square cover tiles below with game name and backup state. A opens the game.
- Game detail: cover, emulator chip, backup list (newest first), Back up now (A), Restore selected (X). Restore still asks first and backs up the current save before restoring.
- Empty state explains the tab.
- Covers: iiSU art on the SD card (PC investigation: /storage/<vol>/Android/media/com.iisulauncher/iiSULauncher/assets/media/roms/consoles/<system>/<ROM file name without extension>/icon.(jpg|png), square). Placeholder with initials when no art.
