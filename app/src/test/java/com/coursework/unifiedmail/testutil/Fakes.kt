package com.coursework.unifiedmail.testutil

import com.coursework.unifiedmail.data.local.AccountEntity
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
import com.coursework.unifiedmail.data.remote.ImapFetchResult
import com.coursework.unifiedmail.data.remote.ImapFolderInfo
import com.coursework.unifiedmail.data.remote.OutgoingMessage
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.remote.SmtpSender
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.NewAccount
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
    var lastSinceUidRequested: Long? = null

    override suspend fun fetchFolders(config: ImapConfig): Result<List<ImapFolderInfo>> = foldersResult

    override suspend fun fetchInboxMessages(
        config: ImapConfig,
        folderFullName: String,
        sinceUid: Long,
        initialFetchLimit: Int,
    ): Result<ImapFetchResult> {
        lastSinceUidRequested = sinceUid
        return messagesResult
    }
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

    override suspend fun markRead(id: String, isRead: Boolean) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = messages[index].copy(isRead = isRead)
    }

    override suspend fun deleteById(id: String) {
        messages.removeAll { it.id == id }
    }

    override fun observeConversations(accountId: String, folderName: String): Flow<List<ConversationSummary>> =
        flowOf(toConversations(messages.filter { it.accountId == accountId && it.folderName == folderName }))

    override fun observeUnifiedConversations(folderName: String): Flow<List<ConversationSummary>> =
        flowOf(toConversations(messages.filter { it.folderName == folderName }))

    override fun searchUnified(folderName: String, query: String): Flow<List<ConversationSummary>> =
        flowOf(
            toConversations(
                messages.filter {
                    it.folderName == folderName && (it.subject?.contains(query, ignoreCase = true) == true)
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
