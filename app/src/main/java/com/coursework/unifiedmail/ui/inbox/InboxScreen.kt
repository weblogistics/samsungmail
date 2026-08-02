package com.coursework.unifiedmail.ui.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.R
import com.coursework.unifiedmail.ui.components.LastSyncedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onMessageClick: (uid: Long) -> Unit,
    onComposeClick: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::sync, enabled = !isSyncing) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onComposeClick) {
                Icon(Icons.Filled.Edit, contentDescription = "Compose")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LastSyncedText(
                isSyncing = isSyncing,
                lastSyncedAtEpochMillis = lastSyncedAt,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.no_messages_yet),
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
                                swipeRightAction = settings.swipeRightAction,
                                swipeLeftAction = settings.swipeLeftAction,
                                onClick = { onMessageClick(message.uid) },
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
