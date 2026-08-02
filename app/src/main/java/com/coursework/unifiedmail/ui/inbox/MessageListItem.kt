package com.coursework.unifiedmail.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.settings.SwipeAction
import com.coursework.unifiedmail.ui.components.SenderAvatar
import com.coursework.unifiedmail.ui.theme.SwipeActionBlue
import com.coursework.unifiedmail.ui.theme.SwipeActionIconTint
import com.coursework.unifiedmail.ui.theme.SwipeActionOrange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shared row used by both the per-account Inbox and the unified Inbox. Which action each swipe
 * direction performs is configurable (Settings screen); a direction whose action is NONE has its
 * gesture disabled outright rather than accepting the swipe and doing nothing. REMOVE is
 * local-cache-only — no IMAP write support yet, so it never reaches the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListItem(
    message: MessageEntity,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onRemove: () -> Unit,
    swipeRightAction: SwipeAction,
    swipeLeftAction: SwipeAction,
    accountColor: Color? = null,
    conversationCount: Int = 1,
) {
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
                enableDismissFromStartToEnd = swipeRightAction != SwipeAction.NONE,
                enableDismissFromEndToStart = swipeLeftAction != SwipeAction.NONE,
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
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onClick)
                        .padding(vertical = 6.dp),
                    leadingContent = { SenderAvatar(senderLabel) },
                    headlineContent = {
                        val subjectText = message.subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
                        Text(
                            text = if (conversationCount > 1) "$subjectText ($conversationCount)" else subjectText,
                            fontWeight = fontWeight,
                            maxLines = 1,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(text = senderLabel, fontWeight = fontWeight, maxLines = 1)
                            if (message.bodyPreview.isNotBlank()) {
                                Text(text = message.bodyPreview, maxLines = 1)
                            }
                        }
                    },
                    trailingContent = {
                        Text(text = formatRelativeDate(message.sentDateEpochMillis ?: message.receivedDateEpochMillis))
                    },
                )
            }
        }
    }
}

// Colors match the real app's list_list_swipe_bg_color (orange) / list_list_right_swipe_bg_color
// (blue) tokens. TOGGLE_READ always renders orange/envelope, REMOVE always blue/trash —
// consistent regardless of which physical direction each is currently bound to.
@Composable
private fun SwipeBackground(action: SwipeAction, direction: SwipeToDismissBoxValue) {
    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }
    val (color, icon) = when (action) {
        SwipeAction.TOGGLE_READ -> SwipeActionOrange to Icons.Filled.Email
        SwipeAction.REMOVE -> SwipeActionBlue to Icons.Filled.Delete
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

private fun formatRelativeDate(epochMillis: Long?): String {
    if (epochMillis == null) return ""
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val date = dateTime.toLocalDate()

    return when {
        date == today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
