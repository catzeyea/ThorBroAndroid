# ThorBro Android – Files as a full file manager (reference for Claude Code)

Boards in boards/ (AndroidFM1-5, .png + .dc.html). Mock data only. The goal: the Files tab can fully replace the stock file manager. We build on this later, so keep the code easy to extend.

## Layout
- Narrow left bar: storages (Internal, SD card), pinned folders, Trash with item count. The list/grid takes almost the full width: about 10 rows visible instead of about 6.
- Top bar: breadcrumb path, sort (Name, with direction; later size/date), List / Grid toggle, Search (first version: filter by name in the open folder).
- List: preview thumbnail or type chip, name, size, changed date. Grid: 6 columns with previews, folders as tiles.

## Controls (controller first, touch too)
- A open, B up one folder (or close menu / clear selection), X mark, Select actions menu, L1/R1 tabs, Y global menu.
- X was "Scan this folder": Scan moves into the actions menu (and keeps working exactly as before, incl. info card, pins and default scan folder).
- Touch: tap opens, long-press marks, normal scrolling. Physical X/Y/Select on the Thor are still unverified.

## Actions (menu on the selection, or on the focused item when nothing is marked)
Copy, Move to ..., Rename, Properties, Pin folder, Scan this folder, Move to Trash. Also needed: New folder, Paste (clipboard like the PC app).

## Trash
Delete always means "Move to Trash": the file goes to ThorBro/trash on the SAME storage with a small metadata entry (original path, time). Restore puts it back; if the original folder is gone the user picks a place. Empty Trash asks first. Nothing is deleted for good otherwise.

## Safety (keep all existing rules)
Never overwrite, no File.renameTo, case-only renames via temp name, FAT-safe names, cross-storage = copy, verify, delete. Log in ThorBro/logs. Protect storage roots, Android/, and the library root folders from being trashed or renamed.

## Motion
Console feel with cheap animations only (translation/alpha via graphicsLayer): focus ring glides to the next item, list slides in a few px on folder change, menu pops in. A setting turns animations off. No layout-changing animations.

## Performance
We sit near the 16.7 ms budget (60 Hz). Previews: placeholder in the first frame, background decode at target size, memory cache + small disk thumbnails, never decode on the main thread, limit concurrent decodes while scrolling.
