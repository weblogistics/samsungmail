package com.coursework.unifiedmail.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MailSecurity
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.MailConnectionTester
import com.coursework.unifiedmail.data.remote.MailTestResult
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditAccountUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val emailAddress: String = "",
    val username: String = "",
    val password: String = "",
    val imapHost: String = "",
    val imapPort: String = "993",
    val imapSecurity: MailSecurity = MailSecurity.SSL_TLS,
    val smtpHost: String = "",
    val smtpPort: String = "465",
    val smtpSecurity: MailSecurity = MailSecurity.SSL_TLS,
    val notificationsEnabled: Boolean = true,
    val connectionStatus: ConnectionCheckStatus = ConnectionCheckStatus.IDLE,
    val connectionMessage: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
) {
    val canSave: Boolean
        get() = !isLoading &&
            displayName.isNotBlank() &&
            emailAddress.isNotBlank() &&
            username.isNotBlank() &&
            imapHost.isNotBlank() &&
            imapPort.toIntOrNull() != null &&
            smtpHost.isNotBlank() &&
            smtpPort.toIntOrNull() != null
}

@HiltViewModel
class EditAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val mailConnectionTester: MailConnectionTester,
) : ViewModel() {

    private val accountId: String = checkNotNull(savedStateHandle["accountId"])

    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    // The password typed by the user takes priority when testing; otherwise fall back to the
    // already-stored one. Never shown in the password field — a stored secret is never
    // re-displayed, even to its own owner.
    private var storedPassword: String? = null

    init {
        viewModelScope.launch {
            val account = accountRepository.getAccount(accountId)
            storedPassword = accountRepository.getPassword(accountId)
            if (account != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        displayName = account.displayName,
                        emailAddress = account.emailAddress,
                        username = account.username,
                        imapHost = account.imapHost,
                        imapPort = account.imapPort.toString(),
                        imapSecurity = account.imapSecurity,
                        smtpHost = account.smtpHost,
                        smtpPort = account.smtpPort.toString(),
                        smtpSecurity = account.smtpSecurity,
                        notificationsEnabled = account.notificationsEnabled,
                    )
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onEmailAddressChange(value: String) = _uiState.update { it.copy(emailAddress = value) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onImapHostChange(value: String) = _uiState.update { it.copy(imapHost = value) }
    fun onImapPortChange(value: String) = _uiState.update { it.copy(imapPort = value) }
    fun onImapSecurityChange(value: MailSecurity) = _uiState.update { it.copy(imapSecurity = value) }
    fun onSmtpHostChange(value: String) = _uiState.update { it.copy(smtpHost = value) }
    fun onSmtpPortChange(value: String) = _uiState.update { it.copy(smtpPort = value) }
    fun onSmtpSecurityChange(value: MailSecurity) = _uiState.update { it.copy(smtpSecurity = value) }
    fun onNotificationsEnabledChange(value: Boolean) = _uiState.update { it.copy(notificationsEnabled = value) }

    fun requestDelete() = _uiState.update { it.copy(showDeleteConfirmation = true) }
    fun cancelDelete() = _uiState.update { it.copy(showDeleteConfirmation = false) }

    private fun effectivePassword(state: EditAccountUiState): String? =
        state.password.takeIf { it.isNotBlank() } ?: storedPassword

    fun testConnection() {
        val state = _uiState.value
        val imapPort = state.imapPort.toIntOrNull()
        val smtpPort = state.smtpPort.toIntOrNull()
        val password = effectivePassword(state)
        if (imapPort == null || smtpPort == null) {
            _uiState.update {
                it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "Port must be a number")
            }
            return
        }
        if (password == null) {
            _uiState.update {
                it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "Enter a password to test")
            }
            return
        }

        _uiState.update { it.copy(connectionStatus = ConnectionCheckStatus.TESTING, connectionMessage = null) }

        viewModelScope.launch {
            val imapResult = mailConnectionTester.testImapLogin(
                ImapConfig(state.imapHost, imapPort, state.imapSecurity, state.username, password),
            )
            if (imapResult is MailTestResult.Failure) {
                _uiState.update {
                    it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "IMAP: ${imapResult.reason}")
                }
                return@launch
            }

            val smtpResult = mailConnectionTester.testSmtpLogin(
                SmtpConfig(state.smtpHost, smtpPort, state.smtpSecurity, state.username, password),
            )
            if (smtpResult is MailTestResult.Failure) {
                _uiState.update {
                    it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "SMTP: ${smtpResult.reason}")
                }
                return@launch
            }

            _uiState.update {
                it.copy(connectionStatus = ConnectionCheckStatus.SUCCESS, connectionMessage = "Connection successful")
            }
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        val imapPort = state.imapPort.toIntOrNull() ?: return
        val smtpPort = state.smtpPort.toIntOrNull() ?: return
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val existing = accountRepository.getAccount(accountId) ?: return@launch
            val updated = existing.copy(
                displayName = state.displayName,
                emailAddress = state.emailAddress,
                username = state.username,
                imapHost = state.imapHost,
                imapPort = imapPort,
                imapSecurity = state.imapSecurity,
                smtpHost = state.smtpHost,
                smtpPort = smtpPort,
                smtpSecurity = state.smtpSecurity,
                notificationsEnabled = state.notificationsEnabled,
            )
            accountRepository.updateAccount(updated, newPassword = state.password.takeIf { it.isNotBlank() })
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val existing = accountRepository.getAccount(accountId) ?: return@launch
            accountRepository.deleteAccount(existing)
            _uiState.update { it.copy(showDeleteConfirmation = false, deleted = true) }
        }
    }
}
