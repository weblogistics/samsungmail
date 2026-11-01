package com.coursework.unifiedmail.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.domain.folders.FolderTree
import com.coursework.unifiedmail.ui.theme.colorForKey

private const val MAX_RECENT_FOLDERS_SHOWN = 2

/**
 * Account switcher + folder-style navigation, matching Samsung Email's hamburger-drawer pattern.
 * An expanded account lists its folders in three tiers, so the handful anyone actually uses
 * aren't buried in a long alphabetical tree: the common ones
 * (Inbox/Sent/Drafts/Outbox/Trash/Archive/Spam — see FolderTree.commonFolders), then the two most
 * recently opened, then the complete tree.
 */
@Composable
fun AppDrawerContent(
    accounts: List<AccountEntity>,
    // Only populated for an account once its row has been expanded at least once (see
    // onExpandAccount) — null here means "not loaded yet", not "no folders".
    accountFolders: Map<String, List<FolderEntity>>,
    // That account's recently-opened folders, most-recent-first — see
    // AppSettings.recentFoldersFor. Only the first [MAX_RECENT_FOLDERS_SHOWN] that still exist
    // and aren't already a shortcut get their own section.
    recentFoldersFor: (accountId: String) -> List<String> = { emptyList() },
    onCombinedViewClick: () -> Unit,
    onExpandAccount: (accountId: String) -> Unit,
    onFolderClick: (accountId: String, folder: FolderEntity) -> Unit,
    onDraftsClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // Collapsed by default for every account — expanding one is an explicit choice, so the
    // common case (one or two accounts, nobody cares about folders) stays a short, uncluttered
    // list instead of every account's folder tree dumped into the drawer up front.
    var expandedAccountIds by remember { mutableStateOf(setOf<String>()) }

    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "Unified Mail",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()

            NavigationDrawerItem(
                label = { Text("Combined view") },
                selected = true,
                icon = { Icon(Icons.Filled.Inbox, contentDescription = null) },
                onClick = onCombinedViewClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Drafts") },
                selected = false,
                icon = { Icon(Icons.Filled.Drafts, contentDescription = null) },
                onClick = onDraftsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            if (accounts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Accounts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                accounts.forEach { account ->
                    val isExpanded = account.id in expandedAccountIds
                    val toggleExpanded = {
                        if (!isExpanded) onExpandAccount(account.id)
                        expandedAccountIds = if (isExpanded) expandedAccountIds - account.id else expandedAccountIds + account.id
                    }
                    NavigationDrawerItem(
                        label = { Text(account.displayName) },
                        selected = false,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(colorForKey(account.id), shape = CircleShape),
                            )
                        },
                        // The chevron is just a visual cue that the row expands — tapping anywhere
                        // on the row does the same thing, rather than navigating to a separate
                        // screen the way this used to.
                        badge = {
                            Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isExpanded) "Hide folders" else "Show folders",
                            )
                        },
                        onClick = toggleExpanded,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    if (isExpanded) {
                        val folders = accountFolders[account.id]
                        if (folders == null) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 56.dp, top = 4.dp, bottom = 8.dp)
                                    .size(20.dp),
                            )
                        } else {
                            val common = FolderTree.commonFolders(
                                folders,
                                account.trashFolderFullName,
                                account.archiveFolderFullName,
                                account.spamFolderFullName,
                            )
                            val commonIds = common.map { it.id }.toSet()
                            // Anything already sitting in the shortcuts above would just be a
                            // duplicate one row further down.
                            val recent = recentFoldersFor(account.id)
                                .mapNotNull { fullName -> folders.firstOrNull { it.fullName == fullName } }
                                .filter { it.id !in commonIds }
                                .distinctBy { it.id }
                                .take(MAX_RECENT_FOLDERS_SHOWN)

                            common.forEach { folder ->
                                FolderRow(folder.displayName) { onFolderClick(account.id, folder) }
                            }
                            if (recent.isNotEmpty()) {
                                FolderSectionCaption("Recent")
                                recent.forEach { folder ->
                                    FolderRow(folder.displayName) { onFolderClick(account.id, folder) }
                                }
                            }
                            // Deliberately the *whole* tree, shortcuts included: filtering the
                            // tiers above back out would orphan their subfolders (on a Maildir
                            // server most folders are children of INBOX), and the point of this
                            // section is that it's the complete list in its real hierarchy.
                            if (common.isNotEmpty() || recent.isNotEmpty()) {
                                FolderSectionCaption("All folders")
                            }
                            FolderTree.build(folders).let { tree -> FolderTree.flattenVisible(tree, emptySet()) }.forEach { node ->
                                FolderRow(node.folder.displayName, depth = node.depth) {
                                    onFolderClick(account.id, node.folder)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Manage accounts") },
                selected = false,
                icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                onClick = onManageAccountsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = false,
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = onSettingsClick,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

/** One folder line under an expanded account, indented past the account's own icon column. */
@Composable
private fun FolderRow(displayName: String, depth: Int = 0, onClick: () -> Unit) {
    Text(
        text = displayName,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (56 + depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
    )
}

/** Divides one expanded account's folder tiers — aligned with the folder rows, not the account rows. */
@Composable
private fun FolderSectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 2.dp, end = 16.dp),
    )
}
