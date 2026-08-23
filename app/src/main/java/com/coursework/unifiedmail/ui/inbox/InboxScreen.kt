package com.coursework.unifiedmail.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.R
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.ui.components.EmptyState
import com.coursework.unifiedmail.ui.components.LastSyncedText
import com.coursework.unifiedmail.ui.components.MessageFilterButton
import com.coursework.unifiedmail.ui.components.MessageListSkeleton
import com.coursework.unifiedmail.ui.components.MoveToFolderDialog
import com.coursework.unifiedmail.ui.components.rememberIsScrollingUp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onMessageClick: (folderKey: String, uid: Long) -> Unit,
    onThreadClick: (folderKey: String, conversationId: String) -> Unit,
    onComposeClick: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionModeActive = selectedIds.isNotEmpty()
    val undoableAction by viewModel.undoableAction.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val hasAttachmentFilter by viewModel.hasAttachmentFilter.collectAsState()
    val unreadOnlyFilter by viewModel.unreadOnlyFilter.collectAsState()
    val flaggedOnlyFilter by viewModel.flaggedOnlyFilter.collectAsState()
    val isServerSearching by viewModel.isServerSearching.collectAsState()
    val isTrashFolder by viewModel.isTrashFolder.collectAsState()
    val isEmptyingTrash by viewModel.isEmptyingTrash.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val account by viewModel.account.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    val anyFilterActive = hasAttachmentFilter || unreadOnlyFilter || flaggedOnlyFilter
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val isScrollingUp by rememberIsScrollingUp(listState)
    var showMoveDialog by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(undoableAction) {
        val action = undoableAction ?: return@LaunchedEffect
        // Long, not the default Short — an "undo this move" window needs enough time to actually
        // notice and react to, not just enough to render.
        val result = snackbarHostState.showSnackbar(message = action.label, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastMove() else viewModel.dismissUndo()
    }
    LaunchedEffect(syncError) {
        val message = syncError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
        viewModel.dismissSyncError()
    }
    LaunchedEffect(infoMessage) {
        val message = infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        viewModel.dismissInfoMessage()
    }

    if (showMoveDialog) {
        MoveToFolderDialog(
            folders = folders,
            trashFolderFullName = account?.trashFolderFullName,
            archiveFolderFullName = account?.archiveFolderFullName,
            spamFolderFullName = account?.spamFolderFullName,
            recentFolderFullNames = settings.recentMoveFoldersFor(viewModel.accountId),
            mostUsedFolderFullNames = settings.mostUsedMoveFoldersFor(viewModel.accountId),
            onDismiss = { showMoveDialog = false },
            onSelect = { folderKey ->
                showMoveDialog = false
                viewModel.bulkMoveTo(folderKey)
            },
        )
    }
    if (showEmptyTrashConfirmation) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmation = false },
            title = { Text("Empty trash?") },
            text = { Text("Every message in Trash will be permanently deleted from the server. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showEmptyTrashConfirmation = false; viewModel.emptyTrash() }) {
                    Text("Empty trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionModeActive) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.bulkSetRead(true) }) {
                            Icon(Icons.Filled.MarkEmailRead, contentDescription = "Mark read")
                        }
                        IconButton(onClick = { viewModel.bulkSetRead(false) }) {
                            Icon(Icons.Filled.MarkEmailUnread, contentDescription = "Mark unread")
                        }
                        IconButton(onClick = viewModel::bulkArchive) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archive")
                        }
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to")
                        }
                        IconButton(onClick = viewModel::bulkMoveToTrash) {
                            Icon(Icons.Filled.Delete, contentDescription = "Move to trash")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        val title = if (viewModel.folderKey == MailRepository.INBOX_FOLDER_KEY) {
                            stringResource(R.string.inbox_title)
                        } else {
                            viewModel.folderKey
                        }
                        Text(title)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (isTrashFolder) {
                            IconButton(onClick = { showEmptyTrashConfirmation = true }, enabled = !isEmptyingTrash) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = "Empty trash")
                            }
                        }
                        IconButton(onClick = viewModel::markAllRead) {
                            Icon(Icons.Filled.DoneAll, contentDescription = "Mark all read")
                        }
                        IconButton(onClick = viewModel::sync, enabled = !isSyncing) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // Hidden while scrolling down through a long list so it doesn't sit over content;
            // reappears the moment the user scrolls back up (or stops).
            AnimatedVisibility(visible = isScrollingUp, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = onComposeClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Compose")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LastSyncedText(
                isSyncing = isSyncing,
                lastSyncedAtEpochMillis = lastSyncedAt,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search this folder") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                MessageFilterButton(
                    hasAttachmentOnly = hasAttachmentFilter,
                    unreadOnly = unreadOnlyFilter,
                    flaggedOnly = flaggedOnlyFilter,
                    onHasAttachmentOnlyChange = viewModel::onHasAttachmentFilterChange,
                    onUnreadOnlyChange = viewModel::onUnreadOnlyFilterChange,
                    onFlaggedOnlyChange = viewModel::onFlaggedOnlyFilterChange,
                    onClearFilters = viewModel::clearFilters,
                )
            }
            // Always available once there's a query, not gated on local results coming up
            // empty — the local cache is never guaranteed to have what the server does, and
            // waiting for an empty result before offering this made it easy to miss entirely.
            if (searchQuery.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    if (isServerSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        TextButton(onClick = viewModel::searchServer) {
                            Text("Search server for \"$searchQuery\"")
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = viewModel::sync,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (messages.isEmpty() && isSyncing && searchQuery.isBlank() && !anyFilterActive) {
                    // Distinguishes "this folder's first sync is still running" from "this folder
                    // is genuinely empty" — a blank list + spinner reads the same as either.
                    MessageListSkeleton()
                } else if (messages.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Inbox,
                        message = if (searchQuery.isBlank() && !anyFilterActive) {
                            stringResource(R.string.no_messages_yet)
                        } else {
                            "No messages match."
                        },
                    )
                } else {
                    LazyColumn(state = listState) {
                        items(messages, key = { it.latestMessage.id }) { summary ->
                            val message = summary.latestMessage
                            MessageListItem(
                                message = message,
                                conversationCount = summary.messageCount,
                                density = settings.listDensity,
                                swipeRightAction = settings.swipeRightAction,
                                swipeLeftAction = settings.swipeLeftAction,
                                onClick = {
                                    if (summary.messageCount > 1) {
                                        onThreadClick(message.folderName, message.conversationId)
                                    } else {
                                        onMessageClick(message.folderName, message.uid)
                                    }
                                },
                                onToggleRead = { viewModel.setMessageRead(message, !message.isRead) },
                                onRemove = { viewModel.moveToTrash(message) },
                                onArchive = { viewModel.archiveMessage(message) },
                                onToggleFlag = { viewModel.setMessageFlagged(message, !message.isFlagged) },
                                isSelected = message.id in selectedIds,
                                selectionModeActive = selectionModeActive,
                                onLongClick = { viewModel.toggleSelection(message.id) },
                                onToggleSelect = { viewModel.toggleSelection(message.id) },
                            )
                            HorizontalDivider()
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoadingOlder) {
                                    CircularProgressIndicator()
                                } else {
                                    TextButton(onClick = viewModel::loadOlderMessages) {
                                        Text("Load older messages")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
