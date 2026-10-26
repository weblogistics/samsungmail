package com.coursework.unifiedmail.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.ui.components.EmptyState
import com.coursework.unifiedmail.ui.components.FlatTextField
import com.coursework.unifiedmail.ui.components.LastSyncedText
import com.coursework.unifiedmail.ui.components.MAX_LIST_PANE_WIDTH_DP
import com.coursework.unifiedmail.ui.components.MIN_LIST_PANE_WIDTH_DP
import com.coursework.unifiedmail.ui.components.MessageFilterButton
import com.coursework.unifiedmail.ui.components.MessageListSkeleton
import com.coursework.unifiedmail.ui.components.SplitPaneDivider
import com.coursework.unifiedmail.ui.components.rememberIsScrollingUp
import com.coursework.unifiedmail.ui.components.rememberIsWideScreen
import com.coursework.unifiedmail.ui.components.showSnackbarFor
import com.coursework.unifiedmail.ui.nav.AppDrawerContent
import com.coursework.unifiedmail.ui.nav.MessageDetailPane
import com.coursework.unifiedmail.ui.nav.detailPaneMessageRoute
import com.coursework.unifiedmail.ui.nav.detailPaneThreadRoute
import com.coursework.unifiedmail.ui.nav.rememberDetailPaneSelection
import com.coursework.unifiedmail.ui.theme.colorForKey
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedInboxScreen(
    onManageAccountsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFolderClick: (accountId: String, folderKey: String) -> Unit,
    onDraftsClick: () -> Unit,
    onMessageClick: (accountId: String, uid: Long) -> Unit,
    onThreadClick: (accountId: String, conversationId: String) -> Unit,
    onComposeClick: (accountId: String) -> Unit,
    onReplyClick: (accountId: String, folderKey: String, uid: Long) -> Unit,
    onReplyAllClick: (accountId: String, folderKey: String, uid: Long) -> Unit,
    onForwardClick: (accountId: String, folderKey: String, uid: Long) -> Unit,
    viewModel: UnifiedInboxViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val accountFolders by viewModel.accountFolders.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val hasAttachmentFilter by viewModel.hasAttachmentFilter.collectAsState()
    val unreadOnlyFilter by viewModel.unreadOnlyFilter.collectAsState()
    val flaggedOnlyFilter by viewModel.flaggedOnlyFilter.collectAsState()
    val anyFilterActive = hasAttachmentFilter || unreadOnlyFilter || flaggedOnlyFilter
    val isServerSearching by viewModel.isServerSearching.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionModeActive = selectedIds.isNotEmpty()
    val undoableAction by viewModel.undoableAction.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    var showAccountPicker by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val isScrollingUp by rememberIsScrollingUp(listState)
    // Starts expanded if there's already a query (e.g. restored after rotation) so an
    // in-progress search is never hidden behind the icon unexpectedly.
    var isSearchExpanded by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) searchFocusRequester.requestFocus()
    }
    val isWideScreen = rememberIsWideScreen(settings.dualPaneMinWidthDp)
    // Only ever navigated on wide screens (see below) — on a phone-width run the list pane
    // always fills the screen and navigates the outer app-level controller exactly as before.
    val detailNavController = rememberNavController()
    val detailPaneSelection = rememberDetailPaneSelection(detailNavController)
    // null until the user drags the splitter this session; falls back to the persisted width
    // (settings.listPaneWidthDp) until then, so it also picks up that value once it's loaded
    // rather than freezing on the pre-DataStore default.
    var draggedPaneWidthDp by remember { mutableStateOf<Float?>(null) }
    // Wide screens only: the open message/thread expanded over the list (see MessageDetailPane).
    var isDetailFullScreen by rememberSaveable { mutableStateOf(false) }
    val showDetailFullScreen = isWideScreen && isDetailFullScreen
    val paneWidthDp = (draggedPaneWidthDp ?: settings.listPaneWidthDp.toFloat())
        .coerceIn(MIN_LIST_PANE_WIDTH_DP.toFloat(), MAX_LIST_PANE_WIDTH_DP.toFloat())

    LaunchedEffect(undoableAction) {
        val action = undoableAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbarFor(
            message = action.label,
            actionLabel = "Undo",
            durationMillis = settings.undoDurationSeconds * 1000L,
        )
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

    val composeButton: @Composable () -> Unit = {
        // Hidden while scrolling down through a long list so it doesn't sit over content;
        // reappears the moment the user scrolls back up (or stops).
        AnimatedVisibility(visible = isScrollingUp && !showDetailFullScreen, enter = fadeIn(), exit = fadeOut()) {
            FloatingActionButton(
                onClick = {
                    if (accounts.size <= 1) {
                        accounts.firstOrNull()?.let { onComposeClick(it.id) }
                    } else {
                        showAccountPicker = true
                    }
                },
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Compose")
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                accounts = accounts,
                accountFolders = accountFolders,
                onCombinedViewClick = { scope.launch { drawerState.close() } },
                onExpandAccount = { accountId -> viewModel.loadFoldersIfNeeded(accountId) },
                onFolderClick = { accountId, folderKey ->
                    scope.launch { drawerState.close() }
                    onFolderClick(accountId, folderKey)
                },
                onDraftsClick = {
                    scope.launch { drawerState.close() }
                    onDraftsClick()
                },
                onManageAccountsClick = {
                    scope.launch { drawerState.close() }
                    onManageAccountsClick()
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onSettingsClick()
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showDetailFullScreen) {
                    // The open message's own top bar stands in for this one.
                } else if (selectionModeActive) {
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
                            IconButton(onClick = viewModel::bulkMoveToTrash) {
                                Icon(Icons.Filled.Delete, contentDescription = "Move to trash")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (isSearchExpanded) {
                                FlatTextField(
                                    value = searchQuery,
                                    onValueChange = viewModel::onSearchQueryChange,
                                    placeholder = { Text("Search mail") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester),
                                )
                            } else {
                                Text("Unified Inbox")
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (isSearchExpanded) {
                                        isSearchExpanded = false
                                        viewModel.onSearchQueryChange("")
                                    } else {
                                        scope.launch { drawerState.open() }
                                    }
                                },
                            ) {
                                if (isSearchExpanded) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                                } else {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                                }
                            }
                        },
                        actions = {
                            if (isSearchExpanded) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                    }
                                }
                            } else {
                                IconButton(onClick = { isSearchExpanded = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search mail")
                                }
                                MessageFilterButton(
                                    hasAttachmentOnly = hasAttachmentFilter,
                                    unreadOnly = unreadOnlyFilter,
                                    flaggedOnly = flaggedOnlyFilter,
                                    onHasAttachmentOnlyChange = viewModel::onHasAttachmentFilterChange,
                                    onUnreadOnlyChange = viewModel::onUnreadOnlyFilterChange,
                                    onFlaggedOnlyChange = viewModel::onFlaggedOnlyFilterChange,
                                    onClearFilters = viewModel::clearFilters,
                                )
                                IconButton(onClick = viewModel::sync, enabled = !isSyncing) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                }
                            }
                        },
                    )
                }
            },
            // On wide screens the button sits in the list pane instead (below), so it doesn't cover
            // the open message's bottom bar.
            floatingActionButton = { if (!isWideScreen) composeButton() },
        ) { padding ->
            if (isWideScreen) {
                // consumeWindowInsets: the detail pane's own Scaffold/TopAppBar would otherwise pad for the
                // status and navigation bars a second time, leaving a gap above the open message.
                Row(modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                    if (!showDetailFullScreen) {
                        Box(modifier = Modifier.width(paneWidthDp.dp)) {
                            UnifiedMessageListPane(
                                modifier = Modifier.fillMaxSize(),
                                isRowOpen = detailPaneSelection,
                                messages = messages,
                                accounts = accounts,
                                isSyncing = isSyncing,
                                lastSyncedAt = lastSyncedAt,
                                searchQuery = searchQuery,
                                anyFilterActive = anyFilterActive,
                                isServerSearching = isServerSearching,
                                settings = settings,
                                selectedIds = selectedIds,
                                selectionModeActive = selectionModeActive,
                                viewModel = viewModel,
                                onMessageClick = { accountId, uid ->
                                    detailNavController.navigate(detailPaneMessageRoute(accountId, MailRepository.INBOX_FOLDER_KEY, uid))
                                },
                                onThreadClick = { accountId, conversationId ->
                                    detailNavController.navigate(detailPaneThreadRoute(accountId, MailRepository.INBOX_FOLDER_KEY, conversationId))
                                },
                                listState = listState,
                            )
                            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { composeButton() }
                        }
                        SplitPaneDivider(
                            onDrag = { deltaDp ->
                                draggedPaneWidthDp = ((draggedPaneWidthDp ?: settings.listPaneWidthDp.toFloat()) + deltaDp)
                                    .coerceIn(MIN_LIST_PANE_WIDTH_DP.toFloat(), MAX_LIST_PANE_WIDTH_DP.toFloat())
                            },
                            onDragStopped = {
                                draggedPaneWidthDp?.let { viewModel.setListPaneWidthDp(it.roundToInt()) }
                            },
                        )
                    }
                    MessageDetailPane(
                        navController = detailNavController,
                        onReply = onReplyClick,
                        onReplyAll = onReplyAllClick,
                        onForward = onForwardClick,
                        isFullScreen = isDetailFullScreen,
                        onFullScreenChange = { isDetailFullScreen = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                UnifiedMessageListPane(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    messages = messages,
                    accounts = accounts,
                    isSyncing = isSyncing,
                    lastSyncedAt = lastSyncedAt,
                    searchQuery = searchQuery,
                    anyFilterActive = anyFilterActive,
                    isServerSearching = isServerSearching,
                    settings = settings,
                    selectedIds = selectedIds,
                    selectionModeActive = selectionModeActive,
                    viewModel = viewModel,
                    onMessageClick = onMessageClick,
                    onThreadClick = onThreadClick,
                    listState = listState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedMessageListPane(
    messages: List<ConversationSummary>,
    accounts: List<AccountEntity>,
    isSyncing: Boolean,
    lastSyncedAt: Long?,
    searchQuery: String,
    anyFilterActive: Boolean,
    isServerSearching: Boolean,
    settings: AppSettings,
    selectedIds: Set<String>,
    selectionModeActive: Boolean,
    viewModel: UnifiedInboxViewModel,
    onMessageClick: (accountId: String, uid: Long) -> Unit,
    onThreadClick: (accountId: String, conversationId: String) -> Unit,
    listState: LazyListState,
    // Wide screens: whether a row is the one open in the detail pane (highlighted).
    isRowOpen: (ConversationSummary) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LastSyncedText(
            isSyncing = isSyncing,
            lastSyncedAtEpochMillis = lastSyncedAt,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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
            if (accounts.isEmpty()) {
                EmptyState(icon = Icons.Filled.Inbox, message = "No accounts yet. Open the menu to add one.")
            } else if (messages.isEmpty() && isSyncing && searchQuery.isBlank() && !anyFilterActive) {
                // Distinguishes "still syncing" from "genuinely empty" — a blank list +
                // spinner reads the same as either.
                MessageListSkeleton()
            } else if (messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Inbox,
                    message = when {
                        searchQuery.isBlank() && !anyFilterActive -> "No messages yet. Pull to sync."
                        // Filter-only (e.g. "Has attachment") with no text query — server
                        // search doesn't support this filter, so there's no fallback to offer.
                        searchQuery.isBlank() -> "No messages match this filter."
                        // The "Search server" action itself lives above, always visible
                        // whenever there's a query — not repeated here.
                        else -> "No local results for \"$searchQuery\"."
                    },
                )
            } else {
                LazyColumn(state = listState) {
                    items(messages, key = { it.latestMessage.id }) { summary ->
                        val message = summary.latestMessage
                        MessageListItem(
                            message = message,
                            conversationCount = summary.messageCount,
                            hasUnread = summary.unreadCount > 0,
                            hasAttachments = summary.attachmentCount > 0,
                            density = settings.listDensity,
                            senderFirstInList = settings.senderFirstInList,
                            accountColor = colorForKey(message.accountId),
                            swipeRightAction = settings.swipeRightAction,
                            swipeLeftAction = settings.swipeLeftAction,
                            onClick = {
                                if (summary.messageCount > 1) {
                                    onThreadClick(message.accountId, message.conversationId)
                                } else {
                                    viewModel.recordReadingOrder()
                                    onMessageClick(message.accountId, message.uid)
                                }
                            },
                            onToggleRead = { viewModel.toggleRowRead(summary) },
                            onRemove = { viewModel.moveRowToTrash(summary) },
                            onArchive = { viewModel.archiveRow(summary) },
                            onToggleFlag = { viewModel.toggleRowFlag(summary) },
                            isSelected = message.id in selectedIds,
                            isOpen = isRowOpen(summary),
                            selectionModeActive = selectionModeActive,
                            onLongClick = { viewModel.toggleSelection(message.id) },
                            onToggleSelect = { viewModel.toggleSelection(message.id) },
                        )
                        HorizontalDivider()
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
