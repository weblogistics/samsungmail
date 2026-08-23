package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.domain.folders.FolderTree

/**
 * Folder picker for "Move to" — used by both MessageDetailScreen (single message) and the
 * per-account inbox screen's bulk-selection toolbar. Sections, in order:
 * 1. Special folders — the account's Trash/Archive/Spam, resolved via
 *    FolderTree.resolveSpecialFolders (explicit Settings choice, else a single best name-match
 *    fallback — never more than one folder per kind, which is what previously duplicated
 *    lookalikes into this section).
 * 2. Recently used destinations, most-recent-first, capped at 3.
 * 3. Most-used destinations by selection count, capped at 2 — independent of recency, since a
 *    folder used constantly-but-not-recently should still surface. See
 *    SettingsRepository.recordMoveFolderUsage.
 * 4. Everything else, as a tree (indented by nesting depth, see FolderTree.build) rather than a
 *    flat alphabetical list — a folder's place in its own hierarchy is still useful context once
 *    it's this far down the picker.
 * Each tier excludes folders already shown in an earlier one. Scrollable — a flat unbounded list
 * previously made folders past the first screenful unreachable.
 */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    trashFolderFullName: String? = null,
    archiveFolderFullName: String? = null,
    spamFolderFullName: String? = null,
    recentFolderFullNames: List<String> = emptyList(),
    mostUsedFolderFullNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSelect: (folderKey: String) -> Unit,
) {
    val special = FolderTree.resolveSpecialFolders(folders, trashFolderFullName, archiveFolderFullName, spamFolderFullName)
    val specialIds = special.map { it.id }.toSet()

    val recent = recentFolderFullNames
        .mapNotNull { fullName -> folders.firstOrNull { it.fullName == fullName && it.id !in specialIds } }
        .distinctBy { it.id }
        .take(MAX_RECENT_SHOWN)
    val recentIds = recent.map { it.id }.toSet()

    val mostUsed = mostUsedFolderFullNames
        .mapNotNull { fullName -> folders.firstOrNull { it.fullName == fullName && it.id !in specialIds && it.id !in recentIds } }
        .distinctBy { it.id }
        .take(MAX_MOST_USED_SHOWN)
    val mostUsedIds = mostUsed.map { it.id }.toSet()

    val shownIds = specialIds + recentIds + mostUsedIds
    val otherTree = FolderTree.flattenVisible(FolderTree.build(folders), collapsedFullNames = emptySet())
        .filter { it.folder.id !in shownIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (special.isNotEmpty()) {
                    items(special, key = { "special-${it.id}" }) { folder ->
                        FolderRow(folder = folder, onClick = { onSelect(MailRepository.localFolderKey(folder.fullName)) })
                    }
                }
                if (recent.isNotEmpty()) {
                    if (special.isNotEmpty()) item { HorizontalDivider() }
                    item { SectionCaption("Recently used") }
                    items(recent, key = { "recent-${it.id}" }) { folder ->
                        FolderRow(folder = folder, onClick = { onSelect(MailRepository.localFolderKey(folder.fullName)) })
                    }
                }
                if (mostUsed.isNotEmpty()) {
                    if (special.isNotEmpty() || recent.isNotEmpty()) item { HorizontalDivider() }
                    item { SectionCaption("Frequently used") }
                    items(mostUsed, key = { "frequent-${it.id}" }) { folder ->
                        FolderRow(folder = folder, onClick = { onSelect(MailRepository.localFolderKey(folder.fullName)) })
                    }
                }
                if (otherTree.isNotEmpty()) {
                    if (special.isNotEmpty() || recent.isNotEmpty() || mostUsed.isNotEmpty()) item { HorizontalDivider() }
                    items(otherTree, key = { "tree-${it.folder.id}" }) { node ->
                        FolderRow(
                            folder = node.folder,
                            depth = node.depth,
                            onClick = { onSelect(MailRepository.localFolderKey(node.folder.fullName)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val MAX_RECENT_SHOWN = 3
private const val MAX_MOST_USED_SHOWN = 2

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun FolderRow(folder: FolderEntity, depth: Int = 0, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(folder.displayName, modifier = Modifier.padding(start = (depth * 16).dp)) },
    )
}
