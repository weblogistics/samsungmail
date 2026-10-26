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
import com.coursework.unifiedmail.data.remote.DownloadedAttachment
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
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val attachmentStorage: AttachmentStorage,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    readingOrder: ReadingOrder,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    val folderKey: String = savedStateHandle["folderKey"] ?: MailRepository.INBOX_FOLDER_KEY
    val uid: Long = checkNotNull(savedStateHandle["uid"])

    /** The messages listed above/below this one where it was opened from — see [ReadingOrder]. */
    val previousMessage: MessageKey?
    val nextMessage: MessageKey?

    init {
        val (previous, next) = readingOrder.neighbours(MessageKey(accountId, folderKey, uid))
        previousMessage = previous
        nextMessage = next
    }

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

    // One-shot confirmation for the explicit "Download" (save without opening) action — same
    // fire-once shape as downloadedFileUri, shown as a snackbar and cleared via
    // consumeDownloadSavedMessage().
    private val _downloadSavedMessage = MutableStateFlow<String?>(null)
    val downloadSavedMessage: StateFlow<String?> = _downloadSavedMessage.asStateFlow()

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

    /** Opening a message marks it read (see init); this lets the user flip it back from here. */
    fun toggleRead() {
        val current = _message.value ?: return
        val isRead = !current.isRead
        _message.value = current.copy(isRead = isRead)
        viewModelScope.launch { mailRepository.setMessageRead(accountId, folderKey, uid, isRead) }
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
            try {
                // A previous open of this same attachment may already have it on disk — reuse
                // that instead of re-fetching the same bytes from the server every single tap.
                val cached = attachmentStorage.cached(attachment.id, attachment.fileName, attachment.mimeType)
                if (cached != null) {
                    _downloadedFileUri.value = cached
                } else {
                    val result = mailRepository.downloadAttachment(accountId, folderKey, uid, attachment.indexInMessage)
                    result.onSuccess { downloaded ->
                        val uri = attachmentStorage.save(downloaded, attachment.id)
                        _downloadedFileUri.value = uri to attachmentStorage.viewerMimeType(downloaded)
                    }.onFailure {
                        _downloadError.value = describeMailError(it)
                    }
                }
            } catch (e: IOException) {
                // Local disk I/O (full storage, an OEM storage quirk, getExternalFilesDir()
                // returning null) — distinct from describeMailError's network-failure framing,
                // and must not propagate: an uncaught exception here would crash the app instead
                // of just failing this one download, and leave the chip stuck "downloading"
                // forever since the finally below is what actually clears that state.
                _downloadError.value = "Couldn't save the attachment — check your device's available storage"
            } finally {
                _downloadingIndex.value = null
            }
        }
    }

    fun consumeDownloadedFile() {
        _downloadedFileUri.value = null
    }

    /** The explicit long-press "Download" action — saves to the real Downloads folder, but (unlike [downloadAttachment]) doesn't open it. */
    fun saveAttachmentToDownloads(attachment: AttachmentEntity) {
        if (_downloadingIndex.value != null) return
        viewModelScope.launch {
            _downloadingIndex.value = attachment.indexInMessage
            _downloadError.value = null
            try {
                // Same cache [downloadAttachment] reads/writes — either action can warm it for the other.
                val cachedBytes = attachmentStorage.cachedBytes(attachment.id, attachment.fileName)
                if (cachedBytes != null) {
                    val downloaded = DownloadedAttachment(attachment.fileName, attachment.mimeType, cachedBytes)
                    attachmentStorage.saveToDownloads(downloaded, attachment.id)
                    _downloadSavedMessage.value = "Saved \"${downloaded.fileName}\" to Downloads"
                } else {
                    val result = mailRepository.downloadAttachment(accountId, folderKey, uid, attachment.indexInMessage)
                    result.onSuccess { downloaded ->
                        attachmentStorage.save(downloaded, attachment.id)
                        attachmentStorage.saveToDownloads(downloaded, attachment.id)
                        _downloadSavedMessage.value = "Saved \"${downloaded.fileName}\" to Downloads"
                    }.onFailure {
                        _downloadError.value = describeMailError(it)
                    }
                }
            } catch (e: IOException) {
                // See downloadAttachment's matching catch — local disk I/O failure, not network.
                _downloadError.value = "Couldn't save the attachment — check your device's available storage"
            } finally {
                _downloadingIndex.value = null
            }
        }
    }

    fun consumeDownloadSavedMessage() {
        _downloadSavedMessage.value = null
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
