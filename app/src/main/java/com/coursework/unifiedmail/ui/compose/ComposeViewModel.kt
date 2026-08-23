package com.coursework.unifiedmail.ui.compose

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.files.AttachmentStorage
import com.coursework.unifiedmail.data.files.PickedAttachment
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.ComposeDraft
import com.coursework.unifiedmail.data.repository.DraftContent
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.domain.richtext.PendingStyle
import com.coursework.unifiedmail.domain.richtext.RichText
import com.coursework.unifiedmail.domain.richtext.TextRun
import com.coursework.unifiedmail.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ComposeMode { NEW, REPLY, REPLY_ALL, FORWARD }

data class ComposeUiState(
    val mode: ComposeMode = ComposeMode.NEW,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    // Bcc eats screen space most compose sessions never need — hidden until the user asks for
    // it, or restored automatically for a resumed draft that already has a Bcc set.
    val showBcc: Boolean = false,
    val subject: String = "",
    val bodyRuns: List<TextRun> = emptyList(),
    val bodySelection: TextRange = TextRange.Zero,
    val pendingStyle: PendingStyle = PendingStyle(),
    val showLinkDialog: Boolean = false,
    // Set only when forwarding a message that has an HTML body — shown read-only below the
    // editor (see ComposeScreen) and reattached verbatim to the outgoing HTML in send(), since
    // the rich-text editor has no way to represent arbitrary original formatting/images itself.
    val quotedHtml: String? = null,
    // Set only when replying/replying-all — the quoted original, kept out of bodyRuns so it
    // shows read-only below a divider (see ComposeScreen) instead of as text mixed in with the
    // new message, and reattached verbatim in send().
    val quotedText: String? = null,
    val attachments: List<PickedAttachment> = emptyList(),
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
    private val accountRepository: AccountRepository,
    private val attachmentStorage: AttachmentStorage,
) : ViewModel() {

    // Loading an existing draft (from the Drafts list) reuses its own id rather than minting a
    // fresh one, so re-saving during this session keeps overwriting the same row instead of
    // forking a duplicate. Otherwise generated up front (not at send time) so attachment files
    // can be copied into private storage — see AttachmentStorage.copyForOutbox — as soon as
    // they're picked, keyed to the outbox row this draft will eventually become. See
    // MailRepository.queueMessageForSending's `id` parameter.
    private val existingDraftId: String? = savedStateHandle.get<String>("draftId")?.takeIf { it.isNotBlank() }
    private val draftId: String = existingDraftId ?: UUID.randomUUID().toString()

    private val accountId: String = checkNotNull(savedStateHandle["accountId"])
    private val mode: ComposeMode = when (savedStateHandle.get<String>("mode")) {
        "reply" -> ComposeMode.REPLY
        "reply_all" -> ComposeMode.REPLY_ALL
        "forward" -> ComposeMode.FORWARD
        else -> ComposeMode.NEW
    }
    private val sourceUid: Long = savedStateHandle.get<Long>("sourceUid") ?: -1L
    private val sourceFolderKey: String = savedStateHandle["sourceFolderKey"] ?: MailRepository.INBOX_FOLDER_KEY

    private val _uiState = MutableStateFlow(ComposeUiState(mode = mode))
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    // Only the immediate parent is tracked, not the full ancestor chain — good enough for
    // In-Reply-To/References on a reply; full conversation threading is a later milestone. Set
    // either from a freshly-opened reply/forward's source message, or (mutually exclusively)
    // from a resumed draft's own already-saved headers.
    private var sourceMessage: MessageEntity? = null
    private var restoredInReplyTo: String? = null
    private var restoredReferences: String? = null

    init {
        viewModelScope.launch {
            if (existingDraftId != null) {
                loadExistingDraft(existingDraftId)
            } else {
                val account = accountRepository.getAccount(accountId)
                val signatureBlock = signatureBlock(account?.signature)
                if (mode == ComposeMode.NEW) {
                    if (signatureBlock.isNotEmpty()) _uiState.update { it.copy(bodyRuns = RichText.fromPlainText(signatureBlock)) }
                } else if (sourceUid >= 0) {
                    val source = mailRepository.getMessage(accountId, sourceFolderKey, sourceUid) ?: return@launch
                    sourceMessage = source
                    _uiState.update { it.copy(to = "", cc = "", subject = "", bodyRuns = emptyList()) }
                    prefillFromSource(source, account?.emailAddress, signatureBlock)
                }
            }
        }
        // A separate, concurrently-running collector rather than a step at the end of the block
        // above — autosaving stays live for the rest of the session regardless of which prefill
        // branch ran. `drop(1)` skips the state already in place once this starts observing
        // (whatever the prefill above already loaded), so only actual further edits get saved.
        viewModelScope.launch {
            _uiState.drop(1).debounce(AUTOSAVE_DEBOUNCE_MS).collect { state -> autosave(state) }
        }
    }

    private suspend fun loadExistingDraft(id: String) {
        val draft = mailRepository.getDraft(id) ?: return
        restoredInReplyTo = draft.inReplyToMessageIdHeader
        restoredReferences = draft.referencesHeader
        _uiState.update {
            it.copy(
                to = draft.to,
                cc = draft.cc,
                bcc = draft.bcc,
                showBcc = draft.bcc.isNotBlank(),
                subject = draft.subject,
                bodyRuns = RichText.fromHtml(draft.bodyHtml),
                quotedHtml = draft.quotedHtml,
                quotedText = draft.quotedText,
            )
        }
    }

    /** No-ops for a blank/already-sent draft — an abandoned brand-new compose shouldn't leave a phantom empty row. */
    private suspend fun autosave(state: ComposeUiState) {
        if (state.sent) return
        val isBlank = state.to.isBlank() && state.cc.isBlank() && state.bcc.isBlank() &&
            state.subject.isBlank() && state.bodyRuns.isEmpty()
        if (isBlank) return
        mailRepository.saveDraft(
            draftId,
            DraftContent(
                accountId = accountId,
                to = state.to,
                cc = state.cc,
                bcc = state.bcc,
                subject = state.subject,
                bodyHtml = RichText.toHtml(state.bodyRuns),
                quotedHtml = state.quotedHtml,
                quotedText = state.quotedText,
                inReplyToMessageIdHeader = sourceMessage?.messageIdHeader ?: restoredInReplyTo,
                referencesHeader = sourceMessage?.messageIdHeader ?: restoredReferences,
            ),
        )
    }

    /**
     * "\n\n-- \n{signature}" placed above the quote, below where the user actually types —
     * mirrors common client UX. Empty when the account has no signature configured. Signature
     * and quoted content deliberately stay plain (unstyled) text — this doesn't attempt to
     * preserve the original message's rich formatting, just its words.
     */
    private fun signatureBlock(signature: String?): String =
        signature?.takeIf { it.isNotBlank() }?.let { "\n\n-- \n$it\n" } ?: ""

    private fun prefillFromSource(source: MessageEntity, ownAddress: String?, signatureBlock: String) {
        when (mode) {
            ComposeMode.REPLY -> {
                _uiState.update {
                    it.copy(
                        to = source.fromAddress.orEmpty(),
                        subject = withPrefix(source.subject, "Re:"),
                        bodyRuns = RichText.fromPlainText(signatureBlock),
                        quotedText = quoteBody(source),
                    )
                }
            }
            ComposeMode.REPLY_ALL -> {
                val ccRecipients = (splitAddresses(source.toAddresses) + splitAddresses(source.ccAddresses))
                    .filter { !it.equals(ownAddress, ignoreCase = true) && !it.equals(source.fromAddress, ignoreCase = true) }
                    .distinct()
                _uiState.update {
                    it.copy(
                        to = source.fromAddress.orEmpty(),
                        cc = ccRecipients.joinToString(", "),
                        subject = withPrefix(source.subject, "Re:"),
                        bodyRuns = RichText.fromPlainText(signatureBlock),
                        quotedText = quoteBody(source),
                    )
                }
            }
            ComposeMode.FORWARD -> {
                val html = source.bodyHtml
                if (html != null) {
                    // The rich-text editor can't represent arbitrary HTML formatting from the
                    // original message, so it isn't flattened into bodyRuns (that would lose
                    // images/layout, which was the actual bug) — it's carried as-is in quotedHtml
                    // and shown read-only below the editor (see ComposeScreen), then reattached
                    // verbatim in send()'s bodyHtml. bodyRuns stays just the signature: a blank
                    // slate for whatever note the user types above the forwarded content.
                    _uiState.update {
                        it.copy(
                            to = "",
                            subject = withPrefix(source.subject, "Fwd:"),
                            bodyRuns = RichText.fromPlainText(signatureBlock),
                            quotedHtml = html,
                        )
                    }
                } else {
                    val quoted = RichText.fromPlainText(signatureBlock + quoteBody(source))
                    _uiState.update { it.copy(to = "", subject = withPrefix(source.subject, "Fwd:"), bodyRuns = quoted) }
                }
            }
            ComposeMode.NEW -> Unit
        }
    }

    private fun splitAddresses(addresses: String?): List<String> =
        addresses?.split(";", ",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    private fun quoteBody(source: MessageEntity): String {
        val original = source.bodyText ?: source.bodyPreview
        val from = source.fromPersonal?.takeIf { it.isNotBlank() } ?: source.fromAddress ?: "unknown sender"
        val quotedLines = original.lines().joinToString("\n") { "> $it" }
        return "On a previous message, $from wrote:\n$quotedLines"
    }

    private fun withPrefix(subject: String?, prefix: String): String {
        val base = subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
        return if (base.startsWith(prefix, ignoreCase = true)) base else "$prefix $base"
    }

    fun onToChange(value: String) = _uiState.update { it.copy(to = value) }
    fun onCcChange(value: String) = _uiState.update { it.copy(cc = value) }
    fun onBccChange(value: String) = _uiState.update { it.copy(bcc = value) }
    fun onSubjectChange(value: String) = _uiState.update { it.copy(subject = value) }
    fun showBccField() = _uiState.update { it.copy(showBcc = true) }

    /** The bridge from the rich-text field's plain [TextFieldValue] edits back into [ComposeUiState.bodyRuns]. */
    fun onBodyValueChange(new: TextFieldValue) {
        _uiState.update { current ->
            val oldText = RichText.plainTextOf(current.bodyRuns)
            if (new.text == oldText) {
                current.copy(bodySelection = new.selection)
            } else {
                val newRuns = RichText.reconcileEdit(current.bodyRuns, oldText, new.text, current.pendingStyle)
                current.copy(bodyRuns = newRuns, bodySelection = new.selection)
            }
        }
    }

    fun toggleBold() = toggleStyleAt(RichText::toggleBold) { it.copy(bold = !it.bold) }
    fun toggleItalic() = toggleStyleAt(RichText::toggleItalic) { it.copy(italic = !it.italic) }
    fun toggleUnderline() = toggleStyleAt(RichText::toggleUnderline) { it.copy(underline = !it.underline) }
    fun toggleStrikethrough() = toggleStyleAt(RichText::toggleStrikethrough) { it.copy(strikethrough = !it.strikethrough) }

    fun toggleBullet() {
        _uiState.update { current ->
            current.copy(bodyRuns = RichText.toggleBullet(current.bodyRuns, current.bodySelection.min))
        }
    }

    fun toggleNumbering() {
        _uiState.update { current ->
            current.copy(bodyRuns = RichText.toggleNumbering(current.bodyRuns, current.bodySelection.min))
        }
    }

    /** Only meaningful with a non-collapsed selection to link — a collapsed cursor has no text to turn into a link. */
    fun requestInsertLink() {
        if (!_uiState.value.bodySelection.collapsed) {
            _uiState.update { it.copy(showLinkDialog = true) }
        }
    }

    fun dismissLinkDialog() = _uiState.update { it.copy(showLinkDialog = false) }

    fun insertLink(url: String) {
        _uiState.update { current ->
            val selection = current.bodySelection
            if (selection.collapsed || url.isBlank()) return@update current.copy(showLinkDialog = false)
            current.copy(
                bodyRuns = RichText.setLink(current.bodyRuns, selection.min, selection.max, url),
                showLinkDialog = false,
            )
        }
    }

    /**
     * A non-collapsed selection restyles that range directly; a collapsed one (just a blinking
     * cursor, nothing to restyle) instead flips [ComposeUiState.pendingStyle], which
     * [onBodyValueChange] applies to whatever gets typed next.
     */
    private fun toggleStyleAt(applyToSelection: (List<TextRun>, Int, Int) -> List<TextRun>, flipPending: (PendingStyle) -> PendingStyle) {
        _uiState.update { current ->
            val selection = current.bodySelection
            if (selection.collapsed) {
                current.copy(pendingStyle = flipPending(current.pendingStyle))
            } else {
                current.copy(bodyRuns = applyToSelection(current.bodyRuns, selection.min, selection.max))
            }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            val picked = uris.mapNotNull { uri -> attachmentStorage.copyForOutbox(uri, draftId) }
            _uiState.update { it.copy(attachments = it.attachments + picked) }
        }
    }

    fun removeAttachment(attachment: PickedAttachment) {
        _uiState.update { it.copy(attachments = it.attachments - attachment) }
        File(attachment.localFilePath).delete()
    }

    fun send() {
        val state = _uiState.value
        val recipients = state.to.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        if (recipients.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one recipient") }
            return
        }

        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val isReply = mode == ComposeMode.REPLY || mode == ComposeMode.REPLY_ALL || restoredInReplyTo != null
            val inReplyTo = sourceMessage?.messageIdHeader ?: restoredInReplyTo
            val references = sourceMessage?.messageIdHeader ?: restoredReferences
            // A forwarded HTML original (see quotedHtml) is reattached here rather than in
            // bodyRuns — it never went through the rich-text model, so RichText.toHtml never
            // touches it. The plain-text fallback part gets an equivalent flattened quote so
            // plain-text-only recipients still see the original content.
            val quotedHtml = state.quotedHtml
            // A reply's quoted original (see quotedText) never went through the rich-text model
            // either — it's reattached here behind an actual <hr>, matching how it's shown in
            // ComposeScreen: the new message on top, a rule, then the read-only original below.
            val quotedText = state.quotedText
            val bodyHtml = RichText.toHtml(state.bodyRuns) +
                (quotedHtml?.let { "<br><br>---------- Forwarded message ----------<br>$it" } ?: "") +
                (quotedText?.let { "<hr>${RichText.toHtml(RichText.fromPlainText(it))}" } ?: "")
            val body = RichText.plainTextOf(state.bodyRuns) +
                (sourceMessage?.takeIf { quotedHtml != null }?.let { "\n\n" + quoteBody(it) } ?: "") +
                (quotedText?.let { "\n\n----------\n$it" } ?: "")
            val draft = ComposeDraft(
                to = recipients,
                cc = state.cc.split(",", ";").map { it.trim() }.filter { it.isNotBlank() },
                bcc = state.bcc.split(",", ";").map { it.trim() }.filter { it.isNotBlank() },
                subject = state.subject,
                body = body,
                bodyHtml = bodyHtml,
                inReplyToMessageIdHeader = if (isReply) inReplyTo else null,
                referencesHeader = if (isReply) references else null,
            )
            mailRepository.queueMessageForSending(accountId, draft, id = draftId)
            if (state.attachments.isNotEmpty()) {
                mailRepository.attachFilesToOutbox(draftId, state.attachments)
            }
            // Only for a reply composed fresh this session — a resumed draft of a reply only
            // carries the parent's Message-ID header (see restoredInReplyTo), not its
            // accountId/folderKey/uid, so there's nothing to mark answered against.
            if (isReply) {
                sourceMessage?.let { source -> mailRepository.markAnswered(source.accountId, source.folderName, source.uid) }
            }
            mailRepository.deleteDraft(draftId)
            SyncScheduler.enqueueOutboxSend(appContext)
            _uiState.update { it.copy(isSending = false, sent = true) }
        }
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 600L
    }
}
