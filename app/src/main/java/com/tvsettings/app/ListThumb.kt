package com.tvsettings.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// How far the list moves per pixel of pointer travel, on top of the thumb ratio.
private const val DRAG_GAIN = 2.5f

// A press in the thumb's strip (the right edge) drags the list; presses anywhere else are left alone.
// Track movement is scaled to content movement by total/visible items, which is close enough for lists of similar rows.
fun Modifier.thumbDrag(state: LazyListState, scope: CoroutineScope): Modifier = pointerInput(state) {
    val stripPx = 24.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x < size.width - stripPx) return@awaitEachGesture
        down.consume()
        drag(down.id) { change ->
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            val dy = change.position.y - change.previousPosition.y
            change.consume()
            if (total > visible && visible > 0) {
                scope.launch { state.scrollBy(dy * total / visible * DRAG_GAIN) }
            }
        }
    }
}

// Drag anywhere on a horizontal list to move it; the pointer's horizontal travel drives the scroll.
fun Modifier.rowDrag(state: LazyListState, scope: CoroutineScope): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        drag(down.id) { change ->
            val info = state.layoutInfo
            val dx = change.position.x - change.previousPosition.x
            change.consume()
            if (info.totalItemsCount > info.visibleItemsInfo.size) {
                scope.launch { state.scrollBy(dx * DRAG_GAIN) }
            }
        }
    }
}
