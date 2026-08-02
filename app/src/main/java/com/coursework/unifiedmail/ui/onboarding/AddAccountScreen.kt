package com.coursework.unifiedmail.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.R
import com.coursework.unifiedmail.data.local.MailSecurity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onAccountSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.savedAccountId) {
        if (state.savedAccountId != null) onAccountSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text(stringResource(R.string.field_display_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.emailAddress,
                onValueChange = viewModel::onEmailAddressChange,
                label = { Text(stringResource(R.string.field_email_address)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.field_username)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.field_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Incoming mail (IMAP)")
            OutlinedTextField(
                value = state.imapHost,
                onValueChange = viewModel::onImapHostChange,
                label = { Text(stringResource(R.string.field_imap_host)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.imapPort,
                onValueChange = viewModel::onImapPortChange,
                label = { Text(stringResource(R.string.field_imap_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SecurityChipRow(selected = state.imapSecurity, onSelected = viewModel::onImapSecurityChange)

            SectionLabel("Outgoing mail (SMTP)")
            OutlinedTextField(
                value = state.smtpHost,
                onValueChange = viewModel::onSmtpHostChange,
                label = { Text(stringResource(R.string.field_smtp_host)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.smtpPort,
                onValueChange = viewModel::onSmtpPortChange,
                label = { Text(stringResource(R.string.field_smtp_port)) },
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
                Text(stringResource(R.string.action_test_connection))
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
                onClick = viewModel::saveAccount,
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save_account))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SecurityChipRow(selected: MailSecurity, onSelected: (MailSecurity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MailSecurity.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(option.label()) },
            )
        }
    }
}

private fun MailSecurity.label(): String = when (this) {
    MailSecurity.NONE -> "None"
    MailSecurity.SSL_TLS -> "SSL/TLS"
    MailSecurity.STARTTLS -> "STARTTLS"
}
