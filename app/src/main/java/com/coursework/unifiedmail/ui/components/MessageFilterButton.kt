package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Filter icon + dropdown, sitting to the right of a search field — standard checkbox-style
 * filters (Unread/Flagged/Has attachment) rather than a single-purpose chip. Toggling a filter
 * leaves the menu open so more than one can be combined in one interaction; only "Clear filters"
 * closes it. The icon itself is tinted to show at a glance whether any filter is active.
 */
@Composable
fun MessageFilterButton(
    hasAttachmentOnly: Boolean,
    unreadOnly: Boolean,
    flaggedOnly: Boolean,
    onHasAttachmentOnlyChange: (Boolean) -> Unit,
    onUnreadOnlyChange: (Boolean) -> Unit,
    onFlaggedOnlyChange: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anyActive = hasAttachmentOnly || unreadOnly || flaggedOnly

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = "Filter messages",
                tint = if (anyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Unread") },
                leadingIcon = { Checkbox(checked = unreadOnly, onCheckedChange = null) },
                onClick = { onUnreadOnlyChange(!unreadOnly) },
            )
            DropdownMenuItem(
                text = { Text("Flagged") },
                leadingIcon = { Checkbox(checked = flaggedOnly, onCheckedChange = null) },
                onClick = { onFlaggedOnlyChange(!flaggedOnly) },
            )
            DropdownMenuItem(
                text = { Text("Has attachment") },
                leadingIcon = { Checkbox(checked = hasAttachmentOnly, onCheckedChange = null) },
                onClick = { onHasAttachmentOnlyChange(!hasAttachmentOnly) },
            )
            if (anyActive) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Clear filters") },
                    onClick = {
                        onClearFilters()
                        expanded = false
                    },
                )
            }
        }
    }
}
