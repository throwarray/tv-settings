package com.tvsettings.app

import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

// Fires on OK/Enter key-down and swallows the release, so holding OK while moving can't activate the new target.
// Must sit before clickable/toggleable in the chain so it sees the event first.
fun Modifier.keyDownClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val activate = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
    if (!enabled || !activate) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onClick()
    true
}

// OK tap runs onClick on release; holding OK runs onHold once when the key starts repeating, and the release is swallowed.
fun Modifier.clickOrHold(onClick: () -> Unit, onHold: () -> Unit): Modifier = composed {
    val held = remember { BooleanArray(1) }
    onPreviewKeyEvent { event ->
        val activate = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!activate) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            if (event.nativeKeyEvent.repeatCount > 0) {
                if (!held[0]) {
                    held[0] = true
                    onHold()
                }
            } else {
                held[0] = false
            }
        } else {
            if (!held[0]) onClick()
            held[0] = false
        }
        true
    }
}
