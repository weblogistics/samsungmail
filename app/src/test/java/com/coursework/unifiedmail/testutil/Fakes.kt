package com.coursework.unifiedmail.testutil

import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.AttachmentDao
import com.coursework.unifiedmail.data.local.AttachmentEntity
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.DraftDao
import com.coursework.unifiedmail.data.local.DraftEntity
import com.coursework.unifiedmail.data.local.FolderDao
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.local.MessageDao
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.local.OutboxAttachmentDao
import com.coursework.unifiedmail.data.local.OutboxAttachmentEntity
import com.coursework.unifiedmail.data.local.OutboxDao
import com.coursework.unifiedmail.data.local.OutboxEntity
import com.coursework.unifiedmail.data.local.OutboxStatus
import com.coursework.unifiedmail.data.remote.DownloadedAttachment
import com.coursework.unifiedmail.data.remote.ImapClient
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.ImapFetchResult
import com.coursework.unifiedmail.data.remote.ImapFolderInfo
import com.coursework.unifiedmail.data.remote.ImapMessageInfo
import com.coursework.unifiedmail.data.remote.OutgoingMessage
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.remote.SmtpSender
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.NewAccount
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.AppSettingsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** Hand-written in-memory fakes for MailRepository's dependencies — no Room/Android/network. */

class FakeAccountRepository : AccountRepository {
    val accounts = mutableMapOf<String, AccountEntity>()
    val passwords = mutableMapOf<String, String>()

    override fun observeAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override fun observeActiveAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.filter { it.isActive })
    override suspend fun getAccount(accountId: String): AccountEntity? = accounts[accountId]
    override suspend fun getAllAccountsOnce(): List<AccountEntity> = accounts.values.toList()

    override suspend fun addAccount(newAccount: NewAccount): AccountEntity {
        val entity = AccountEntity(
            id = UUID.randomUUID().toString(),
            displayName = newAccount.displayName,
            emailAddress = newAccount.emailAddress,
            username = newAccount.username,
            imapHost = newAccount.imapHost,
            imapPort = newAccount.imapPort,
            imapSecurity = newAccount.imapSecurity,
            smtpHost = newAccount.smtpHost,
            smtpPort = newAccount.smtpPort,
            smtpSecurity = newAccount.smtpSecurity,
            createdAtEpochMillis = 0L,
        )
        accounts[entity.id] = entity
        passwords[entity.id] = newAccount.password
        return entity
    }

    override suspend fun updateAccount(account: AccountEntity, newPassword: String?) {
        accounts[account.id] = account
        if (!newPassword.isNullOrBlank()) {
            passwords[account.id] = newPassword
        }
    }

    override suspend fun deleteAccount(account: AccountEntity) {
        accounts.remove(account.id)
        passwords.remove(account.id)
    }

    override fun getPassword(accountId: String): String? = passwords[accountId]
}

class FakeImapClient : ImapClient {
    var foldersResult: Result<List<ImapFolderInfo>> = Result.success(listOf(ImapFolderInfo("INBOX", "INBOX")))
    var messagesResult: Result<ImapFetchResult> = Result.success(ImapFetchResult(emptyList(), 0))
    var moveResult: Result<Unit> = Result.success(Unit)
    var setSeenFlagResult: Result<Unit> = Result.success(Unit)
    var setFlaggedFlagResult: Result<Unit> = Result.success(Unit)
    var searchResult: Result<List<ImapMessageInfo>> = Result.success(emptyList())
    var lastSinceUidRequested: Long? = null
    var lastSyncWindowDaysRequested: Int? = null
    var lastMoveRequested: Triple<String, Long, String>? = null
    var lastSeenFlagRequested: Triple<String, Long, Boolean>? = null
    var lastFlaggedFlagRequested: Triple<String, Long, Boolean>? = null
    var lastSearchRequested: Pair<String, String>? = null

    override suspend fun fetchFolders(config: ImapConfig): Result<List<ImapFolderInfo>> = foldersResult

    override suspend fun fetchFolderMessages(
        config: ImapConfig,
        folderFullName: String,
        sinceUid: Long,
        syncWindowDays: Int,
    ): Result<ImapFetchResult> {
        lastSinceUidRequested = sinceUid
        lastSyncWindowDaysRequested = syncWindowDays
        return messagesResult
    }

    override suspend fun moveMessage(
        config: ImapConfig,
        sourceFolderFullName: String,
        uid: Long,
        targetFolderFullName: String,
    ): Result<Unit> {
        lastMoveRequested = Triple(sourceFolderFullName, uid, targetFolderFullName)
        return moveResult
    }

    override suspend fun setSeenFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        seen: Boolean,
    ): Result<Unit> {
        lastSeenFlagRequested = Triple(folderFullName, uid, seen)
        return setSeenFlagResult
    }

    override suspend fun setFlaggedFlag(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        flagged: Boolean,
    ): Result<Unit> {
        lastFlaggedFlagRequested = Triple(folderFullName, uid, flagged)
        return setFlaggedFlagResult
    }

    override suspend fun searchFolder(
        config: ImapConfig,
        folderFullName: String,
        query: String,
    ): Result<List<ImapMessageInfo>> {
        lastSearchRequested = folderFullName to query
        return searchResult
    }

    var findMessageByMessageIdResult: Result<ImapMessageInfo?> = Result.success(null)
    var lastFindMessageByMessageIdRequested: Pair<String, String>? = null

    override suspend fun findMessageByMessageId(
        config: ImapConfig,
        folderFullName: String,
        messageIdHeader: String,
    ): Result<ImapMessageInfo?> {
        lastFindMessageByMessageIdRequested = folderFullName to messageIdHeader
        return findMessageByMessageIdResult
    }

    var setAnsweredFlagResult: Result<Unit> = Result.success(Unit)
    var lastAnsweredFlagRequested: Pair<String, Long>? = null

    override suspend fun setAnsweredFlag(config: ImapConfig, folderFullName: String, uid: Long): Result<Unit> {
        lastAnsweredFlagRequested = folderFullName to uid
        return setAnsweredFlagResult
    }

    var emptyFolderResult: Result<Unit> = Result.success(Unit)
    var lastEmptyFolderRequested: String? = null

    override suspend fun emptyFolder(config: ImapConfig, folderFullName: String): Result<Unit> {
        lastEmptyFolderRequested = folderFullName
        return emptyFolderResult
    }

    var olderMessagesResult: Result<List<ImapMessageInfo>> = Result.success(emptyList())
    var lastFetchOlderRequested: Pair<Long, Int>? = null

    override suspend fun fetchOlderMessages(
        config: ImapConfig,
        folderFullName: String,
        beforeUid: Long,
        limit: Int,
    ): Result<List<ImapMessageInfo>> {
        lastFetchOlderRequested = beforeUid to limit
        return olderMessagesResult
    }

    var fetchAttachmentResult: Result<DownloadedAttachment> = Result.success(DownloadedAttachment("file", null, ByteArray(0)))
    var lastFetchAttachmentRequested: Pair<Long, Int>? = null

    override suspend fun fetchAttachment(
        config: ImapConfig,
        folderFullName: String,
        uid: Long,
        attachmentIndex: Int,
    ): Result<DownloadedAttachment> {
        lastFetchAttachmentRequested = uid to attachmentIndex
        return fetchAttachmentResult
    }
}

class FakeAppSettingsProvider : AppSettingsProvider {
    var settings = AppSettings()
    override suspend fun currentSettings(): AppSettings = settings
}

class FakeSmtpSender : SmtpSender {
    /** Default result when no queued result is available. */
    var result: Result<Unit> = Result.success(Unit)

    /** Consumed one at a time, oldest first — lets a test script different outcomes per send() call. */
    private val queuedResults = ArrayDeque<Result<Unit>>()
    val sentMessages = mutableListOf<OutgoingMessage>()

    fun enqueueResult(next: Result<Unit>) {
        queuedResults.addLast(next)
    }

    override suspend fun send(config: SmtpConfig, message: OutgoingMessage): Result<Unit> {
        sentMessages.add(message)
        return if (queuedResults.isNotEmpty()) queuedResults.removeFirst() else result
    }
}

class FakeFolderDao : FolderDao {
    val folders = mutableMapOf<String, FolderEntity>()

    override suspend fun insertAllIfAbsent(folders: List<FolderEntity>) {
        for (folder in folders) {
            if (!this.folders.containsKey(folder.id)) this.folders[folder.id] = folder
        }
    }

    override suspend fun getById(id: String): FolderEntity? = folders[id]
    override suspend fun getForAccount(accountId: String): List<FolderEntity> = folders.values.filter { it.accountId == accountId }

    override suspend fun updateLastSyncedUid(id: String, uid: Long) {
        folders[id]?.let { folders[id] = it.copy(lastSyncedUid = uid) }
    }

    override suspend fun resetLastSyncedUid(accountId: String) {
        for ((id, folder) in folders) {
            if (folder.accountId == accountId) folders[id] = folder.copy(lastSyncedUid = 0)
        }
    }

    override suspend fun deleteForAccount(accountId: String) {
        folders.values.removeAll { it.accountId == accountId }
    }
}

class FakeAttachmentDao : AttachmentDao {
    val attachments = mutableListOf<AttachmentEntity>()

    override suspend fun upsertAll(attachments: List<AttachmentEntity>) {
        for (attachment in attachments) {
            this.attachments.removeAll { it.id == attachment.id }
            this.attachments.add(attachment)
        }
    }

    override suspend fun getForMessage(messageId: String): List<AttachmentEntity> =
        attachments.filter { it.messageId == messageId }.sortedBy { it.indexInMessage }

    override suspend fun deleteForMessage(messageId: String) {
        attachments.removeAll { it.messageId == messageId }
    }

    override suspend fun deleteForAccount(accountId: String) {
        attachments.removeAll { it.messageId.startsWith("$accountId|") }
    }
}

class FakeMessageDao : MessageDao {
    val messages = mutableListOf<MessageEntity>()

    override suspend fun upsertAll(messages: List<MessageEntity>) {
        for (message in messages) {
            this.messages.removeAll { it.id == message.id }
            this.messages.add(message)
        }
    }

    override suspend fun getByUid(accountId: String, folderName: String, uid: Long): MessageEntity? =
        messages.firstOrNull { it.accountId == accountId && it.folderName == folderName && it.uid == uid }

    override suspend fun getUnread(accountId: String, folderName: String): List<MessageEntity> =
        messages.filter { it.accountId == accountId && it.folderName == folderName && !it.isRead }

    override suspend fun getMinUid(accountId: String, folderName: String): Long? =
        messages.filter { it.accountId == accountId && it.folderName == folderName }.minOfOrNull { it.uid }

    override suspend fun markRead(id: String, isRead: Boolean) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(isRead = isRead)
    }

    override suspend fun markFlagged(id: String, isFlagged: Boolean) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(isFlagged = isFlagged)
    }

    override suspend fun markAnswered(id: String, isAnswered: Boolean) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(isAnswered = isAnswered)
    }

    override suspend fun deleteById(id: String) {
        messages.removeAll { it.id == id }
    }

    override suspend fun deleteForAccount(accountId: String) {
        messages.removeAll { it.accountId == accountId }
    }

    override suspend fun deleteMissing(accountId: String, folderName: String, presentUids: List<Long>) {
        messages.removeAll {
            it.accountId == accountId && it.folderName == folderName && it.uid !in presentUids
        }
    }

    override fun observeConversations(accountId: String, folderName: String): Flow<List<ConversationSummary>> =
        flowOf(toConversations(messages.filter { it.accountId == accountId && it.folderName == folderName }))

    override fun observeConversationMessages(accountId: String, folderName: String, conversationId: String): Flow<List<MessageEntity>> =
        flowOf(
            messages
                .filter { it.accountId == accountId && it.folderName == folderName && it.conversationId == conversationId }
                .sortedBy { it.sentDateEpochMillis ?: it.receivedDateEpochMillis ?: 0L },
        )

    override fun observeUnifiedConversations(folderName: String): Flow<List<ConversationSummary>> =
        flowOf(toConversations(messages.filter { it.folderName == folderName }))

    override fun searchUnified(
        folderName: String,
        query: String,
        hasAttachmentOnly: Boolean,
        unreadOnly: Boolean,
        flaggedOnly: Boolean,
        fromQuery: String,
    ): Flow<List<ConversationSummary>> =
        flowOf(
            toConversations(
                messages.filter { message ->
                    message.folderName == folderName &&
                        (query.isEmpty() || message.subject?.contains(query, ignoreCase = true) == true) &&
                        (!hasAttachmentOnly || message.hasAttachments) &&
                        (!unreadOnly || !message.isRead) &&
                        (!flaggedOnly || message.isFlagged) &&
                        (fromQuery.isEmpty() || message.fromAddress?.contains(fromQuery, ignoreCase = true) == true)
                },
            ),
        )

    override fun searchInFolder(
        accountId: String,
        folderName: String,
        query: String,
        hasAttachmentOnly: Boolean,
        unreadOnly: Boolean,
        flaggedOnly: Boolean,
    ): Flow<List<ConversationSummary>> =
        flowOf(
            toConversations(
                messages.filter { message ->
                    message.accountId == accountId && message.folderName == folderName &&
                        (query.isEmpty() || message.subject?.contains(query, ignoreCase = true) == true) &&
                        (!hasAttachmentOnly || message.hasAttachments) &&
                        (!unreadOnly || !message.isRead) &&
                        (!flaggedOnly || message.isFlagged)
                },
            ),
        )

    private fun toConversations(list: List<MessageEntity>): List<ConversationSummary> =
        list.groupBy { it.conversationId }.map { (_, group) ->
            val latest = group.maxBy { it.sentDateEpochMillis ?: it.receivedDateEpochMillis ?: 0L }
            ConversationSummary(latest, group.size)
        }
}

class FakeOutboxDao : OutboxDao {
    val items = mutableMapOf<String, OutboxEntity>()

    override suspend fun insert(item: OutboxEntity) {
        items[item.id] = item
    }

    override suspend fun getById(id: String): OutboxEntity? = items[id]

    override suspend fun getUnsent(): List<OutboxEntity> =
        items.values.filter { it.status != OutboxStatus.SENT }.sortedBy { it.createdAtEpochMillis }

    override suspend fun updateStatus(id: String, status: OutboxStatus, errorMessage: String?) {
        items[id]?.let { items[id] = it.copy(status = status, errorMessage = errorMessage) }
    }
}

class FakeDraftDao : DraftDao {
    val drafts = mutableMapOf<String, DraftEntity>()

    override suspend fun upsert(draft: DraftEntity) {
        drafts[draft.id] = draft
    }

    override suspend fun getById(id: String): DraftEntity? = drafts[id]

    override fun observeAll(): Flow<List<DraftEntity>> = flowOf(drafts.values.sortedByDescending { it.updatedAtEpochMillis })

    override suspend fun delete(id: String) {
        drafts.remove(id)
    }
}

class FakeOutboxAttachmentDao : OutboxAttachmentDao {
    val attachments = mutableListOf<OutboxAttachmentEntity>()

    override suspend fun insertAll(attachments: List<OutboxAttachmentEntity>) {
        this.attachments.addAll(attachments)
    }

    override suspend fun getForOutbox(outboxId: String): List<OutboxAttachmentEntity> =
        attachments.filter { it.outboxId == outboxId }

    override suspend fun deleteForOutbox(outboxId: String) {
        attachments.removeAll { it.outboxId == outboxId }
    }
}
