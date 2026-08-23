package com.coursework.unifiedmail.ui.thread

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.ui.components.SenderAvatar
import com.coursework.unifiedmail.ui.components.formatRelativeDate

/**
 * Lists every message in a conversation so the user can pick which one to open — a threaded row
 * in the inbox otherwise only ever showed the latest message. No swipe/bulk-selection here (that
 * belongs to the inbox list); tapping a row just opens that specific message via [onMessageClick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    onMessageClick: (folderKey: String, uid: Long) -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (messages.size > 1) "${messages.size} messages" else "Thread") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(messages, key = { it.id }) { message ->
                ThreadMessageRow(
                    message = message,
                    onClick = { onMessageClick(viewModel.folderKey, message.uid) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ThreadMessageRow(message: MessageEntity, onClick: () -> Unit) {
    val fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold
    val senderLabel = message.fromPersonal?.takeIf { it.isNotBlank() } ?: message.fromAddress ?: "Unknown sender"

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                if (!message.isRead) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        },
    )
}
