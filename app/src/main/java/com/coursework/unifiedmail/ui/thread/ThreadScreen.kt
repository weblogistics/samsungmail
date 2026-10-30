package com.coursework.unifiedmail.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.ui.components.EmptyState
import com.coursework.unifiedmail.ui.components.SenderAvatar
import com.coursework.unifiedmail.ui.components.formatRelativeDate
import com.coursework.unifiedmail.ui.components.showSnackbarFor
import com.coursework.unifiedmail.ui.inbox.SwipeActionsBox

/**
 * Lists every message in a conversation so the user can pick which one to open — a threaded row
 * in the inbox otherwise only ever showed the latest message. Rows take the same configurable
 * swipe actions as the inbox (with Undo for moves); bulk selection stays an inbox-only feature.
 * Tapping a row opens that specific message via [onMessageClick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    onMessageClick: (folderKey: String, uid: Long) -> Unit,
    // Extra top-bar actions supplied by the host — the wide-screen pane's full-screen toggle.
    paneActions: @Composable () -> Unit = {},
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val undoableAction by viewModel.undoableAction.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(undoableAction) {
        val action = undoableAction ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbarFor(
            message = action.label,
            actionLabel = "Undo",
            durationMillis = settings.undoDurationSeconds * 1000L,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastMove() else viewModel.dismissUndo()
    }
    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
        viewModel.dismissError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (messages.size > 1) "${messages.size} messages" else "Thread") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { paneActions() },
            )
        },
    ) { padding ->
        // Stays on screen (rather than popping back) once the last message is swiped away, so its
        // Undo snackbar is still there to use.
        if (messages.isEmpty() && undoableAction != null) {
            EmptyState(icon = Icons.Filled.Email, message = "No messages left in this thread", modifier = Modifier.padding(padding))
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(messages, key = { it.id }) { message ->
                SwipeActionsBox(
                    swipeRightAction = settings.swipeRightAction,
                    swipeLeftAction = settings.swipeLeftAction,
                    onToggleRead = { viewModel.setMessageRead(message, !message.isRead) },
                    onRemove = { viewModel.moveToTrash(message) },
                    onArchive = { viewModel.archiveMessage(message) },
                    onToggleFlag = { viewModel.setMessageFlagged(message, !message.isFlagged) },
                ) {
                    ThreadMessageRow(
                        message = message,
                        onClick = {
                            viewModel.recordReadingOrder()
                            onMessageClick(viewModel.folderKey, message.uid)
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ThreadMessageRow(message: MessageEntity, onClick: () -> Unit) {
    val fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold
    val senderLabel = message.fromPersonal?.takeIf { it.isNotBlank() } ?: message.fromAddress ?: "Unknown sender"
    val unreadStripeColor = MaterialTheme.colorScheme.primary
    // Same faint-tint approach as the inbox list (MessageListItem.rowColor) — see its comment
    // for why this has to be set via ListItem's own `colors`, not just an outer background.
    val rowColor = if (message.isFlagged) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            // Same unread cue as the inbox list (bold + left stripe). Drawn over the content
            // because ListItem paints its own opaque container; its start padding keeps it clear.
            .drawWithContent {
                drawContent()
                if (!message.isRead) drawRect(unreadStripeColor, size = Size(4.dp.toPx(), size.height))
            }
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = rowColor),
        leadingContent = { SenderAvatar(senderLabel) },
        headlineContent = { Text(text = senderLabel, fontWeight = fontWeight, maxLines = 1) },
        supportingContent = {
            if (message.bodyPreview.isNotBlank()) {
                Text(text = message.bodyPreview, maxLines = 1)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatRelativeDate(message.sentDateEpochMillis ?: message.receivedDateEpochMillis))
                if (message.isFlagged) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = "Flagged",
                        modifier = Modifier.padding(top = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}
