# ThorBro Android – explanations + Saves by emulator: STEP 1, INVESTIGATE ONLY

Do NOT commit or push. Do NOT change code in this step. Mark every claim "verified" or "unverified". No guessing.
Read DESIGN-ANDROID-UPDATE-2.md and boards/ first. Branch from feat/android-restructure.

Find out:
A. Cover art on the Android side: can the app read the iiSU art folder on the SD card with its all-files access (path in the spec)? Test read-only on the real Thor. How do you match a save (title ID) to a ROM file name / art folder? Note the BotW case (the base file carries an update ID). Which image size/format is cheap to load as a 150 dp tile? How would you cache/decode without frame drops (we are close to the 16.7 ms budget)?
B. Scan text: what exactly does the Android scan look at (extension, title ID, header sniffing)? Write the three info-card sentences so they are 100 % true. List any difference to the PC rules.
C. Saves grouping: which emulators does the app detect today (Eden, Eden Duo, Citron, Sudachi, yuzu)? Which are installed on this Thor? How many saves per emulator, and can "backed up x of y" be computed cheaply (backup folder lookup)? Is Peanuts (dev.nxnative.android.peanuts, saves at Android/data/<pkg>/files/saves/<TitleID>) readable from the app? Read/backup only, no restore test.
D. Focus/controller: how would you build the emulator-section + tile grid so that D-pad navigation and the focus ring stay in the first frame (as in the last restructure)? Any risk for jank with 20+ image tiles?
E. Small items for the same step: (1) .jar in the scan: send to Review instead of listing it as a j2me game. (2) thorbro-android-0.1.0.apk in the repo root is the old debug build: how do you replace it with the release build (signed with the debug key, as before) and what must the README say? Only investigate and propose, change nothing.

Report: short sections A-E, verified/unverified table, open decisions for me. No code.
