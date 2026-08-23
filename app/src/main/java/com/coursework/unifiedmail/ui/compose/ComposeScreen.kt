package com.coursework.unifiedmail.ui.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.ui.components.FlatTextField
import com.coursework.unifiedmail.ui.message.HtmlMessageBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onSent: () -> Unit,
    onBack: () -> Unit,
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris)
    }

    LaunchedEffect(state.sent) {
        if (state.sent) onSent()
    }

    if (state.showLinkDialog) {
        var url by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = viewModel::dismissLinkDialog,
            title = { Text("Insert link") },
            text = {
                FlatTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.insertLink(url) }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLinkDialog) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state.mode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach files")
                    }
                    // A full-width bottom button here gets covered by the IME the moment the
                    // user is actually typing — a compact top-right action stays reachable
                    // regardless of keyboard state.
                    FilledIconButton(
                        onClick = viewModel::send,
                        enabled = state.canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlatTextField(
                value = state.to,
                onValueChange = viewModel::onToChange,
                label = { Text("To") },
                placeholder = { Text("comma-separated addresses") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FlatTextField(
                    value = state.cc,
                    onValueChange = viewModel::onCcChange,
                    label = { Text("Cc") },
                    placeholder = { Text("comma-separated addresses") },
                    modifier = Modifier.weight(1f),
                )
                // Bcc is rarely used and eats screen space every compose session doesn't need —
                // hidden by default, revealed on request (see ComposeUiState.showBcc).
                if (!state.showBcc) {
                    TextButton(onClick = viewModel::showBccField) { Text("Bcc") }
                }
            }
            if (state.showBcc) {
                FlatTextField(
                    value = state.bcc,
                    onValueChange = viewModel::onBccChange,
                    label = { Text("Bcc") },
                    placeholder = { Text("comma-separated addresses") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FlatTextField(
                value = state.subject,
                onValueChange = viewModel::onSubjectChange,
                label = { Text("Subject") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.attachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    items(state.attachments, key = { it.localFilePath }) { attachment ->
                        AssistChip(
                            onClick = { viewModel.removeAttachment(attachment) },
                            label = { Text(attachment.fileName) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.AttachFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove ${attachment.fileName}",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }
            RichTextEditorField(
                runs = state.bodyRuns,
                selection = state.bodySelection,
                pendingStyle = state.pendingStyle,
                onValueChange = viewModel::onBodyValueChange,
                onToggleBold = viewModel::toggleBold,
                onToggleItalic = viewModel::toggleItalic,
                onToggleUnderline = viewModel::toggleUnderline,
                onToggleStrikethrough = viewModel::toggleStrikethrough,
                onToggleBullet = viewModel::toggleBullet,
                onToggleNumbering = viewModel::toggleNumbering,
                onInsertLinkClick = viewModel::requestInsertLink,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (state.quotedHtml != null || state.quotedText != null) 0.5f else 1f, fill = true),
            )

            // Read-only — the rich-text editor above can't represent the original message's own
            // formatting/images, so a forwarded HTML original is shown as-is here instead of
            // being flattened into editable text (which was the actual bug this replaces).
            state.quotedHtml?.let { quotedHtml ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Forwarded message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                HtmlMessageBody(
                    html = quotedHtml,
                    // Already shown to the user once in MessageDetailScreen before they chose to
                    // forward it — no separate "show images" prompt needed a second time here.
                    showRemoteContent = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f, fill = true),
                )
            }

            // Read-only, same reasoning as the forwarded-message block above — a reply's quoted
            // original is kept out of the editable body so a horizontal rule can separate the new
            // message from the previous one, instead of the two running together as one blob.
            state.quotedText?.let { quotedText ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = quotedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f, fill = true)
                        .verticalScroll(rememberScrollState()),
                )
            }

            state.error?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun titleFor(mode: ComposeMode): String = when (mode) {
    ComposeMode.NEW -> "New message"
    ComposeMode.REPLY -> "Reply"
    ComposeMode.REPLY_ALL -> "Reply all"
    ComposeMode.FORWARD -> "Forward"
}
