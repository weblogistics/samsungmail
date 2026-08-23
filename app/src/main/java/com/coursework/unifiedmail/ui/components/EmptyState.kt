package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.ui.theme.Spacing

/**
 * Shared "nothing here" visual for list screens (Inbox, Unified Inbox, Drafts, ...) — an icon
 * plus message reads more clearly as "genuinely empty" than plain centered text, which is easy
 * to mistake for a loading glitch or rendering error.
 */
@Composable
fun EmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Spacing.sm).size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
