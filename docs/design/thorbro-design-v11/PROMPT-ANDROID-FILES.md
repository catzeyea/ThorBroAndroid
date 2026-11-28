# ThorBro Android – full file manager in the Files tab: IMPLEMENT (in stages)

Do NOT commit or push. Alek commits himself. UI text and code comments in English.
Create a NEW branch from the current branch with a short, descriptive name of your choice and tell me the name.
Thor must be connected. Spec: DESIGN-ANDROID-FILES.md + boards/AndroidFM1-5 (put them in docs/design/ if they are not there).
Mark everything you could not verify on the device. Run the tests after each stage. Measure tab switching at the start and at the end (60 Hz, release build with speed-profile, same method as before) and report before/after.

## SAFETY FOR YOUR TESTS
This app can delete and move files. Run every device test ONLY in a throwaway folder you create (e.g. Download/ThorBro-test/), never in my ROM library, ThorBro/SaveBackups or other real folders. Delete only what you created. Tell me what you created and removed.

## Stage 0 - investigate first, report, then continue
- Read the current Files code (list, scan, pins, default folder, copy/move engine) and say what you reuse.
- Previews: image decode cost, video/other thumbnails (images only is fine for v1), memory budget, how many concurrent decodes are safe on this device.
- Trash design: ThorBro/trash per storage, metadata format, name clashes, restore when the original folder is gone, behaviour across storages, what counts as "same storage" for SD vs internal.
- Opening files in another app on Android 13 with all-files access (FileProvider or direct content Uri): verify what works on this firmware. Test with a file you created.
- Controls: X = mark, Select = actions menu. Check conflicts with existing key handling.
- List the risks you see for the 16.7 ms budget (rows with thumbnails, animated focus ring) before building.

## Stage 1 - layout
Narrow left bar (storages, pinned, Trash with count), wide list with breadcrumb, sort, List/Grid toggle, Search (name filter in the open folder for now), about 10 rows visible, footer hints. Keep Scan reachable (menu) with its info card, pins and default scan folder unchanged.

## Stage 2 - previews and grid
List thumbnails, grid with 6 columns and folder tiles. Placeholder in the first frame, background decode, caches. Focus ring in the first frame must still hold. Measure with real image folders (use copies in your test folder).

## Stage 3 - selection and actions
Mark with X (checkbox rows, "N selected" bar, B clears), actions menu with Select: Copy, Move to ..., Rename, Properties (folder size computed in the background), Pin folder, Scan this folder, New folder, Paste. Reuse the safe move/copy engine (no overwrite, copy-verify-delete across storages, FAT-safe names, log). Name clashes: never overwrite, offer keep both with a counter.

## Stage 4 - Trash
"Move to Trash" with confirmation dialog, Trash view (origin, date, size), Restore, Empty Trash with confirmation. Protect storage roots, Android/, and library root folders from trash/rename. Add tests for metadata, restore with missing folder, name clashes.

## Stage 5 - touch and motion
Touch: tap opens, long-press marks, scrolling. Cheap animations only (graphicsLayer translation/alpha): focus ring glides, list slides in a few px on folder change, menu pops in. Add a setting "Animations" (on/off, default on) in Setup. Measure frame times with animations on and off; tell me honestly if on does not fit the budget.

## Report
Per stage: what works, what you ran on the real Thor (in the test folder), what is unverified, files changed, measured numbers before/after. Stop and ask me if something differs from this plan.
Do NOT commit or push.
