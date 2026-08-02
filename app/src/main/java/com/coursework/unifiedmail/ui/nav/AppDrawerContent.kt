package com.coursework.unifiedmail.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.ui.theme.colorForKey

/** Account switcher + folder-style navigation, matching Samsung Email's hamburger-drawer pattern. */
@Composable
fun AppDrawerContent(
    accounts: List<AccountEntity>,
    onCombinedViewClick: () -> Unit,
    onAccountClick: (accountId: String) -> Unit,
    onManageAccountsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    ModalDrawerSheet {
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

        if (accounts.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            accounts.forEach { account ->
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
                    onClick = { onAccountClick(account.id) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
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
