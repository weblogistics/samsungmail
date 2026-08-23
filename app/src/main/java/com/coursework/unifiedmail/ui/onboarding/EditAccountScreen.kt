package com.coursework.unifiedmail.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.ui.components.FlatTextField
import com.coursework.unifiedmail.ui.components.RadioOptionDialog
import com.coursework.unifiedmail.ui.components.SecurityChipRow
import com.coursework.unifiedmail.ui.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountScreen(
    onAccountSaved: () -> Unit,
    onAccountDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showTrashDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showSpamDialog by remember { mutableStateOf(false) }
    var showEmptyTrashConfirmation by remember { mutableStateOf(false) }

    if (showTrashDialog) {
        RadioOptionDialog(
            title = "Trash folder",
            options = listOf(null) + state.folders.map { it.fullName },
            selected = state.trashFolderFullName,
            labelFor = { fullName -> state.folders.firstOrNull { it.fullName == fullName }?.displayName ?: "Auto-detect" },
            onDismiss = { showTrashDialog = false },
            onSelect = {
                viewModel.onTrashFolderChange(it)
                showTrashDialog = false
            },
            extraContent = {
                TextButton(
                    onClick = {
                        showTrashDialog = false
                        showEmptyTrashConfirmation = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Empty trash now", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (showArchiveDialog) {
        RadioOptionDialog(
            title = "Archive folder",
            options = listOf(null) + state.folders.map { it.fullName },
            selected = state.archiveFolderFullName,
            labelFor = { fullName -> state.folders.firstOrNull { it.fullName == fullName }?.displayName ?: "Auto-detect" },
            onDismiss = { showArchiveDialog = false },
            onSelect = {
                viewModel.onArchiveFolderChange(it)
                showArchiveDialog = false
            },
        )
    }
    if (showSpamDialog) {
        RadioOptionDialog(
            title = "Spam folder",
            options = listOf(null) + state.folders.map { it.fullName },
            selected = state.spamFolderFullName,
            labelFor = { fullName -> state.folders.firstOrNull { it.fullName == fullName }?.displayName ?: "Auto-detect" },
            onDismiss = { showSpamDialog = false },
            onSelect = {
                viewModel.onSpamFolderChange(it)
                showSpamDialog = false
            },
        )
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onAccountSaved()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onAccountDeleted()
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete account?") },
            text = { Text("This removes the account and its cached mail from this device. Nothing is deleted from the mail server.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            },
        )
    }
    if (showEmptyTrashConfirmation) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmation = false },
            title = { Text("Empty trash?") },
            text = { Text("Every message in Trash will be permanently deleted from the server. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showEmptyTrashConfirmation = false; viewModel.emptyTrash() }) {
                    Text("Empty trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlatTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
            )
            FlatTextField(
                value = state.emailAddress,
                onValueChange = viewModel::onEmailAddressChange,
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            FlatTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
            )
            FlatTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                placeholder = { Text("Leave blank to keep current password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Notifications for new mail")
                Switch(checked = state.notificationsEnabled, onCheckedChange = viewModel::onNotificationsEnabledChange)
            }

            SectionLabel("Mail organization")
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showTrashDialog = true }),
                headlineContent = { Text("Trash folder") },
                supportingContent = {
                    Text(state.folders.firstOrNull { it.fullName == state.trashFolderFullName }?.displayName ?: "Auto-detect")
                },
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showArchiveDialog = true }),
                headlineContent = { Text("Archive folder") },
                supportingContent = {
                    Text(state.folders.firstOrNull { it.fullName == state.archiveFolderFullName }?.displayName ?: "Auto-detect")
                },
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showSpamDialog = true }),
                headlineContent = { Text("Spam folder") },
                supportingContent = {
                    Text(state.folders.firstOrNull { it.fullName == state.spamFolderFullName }?.displayName ?: "Auto-detect")
                },
            )
            FlatTextField(
                value = state.signature,
                onValueChange = viewModel::onSignatureChange,
                label = { Text("Signature") },
                placeholder = { Text("Appended to messages you compose") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Incoming mail (IMAP)")
            FlatTextField(
                value = state.imapHost,
                onValueChange = viewModel::onImapHostChange,
                label = { Text("IMAP host") },
                modifier = Modifier.fillMaxWidth(),
            )
            FlatTextField(
                value = state.imapPort,
                onValueChange = viewModel::onImapPortChange,
                label = { Text("IMAP port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SecurityChipRow(selected = state.imapSecurity, onSelected = viewModel::onImapSecurityChange)

            SectionLabel("Outgoing mail (SMTP)")
            FlatTextField(
                value = state.smtpHost,
                onValueChange = viewModel::onSmtpHostChange,
                label = { Text("SMTP host") },
                modifier = Modifier.fillMaxWidth(),
            )
            FlatTextField(
                value = state.smtpPort,
                onValueChange = viewModel::onSmtpPortChange,
                label = { Text("SMTP port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SecurityChipRow(selected = state.smtpSecurity, onSelected = viewModel::onSmtpSecurityChange)

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = state.canSave && state.connectionStatus != ConnectionCheckStatus.TESTING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.connectionStatus == ConnectionCheckStatus.TESTING) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Test connection")
            }

            state.connectionMessage?.let { message ->
                val color = if (state.connectionStatus == ConnectionCheckStatus.SUCCESS) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(text = message, color = color)
            }

            Button(
                onClick = viewModel::saveChanges,
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save changes")
            }

            SectionLabel("Maintenance")
            OutlinedButton(
                onClick = viewModel::forceFullResync,
                enabled = !state.isResyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isResyncing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Refresh all mail")
            }
            Text(
                "Re-downloads cached mail from the server — use this if attachments or other details are missing from messages synced before this device last updated.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.resyncMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(
                onClick = { showEmptyTrashConfirmation = true },
                enabled = !state.isEmptyingTrash,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isEmptyingTrash) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Empty trash")
            }

            OutlinedButton(
                onClick = viewModel::requestDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete account")
            }
        }
    }
}
