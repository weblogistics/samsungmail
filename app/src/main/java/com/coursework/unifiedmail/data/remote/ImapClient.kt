package com.coursework.unifiedmail.data.remote

import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimePart
import javax.mail.search.BodyTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.HeaderTerm
import javax.mail.search.OrTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SubjectTerm

data class ImapFolderInfo(
    val fullName: String,
    val displayName: String,
    // Hierarchy delimiter for this folder's fullName (e.g. '/' or '.') — lets callers derive a
    // parent path without hardcoding a delimiter, since it varies by server/namespace.
    val separator: Char = '/',
)

data class ImapAttachmentInfo(
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

data class ImapMessageInfo(
    val uid: Long,
    val messageIdHeader: String?,
    val inReplyToHeader: String?,
    val referencesHeader: String?,
    val subject: String?,
    val fromAddress: String?,
    val fromPersonal: String?,
    val toAddresses: String?,
    val ccAddresses: String?,
    val sentDateEpochMillis: Long?,
    val receivedDateEpochMillis: Long?,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val hasAttachments: Boolean,
    val bodyPreview: String,
    val bodyText: String?,
    val bodyHtml: String?,
    // Position in this list == indexInMessage in AttachmentEntity — see MailRepository.syncFolder.
    val attachments: List<ImapAttachmentInfo> = emptyList(),
    val isAnswered: Boolean = false,
)

data class DownloadedAttachment(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray,
)

data class ImapFetchResult(
    val messages: List<ImapMessageInfo>,
    val newLastSyncedUid: Long,
    // Every UID currently in the folder, not just newly-fetched ones — lets the caller prune
    // local rows for messages deleted/moved on the server via another client. Defaults to null
    // (not reconciled) for callers/tests that don't care about deletions.
    val presentUids: List<Long>? = null,
)

/** Behind an interface so MailRepository can be unit-tested against a fake instead of real IMAP. */
interface ImapClient {
    suspend fun fetchFolders(config: ImapConfig): Result<List<ImapFolderInfo>>

    suspend fun fetchFolderMessages(
        config: ImapConfig,
        folderFullName: String,
        sinceUid: Long,
        syncWindowDays: Int,
    ): Result<ImapFetchResult>

    /**
     * Fetches up to [limit] messages with UID strictly less than [beforeUid], most recent first —
     * "load older mail" pagination past whatever [fetchFolderMessages] already cached (that
     * method is delta/window-bound and never revisits older mail on its own). Purely additive:
     * never touches a sync watermark, unlike [fetchFolderMessages].
     */
    suspend fun fetchOlderMessages(
        config: ImapConfig,
        folderFullName: String,
        beforeUid: Long,
        limit: Int,
    ): Result<List<ImapMessageInfo>>

    /**
     * Portable move: COPY to the target folder, flag the original \Deleted, EXPUNGE the source.
     * Works on any IMAP server, unlike relying on the optional MOVE extension. The moved
     * message gets a new UID in the destination folder (assigned by the server) — callers
     * should not assume the source [uid] carries over.
     */
    suspend fun moveMessage(
        config: ImapConfig,
        sourceFolderFullName: String,
        uid: Long,
        targetFolderFullName: String,
    ): Result<Unit>

    /** Pushes the local read/unread state to the server's \Seen flag. */
    suspend fun setSeenFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        seen: Boolean,
    ): Result<Unit>

    /** Pushes the local star/flag state to the server's \Flagged flag. */
    suspend fun setFlaggedFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        flagged: Boolean,
    ): Result<Unit>

    /** Marks a message \Answered on the server — best-effort, after sending a reply from this app. */
    suspend fun setAnsweredFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
    ): Result<Unit>

    /** Permanently deletes every message in a folder (flags \Deleted, then expunges) — "Empty trash". */
    suspend fun emptyFolder(
        config: ImapConfig,
        folderFullName: String,
    ): Result<Unit>

    /**
     * Server-side search — matches on subject, sender, or body (an OR of all three), unlike the
     * app's own local search which only ever sees whatever's already been synced. Explicit,
     * user-initiated fallback for when local results come up empty; capped like an initial sync
     * (most recent first) so a broad query on a big mailbox can't run away.
     */
    suspend fun searchFolder(
        config: ImapConfig,
        folderFullName: String,
        query: String,
    ): Result<List<ImapMessageInfo>>

    /**
     * Finds a message by its exact Message-ID header, independent of date or count windows —
     * unlike [fetchFolderMessages], which bounds its first-sync fetch by [syncWindowDays] and
     * [ImapClientImpl.MAX_INITIAL_SYNC_MESSAGES]. Needed by [MailRepository.undoMove]: the
     * message being searched for was just moved into this folder, but its own *original*
     * received date (preserved by IMAP COPY) can easily fall outside the sync window even though
     * it's the newest arrival in the folder — a plain re-sync would silently never find it.
     */
    suspend fun findMessageByMessageId(
        config: ImapConfig,
        folderFullName: String,
        messageIdHeader: String,
    ): Result<ImapMessageInfo?>

    /**
     * Re-fetches the whole message by UID (a fresh connection, same as every other method here)
     * and walks its multipart tree counting attachment-classified parts until reaching
     * [attachmentIndex] — deliberately not using IMAP's low-level dotted-part-number addressing
     * (`BODY[1.2]`), which JavaMail has no simple high-level API for; re-fetching is simple/robust
     * at the mailbox sizes this app targets, same tradeoff as the deletion-reconciliation UID
     * re-listing. Bytes are never cached at sync time, so this is always a fresh round trip.
     */
    suspend fun fetchAttachment(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        attachmentIndex: Int,
    ): Result<DownloadedAttachment>
}

/**
 * Thin wrapper over JavaMail's IMAP support. Every public function opens its own connection and
 * closes it before returning — fine at the sync frequencies and mailbox sizes this app targets;
 * a persistent/pooled connection (needed for IMAP IDLE push) is out of scope for now.
 */
class ImapClientImpl @Inject constructor() : ImapClient {

    override suspend fun fetchFolders(config: ImapConfig): Result<List<ImapFolderInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                store.defaultFolder.list("*")
                    .filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }
                    .map {
                        ImapFolderInfo(
                            fullName = it.fullName,
                            displayName = it.name,
                            separator = runCatching { it.separator }.getOrDefault('/'),
                        )
                    }
            } finally {
                store.close()
            }
        }
    }

    /**
     * Fetches messages newer than [sinceUid]. On first sync ([sinceUid] <= 0), [syncWindowDays]
     * bounds how far back to look (via IMAP `SEARCH SINCE`) — `0` (matches
     * AppSettings.ALL_MAIL_SYNC_WINDOW_DAYS; not referenced directly to keep this package
     * decoupled from data.settings) means no date bound at all. Either way the result is still
     * capped at [MAX_INITIAL_SYNC_MESSAGES] (most recent first) to avoid downloading
     * years/thousands of messages of mailbox history the first time an account or folder is
     * synced, regardless of how wide the configured window is.
     */
    override suspend fun fetchFolderMessages(
        config: ImapConfig,
        folderFullName: String,
        sinceUid: Long,
        syncWindowDays: Int,
    ): Result<ImapFetchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_ONLY)
                try {
                    val newLastSyncedUid = maxOf(sinceUid, folder.getUIDNext() - 1)

                    val messages: Array<Message> = if (sinceUid <= 0) {
                        val candidates = if (syncWindowDays > 0) {
                            val cutoff = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(syncWindowDays.toLong()))
                            folder.search(ReceivedDateTerm(ComparisonTerm.GE, cutoff))
                        } else {
                            folder.messages
                        }
                        // IMAP SEARCH/folder.messages order isn't guaranteed newest-first, so sort
                        // by sequence number (ascending = oldest first, same as the old
                        // count-slicing did) before taking the most recent MAX_INITIAL_SYNC_MESSAGES.
                        candidates.sortedBy { it.messageNumber }.takeLast(MAX_INITIAL_SYNC_MESSAGES).toTypedArray()
                    } else {
                        folder.getMessagesByUID(sinceUid + 1, UIDFolder.LASTUID)
                    }

                    if (messages.isNotEmpty()) {
                        val fetchProfile = FetchProfile().apply {
                            add(FetchProfile.Item.ENVELOPE)
                            add(FetchProfile.Item.FLAGS)
                            add(UIDFolder.FetchProfileItem.UID)
                            add(FetchProfile.Item.CONTENT_INFO)
                        }
                        folder.fetch(messages, fetchProfile)
                    }

                    val infos = messages.mapNotNull { message ->
                        runCatching { toMessageInfo(folder, message) }.getOrNull()
                    }

                    // Cheap UID-only listing of the whole folder (reuses the already-open
                    // connection) so the caller can prune local rows for messages that were
                    // deleted/moved on the server via another client — fetchFolderMessages only
                    // ever looks at UID > sinceUid otherwise, so a disappearing UID would
                    // otherwise never be noticed.
                    val allMessages = folder.messages
                    if (allMessages.isNotEmpty()) {
                        folder.fetch(allMessages, FetchProfile().apply { add(UIDFolder.FetchProfileItem.UID) })
                    }
                    val presentUids = allMessages.map { folder.getUID(it) }

                    ImapFetchResult(messages = infos, newLastSyncedUid = newLastSyncedUid, presentUids = presentUids)
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    override suspend fun fetchOlderMessages(
        config: ImapConfig,
        folderFullName: String,
        beforeUid: Long,
        limit: Int,
    ): Result<List<ImapMessageInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (beforeUid <= 1L) return@runCatching emptyList()

            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_ONLY)
                try {
                    val messages = folder.getMessagesByUID(1, beforeUid - 1)
                        .sortedBy { it.messageNumber }
                        .takeLast(limit)
                        .toTypedArray()

                    if (messages.isNotEmpty()) {
                        val fetchProfile = FetchProfile().apply {
                            add(FetchProfile.Item.ENVELOPE)
                            add(FetchProfile.Item.FLAGS)
                            add(UIDFolder.FetchProfileItem.UID)
                            add(FetchProfile.Item.CONTENT_INFO)
                        }
                        folder.fetch(messages, fetchProfile)
                    }

                    messages.mapNotNull { message -> runCatching { toMessageInfo(folder, message) }.getOrNull() }
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    override suspend fun moveMessage(
        config: ImapConfig,
        sourceFolderFullName: String,
        uid: Long,
        targetFolderFullName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val sourceFolder = store.getFolder(sourceFolderFullName) as IMAPFolder
                sourceFolder.open(Folder.READ_WRITE)
                try {
                    val message = sourceFolder.getMessageByUID(uid)
                        ?: error("Message no longer exists on the server (uid=$uid)")
                    val targetFolder = store.getFolder(targetFolderFullName)
                    sourceFolder.copyMessages(arrayOf(message), targetFolder)
                    message.setFlag(Flags.Flag.DELETED, true)
                    sourceFolder.expunge()
                    Unit
                } finally {
                    sourceFolder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    override suspend fun setSeenFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        seen: Boolean,
    ): Result<Unit> = setFlag(config, folderFullName, uid, Flags.Flag.SEEN, seen)

    override suspend fun setFlaggedFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        flagged: Boolean,
    ): Result<Unit> = setFlag(config, folderFullName, uid, Flags.Flag.FLAGGED, flagged)

    override suspend fun setAnsweredFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
    ): Result<Unit> = setFlag(config, folderFullName, uid, Flags.Flag.ANSWERED, true)

    override suspend fun emptyFolder(
        config: ImapConfig,
        folderFullName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_WRITE)
                try {
                    val messages = folder.messages
                    if (messages.isNotEmpty()) {
                        folder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                        folder.expunge()
                    }
                    Unit
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    private suspend fun setFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        flag: Flags.Flag,
        value: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_WRITE)
                try {
                    val message = folder.getMessageByUID(uid)
                        ?: error("Message no longer exists on the server (uid=$uid)")
                    message.setFlag(flag, value)
                    Unit
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    override suspend fun searchFolder(
        config: ImapConfig,
        folderFullName: String,
        query: String,
    ): Result<List<ImapMessageInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_ONLY)
                try {
                    val term = OrTerm(arrayOf(SubjectTerm(query), FromStringTerm(query), BodyTerm(query)))
                    val matches = folder.search(term)
                        .sortedByDescending { it.messageNumber }
                        .take(SERVER_SEARCH_LIMIT)
                        .toTypedArray()

                    if (matches.isNotEmpty()) {
                        val fetchProfile = FetchProfile().apply {
                            add(FetchProfile.Item.ENVELOPE)
                            add(FetchProfile.Item.FLAGS)
                            add(UIDFolder.FetchProfileItem.UID)
                            add(FetchProfile.Item.CONTENT_INFO)
                        }
                        folder.fetch(matches, fetchProfile)
                    }

                    matches.mapNotNull { message ->
                        runCatching { toMessageInfo(folder, message) }.getOrNull()
                    }
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    override suspend fun findMessageByMessageId(
        config: ImapConfig,
        folderFullName: String,
        messageIdHeader: String,
    ): Result<ImapMessageInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_ONLY)
                try {
                    val message = folder.search(HeaderTerm("Message-ID", messageIdHeader)).firstOrNull()
                        ?: return@runCatching null
                    val fetchProfile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(UIDFolder.FetchProfileItem.UID)
                        add(FetchProfile.Item.CONTENT_INFO)
                    }
                    folder.fetch(arrayOf(message), fetchProfile)
                    toMessageInfo(folder, message)
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    override suspend fun fetchAttachment(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        attachmentIndex: Int,
    ): Result<DownloadedAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            try {
                val folder = store.getFolder(folderFullName) as IMAPFolder
                folder.open(Folder.READ_ONLY)
                try {
                    val message = folder.getMessageByUID(uid)
                        ?: error("Message no longer exists on the server (uid=$uid)")
                    findAttachmentPart(message, attachmentIndex, IntArray(1))
                        ?.let { part ->
                            DownloadedAttachment(
                                fileName = part.fileName ?: "attachment",
                                mimeType = runCatching { part.contentType }.getOrNull(),
                                bytes = part.inputStream.use { it.readBytes() },
                            )
                        }
                        ?: error("Attachment no longer exists on the server (uid=$uid, index=$attachmentIndex)")
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    /** Depth-first walk counting [isAttachmentPart] hits in [counter][0] until reaching [targetIndex]. */
    private fun findAttachmentPart(part: Part, targetIndex: Int, counter: IntArray): Part? {
        if (!part.isMimeType("multipart/*")) return null
        val multipart = part.content as Multipart
        for (i in 0 until multipart.count) {
            val bodyPart = multipart.getBodyPart(i)
            if (isAttachmentPart(bodyPart)) {
                if (counter[0] == targetIndex) return bodyPart
                counter[0]++
            } else {
                findAttachmentPart(bodyPart, targetIndex, counter)?.let { return it }
            }
        }
        return null
    }

    private fun toMessageInfo(folder: IMAPFolder, message: Message): ImapMessageInfo {
        val from = message.from?.firstOrNull() as? InternetAddress
        val toAddresses = message.getRecipients(Message.RecipientType.TO)
            ?.filterIsInstance<InternetAddress>()
            ?.joinToString("; ") { it.toUnicodeString() }
        val ccAddresses = message.getRecipients(Message.RecipientType.CC)
            ?.filterIsInstance<InternetAddress>()
            ?.joinToString("; ") { it.toUnicodeString() }

        val body = parseBody(message)
        val preview = (body.plainText ?: "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(BODY_PREVIEW_CHARS)

        return ImapMessageInfo(
            uid = folder.getUID(message),
            messageIdHeader = message.getHeader("Message-ID")?.firstOrNull(),
            inReplyToHeader = message.getHeader("In-Reply-To")?.firstOrNull(),
            referencesHeader = message.getHeader("References")?.firstOrNull(),
            subject = message.subject,
            fromAddress = from?.address,
            fromPersonal = from?.personal,
            toAddresses = toAddresses,
            ccAddresses = ccAddresses,
            sentDateEpochMillis = message.sentDate?.time,
            receivedDateEpochMillis = message.receivedDate?.time,
            isRead = message.flags.contains(Flags.Flag.SEEN),
            isFlagged = message.flags.contains(Flags.Flag.FLAGGED),
            hasAttachments = body.attachments.isNotEmpty(),
            bodyPreview = preview,
            bodyText = body.plainText,
            bodyHtml = body.html,
            attachments = body.attachments,
            isAnswered = message.flags.contains(Flags.Flag.ANSWERED),
        )
    }

    private data class ParsedBody(val plainText: String?, val html: String?, val attachments: List<ImapAttachmentInfo>)

    /**
     * Same classification JavaMail parts get everywhere in this class — extracted so sync-time
     * attachment metadata ([parseBody]) and download-time part-location ([fetchAttachment]) can
     * never drift apart from each other.
     */
    private fun isAttachmentPart(part: Part): Boolean =
        part.disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true ||
            (!part.fileName.isNullOrBlank() && !part.isMimeType("text/*"))

    /**
     * Walks a (possibly multipart) message once, capturing plain text and HTML independently —
     * a multipart/alternative message commonly has both, and the list preview / plain-text
     * fallback should always prefer the genuine text/plain part rather than an HTML-stripped
     * derivation of it. Only falls back to stripping HTML when there's no text/plain part at all.
     * Attachment bytes are never fetched here (kept sync light) — only metadata, in walk order
     * (that order is [ImapAttachmentInfo]'s index, used to relocate the same part on download).
     *
     * Inline images (a Content-ID header, referenced from the HTML as `cid:...` — common for
     * signature logos and forwarded images) are the one exception: their bytes are read
     * immediately and inlined as `data:` URIs into the returned HTML, since there's no separate
     * "download this part" step in this UI for something the HTML expects to just render.
     * Bounded by [MAX_INLINE_IMAGES_TOTAL_BYTES] combined — a message with unusually large
     * inline images beyond that budget just keeps the unresolved `cid:` reference (same as
     * before this existed: a broken image icon, not a sync failure).
     */
    private fun parseBody(part: Part): ParsedBody {
        var plainText: String? = null
        var html: String? = null
        val attachments = mutableListOf<ImapAttachmentInfo>()
        val inlineImages = mutableMapOf<String, Pair<String, ByteArray>>()
        var inlineImagesBytesUsed = 0L

        fun contentIdOf(p: Part): String? =
            (p as? MimePart)?.contentID?.trim('<', '>')?.takeIf { it.isNotBlank() }

        fun walk(p: Part) {
            runCatching {
                when {
                    p.isMimeType("multipart/*") -> {
                        val multipart = p.content as Multipart
                        for (i in 0 until multipart.count) {
                            val bodyPart = multipart.getBodyPart(i)
                            val contentId = contentIdOf(bodyPart)
                            when {
                                contentId != null && bodyPart.isMimeType("image/*") -> {
                                    runCatching {
                                        val bytes = bodyPart.inputStream.use { it.readBytes() }
                                        if (inlineImagesBytesUsed + bytes.size <= MAX_INLINE_IMAGES_TOTAL_BYTES) {
                                            inlineImages[contentId] = bodyPart.contentType to bytes
                                            inlineImagesBytesUsed += bytes.size
                                        }
                                    }
                                }
                                isAttachmentPart(bodyPart) -> {
                                    attachments.add(
                                        ImapAttachmentInfo(
                                            fileName = bodyPart.fileName ?: "attachment",
                                            mimeType = runCatching { bodyPart.contentType }.getOrNull(),
                                            sizeBytes = runCatching { bodyPart.size.toLong() }.getOrNull()?.takeIf { it >= 0 },
                                        ),
                                    )
                                }
                                else -> walk(bodyPart)
                            }
                        }
                    }
                    plainText == null && p.isMimeType("text/plain") -> {
                        plainText = (p.content as? String)?.take(BODY_TEXT_MAX_CHARS)
                    }
                    html == null && p.isMimeType("text/html") -> {
                        html = (p.content as? String)?.take(BODY_HTML_MAX_CHARS)
                    }
                }
            }
            // Best-effort: malformed/unfetchable parts are skipped rather than failing the sync.
        }

        walk(part)
        val effectivePlainText = plainText ?: stripHtml(html)?.take(BODY_TEXT_MAX_CHARS)
        val effectiveHtml = html?.let { resolveInlineImages(it, inlineImages) }
        return ParsedBody(effectivePlainText, effectiveHtml, attachments)
    }

    /** Replaces `cid:<id>` references in [html] with `data:` URIs for whichever ids were captured during the walk. */
    private fun resolveInlineImages(html: String, inlineImages: Map<String, Pair<String, ByteArray>>): String {
        if (inlineImages.isEmpty()) return html
        var result = html
        for ((contentId, typeAndBytes) in inlineImages) {
            val (contentType, bytes) = typeAndBytes
            val mimeType = contentType.substringBefore(';').trim().ifBlank { "image/png" }
            val dataUri = "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
            result = result.replace("cid:$contentId", dataUri, ignoreCase = true)
        }
        return result
    }

    private fun stripHtml(html: String?): String? = html
        ?.replace(Regex("<[^>]*>"), " ")
        ?.replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()

    private companion object {
        const val BODY_TEXT_MAX_CHARS = 20_000
        const val BODY_HTML_MAX_CHARS = 200_000
        const val BODY_PREVIEW_CHARS = 140
        // Combined raw-byte budget for inline (cid:) images embedded per message — bounds how
        // much a single sync can inflate one row's stored HTML (~1.4x after base64 encoding).
        const val MAX_INLINE_IMAGES_TOTAL_BYTES = 3 * 1024 * 1024L
        // Safety valve independent of the user's configured sync window (which can be "All
        // mail") — the previous hardcoded initial-sync limit, kept as an upper bound rather than
        // removed outright.
        const val MAX_INITIAL_SYNC_MESSAGES = 50
        // Same reasoning as MAX_INITIAL_SYNC_MESSAGES — a server search is explicit and
        // user-initiated, but still shouldn't be able to pull an unbounded number of full
        // message bodies from a broad query on a large mailbox.
        const val SERVER_SEARCH_LIMIT = 50
    }
}
