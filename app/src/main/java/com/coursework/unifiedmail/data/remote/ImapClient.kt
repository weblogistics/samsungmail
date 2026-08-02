package com.coursework.unifiedmail.data.remote

import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class ImapFolderInfo(
    val fullName: String,
    val displayName: String,
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
    val sentDateEpochMillis: Long?,
    val receivedDateEpochMillis: Long?,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val hasAttachments: Boolean,
    val bodyPreview: String,
    val bodyText: String?,
)

data class ImapFetchResult(
    val messages: List<ImapMessageInfo>,
    val newLastSyncedUid: Long,
)

/** Behind an interface so MailRepository can be unit-tested against a fake instead of real IMAP. */
interface ImapClient {
    suspend fun fetchFolders(config: ImapConfig): Result<List<ImapFolderInfo>>

    suspend fun fetchInboxMessages(
        config: ImapConfig,
        folderFullName: String,
        sinceUid: Long,
        initialFetchLimit: Int,
    ): Result<ImapFetchResult>
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
                    .map { ImapFolderInfo(fullName = it.fullName, displayName = it.name) }
            } finally {
                store.close()
            }
        }
    }

    /**
     * Fetches messages newer than [sinceUid]. On first sync ([sinceUid] <= 0) only the most
     * recent [initialFetchLimit] messages are pulled, to avoid downloading years of mailbox
     * history the first time an account is added.
     */
    override suspend fun fetchInboxMessages(
        config: ImapConfig,
        folderFullName: String,
        sinceUid: Long,
        initialFetchLimit: Int,
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
                        val total = folder.messageCount
                        if (total == 0) emptyArray()
                        else {
                            val start = maxOf(1, total - initialFetchLimit + 1)
                            folder.getMessages(start, total)
                        }
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

                    ImapFetchResult(messages = infos, newLastSyncedUid = newLastSyncedUid)
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }
    }

    private fun toMessageInfo(folder: IMAPFolder, message: Message): ImapMessageInfo {
        val from = message.from?.firstOrNull() as? InternetAddress
        val toAddresses = message.getRecipients(Message.RecipientType.TO)
            ?.filterIsInstance<InternetAddress>()
            ?.joinToString("; ") { it.toUnicodeString() }

        val body = parseBody(message)
        val preview = (body.text ?: "")
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
            sentDateEpochMillis = message.sentDate?.time,
            receivedDateEpochMillis = message.receivedDate?.time,
            isRead = message.flags.contains(Flags.Flag.SEEN),
            isFlagged = message.flags.contains(Flags.Flag.FLAGGED),
            hasAttachments = body.hasAttachments,
            bodyPreview = preview,
            bodyText = body.text,
        )
    }

    private data class ParsedBody(val text: String?, val hasAttachments: Boolean)

    /** Walks a (possibly multipart) message once, extracting plain text and flagging attachments. */
    private fun parseBody(part: Part): ParsedBody {
        var text: String? = null
        var hasAttachments = false

        fun walk(p: Part) {
            runCatching {
                when {
                    p.isMimeType("multipart/*") -> {
                        val multipart = p.content as Multipart
                        for (i in 0 until multipart.count) {
                            val bodyPart = multipart.getBodyPart(i)
                            val isAttachment = bodyPart.disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true ||
                                (!bodyPart.fileName.isNullOrBlank() && !bodyPart.isMimeType("text/*"))
                            if (isAttachment) {
                                hasAttachments = true
                            } else {
                                walk(bodyPart)
                            }
                        }
                    }
                    text == null && p.isMimeType("text/plain") -> {
                        text = (p.content as? String)?.take(BODY_TEXT_MAX_CHARS)
                    }
                    text == null && p.isMimeType("text/html") -> {
                        text = stripHtml(p.content as? String)?.take(BODY_TEXT_MAX_CHARS)
                    }
                }
            }
            // Best-effort: malformed/unfetchable parts are skipped rather than failing the sync.
        }

        walk(part)
        return ParsedBody(text, hasAttachments)
    }

    private fun stripHtml(html: String?): String? = html
        ?.replace(Regex("<[^>]*>"), " ")
        ?.replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()

    private companion object {
        const val BODY_TEXT_MAX_CHARS = 20_000
        const val BODY_PREVIEW_CHARS = 140
    }
}
