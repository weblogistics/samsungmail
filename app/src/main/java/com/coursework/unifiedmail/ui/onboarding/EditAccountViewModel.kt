package com.coursework.unifiedmail.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.local.MailSecurity
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.MailConnectionTester
import com.coursework.unifiedmail.data.remote.MailServerDefaults
import com.coursework.unifiedmail.data.remote.MailTestResult
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SyncOutcome
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
    val folders: List<FolderEntity> = emptyList(),
    // Server folder fullName, or null for "auto-detect" (MailRepository's name-based guessing).
    val trashFolderFullName: String? = null,
    val archiveFolderFullName: String? = null,
    val spamFolderFullName: String? = null,
    val signature: String = "",
    val connectionStatus: ConnectionCheckStatus = ConnectionCheckStatus.IDLE,
    val connectionMessage: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isResyncing: Boolean = false,
    val resyncMessage: String? = null,
    val isEmptyingTrash: Boolean = false,
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
    private val mailRepository: MailRepository,
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
                        trashFolderFullName = account.trashFolderFullName,
                        archiveFolderFullName = account.archiveFolderFullName,
                        spamFolderFullName = account.spamFolderFullName,
                        signature = account.signature.orEmpty(),
                    )
                }
            }
            // Already synced by normal use (this is edit, not first-time setup) — just reads the
            // cached list, no network round trip.
            _uiState.update { it.copy(folders = mailRepository.getFolders(accountId)) }
        }
    }

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }
    fun onEmailAddressChange(value: String) = _uiState.update { it.copy(emailAddress = value) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onImapHostChange(value: String) = _uiState.update { it.copy(imapHost = value) }
    fun onImapPortChange(value: String) = _uiState.update { it.copy(imapPort = value) }
    fun onImapSecurityChange(value: MailSecurity) =
        _uiState.update { it.copy(imapSecurity = value, imapPort = MailServerDefaults.imapPort(value).toString()) }
    fun onSmtpHostChange(value: String) = _uiState.update { it.copy(smtpHost = value) }
    fun onSmtpPortChange(value: String) = _uiState.update { it.copy(smtpPort = value) }
    fun onSmtpSecurityChange(value: MailSecurity) =
        _uiState.update { it.copy(smtpSecurity = value, smtpPort = MailServerDefaults.smtpPort(value).toString()) }
    fun onNotificationsEnabledChange(value: Boolean) = _uiState.update { it.copy(notificationsEnabled = value) }
    fun onTrashFolderChange(fullName: String?) = _uiState.update { it.copy(trashFolderFullName = fullName) }
    fun onArchiveFolderChange(fullName: String?) = _uiState.update { it.copy(archiveFolderFullName = fullName) }
    fun onSpamFolderChange(fullName: String?) = _uiState.update { it.copy(spamFolderFullName = fullName) }
    fun onSignatureChange(value: String) = _uiState.update { it.copy(signature = value) }

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
                trashFolderFullName = state.trashFolderFullName,
                archiveFolderFullName = state.archiveFolderFullName,
                spamFolderFullName = state.spamFolderFullName,
                signature = state.signature.takeIf { it.isNotBlank() },
            )
            accountRepository.updateAccount(updated, newPassword = state.password.takeIf { it.isNotBlank() })
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val existing = accountRepository.getAccount(accountId) ?: return@launch
            mailRepository.deleteAccountAndCache(existing)
            _uiState.update { it.copy(showDeleteConfirmation = false, deleted = true) }
        }
    }

    /**
     * Forces every cached folder's next sync to be a full re-fetch instead of the usual UID
     * delta — the only way to backfill data (e.g. attachment metadata) added to the schema after
     * a message was already cached, since normal sync never re-fetches what it already has. Only
     * the Inbox is synced immediately here for feedback; other folders pick up the reset next
     * time they're opened.
     */
    fun forceFullResync() {
        if (_uiState.value.isResyncing) return
        _uiState.update { it.copy(isResyncing = true, resyncMessage = null) }
        viewModelScope.launch {
            mailRepository.resetSyncProgress(accountId)
            val message = when (val outcome = mailRepository.syncAccount(accountId)) {
                is SyncOutcome.Success -> "Refreshed Inbox — re-downloaded ${outcome.newMessageCount} messages"
                is SyncOutcome.Failure -> outcome.reason
            }
            _uiState.update { it.copy(isResyncing = false, resyncMessage = message) }
        }
    }

    /**
     * Also reachable from the Trash folder's own toolbar (only shown while actually viewing it) —
     * duplicated here since that entry point turned out to be too easy to miss: it requires
     * navigating drawer → account → folder list → Trash before it appears at all.
     */
    fun emptyTrash() {
        if (_uiState.value.isEmptyingTrash) return
        _uiState.update { it.copy(isEmptyingTrash = true, resyncMessage = null) }
        viewModelScope.launch {
            val message = when (val outcome = mailRepository.emptyTrash(accountId)) {
                is SyncOutcome.Success -> "Trash emptied"
                is SyncOutcome.Failure -> outcome.reason
            }
            _uiState.update { it.copy(isEmptyingTrash = false, resyncMessage = message) }
        }
    }
}
