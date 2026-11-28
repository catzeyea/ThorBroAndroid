package dev.catzeyea.thorbro.android.ui

/**
 * Texts of the Scan info card and "How it works". Every sentence must stay true for the code
 * (ScanPlanner, Classifier, FileMover, MoveLog). In particular: there is no later undo, only the
 * Undo button on the result screen.
 */
object HelpTexts {

    data class Point(val title: String, val text: String)

    fun scanIntro(folder: String) = "Scan reads one folder, here $folder. Nothing is moved until you say so."

    val SCAN_POINTS = listOf(
        Point(
            "What it looks at",
            "Only files directly in this folder. Folders, unfinished downloads and files that look like apps, saves, " +
                "cheats or firmware stay where they are.",
        ),
        // Adapted from the spec (decision): unsure guesses can be switched on in the preview.
        Point(
            "What gets sorted",
            "A file is switched on only when its Switch title ID, its file header or its file extension points to " +
                "exactly one system in your biggest ROMs folder. Unsure files start switched off; you decide.",
        ),
        Point(
            "Safe by default",
            "Nothing moves until you press A, nothing is overwritten, and every move is written to ThorBro/logs. " +
                "Undo on the result screen puts the files back.",
        ),
    )

    const val START = "How your Thor is doing: battery, storage, CPU and temperature. ThorBro looks happy, busy, tired or " +
        "stuffed, and sleeps when no PC is connected."
    const val FILES = "Browse the Thor and sort it. Select opens the actions: scan a folder, pin it, make it the default scan folder."
    const val SAVES = "Your emulator saves, grouped by emulator. Back up before you try something risky, restore when you " +
        "need it. Peanuts saves can be backed up, but not restored yet."
    const val STREAM = "Connect to your PC and start your PC games on the Thor. Setup tells you what to do on the PC first."
}
