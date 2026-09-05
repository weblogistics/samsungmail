package com.coursework.unifiedmail.ui.message

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.AttachmentEntity
import com.coursework.unifiedmail.ui.components.MoveToFolderDialog
import com.coursework.unifiedmail.ui.components.SenderAvatar
import com.coursework.unifiedmail.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val showHeaders by viewModel.showHeaders.collectAsState()
    val headers by viewModel.headers.collectAsState()
    val headersLoading by viewModel.headersLoading.collectAsState()
    val headersError by viewModel.headersError.collectAsState()
    var showMoveDialog by remember { mutableStateOf(false) }
    var showRemoteContent by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf<String?>(null) }
    // Set when the user long-presses a link in the HTML body — see HtmlMessageBody's
    // onLinkLongPress — and shown in a small "where does this go" dialog below.
    var longPressedLinkUrl by remember { mutableStateOf<String?>(null) }
    // Set when the user long-presses the sender name/avatar — the header row usually shows just
    // a display name, not the actual address it came from.
    var showSenderAddress by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

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

    if (showHeaders) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHeaders,
            title = { Text("Message headers") },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    when {
                        headersLoading -> CircularProgressIndicator()
                        headersError != null -> Text(headersError!!, color = MaterialTheme.colorScheme.error)
                        else -> Text(headers.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !headers.isNullOrEmpty(),
                    onClick = { clipboardManager.setText(AnnotatedString(headers.orEmpty())) },
                ) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissHeaders) { Text("Close") }
            },
        )
    }

    longPressedLinkUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { longPressedLinkUrl = null },
            title = { Text("Link destination") },
            text = { Text(url) },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: ActivityNotFoundException) {
                            // No app to handle it — the URL is already shown above, nothing more to do.
                        }
                        longPressedLinkUrl = null
                    },
                ) { Text("Open") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(url))
                        longPressedLinkUrl = null
                    },
                ) { Text("Copy link") }
            },
        )
    }

    if (showSenderAddress) {
        val current = message
        val senderName = current?.fromPersonal?.takeIf { it.isNotBlank() } ?: current?.fromAddress ?: "Unknown sender"
        val address = current?.fromAddress ?: "Unknown address"
        AlertDialog(
            onDismissRequest = { showSenderAddress = false },
            title = { Text(senderName) },
            text = { Text(address) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(address))
                        showSenderAddress = false
                    },
                ) { Text("Copy address") }
            },
            dismissButton = {
                TextButton(onClick = { showSenderAddress = false }) { Text("Close") }
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
                    IconButton(onClick = viewModel::openHeaders) {
                        Icon(Icons.Filled.Info, contentDescription = "View headers")
                    }
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
                Column(modifier = Modifier.padding(Spacing.md)) {
                    moveError?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.error)
                    }

                    val senderLabel = current.fromPersonal?.takeIf { it.isNotBlank() }
                        ?: current.fromAddress ?: "Unknown sender"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        // The row usually shows just a display name, not the address it actually
                        // came from — long-press to see it (see the showSenderAddress dialog
                        // above). onClick is a required no-op; there's nothing to do on a plain tap.
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { showSenderAddress = true }),
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
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.padding(top = Spacing.sm),
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
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))
                }

                val html = current.bodyHtml
                if (html != null) {
                    HtmlMessageBody(
                        html = html,
                        showRemoteContent = showRemoteContent,
                        textZoomPercent = settings.messageTextSize.scalePercent,
                        onLinkLongPress = { url -> longPressedLinkUrl = url },
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
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
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
