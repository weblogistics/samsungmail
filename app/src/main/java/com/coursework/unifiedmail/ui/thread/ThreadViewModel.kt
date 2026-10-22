package com.coursework.unifiedmail.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.MoveOutcome
import com.coursework.unifiedmail.data.repository.UndoMoveOutcome
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.ui.inbox.UndoableMoveAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Per-message swipe actions mirror InboxViewModel's, so a message behaves the same wherever it's listed. */
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    val folderKey: String = checkNotNull(savedStateHandle["folderKey"])
    val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    val messages: StateFlow<List<MessageEntity>> =
        mailRepository.observeConversationMessages(accountId, folderKey, conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _undoableAction = MutableStateFlow<UndoableMoveAction?>(null)
    val undoableAction: StateFlow<UndoableMoveAction?> = _undoableAction.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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

    fun moveToTrash(message: MessageEntity) {
        viewModelScope.launch {
            when (val result = mailRepository.moveMessageToTrash(message.accountId, message.folderName, message.uid)) {
                is MoveOutcome.Success ->
                    _undoableAction.value = UndoableMoveAction(message, message.folderName, result.destinationFolderKey, "Moved to trash")
                is MoveOutcome.Failure -> _error.value = result.reason
            }
        }
    }

    fun archiveMessage(message: MessageEntity) {
        viewModelScope.launch {
            when (val result = mailRepository.moveMessageToArchive(message.accountId, message.folderName, message.uid)) {
                is MoveOutcome.Success ->
                    _undoableAction.value = UndoableMoveAction(message, message.folderName, result.destinationFolderKey, "Archived")
                is MoveOutcome.Failure -> _error.value = result.reason
            }
        }
    }

    /**
     * Same as InboxViewModel.undoLastMove: the restored message only reappears in the local cache
     * after this folder re-syncs, so that's done explicitly to bring it back into the thread.
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
                is UndoMoveOutcome.Failure -> _error.value = result.reason
                is UndoMoveOutcome.Success -> mailRepository.syncFolder(accountId, action.originalFolderKey)
            }
        }
    }

    fun dismissUndo() {
        _undoableAction.value = null
    }

    fun dismissError() {
        _error.value = null
    }
}
