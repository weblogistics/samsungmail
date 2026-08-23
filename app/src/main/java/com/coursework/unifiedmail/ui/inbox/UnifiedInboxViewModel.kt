package com.coursework.unifiedmail.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.MoveOutcome
import com.coursework.unifiedmail.data.repository.SyncOutcome
import com.coursework.unifiedmail.data.repository.UndoMoveOutcome
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _hasAttachmentFilter = MutableStateFlow(false)
    val hasAttachmentFilter: StateFlow<Boolean> = _hasAttachmentFilter.asStateFlow()

    private val _unreadOnlyFilter = MutableStateFlow(false)
    val unreadOnlyFilter: StateFlow<Boolean> = _unreadOnlyFilter.asStateFlow()

    private val _flaggedOnlyFilter = MutableStateFlow(false)
    val flaggedOnlyFilter: StateFlow<Boolean> = _flaggedOnlyFilter.asStateFlow()

    private data class Filters(val hasAttachmentOnly: Boolean, val unreadOnly: Boolean, val flaggedOnly: Boolean) {
        val isAnyActive: Boolean get() = hasAttachmentOnly || unreadOnly || flaggedOnly
    }

    private val filters = combine(_hasAttachmentFilter, _unreadOnlyFilter, _flaggedOnlyFilter, ::Filters)

    val messages: StateFlow<List<ConversationSummary>> = _searchQuery
        // Debounced only for the query that actually hits the database — searchQuery itself
        // (bound directly to the text field) stays undebounced so typing never feels laggy.
        .debounce(SEARCH_DEBOUNCE_MS)
        .combine(filters) { query, filterState -> query to filterState }
        .flatMapLatest { (query, filterState) ->
            if (query.isBlank() && !filterState.isAnyActive) {
                mailRepository.observeUnifiedInbox()
            } else {
                mailRepository.searchUnifiedInbox(
                    query.trim(),
                    hasAttachmentOnly = filterState.hasAttachmentOnly,
                    unreadOnly = filterState.unreadOnly,
                    flaggedOnly = filterState.flaggedOnly,
                )
            }
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

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isServerSearching = MutableStateFlow(false)
    val isServerSearching: StateFlow<Boolean> = _isServerSearching.asStateFlow()

    private val _undoableAction = MutableStateFlow<UndoableMoveAction?>(null)
    val undoableAction: StateFlow<UndoableMoveAction?> = _undoableAction.asStateFlow()

    // One-shot success confirmations (e.g. "Archived 3 messages") for actions that otherwise
    // complete silently — separate from syncError so a Snackbar can style/queue them differently.
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    init {
        sync()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onHasAttachmentFilterChange(value: Boolean) {
        _hasAttachmentFilter.value = value
    }

    fun onUnreadOnlyFilterChange(value: Boolean) {
        _unreadOnlyFilter.value = value
    }

    fun onFlaggedOnlyFilterChange(value: Boolean) {
        _flaggedOnlyFilter.value = value
    }

    fun clearFilters() {
        _hasAttachmentFilter.value = false
        _unreadOnlyFilter.value = false
        _flaggedOnlyFilter.value = false
    }

    /**
     * Explicit fallback offered once local results for the current query come up empty — see
     * MailRepository.searchServerUnified. Matches get upserted into the local cache, so the
     * existing reactive [messages] query (driven by [searchQuery]) picks them up on its own;
     * nothing here needs to hold a separate "server results" list.
     */
    fun searchServer() {
        val query = _searchQuery.value.trim()
        if (query.isBlank() || _isServerSearching.value) return
        viewModelScope.launch {
            _isServerSearching.value = true
            _syncError.value = null
            when (val result = mailRepository.searchServerUnified(query)) {
                is SyncOutcome.Failure -> _syncError.value = result.reason
                is SyncOutcome.Success -> Unit
            }
            _isServerSearching.value = false
        }
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

    fun dismissSyncError() {
        _syncError.value = null
    }

    fun dismissInfoMessage() {
        _infoMessage.value = null
    }

    fun setMessageRead(message: MessageEntity, isRead: Boolean) {
        viewModelScope.launch {
            mailRepository.setMessageRead(message.accountId, message.folderName, message.uid, isRead)
        }
    }

    fun setMessageFlagged(message: MessageEntity, isFlagged: Boolean) {
        viewModelScope.launch {
            mailRepository.setMessageFlagged(message.accountId, message.folderName, message.uid, isFlagged)
        }
    }

    /** Real server-side move to Trash — not just a local cache removal. Offers Undo (see [undoLastMove]) on success. */
    fun moveToTrash(message: MessageEntity) {
        viewModelScope.launch {
            when (val result = mailRepository.moveMessageToTrash(message.accountId, message.folderName, message.uid)) {
                is MoveOutcome.Success ->
                    _undoableAction.value = UndoableMoveAction(message, message.folderName, result.destinationFolderKey, "Moved to trash")
                is MoveOutcome.Failure -> _syncError.value = result.reason
            }
        }
    }

    /** Real server-side move to Archive — not just a local cache removal. Offers Undo (see [undoLastMove]) on success. */
    fun archiveMessage(message: MessageEntity) {
        viewModelScope.launch {
            when (val result = mailRepository.moveMessageToArchive(message.accountId, message.folderName, message.uid)) {
                is MoveOutcome.Success ->
                    _undoableAction.value = UndoableMoveAction(message, message.folderName, result.destinationFolderKey, "Archived")
                is MoveOutcome.Failure -> _syncError.value = result.reason
            }
        }
    }

    /**
     * Reverses the most recent archive/trash swipe — see MailRepository.undoMove for how, given
     * moves don't track the destination UID. A successful undo lands the message back on the
     * server, but the local cache for that folder doesn't know until its own next sync — since
     * that's the account/folder shown right here, it's re-synced explicitly rather than leaving
     * the user staring at a list that still doesn't show the message they just brought back.
     */
    fun undoLastMove() {
        val action = _undoableAction.value ?: return
        _undoableAction.value = null
        viewModelScope.launch {
            val result = mailRepository.undoMove(
                action.message.accountId,
                action.originalFolderKey,
                action.destinationFolderKey,
                action.message.messageIdHeader,
            )
            when (result) {
                is UndoMoveOutcome.Failure -> _syncError.value = result.reason
                is UndoMoveOutcome.Success -> mailRepository.syncFolder(action.message.accountId, action.originalFolderKey)
            }
        }
    }

    fun dismissUndo() {
        _undoableAction.value = null
    }

    fun toggleSelection(messageId: String) {
        _selectedIds.update { current ->
            if (messageId in current) current - messageId else current + messageId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Marks every currently selected message read/unread, then exits selection mode. */
    fun bulkSetRead(isRead: Boolean) {
        val selected = _selectedIds.value
        val targets = messages.value.map { it.latestMessage }.filter { it.id in selected }
        viewModelScope.launch {
            targets.forEach { mailRepository.setMessageRead(it.accountId, it.folderName, it.uid, isRead) }
            _infoMessage.value = if (isRead) pluralMessage(targets.size, "Marked read") else pluralMessage(targets.size, "Marked unread")
            _selectedIds.value = emptySet()
        }
    }

    /** Moves every currently selected message to Trash, then exits selection mode. */
    fun bulkMoveToTrash() {
        val selected = _selectedIds.value
        val targets = messages.value.map { it.latestMessage }.filter { it.id in selected }
        viewModelScope.launch {
            val failures = targets.mapNotNull { message ->
                val result = mailRepository.moveMessageToTrash(message.accountId, message.folderName, message.uid)
                (result as? MoveOutcome.Failure)?.reason
            }
            reportBulkOutcome(failures, pluralMessage(targets.size, "Moved to trash"))
            _selectedIds.value = emptySet()
        }
    }

    /**
     * Moves every currently selected message to its own account's Archive, then exits selection
     * mode. No bulk "Move to a specific folder" here (unlike the per-account Inbox screen) — a
     * multi-account selection has no single folder picker that makes sense across accounts.
     */
    fun bulkArchive() {
        val selected = _selectedIds.value
        val targets = messages.value.map { it.latestMessage }.filter { it.id in selected }
        viewModelScope.launch {
            val failures = targets.mapNotNull { message ->
                val result = mailRepository.moveMessageToArchive(message.accountId, message.folderName, message.uid)
                (result as? MoveOutcome.Failure)?.reason
            }
            reportBulkOutcome(failures, pluralMessage(targets.size, "Archived"))
            _selectedIds.value = emptySet()
        }
    }

    /** Failures already have their own detail; only surface a success confirmation when everything in the batch actually succeeded. */
    private fun reportBulkOutcome(failures: List<String>, successMessage: String) {
        if (failures.isEmpty()) {
            _infoMessage.value = successMessage
        } else {
            _syncError.value = failures.joinToString("\n")
        }
    }

    private fun pluralMessage(count: Int, verb: String): String = "$verb $count ${if (count == 1) "message" else "messages"}"

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
