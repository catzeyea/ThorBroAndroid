# Stream profile (Android side)

The app reads the stream profile exactly as ThorBro (PC) defines it. The only description of the
format is `docs/STREAM-PROFILE.md` in the ThorBro repo (format `thorbro.stream-profile`, version 1).
This file does not repeat it.

Android-specific additions:

- Profiles larger than 256 KiB, profiles with credential-like field names (`password`, `pin`,
  `token`, …) and blank game names are rejected. A rejected profile never replaces the saved one.
- `pc.uniqueid` is accepted in upper or lower case and sent to Moonlight unchanged.

## Handoff from ThorBro (method A)

The PC doc leaves the transport open. This app accepts the profile as a base64 string extra
(standard alphabet with `=` padding, line breaks are ignored):

```sh
adb shell am start \
  -n dev.catzeyea.thorbro/dev.catzeyea.thorbro.android.MainActivity \
  --es profile_b64 "<BASE64>"
```
