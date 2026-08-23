package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Bounds the list pane can be dragged between — wide enough to stay legible, narrow enough
// that the detail pane never gets crowded out on a screen just past the dual-pane threshold.
const val MIN_LIST_PANE_WIDTH_DP = 280
const val MAX_LIST_PANE_WIDTH_DP = 640

/**
 * A draggable divider between the list and detail panes of a two-pane layout. The visible line
 * is a plain [VerticalDivider]; the draggable hit target around it is wider, since a 1dp-wide
 * touch target isn't usable.
 */
@Composable
fun SplitPaneDivider(
    onDrag: (deltaDp: Float) -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { deltaPx ->
                    with(density) { onDrag(deltaPx.toDp().value) }
                },
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider()
    }
}
