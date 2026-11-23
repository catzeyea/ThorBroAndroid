# ThorBro Android UI design (reference)

Goal: restyle the ThorBro Android app (module `android`) to this look and make it fully usable with a game
controller (AYN Thor, landscape). Same look as the PC app, see tokens below.
Do NOT change what the app does with profiles, parsing or storage. Change UI and navigation.

Reference files (not runnable as-is, custom tags `x-dc` / `sc-for`; read them for exact values):
- `reference/stream.dc.html`, `reference/games.dc.html`, `reference/setup.dc.html`
- `logo-256.png` -> header logo (52dp)
Design size is landscape 1280x720; scale to dp sensibly, do not hardcode pixels.

## Tabs (in this order)
Stream (start tab) | Games | Setup | About. The existing Status content moves into Stream and About.
Switching: L1 = previous tab, R1 = next tab (KEYCODE_BUTTON_L1 / KEYCODE_BUTTON_R1). Tabs are also focusable/clickable.
Header shows small "L1" and "R1" chips at both ends of the tab row.

## Controller rules (most important)
- Everything reachable with D-pad / left stick; logical focus order; no focus traps.
- A (KEYCODE_BUTTON_A) activates the focused item, B (KEYCODE_BUTTON_B) goes back (leave sub-screen, else do nothing).
- Visible focus: 4dp cream ring (#EFE7D2) with ~4dp offset around the focused element. Never rely on color only.
- Touch targets and controller targets at least 48dp high; main actions much larger (see Stream).
- Bottom hint bar on every tab: A Select, B Back, L1/R1 Switch tab (Games tab: "A Play").
- Initial focus: Stream tab -> "Connect to PC" button. Games tab -> first game.
- Keep it working with touch too.

## Screens
Stream: left card "your pc" (profile name, "profile received" chip in sage, Tailscale address, home network
address, game count, hint "Wrong PC or old list? Press Send to Thor app on the PC again."). Right: step 1 card
"Turn on Tailscale" with button "Open Tailscale"; step 2 card "Connect to your PC" with big orange button
"Connect to PC" (A badge inside) and a small secondary button "Open Moonlight only".
  - "Open Tailscale" / "Open Moonlight only": launch the apps via getLaunchIntentForPackage
    (com.tailscale.ipn / com.limelight). If not installed show a short "<App> is not installed" message.
    The manifest <queries> entries already exist. If a launcher helper for these already exists, reuse it.
  - "Connect to PC": same mechanism as the existing "Play on Thor" with the profile's "Desktop" entry if the profile has one,
    otherwise just open Moonlight. Tell me which one you implemented. No profile yet -> show the empty state
    "No PC profile yet. Open Setup." and focus the Setup tab hint.
Games: left list (large rows, selected row = sage fill + cream border + focus ring), right card with the selected
game, text "Starts on <PC name> through Moonlight.", orange "Play on Thor" button. Footer hint for adding games.
Setup: title "Connect your PC in 4 steps", subtitle "You only do this once. After that, Connect on the Stream tab is enough.",
2x2 cards: 1 Tailscale on PC and Thor (once), 2 Sunshine on the PC (once), 3 Send the PC profile (again when your games change),
4 Pair Moonlight (once). Exact texts are in reference/setup.dc.html. Static content, but the screen must be scrollable
with the D-pad if it ever does not fit.
About: keep existing About content in the new style.

## Tokens (same as PC app)
bg #1a1e1f | panel #252a2a | panel-selected #2b312e | ink #EFE7D2 | ink-line rgba(239,231,210,0.78) | mute #B4B6AA (body copy #C4C6BA)
sage #A5B98C | sage-shadow #4a5c38 | orange #F0A94B | orange-shadow #7a4a0f | danger #FF9C84 | shadow #0c0f0f | text on sage/orange #1a1e1f
Shapes: 2dp outlines, irregular corner radii (different value per corner, vary per element), hard offset shadows with no blur
(4-6dp, color #0c0f0f; orange/sage buttons use their own shadow color). Active tab: orange fill, cream border, tiny rotation (-1deg).
Keep it subtle; rotation/irregular radii may be dropped where Compose/Views make them awkward, say so in the report.
Typography: ONE font family for the whole app, JetBrains Mono (bundle it in res/font, no downloadable fonts, app has no INTERNET permission).
Check the font license (OFL) and add the notice to the repo's third-party notices if there is one.
No handwriting fonts, no icon-library icons in buttons/lists, no gradients.

## Build and checks
UI strings and code comments in English. No new permissions (no INTERNET). Keep existing unit tests green and add tests where
logic changes (tab switching by L1/R1, empty state). Run: . .toolchain/env.sh && ./gradlew :android:testDebugUnitTest :android:assembleDebug
