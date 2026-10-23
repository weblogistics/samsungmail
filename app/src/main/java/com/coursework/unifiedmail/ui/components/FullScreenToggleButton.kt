package com.coursework.unifiedmail.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/** Top-bar action in the wide-screen detail pane that expands it over the message list, or restores the split. */
@Composable
fun FullScreenToggleButton(isFullScreen: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (isFullScreen) {
            Icon(Icons.Filled.CloseFullscreen, contentDescription = "Show message list")
        } else {
            Icon(Icons.Filled.OpenInFull, contentDescription = "Full screen")
        }
    }
}
