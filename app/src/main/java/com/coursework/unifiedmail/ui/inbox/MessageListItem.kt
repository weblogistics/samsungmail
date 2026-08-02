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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.ui.theme.colorForKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shared row used by both the per-account Inbox and the unified Inbox. Swipe start-to-end toggles
 * read/unread (snaps back — it's not a real dismissal); swipe end-to-start removes the message
 * from the local cache only. Neither swipe reaches the server: there's no IMAP write/move/delete
 * support yet, so "remove" here means "hide locally," not "delete from the mailbox."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListItem(
    message: MessageEntity,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onRemove: () -> Unit,
    accountColor: Color? = null,
    conversationCount: Int = 1,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleRead()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onRemove()
                    true
                }
                SwipeToDismissBoxValue.Settled -> true
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
                backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
            ) {
                val fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold
                val senderLabel = message.fromPersonal?.takeIf { it.isNotBlank() }
                    ?: message.fromAddress ?: "Unknown sender"

                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onClick),
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

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val (color, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.primary, Icons.Filled.Email, Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.error, Icons.Filled.Delete, Alignment.CenterEnd)
        SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        if (icon != null) {
            val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
            Icon(icon, contentDescription = null, tint = onColor)
        }
    }
}

@Composable
private fun SenderAvatar(senderLabel: String) {
    val color = colorForKey(senderLabel)
    val initial = senderLabel.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
        Text(text = initial, color = onColor, fontWeight = FontWeight.Bold)
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
