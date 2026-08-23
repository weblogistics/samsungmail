package com.coursework.unifiedmail.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.FolderEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    val folderKey: String = savedStateHandle["folderKey"] ?: MailRepository.INBOX_FOLDER_KEY

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

    // Read directly off the repository rather than the `settings` StateFlow below to avoid any
    // dependency on property initialization order between the two.
    private val threadedConversations = settingsRepository.settings.map { it.threadedConversations }.distinctUntilChanged()

    val messages: StateFlow<List<ConversationSummary>> = _searchQuery
        // Debounced only for the query that actually hits the database — searchQuery itself
        // (bound directly to the text field) stays undebounced so typing never feels laggy.
        .debounce(SEARCH_DEBOUNCE_MS)
        .combine(filters) { query, filterState -> query to filterState }
        .combine(threadedConversations) { (query, filterState), threaded -> Triple(query, filterState, threaded) }
        .flatMapLatest { (query, filterState, threaded) ->
            if (query.isBlank() && !filterState.isAnyActive) {
                mailRepository.observeFolder(accountId, folderKey, threaded)
            } else {
                mailRepository.searchFolder(
                    accountId,
                    folderKey,
                    query.trim(),
                    hasAttachmentOnly = filterState.hasAttachmentOnly,
                    unreadOnly = filterState.unreadOnly,
                    flaggedOnly = filterState.flaggedOnly,
                    threaded = threaded,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setListPaneWidthDp(widthDp: Int) {
        viewModelScope.launch { settingsRepository.setListPaneWidthDp(widthDp) }
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _undoableAction = MutableStateFlow<UndoableMoveAction?>(null)
    val undoableAction: StateFlow<UndoableMoveAction?> = _undoableAction.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _isServerSearching = MutableStateFlow(false)
    val isServerSearching: StateFlow<Boolean> = _isServerSearching.asStateFlow()

    private val _isTrashFolder = MutableStateFlow(false)
    val isTrashFolder: StateFlow<Boolean> = _isTrashFolder.asStateFlow()

    private val _isEmptyingTrash = MutableStateFlow(false)
    val isEmptyingTrash: StateFlow<Boolean> = _isEmptyingTrash.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val folders: StateFlow<List<FolderEntity>> = _folders.asStateFlow()

    private val _account = MutableStateFlow<AccountEntity?>(null)
    val account: StateFlow<AccountEntity?> = _account.asStateFlow()

    // One-shot success confirmations (e.g. "Archived 3 messages") for actions that otherwise
    // complete silently — separate from syncError so a Snackbar can style/queue them differently.
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    init {
        sync()
        viewModelScope.launch {
            _isTrashFolder.value = mailRepository.findTrashFolderKey(accountId) == folderKey
            _folders.value = mailRepository.getFolders(accountId)
            _account.value = accountRepository.getAccount(accountId)
        }
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

    /** Explicit fallback for when [messages]'s local search/filter doesn't cover it — see MailRepository.searchServerFolder. */
    fun searchServer() {
        val query = _searchQuery.value.trim()
        if (query.isBlank() || _isServerSearching.value) return
        viewModelScope.launch {
            _isServerSearching.value = true
            _syncError.value = null
            when (val result = mailRepository.searchServerFolder(accountId, folderKey, query)) {
                is SyncOutcome.Failure -> _syncError.value = result.reason
                is SyncOutcome.Success -> Unit
            }
            _isServerSearching.value = false
        }
    }

    /** "Load older mail" — pages further back than this folder's normal sync window/cap ever reaches on its own. */
    fun loadOlderMessages() {
        if (_isLoadingOlder.value) return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            when (val result = mailRepository.loadOlderMessages(accountId, folderKey)) {
                is SyncOutcome.Failure -> _syncError.value = result.reason
                is SyncOutcome.Success -> Unit
            }
            _isLoadingOlder.value = false
        }
    }

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            when (val result = mailRepository.syncFolder(accountId, folderKey)) {
                is SyncOutcome.Failure -> _syncError.value = result.reason
                is SyncOutcome.Success -> Unit
            }
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

    fun markAllRead() {
        viewModelScope.launch {
            mailRepository.markAllReadInFolder(accountId, folderKey)
            _infoMessage.value = "Marked all read"
        }
    }

    /** Permanently empties this account's Trash — only ever called from a folder [isTrashFolder] confirms is actually Trash. */
    fun emptyTrash() {
        if (_isEmptyingTrash.value) return
        viewModelScope.launch {
            _isEmptyingTrash.value = true
            when (val result = mailRepository.emptyTrash(accountId)) {
                is SyncOutcome.Failure -> _syncError.value = result.reason
                is SyncOutcome.Success -> _infoMessage.value = "Trash emptied"
            }
            _isEmptyingTrash.value = false
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
     * server in [UndoableMoveAction.originalFolderKey], but the *local* cache for that folder
     * doesn't know that until its own next sync (same reasoning as moveMessage's destination
     * side never being proactively synced) — since that's the folder already on screen here, a
     * re-sync is triggered explicitly rather than leaving the user staring at a list that still
     * doesn't show the message they just asked to bring back.
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
                is UndoMoveOutcome.Success -> if (action.originalFolderKey == folderKey) sync()
            }
        }
    }

    fun dismissUndo() {
        _undoableAction.value = null
    }

    fun moveMessage(message: MessageEntity, targetFolderKey: String) {
        viewModelScope.launch {
            val result = mailRepository.moveMessage(message.accountId, message.folderName, message.uid, targetFolderKey)
            if (result is MoveOutcome.Failure) {
                _syncError.value = result.reason
            }
        }
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

    /** Moves every currently selected message to Archive, then exits selection mode. */
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

    /** Moves every currently selected message to [targetFolderKey], then exits selection mode. */
    fun bulkMoveTo(targetFolderKey: String) {
        val selected = _selectedIds.value
        val targets = messages.value.map { it.latestMessage }.filter { it.id in selected }
        viewModelScope.launch {
            val failures = targets.mapNotNull { message ->
                val result = mailRepository.moveMessage(message.accountId, message.folderName, message.uid, targetFolderKey)
                (result as? MoveOutcome.Failure)?.reason
            }
            _folders.value.firstOrNull { MailRepository.localFolderKey(it.fullName) == targetFolderKey }?.let { folder ->
                settingsRepository.recordMoveFolderUsage(accountId, folder.fullName)
            }
            reportBulkOutcome(failures, pluralMessage(targets.size, "Moved"))
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
