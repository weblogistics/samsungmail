package com.coursework.unifiedmail.ui.message

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.AttachmentEntity
import com.coursework.unifiedmail.ui.components.MoveToFolderDialog
import com.coursework.unifiedmail.ui.components.SenderAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onBack: () -> Unit,
    onReply: (accountId: String, folderKey: String, uid: Long) -> Unit,
    onReplyAll: (accountId: String, folderKey: String, uid: Long) -> Unit,
    onForward: (accountId: String, folderKey: String, uid: Long) -> Unit,
    viewModel: MessageDetailViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val account by viewModel.account.collectAsState()
    val moveError by viewModel.moveError.collectAsState()
    val movedAway by viewModel.movedAway.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val downloadingIndex by viewModel.downloadingIndex.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val downloadedFileUri by viewModel.downloadedFileUri.collectAsState()
    var showMoveDialog by remember { mutableStateOf(false) }
    var showRemoteContent by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(movedAway) {
        if (movedAway) onBack()
    }

    LaunchedEffect(downloadedFileUri) {
        val (uri, mimeType) = downloadedFileUri ?: return@LaunchedEffect
        openError = try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            null
        } catch (e: ActivityNotFoundException) {
            "No app found to open this file"
        }
        viewModel.consumeDownloadedFile()
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
                viewModel.moveTo(folderKey)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(message?.subject?.takeIf { it.isNotBlank() } ?: "(no subject)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showMoveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to")
                    }
                    IconButton(onClick = viewModel::moveToTrash) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        bottomBar = {
            if (message != null) {
                BottomAppBar {
                    TextButton(
                        onClick = { onReply(viewModel.accountId, viewModel.folderKey, viewModel.uid) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                        Text(" Reply")
                    }
                    TextButton(
                        onClick = { onReplyAll(viewModel.accountId, viewModel.folderKey, viewModel.uid) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReplyAll, contentDescription = null)
                        Text(" Reply all")
                    }
                    TextButton(
                        onClick = { onForward(viewModel.accountId, viewModel.folderKey, viewModel.uid) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
                        Text(" Forward")
                    }
                }
            }
        },
    ) { padding ->
        val current = message
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // The header stays outside the scrollable body: a WebView manages its own scrolling
            // internally (nesting it inside an outer verticalScroll doesn't size correctly), so
            // for HTML messages the body gets the remaining space directly rather than sharing a
            // scroll container with the header.
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    moveError?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.error)
                    }

                    val senderLabel = current.fromPersonal?.takeIf { it.isNotBlank() }
                        ?: current.fromAddress ?: "Unknown sender"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SenderAvatar(senderLabel, size = 48.dp)
                        Column {
                            Text(text = senderLabel, fontWeight = FontWeight.Bold)
                            current.toAddresses?.takeIf { it.isNotBlank() }?.let {
                                Text(text = "To: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (current.bodyHtml != null && !showRemoteContent) {
                        TextButton(onClick = { showRemoteContent = true }) {
                            Text("Show images")
                        }
                    }
                    if (attachments.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            items(attachments, key = { it.id }) { attachment ->
                                AttachmentChip(
                                    attachment = attachment,
                                    isDownloading = downloadingIndex == attachment.indexInMessage,
                                    onClick = { viewModel.downloadAttachment(attachment) },
                                )
                            }
                        }
                    }
                    (downloadError ?: openError)?.let { errorMessage ->
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }

                val html = current.bodyHtml
                if (html != null) {
                    HtmlMessageBody(
                        html = html,
                        showRemoteContent = showRemoteContent,
                        textZoomPercent = settings.messageTextSize.scalePercent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        val baseSize = MaterialTheme.typography.bodyLarge.fontSize
                        Text(
                            text = current.bodyText ?: current.bodyPreview,
                            fontSize = baseSize * (settings.messageTextSize.scalePercent / 100f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: AttachmentEntity, isDownloading: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = !isDownloading,
        label = { Text(formatFileSize(attachment.sizeBytes)?.let { "${attachment.fileName} ($it)" } ?: attachment.fileName) },
        leadingIcon = {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(AssistChipDefaults.IconSize))
            } else {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        },
    )
}

private fun formatFileSize(bytes: Long?): String? {
    if (bytes == null) return null
    val kb = bytes / 1024.0
    return when {
        kb < 1 -> "$bytes B"
        kb < 1024 -> "%.0f KB".format(kb)
        else -> "%.1f MB".format(kb / 1024)
    }
}
