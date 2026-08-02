package com.coursework.unifiedmail.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SyncOutcome
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UnifiedInboxViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val messages: StateFlow<List<ConversationSummary>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) mailRepository.observeUnifiedInbox() else mailRepository.searchUnifiedInbox(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    init {
        sync()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val accountsToSync = accountRepository.getAllAccountsOnce()

            // Concurrent, not sequential: one slow/unreachable account (a real possibility —
            // dead server, DNS timeout) used to block the entire refresh for up to the full
            // connect timeout per account, with zero feedback in the meantime.
            val failures = coroutineScope {
                accountsToSync
                    .map { account -> async { account to mailRepository.syncAccount(account.id) } }
                    .awaitAll()
                    .mapNotNull { (account, result) ->
                        (result as? SyncOutcome.Failure)?.let { "${account.displayName}: ${it.reason}" }
                    }
            }

            _syncError.value = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
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
