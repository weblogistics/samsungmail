package com.coursework.unifiedmail.ui.message

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.files.AttachmentStorage
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.AttachmentEntity
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.remote.describeMailError
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.MoveOutcome
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
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val attachmentStorage: AttachmentStorage,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    val folderKey: String = savedStateHandle["folderKey"] ?: MailRepository.INBOX_FOLDER_KEY
    val uid: Long = checkNotNull(savedStateHandle["uid"])

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<MessageEntity?>(null)
    val message: StateFlow<MessageEntity?> = _message.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderEntity>>(emptyList())
    val folders: StateFlow<List<FolderEntity>> = _folders.asStateFlow()

    private val _account = MutableStateFlow<AccountEntity?>(null)
    val account: StateFlow<AccountEntity?> = _account.asStateFlow()

    private val _moveError = MutableStateFlow<String?>(null)
    val moveError: StateFlow<String?> = _moveError.asStateFlow()

    private val _movedAway = MutableStateFlow(false)
    val movedAway: StateFlow<Boolean> = _movedAway.asStateFlow()

    private val _attachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    val attachments: StateFlow<List<AttachmentEntity>> = _attachments.asStateFlow()

    // Which attachment (by indexInMessage), if any, is currently downloading.
    private val _downloadingIndex = MutableStateFlow<Int?>(null)
    val downloadingIndex: StateFlow<Int?> = _downloadingIndex.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    // One-shot: the screen observes this to fire an ACTION_VIEW intent, then clears it via
    // consumeDownloadedFile() — same shape as movedAway for a fire-once UI event.
    private val _downloadedFileUri = MutableStateFlow<Pair<Uri, String?>?>(null)
    val downloadedFileUri: StateFlow<Pair<Uri, String?>?> = _downloadedFileUri.asStateFlow()

    // "View headers" dialog state. Headers are fetched fresh from the server on first open (see
    // openHeaders) and kept around for the rest of this screen's lifetime rather than
    // re-fetched every time the dialog is reopened.
    private val _showHeaders = MutableStateFlow(false)
    val showHeaders: StateFlow<Boolean> = _showHeaders.asStateFlow()

    private val _headers = MutableStateFlow<String?>(null)
    val headers: StateFlow<String?> = _headers.asStateFlow()

    private val _headersLoading = MutableStateFlow(false)
    val headersLoading: StateFlow<Boolean> = _headersLoading.asStateFlow()

    private val _headersError = MutableStateFlow<String?>(null)
    val headersError: StateFlow<String?> = _headersError.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = mailRepository.getMessage(accountId, folderKey, uid)
            _message.value = loaded
            if (loaded != null && !loaded.isRead) {
                mailRepository.setMessageRead(accountId, folderKey, uid, true)
                _message.value = loaded.copy(isRead = true)
            }
            _folders.value = mailRepository.getFolders(accountId)
            _account.value = accountRepository.getAccount(accountId)
            _attachments.value = mailRepository.getAttachments(accountId, folderKey, uid)
        }
    }

    fun moveToTrash() {
        viewModelScope.launch {
            when (val result = mailRepository.moveMessageToTrash(accountId, folderKey, uid)) {
                is MoveOutcome.Success -> _movedAway.value = true
                is MoveOutcome.Failure -> _moveError.value = result.reason
            }
        }
    }

    fun moveTo(targetFolderKey: String) {
        viewModelScope.launch {
            when (val result = mailRepository.moveMessage(accountId, folderKey, uid, targetFolderKey)) {
                is MoveOutcome.Success -> {
                    _folders.value.firstOrNull { MailRepository.localFolderKey(it.fullName) == targetFolderKey }?.let { folder ->
                        settingsRepository.recordMoveFolderUsage(accountId, folder.fullName)
                    }
                    _movedAway.value = true
                }
                is MoveOutcome.Failure -> _moveError.value = result.reason
            }
        }
    }

    fun downloadAttachment(attachment: AttachmentEntity) {
        if (_downloadingIndex.value != null) return
        viewModelScope.launch {
            _downloadingIndex.value = attachment.indexInMessage
            _downloadError.value = null
            val result = mailRepository.downloadAttachment(accountId, folderKey, uid, attachment.indexInMessage)
            result.onSuccess { downloaded ->
                val uri = attachmentStorage.save(downloaded)
                _downloadedFileUri.value = uri to downloaded.mimeType
            }.onFailure {
                _downloadError.value = describeMailError(it)
            }
            _downloadingIndex.value = null
        }
    }

    fun consumeDownloadedFile() {
        _downloadedFileUri.value = null
    }

    fun openHeaders() {
        _showHeaders.value = true
        if (_headers.value != null || _headersLoading.value) return
        viewModelScope.launch {
            _headersLoading.value = true
            _headersError.value = null
            mailRepository.fetchMessageHeaders(accountId, folderKey, uid)
                .onSuccess { _headers.value = it }
                .onFailure { _headersError.value = describeMailError(it) }
            _headersLoading.value = false
        }
    }

    fun dismissHeaders() {
        _showHeaders.value = false
    }
}
