package dev.catzeyea.thorbro.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Delivers screen buttons (X, Select, B) to the screen that is on top. Every screen registers a
 * handler while it is shown ([OnControllerKeys]); the most recently registered one gets the key.
 */
class KeyRouter {
    private val handlers = mutableListOf<(ControllerButton) -> Boolean>()

    fun push(handler: (ControllerButton) -> Boolean) {
        handlers += handler
    }

    fun remove(handler: (ControllerButton) -> Boolean) {
        handlers.remove(handler)
    }

    /** True if the top screen used the button. */
    fun handle(button: ControllerButton): Boolean = handlers.lastOrNull()?.invoke(button) ?: false
}

val LocalKeyRouter = staticCompositionLocalOf { KeyRouter() }

/** Registers [handler] for screen buttons while this composable is shown. */
@Composable
fun OnControllerKeys(handler: (ControllerButton) -> Boolean) {
    val router = LocalKeyRouter.current
    val current by rememberUpdatedState(handler)
    DisposableEffect(router) {
        val registered: (ControllerButton) -> Boolean = { current(it) }
        router.push(registered)
        onDispose { router.remove(registered) }
    }
}
