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
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.domain.folders.FolderTree
import com.coursework.unifiedmail.ui.theme.colorForKey

/** Account switcher + folder-style navigation, matching Samsung Email's hamburger-drawer pattern. */
@Composable
fun AppDrawerContent(
    accounts: List<AccountEntity>,
    // Only populated for an account once its row has been expanded at least once (see
    // onExpandAccount) — null here means "not loaded yet", not "no folders".
    accountFolders: Map<String, List<FolderEntity>>,
    onCombinedViewClick: () -> Unit,
    onExpandAccount: (accountId: String) -> Unit,
    onFolderClick: (accountId: String, folderKey: String) -> Unit,
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
                            FolderTree.build(folders).let { tree -> FolderTree.flattenVisible(tree, emptySet()) }.forEach { node ->
                                Text(
                                    text = node.folder.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onFolderClick(account.id, MailRepository.localFolderKey(node.folder.fullName)) }
                                        .padding(start = (56 + node.depth * 16).dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
                                )
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
