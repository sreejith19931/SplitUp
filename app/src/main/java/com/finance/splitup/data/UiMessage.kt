package com.finance.splitup.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide lightweight messaging bridge. Replaces the ~70 scattered
 * `Toast.makeText(...).show()` calls with a single stream that MainActivity
 * renders through the Scaffold's SnackbarHost, so user feedback is styled
 * consistently and never overlaps the keyboard or nav bar.
 *
 * Fire-and-forget: safe to call from any thread or layer (screens, helpers,
 * deep-link handlers). Messages emitted while no collector is attached are
 * dropped (same behavior as a toast shown on a backgrounded screen).
 */
object UiMessage {
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<String> = _messages

    /** Show a short-duration message (replaces Toast.LENGTH_SHORT). */
    fun show(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(message)
    }

    /** Show a long-duration message (replaces Toast.LENGTH_LONG). */
    fun showLong(message: String) {
        if (message.isNotBlank()) _messages.tryEmit(message)
    }
}
