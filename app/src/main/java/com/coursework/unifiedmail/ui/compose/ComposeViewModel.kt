package com.coursework.unifiedmail.ui.compose

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.ComposeDraft
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ComposeMode { NEW, REPLY, FORWARD }

data class ComposeUiState(
    val mode: ComposeMode = ComposeMode.NEW,
    val to: String = "",
    val subject: String = "",
    val body: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val sent: Boolean = false,
) {
    val canSend: Boolean get() = to.isNotBlank() && !isSending
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val mailRepository: MailRepository,
) : ViewModel() {

    private val accountId: String = checkNotNull(savedStateHandle["accountId"])
    private val mode: ComposeMode = when (savedStateHandle.get<String>("mode")) {
        "reply" -> ComposeMode.REPLY
        "forward" -> ComposeMode.FORWARD
        else -> ComposeMode.NEW
    }
    private val sourceUid: Long = savedStateHandle.get<Long>("sourceUid") ?: -1L

    private val _uiState = MutableStateFlow(ComposeUiState(mode = mode))
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    // Only the immediate parent is tracked, not the full ancestor chain — good enough for
    // In-Reply-To/References on a reply; full conversation threading is a later milestone.
    private var sourceMessage: MessageEntity? = null

    init {
        if (mode != ComposeMode.NEW && sourceUid >= 0) {
            viewModelScope.launch {
                val source = mailRepository.getInboxMessage(accountId, sourceUid) ?: return@launch
                sourceMessage = source
                _uiState.update { it.copy(to = "", subject = "", body = "") }
                prefillFromSource(source)
            }
        }
    }

    private fun prefillFromSource(source: MessageEntity) {
        val quoted = quoteBody(source)
        when (mode) {
            ComposeMode.REPLY -> _uiState.update {
                it.copy(
                    to = source.fromAddress.orEmpty(),
                    subject = withPrefix(source.subject, "Re:"),
                    body = quoted,
                )
            }
            ComposeMode.FORWARD -> _uiState.update {
                it.copy(
                    to = "",
                    subject = withPrefix(source.subject, "Fwd:"),
                    body = quoted,
                )
            }
            ComposeMode.NEW -> Unit
        }
    }

    private fun quoteBody(source: MessageEntity): String {
        val original = source.bodyText ?: source.bodyPreview
        val from = source.fromPersonal?.takeIf { it.isNotBlank() } ?: source.fromAddress ?: "unknown sender"
        val quotedLines = original.lines().joinToString("\n") { "> $it" }
        return "\n\nOn a previous message, $from wrote:\n$quotedLines"
    }

    private fun withPrefix(subject: String?, prefix: String): String {
        val base = subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
        return if (base.startsWith(prefix, ignoreCase = true)) base else "$prefix $base"
    }

    fun onToChange(value: String) = _uiState.update { it.copy(to = value) }
    fun onSubjectChange(value: String) = _uiState.update { it.copy(subject = value) }
    fun onBodyChange(value: String) = _uiState.update { it.copy(body = value) }

    fun send() {
        val state = _uiState.value
        val recipients = state.to.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        if (recipients.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one recipient") }
            return
        }

        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val parentMessageId = sourceMessage?.messageIdHeader
            val draft = ComposeDraft(
                to = recipients,
                subject = state.subject,
                body = state.body,
                inReplyToMessageIdHeader = if (mode == ComposeMode.REPLY) parentMessageId else null,
                referencesHeader = if (mode == ComposeMode.REPLY) parentMessageId else null,
            )
            mailRepository.queueMessageForSending(accountId, draft)
            SyncScheduler.enqueueOutboxSend(appContext)
            _uiState.update { it.copy(isSending = false, sent = true) }
        }
    }
}
