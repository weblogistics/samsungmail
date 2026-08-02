package com.coursework.unifiedmail.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MailSecurity
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.MailConnectionTester
import com.coursework.unifiedmail.data.remote.MailTestResult
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.NewAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionCheckStatus { IDLE, TESTING, SUCCESS, FAILED }

data class AddAccountUiState(
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
    val connectionStatus: ConnectionCheckStatus = ConnectionCheckStatus.IDLE,
    val connectionMessage: String? = null,
    val isSaving: Boolean = false,
    val savedAccountId: String? = null,
) {
    val canSave: Boolean
        get() = displayName.isNotBlank() &&
            emailAddress.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            imapHost.isNotBlank() &&
            imapPort.toIntOrNull() != null &&
            smtpHost.isNotBlank() &&
            smtpPort.toIntOrNull() != null
}

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mailConnectionTester: MailConnectionTester,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

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

    fun testConnection() {
        val state = _uiState.value
        val imapPort = state.imapPort.toIntOrNull()
        val smtpPort = state.smtpPort.toIntOrNull()
        if (imapPort == null || smtpPort == null) {
            _uiState.update {
                it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "Port must be a number")
            }
            return
        }

        _uiState.update { it.copy(connectionStatus = ConnectionCheckStatus.TESTING, connectionMessage = null) }

        viewModelScope.launch {
            val imapResult = mailConnectionTester.testImapLogin(
                ImapConfig(state.imapHost, imapPort, state.imapSecurity, state.username, state.password),
            )
            if (imapResult is MailTestResult.Failure) {
                _uiState.update {
                    it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "IMAP: ${imapResult.reason}")
                }
                return@launch
            }

            val smtpResult = mailConnectionTester.testSmtpLogin(
                SmtpConfig(state.smtpHost, smtpPort, state.smtpSecurity, state.username, state.password),
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

    fun saveAccount() {
        val state = _uiState.value
        val imapPort = state.imapPort.toIntOrNull() ?: return
        val smtpPort = state.smtpPort.toIntOrNull() ?: return
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = accountRepository.addAccount(
                NewAccount(
                    displayName = state.displayName,
                    emailAddress = state.emailAddress,
                    username = state.username,
                    password = state.password,
                    imapHost = state.imapHost,
                    imapPort = imapPort,
                    imapSecurity = state.imapSecurity,
                    smtpHost = state.smtpHost,
                    smtpPort = smtpPort,
                    smtpSecurity = state.smtpSecurity,
                ),
            )
            _uiState.update { it.copy(isSaving = false, savedAccountId = saved.id) }
        }
    }
}
