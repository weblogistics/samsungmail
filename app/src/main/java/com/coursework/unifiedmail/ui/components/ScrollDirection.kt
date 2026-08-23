package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * True while the list is being scrolled up (or is at rest) — used to hide a FAB while scrolling
 * down through a long list and bring it back as soon as the user reverses direction, rather than
 * leaving it permanently blocking content.
 */
@Composable
fun rememberIsScrollingUp(listState: LazyListState): State<Boolean> {
    return remember(listState) {
        var previousIndex by mutableStateOf(listState.firstVisibleItemIndex)
        var previousScrollOffset by mutableStateOf(listState.firstVisibleItemScrollOffset)
        derivedStateOf {
            if (previousIndex != listState.firstVisibleItemIndex) {
                previousIndex > listState.firstVisibleItemIndex
            } else {
                previousScrollOffset >= listState.firstVisibleItemScrollOffset
            }.also {
                previousIndex = listState.firstVisibleItemIndex
                previousScrollOffset = listState.firstVisibleItemScrollOffset
            }
        }
    }
}
