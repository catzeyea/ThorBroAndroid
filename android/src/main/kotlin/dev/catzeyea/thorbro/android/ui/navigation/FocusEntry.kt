package dev.catzeyea.thorbro.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onPlaced

/**
 * A focus request that could not be served when the new screen was composed, because its first
 * element did not exist yet (rows of a lazy list are only created during layout). The element
 * takes focus as soon as it is placed, still before the frame is drawn.
 */
class PendingFocus {
    /** Identifies the screen the last request was for. */
    var key: Any? = null

    /** A request is waiting for its element. */
    var armed: Boolean = false
}

val LocalPendingFocus = staticCompositionLocalOf { PendingFocus() }

/** Requests focus; false if nothing is attached to [this] yet or it can't take focus. */
fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

/**
 * Attaches [requester] (the screen's first element). If a focus request for this screen is still
 * waiting, the element takes focus when it is placed. Use it for elements inside lazy lists.
 */
@Composable
fun Modifier.focusEntry(requester: FocusRequester): Modifier {
    val pending = LocalPendingFocus.current
    return this
        .focusRequester(requester)
        .onPlaced { if (pending.armed && requester.tryFocus()) pending.armed = false }
}
