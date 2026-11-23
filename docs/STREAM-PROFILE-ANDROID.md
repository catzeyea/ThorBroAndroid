# Stream profile (Android side), version 1

The stream profile is a small JSON document that ThorBro (PC) sends to the Thor app. It tells the app
which PC to stream from and which games to show. The app then starts Moonlight with these values.

This is the minimal format the Android app understands today. The PC side is described in
`~/System/ThorBro/docs/STREAM-PROFILE.md` (separate repo). If the two differ, this app will be adapted.

## Rules

- The document is a JSON object encoded as UTF-8.
- `version` is required. The app accepts **only version 1**. Any other value is rejected with a readable
  message, nothing is saved, and the previous profile stays in place.
- Unknown extra fields are ignored, so ThorBro can add fields within version 1 without breaking the app.
  Changing or removing a field means a new version number.
- **No credentials.** The profile must never contain passwords, PINs, tokens or keys. The app rejects a
  profile that has any field named `password`, `passwd`, `pin`, `token`, `secret`, `credentials`, `apiKey`,
  `api_key` or `auth` (case-insensitive, at any depth).
- Maximum size: 256 KiB of JSON.

## Fields

| Field | Type | Required | Meaning |
|---|---|---|---|
| `version` | integer | yes | Format version. Must be `1`. |
| `createdBy` | string | no | Free text for debugging, for example `"ThorBro 0.5.0"`. |
| `host` | object | yes | The PC that runs Sunshine. |
| `host.uuid` | string | yes | Sunshine's `uniqueid` from `GET http://<pc>:47989/serverinfo`. Format `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` (hex). Sent to Moonlight as the `UUID` extra. |
| `host.name` | string | no | Display name for the UI only. Not sent to Moonlight. |
| `games` | array | yes | List of games. May be empty. |
| `games[].name` | string | yes | App name exactly as in Sunshine's app list (`GET /api/apps`). Sent to Moonlight as `AppName`. Not blank. |
| `games[].appId` | string of digits | no | Sunshine/Moonlight app ID. Sent to Moonlight as `AppId` (always as a **string**, Moonlight reads it with `getStringExtra`). A JSON integer is also accepted and converted to a string. If present, Moonlight prefers it over `AppName`. |
| `games[].hdr` | boolean | no | Request HDR. Default `false`. Sent as the boolean `HDR` extra only when `true`. |

## Example

```json
{
  "version": 1,
  "createdBy": "ThorBro 0.5.0",
  "host": {
    "uuid": "0123abcd-4567-89ef-0123-456789abcdef",
    "name": "Gaming-PC"
  },
  "games": [
    { "name": "Desktop" },
    { "name": "Steam Big Picture", "hdr": true },
    { "name": "Elden Ring", "appId": "881448767" }
  ]
}
```

## How ThorBro hands it over (method A)

ThorBro base64-encodes the JSON (standard alphabet `A-Z a-z 0-9 + /` with `=` padding, no line breaks) and
starts the app's main activity over adb with a string extra:

```sh
adb shell am start \
  -n dev.catzeyea.thorbro/dev.catzeyea.thorbro.android.MainActivity \
  --es profile_b64 "<BASE64>"
```

Base64 avoids all shell quoting problems: the encoded text has no spaces, quotes or `$`. The app opens (or
comes to the front), validates the profile, saves it in its private storage and shows the result on the
Status screen.

Moonlight caveat: launching by `AppName` only works for apps that Moonlight has in its cached app list.
That cache is filled when the user opens the host's app grid in Moonlight once.
