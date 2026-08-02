package com.coursework.unifiedmail.data.repository

import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.FolderDao
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.local.MessageDao
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.local.OutboxDao
import com.coursework.unifiedmail.data.local.OutboxEntity
import com.coursework.unifiedmail.data.local.OutboxStatus
import com.coursework.unifiedmail.data.remote.ImapClient
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.ImapMessageInfo
import com.coursework.unifiedmail.data.remote.OutgoingMessage
import com.coursework.unifiedmail.data.remote.describeMailError
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.remote.SmtpSender
import com.coursework.unifiedmail.domain.threading.ConversationThreading
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOutcome {
    data class Success(val newMessageCount: Int) : SyncOutcome()
    data class Failure(val reason: String) : SyncOutcome()
}

sealed class SendOutcome {
    data object Success : SendOutcome()
    data class Failure(val reason: String) : SendOutcome()
}

data class ComposeDraft(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val inReplyToMessageIdHeader: String? = null,
    val referencesHeader: String? = null,
)

/**
 * Only the INBOX folder is synced (folder list is cached in full for future folder navigation).
 * Also owns outgoing mail: composed messages are queued to an outbox and sent from here so both
 * the sync path and the send path share one place that knows how to load an account's password.
 */
@Singleton
class MailRepository @Inject constructor(
    private val accountRepository: AccountRepository,
    private val imapClient: ImapClient,
    private val smtpSender: SmtpSender,
    private val folderDao: FolderDao,
    private val messageDao: MessageDao,
    private val outboxDao: OutboxDao,
) {
    /** INBOX conversations for one account, collapsed to their latest message, newest first. */
    fun observeInbox(accountId: String): Flow<List<ConversationSummary>> =
        messageDao.observeConversations(accountId, INBOX_FOLDER_KEY)

    /** INBOX conversations across every active account, newest first — the app's home view. */
    fun observeUnifiedInbox(): Flow<List<ConversationSummary>> =
        messageDao.observeUnifiedConversations(INBOX_FOLDER_KEY)

    fun searchUnifiedInbox(query: String): Flow<List<ConversationSummary>> =
        messageDao.searchUnified(INBOX_FOLDER_KEY, query)

    suspend fun getInboxMessage(accountId: String, uid: Long): MessageEntity? =
        messageDao.getByUid(accountId, INBOX_FOLDER_KEY, uid)

    suspend fun setMessageRead(accountId: String, uid: Long, isRead: Boolean) {
        messageDao.markRead(messageId(accountId, uid), isRead)
    }

    /** Removes a message from the local cache only — does not delete it from the server. */
    suspend fun removeMessageLocally(accountId: String, uid: Long) {
        messageDao.deleteById(messageId(accountId, uid))
    }

    suspend fun syncAccount(accountId: String): SyncOutcome {
        val account = accountRepository.getAccount(accountId)
            ?: return SyncOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId)
            ?: return SyncOutcome.Failure("Missing saved credentials")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        val folders = imapClient.fetchFolders(imapConfig).getOrElse {
            return SyncOutcome.Failure(describeMailError(it))
        }
        folderDao.insertAllIfAbsent(
            folders.map { folder ->
                FolderEntity(
                    id = folderId(accountId, localFolderKey(folder.fullName)),
                    accountId = accountId,
                    fullName = folder.fullName,
                    displayName = folder.displayName,
                )
            },
        )

        val inbox = folders.firstOrNull { it.fullName.equals("INBOX", ignoreCase = true) }
            ?: return SyncOutcome.Failure("No INBOX folder on this account")

        val inboxFolderId = folderId(accountId, INBOX_FOLDER_KEY)
        val existingLastUid = folderDao.getById(inboxFolderId)?.lastSyncedUid ?: 0L

        val fetchResult = imapClient.fetchInboxMessages(
            config = imapConfig,
            folderFullName = inbox.fullName,
            sinceUid = existingLastUid,
            initialFetchLimit = INITIAL_FETCH_LIMIT,
        ).getOrElse {
            return SyncOutcome.Failure(describeMailError(it))
        }

        if (fetchResult.messages.isNotEmpty()) {
            messageDao.upsertAll(fetchResult.messages.map { it.toEntity(accountId) })
        }
        folderDao.updateLastSyncedUid(inboxFolderId, fetchResult.newLastSyncedUid)

        return SyncOutcome.Success(fetchResult.messages.size)
    }

    /** Writes a draft to the outbox as PENDING; the caller is responsible for triggering a send. */
    suspend fun queueMessageForSending(accountId: String, draft: ComposeDraft): String {
        val id = UUID.randomUUID().toString()
        outboxDao.insert(
            OutboxEntity(
                id = id,
                accountId = accountId,
                toAddresses = draft.to.joinToString(";"),
                ccAddresses = draft.cc.takeIf { it.isNotEmpty() }?.joinToString(";"),
                bccAddresses = draft.bcc.takeIf { it.isNotEmpty() }?.joinToString(";"),
                subject = draft.subject,
                body = draft.body,
                inReplyToMessageIdHeader = draft.inReplyToMessageIdHeader,
                referencesHeader = draft.referencesHeader,
                status = OutboxStatus.PENDING,
                errorMessage = null,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /**
     * Attempts to send every PENDING/FAILED outbox item. Called both right after composing (via
     * a network-constrained one-time work request) and on every periodic sync, so a message
     * queued while offline still goes out once connectivity returns even if the app was closed.
     */
    suspend fun flushOutbox(): Boolean {
        var allSucceeded = true
        for (item in outboxDao.getUnsent()) {
            if (sendOutboxItem(item) is SendOutcome.Failure) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private suspend fun sendOutboxItem(item: OutboxEntity): SendOutcome {
        val account = accountRepository.getAccount(item.accountId)
        if (account == null) {
            outboxDao.updateStatus(item.id, OutboxStatus.FAILED, "Account not found")
            return SendOutcome.Failure("Account not found")
        }
        val password = accountRepository.getPassword(item.accountId)
        if (password == null) {
            outboxDao.updateStatus(item.id, OutboxStatus.FAILED, "Missing saved credentials")
            return SendOutcome.Failure("Missing saved credentials")
        }

        val smtpConfig = SmtpConfig(
            host = account.smtpHost,
            port = account.smtpPort,
            security = account.smtpSecurity,
            username = account.username,
            password = password,
        )
        val outgoing = OutgoingMessage(
            fromAddress = account.emailAddress,
            fromPersonal = account.displayName,
            to = item.toAddresses.splitAddresses(),
            cc = item.ccAddresses?.splitAddresses() ?: emptyList(),
            bcc = item.bccAddresses?.splitAddresses() ?: emptyList(),
            subject = item.subject,
            body = item.body,
            inReplyTo = item.inReplyToMessageIdHeader,
            references = item.referencesHeader,
        )

        val result = smtpSender.send(smtpConfig, outgoing)
        return if (result.isSuccess) {
            outboxDao.updateStatus(item.id, OutboxStatus.SENT, null)
            SendOutcome.Success
        } else {
            val reason = describeMailError(result.exceptionOrNull() ?: Throwable("Could not send message"))
            outboxDao.updateStatus(item.id, OutboxStatus.FAILED, reason)
            SendOutcome.Failure(reason)
        }
    }

    private fun String.splitAddresses(): List<String> = split(";").map { it.trim() }.filter { it.isNotBlank() }

    private fun ImapMessageInfo.toEntity(accountId: String) = MessageEntity(
        id = messageId(accountId, uid),
        accountId = accountId,
        folderName = INBOX_FOLDER_KEY,
        uid = uid,
        messageIdHeader = messageIdHeader,
        conversationId = ConversationThreading.computeConversationId(
            messageIdHeader = messageIdHeader,
            inReplyToHeader = inReplyToHeader,
            referencesHeader = referencesHeader,
            subject = subject,
        ),
        subject = subject,
        fromAddress = fromAddress,
        fromPersonal = fromPersonal,
        toAddresses = toAddresses,
        sentDateEpochMillis = sentDateEpochMillis,
        receivedDateEpochMillis = receivedDateEpochMillis,
        isRead = isRead,
        isFlagged = isFlagged,
        hasAttachments = hasAttachments,
        bodyPreview = bodyPreview,
        bodyText = bodyText,
    )

    private fun folderId(accountId: String, folderKey: String) = "$accountId|$folderKey"

    private fun messageId(accountId: String, uid: Long) = "$accountId|$INBOX_FOLDER_KEY|$uid"

    // The server's "INBOX" folder isn't guaranteed to come back with that exact casing; folder
    // rows for the inbox are always keyed by the normalized constant so lookups stay consistent.
    private fun localFolderKey(fullName: String): String =
        if (fullName.equals("INBOX", ignoreCase = true)) INBOX_FOLDER_KEY else fullName

    private companion object {
        // Local key for the inbox folder, independent of whatever case the server reports
        // ("INBOX", "Inbox", ...) — keeps observeInbox()'s query stable across providers.
        const val INBOX_FOLDER_KEY = "INBOX"
        const val INITIAL_FETCH_LIMIT = 50
    }
}
