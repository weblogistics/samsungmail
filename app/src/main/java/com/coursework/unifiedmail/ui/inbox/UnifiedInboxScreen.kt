package com.coursework.unifiedmail.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.ui.theme.colorForKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedInboxScreen(
    onManageAccountsClick: () -> Unit,
    onMessageClick: (accountId: String, uid: Long) -> Unit,
    onComposeClick: (accountId: String) -> Unit,
    viewModel: UnifiedInboxViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showAccountPicker by remember { mutableStateOf(false) }

    if (showAccountPicker) {
        ComposeAccountPickerDialog(
            accounts = accounts,
            onDismiss = { showAccountPicker = false },
            onPick = { accountId ->
                showAccountPicker = false
                onComposeClick(accountId)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unified Inbox") },
                actions = {
                    IconButton(onClick = viewModel::sync, enabled = !isSyncing) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    IconButton(onClick = onManageAccountsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Manage accounts")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (accounts.size <= 1) {
                        accounts.firstOrNull()?.let { onComposeClick(it.id) }
                    } else {
                        showAccountPicker = true
                    }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Compose")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search mail") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            syncError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = viewModel::sync,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (accounts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No accounts yet. Tap the settings icon to add one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isBlank()) "No messages yet. Pull to sync." else "No results for \"$searchQuery\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn {
                        items(messages, key = { it.latestMessage.id }) { summary ->
                            val message = summary.latestMessage
                            MessageListItem(
                                message = message,
                                conversationCount = summary.messageCount,
                                accountColor = colorForKey(message.accountId),
                                onClick = { onMessageClick(message.accountId, message.uid) },
                                onToggleRead = { viewModel.setMessageRead(message, !message.isRead) },
                                onRemove = { viewModel.removeMessageLocally(message) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeAccountPickerDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onPick: (accountId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send from") },
        text = {
            Column {
                accounts.forEach { account ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(account.id) },
                        headlineContent = { Text(account.displayName) },
                        supportingContent = { Text(account.emailAddress) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(colorForKey(account.id), shape = CircleShape),
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
