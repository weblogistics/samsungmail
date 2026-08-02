package com.coursework.unifiedmail.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Always-visible sync status line — the app previously only showed a spinner while syncing and
 * nothing afterward, so a successful-but-uneventful refresh (no new mail) was indistinguishable
 * from doing nothing at all. This makes "a sync actually happened" unambiguous.
 */
@Composable
fun LastSyncedText(isSyncing: Boolean, lastSyncedAtEpochMillis: Long?, modifier: Modifier = Modifier) {
    val text = when {
        isSyncing -> "Syncing…"
        lastSyncedAtEpochMillis == null -> "Not synced yet"
        else -> "Synced ${formatRelativeAge(lastSyncedAtEpochMillis)}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun formatRelativeAge(epochMillis: Long): String {
    val minutes = (System.currentTimeMillis() - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}
