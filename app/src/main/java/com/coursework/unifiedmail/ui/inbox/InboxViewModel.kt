package com.coursework.unifiedmail.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SyncOutcome
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])

    val messages: StateFlow<List<ConversationSummary>> = mailRepository.observeInbox(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    init {
        sync()
    }

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            when (val result = mailRepository.syncAccount(accountId)) {
                is SyncOutcome.Failure -> _syncError.value = result.reason
                is SyncOutcome.Success -> Unit
            }
            _lastSyncedAt.value = System.currentTimeMillis()
            _isSyncing.value = false
        }
    }

    fun setMessageRead(message: MessageEntity, isRead: Boolean) {
        viewModelScope.launch {
            mailRepository.setMessageRead(message.accountId, message.uid, isRead)
        }
    }

    fun removeMessageLocally(message: MessageEntity) {
        viewModelScope.launch {
            mailRepository.removeMessageLocally(message.accountId, message.uid)
        }
    }
}
