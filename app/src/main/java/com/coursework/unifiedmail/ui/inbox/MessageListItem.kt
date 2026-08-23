package com.coursework.unifiedmail.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.settings.ListDensity
import com.coursework.unifiedmail.data.settings.SwipeAction
import com.coursework.unifiedmail.ui.components.SenderAvatar
import com.coursework.unifiedmail.ui.components.formatRelativeDate
import com.coursework.unifiedmail.ui.theme.SwipeActionBlue
import com.coursework.unifiedmail.ui.theme.SwipeActionGold
import com.coursework.unifiedmail.ui.theme.SwipeActionGreen
import com.coursework.unifiedmail.ui.theme.SwipeActionIconTint
import com.coursework.unifiedmail.ui.theme.SwipeActionRed

/**
 * Shared row used by both the per-account Inbox and the unified Inbox. Which action each swipe
 * direction performs is configurable (Settings screen); a direction whose action is NONE has its
 * gesture disabled outright rather than accepting the swipe and doing nothing. REMOVE moves the
 * message to the account's Trash folder on the server (a real IMAP move, not just a local-cache
 * removal) — the swipe dismisses optimistically, so a failed move can make the row briefly
 * reappear if the network call fails after the animation completes.
 *
 * Long-pressing a row enters bulk-selection mode ([onLongClick]); while [selectionModeActive] is
 * true, swiping is disabled for every row (it would otherwise conflict with tap-to-toggle) and a
 * checkbox replaces the sender avatar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageListItem(
    message: MessageEntity,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onRemove: () -> Unit,
    onArchive: () -> Unit = {},
    onToggleFlag: () -> Unit = {},
    swipeRightAction: SwipeAction,
    swipeLeftAction: SwipeAction,
    accountColor: Color? = null,
    conversationCount: Int = 1,
    density: ListDensity = ListDensity.COMFORTABLE,
    isSelected: Boolean = false,
    selectionModeActive: Boolean = false,
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val action = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> swipeRightAction
                SwipeToDismissBoxValue.EndToStart -> swipeLeftAction
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            when (action) {
                SwipeAction.TOGGLE_READ -> {
                    onToggleRead()
                    false
                }
                SwipeAction.REMOVE -> {
                    onRemove()
                    true
                }
                SwipeAction.ARCHIVE -> {
                    onArchive()
                    true
                }
                SwipeAction.TOGGLE_FLAG -> {
                    onToggleFlag()
                    false
                }
                SwipeAction.NONE -> false
            }
        },
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        if (accountColor != null) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accountColor),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = !selectionModeActive && swipeRightAction != SwipeAction.NONE,
                enableDismissFromEndToStart = !selectionModeActive && swipeLeftAction != SwipeAction.NONE,
                backgroundContent = {
                    val action = when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.StartToEnd -> swipeRightAction
                        SwipeToDismissBoxValue.EndToStart -> swipeLeftAction
                        SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
                    }
                    SwipeBackground(action, dismissState.dismissDirection)
                },
            ) {
                val fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold
                val senderLabel = message.fromPersonal?.takeIf { it.isNotBlank() }
                    ?: message.fromAddress ?: "Unknown sender"

                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        )
                        .combinedClickable(
                            onClick = if (selectionModeActive) onToggleSelect else onClick,
                            // A small tactile confirmation that a long-press actually registered
                            // and entered selection mode, rather than the row just doing nothing
                            // visible for a beat.
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            },
                        )
                        .padding(vertical = if (density == ListDensity.COMPACT) 0.dp else 6.dp),
                    leadingContent = {
                        if (selectionModeActive) {
                            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                        } else {
                            SenderAvatar(senderLabel)
                        }
                    },
                    headlineContent = {
                        val subjectText = message.subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (message.isAnswered) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Replied",
                                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (conversationCount > 1) "$subjectText ($conversationCount)" else subjectText,
                                fontWeight = fontWeight,
                                maxLines = 1,
                            )
                        }
                    },
                    supportingContent = {
                        Column {
                            Text(text = senderLabel, fontWeight = fontWeight, maxLines = 1)
                            if (density != ListDensity.COMPACT && message.bodyPreview.isNotBlank()) {
                                Text(text = message.bodyPreview, maxLines = 1)
                            }
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = formatRelativeDate(message.sentDateEpochMillis ?: message.receivedDateEpochMillis))
                            if (!message.isRead) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            if (message.hasAttachments) {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = "Has attachments",
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

// TOGGLE_READ renders blue/envelope (matching the blue unread dot used elsewhere in this list),
// REMOVE renders red/trash (destructive — was previously blue, indistinguishable in intent from
// the read/unread toggle), ARCHIVE always green/archive-box, TOGGLE_FLAG always gold/star —
// consistent regardless of which physical direction each is currently bound to.
@Composable
private fun SwipeBackground(action: SwipeAction, direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }
    val (color, icon) = when (action) {
        SwipeAction.TOGGLE_READ -> SwipeActionBlue to Icons.Filled.Email
        SwipeAction.REMOVE -> SwipeActionRed to Icons.Filled.Delete
        SwipeAction.ARCHIVE -> SwipeActionGreen to Icons.Filled.Archive
        SwipeAction.TOGGLE_FLAG -> SwipeActionGold to Icons.Filled.Star
        SwipeAction.NONE -> Color.Transparent to null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = SwipeActionIconTint)
        }
    }
}
