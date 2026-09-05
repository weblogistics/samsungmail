package com.coursework.unifiedmail.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.ui.components.FlatTextField
import com.coursework.unifiedmail.ui.message.HtmlMessageBody
import com.coursework.unifiedmail.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onSent: () -> Unit,
    onBack: () -> Unit,
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris)
    }
    // Contacts feed autocomplete (see DeviceContactsProvider/ComposeViewModel.loadContacts) but
    // aren't required to compose a message, so this is asked for in context here rather than
    // eagerly at app launch — either way the callback reloads suggestions so a grant takes effect
    // immediately instead of waiting for the next compose session.
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshContacts()
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) contactsPermission.launch(Manifest.permission.READ_CONTACTS)
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
                        modifier = Modifier.padding(horizontal = Spacing.sm),
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
                .padding(Spacing.md)
                .fillMaxSize()
                // Without this, the IME just overlaps whatever field the user is typing into
                // instead of the layout shrinking to stay above it — see AndroidManifest's
                // matching windowSoftInputMode="adjustResize" on MainActivity.
                .imePadding()
                // The whole form scrolls as one unit (rather than only the body field getting a
                // fixed leftover slice of the screen via weight()) so that whichever field is
                // focused — body included — can scroll up past To/Cc/Subject to stay above the
                // keyboard instead of being hidden under it. Compose does this scrolling
                // automatically for a focused/edited text field via its own BringIntoViewRequester
                // once there's an actual scrollable ancestor to act on.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            EmailAddressField(
                value = state.to,
                onValueChange = viewModel::onToChange,
                suggestions = contacts,
                label = { Text("To") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                EmailAddressField(
                    value = state.cc,
                    onValueChange = viewModel::onCcChange,
                    suggestions = contacts,
                    label = { Text("Cc") },
                    modifier = Modifier.weight(1f),
                )
                // Bcc is rarely used and eats screen space every compose session doesn't need —
                // hidden by default, revealed on request (see ComposeUiState.showBcc).
                if (!state.showBcc) {
                    TextButton(onClick = viewModel::showBccField) { Text("Bcc") }
                }
            }
            if (state.showBcc) {
                EmailAddressField(
                    value = state.bcc,
                    onValueChange = viewModel::onBccChange,
                    suggestions = contacts,
                    label = { Text("Bcc") },
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
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(vertical = Spacing.xs),
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
                // No weight() here anymore — the outer Column scrolls as a whole (see its
                // modifier above), and weight() can't be combined with an unbounded/scrollable
                // parent. The field just grows with its own content instead; heightIn keeps it a
                // reasonably-sized tap target while still empty.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
            )

            // Read-only — the rich-text editor above can't represent the original message's own
            // formatting/images, so a forwarded HTML original is shown as-is here instead of
            // being flattened into editable text (which was the actual bug this replaces).
            state.quotedHtml?.let { quotedHtml ->
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                Text(
                    text = "Forwarded message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
                HtmlMessageBody(
                    html = quotedHtml,
                    // Already shown to the user once in MessageDetailScreen before they chose to
                    // forward it — no separate "show images" prompt needed a second time here.
                    showRemoteContent = true,
                    // A WebView needs a real bounded height to measure correctly — unlike normal
                    // Compose content it doesn't have an intrinsic "wrap content" size, and
                    // weight()/fillMaxHeight() no longer apply now that the outer Column scrolls
                    // (see its modifier above). It scrolls its own overflow internally within
                    // this fixed box.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
            }

            // Read-only, same reasoning as the forwarded-message block above — a reply's quoted
            // original is kept out of the editable body so a horizontal rule can separate the new
            // message from the previous one, instead of the two running together as one blob.
            state.quotedText?.let { quotedText ->
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                Text(
                    text = quotedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // No weight()/own scroll anymore — it's plain flow content within the outer
                    // Column's single scroll (see that Column's modifier above).
                    modifier = Modifier.fillMaxWidth(),
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
