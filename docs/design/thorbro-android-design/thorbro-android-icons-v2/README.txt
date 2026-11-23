Drop-in replacement for android/src/main/res icon files.
Copy the contents of res/ over android/src/main/res/ (same folder names). Replaces ic_launcher, ic_launcher_round,
ic_launcher_foreground PNGs in all mipmap-*dpi folders, adds ic_launcher_monochrome (new), and replaces both adaptive XMLs
(now with a <monochrome> layer). The background color resource @color/ic_launcher_background (#A9BC8E) is unchanged.
