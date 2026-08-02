package com.coursework.unifiedmail.data.repository

import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.MailSecurity
import com.coursework.unifiedmail.data.local.OutboxStatus
import com.coursework.unifiedmail.data.remote.ImapFetchResult
import com.coursework.unifiedmail.data.remote.ImapFolderInfo
import com.coursework.unifiedmail.data.remote.ImapMessageInfo
import com.coursework.unifiedmail.testutil.FakeAccountRepository
import com.coursework.unifiedmail.testutil.FakeFolderDao
import com.coursework.unifiedmail.testutil.FakeImapClient
import com.coursework.unifiedmail.testutil.FakeMessageDao
import com.coursework.unifiedmail.testutil.FakeOutboxDao
import com.coursework.unifiedmail.testutil.FakeSmtpSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailRepositoryTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var imapClient: FakeImapClient
    private lateinit var smtpSender: FakeSmtpSender
    private lateinit var folderDao: FakeFolderDao
    private lateinit var messageDao: FakeMessageDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var repository: MailRepository

    private val accountId = "acct-1"

    @Before
    fun setUp() {
        accountRepository = FakeAccountRepository()
        imapClient = FakeImapClient()
        smtpSender = FakeSmtpSender()
        folderDao = FakeFolderDao()
        messageDao = FakeMessageDao()
        outboxDao = FakeOutboxDao()
        repository = MailRepository(accountRepository, imapClient, smtpSender, folderDao, messageDao, outboxDao)

        accountRepository.accounts[accountId] = AccountEntity(
            id = accountId,
            displayName = "Test Account",
            emailAddress = "me@example.com",
            username = "me",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapSecurity = MailSecurity.SSL_TLS,
            smtpHost = "smtp.example.com",
            smtpPort = 587,
            smtpSecurity = MailSecurity.STARTTLS,
            createdAtEpochMillis = 0L,
        )
        accountRepository.passwords[accountId] = "hunter2"
    }

    private fun fakeMessage(uid: Long, subject: String = "Hello") = ImapMessageInfo(
        uid = uid,
        messageIdHeader = "<$uid@x>",
        inReplyToHeader = null,
        referencesHeader = null,
        subject = subject,
        fromAddress = "sender@example.com",
        fromPersonal = "Sender",
        toAddresses = "me@example.com",
        sentDateEpochMillis = uid,
        receivedDateEpochMillis = uid,
        isRead = false,
        isFlagged = false,
        hasAttachments = false,
        bodyPreview = "preview",
        bodyText = "body",
    )

    @Test
    fun `syncAccount success caches messages and advances the sync watermark`() = runTest {
        imapClient.messagesResult = Result.success(ImapFetchResult(listOf(fakeMessage(1), fakeMessage(2)), newLastSyncedUid = 2))

        val outcome = repository.syncAccount(accountId)

        assertEquals(SyncOutcome.Success(2), outcome)
        assertEquals(2, messageDao.messages.size)
        assertEquals(2L, folderDao.folders["$accountId|INBOX"]?.lastSyncedUid)
    }

    @Test
    fun `syncAccount fails cleanly when the account does not exist`() = runTest {
        val outcome = repository.syncAccount("missing-account")
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `syncAccount fails cleanly when the password is missing`() = runTest {
        accountRepository.passwords.remove(accountId)
        val outcome = repository.syncAccount(accountId)
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `syncAccount surfaces a folder listing failure`() = runTest {
        imapClient.foldersResult = Result.failure(RuntimeException("boom"))
        val outcome = repository.syncAccount(accountId)
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `syncAccount fails when the server has no INBOX folder`() = runTest {
        imapClient.foldersResult = Result.success(listOf(ImapFolderInfo("Archive", "Archive")))
        val outcome = repository.syncAccount(accountId)
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `second sync requests only messages after the previously recorded uid`() = runTest {
        imapClient.messagesResult = Result.success(ImapFetchResult(listOf(fakeMessage(1)), newLastSyncedUid = 1))
        repository.syncAccount(accountId)
        assertEquals(0L, imapClient.lastSinceUidRequested) // first sync: nothing recorded yet

        imapClient.messagesResult = Result.success(ImapFetchResult(listOf(fakeMessage(2)), newLastSyncedUid = 2))
        repository.syncAccount(accountId)
        assertEquals(1L, imapClient.lastSinceUidRequested) // second sync: picks up from where it left off
    }

    @Test
    fun `queued message is sent and marked SENT on success`() = runTest {
        val id = repository.queueMessageForSending(
            accountId,
            ComposeDraft(to = listOf("dest@example.com"), subject = "Hi", body = "Body"),
        )

        val allSucceeded = repository.flushOutbox()

        assertTrue(allSucceeded)
        assertEquals(OutboxStatus.SENT, outboxDao.items[id]?.status)
    }

    @Test
    fun `queued message is marked FAILED with a reason when sending fails`() = runTest {
        smtpSender.result = Result.failure(RuntimeException("connection refused"))
        val id = repository.queueMessageForSending(
            accountId,
            ComposeDraft(to = listOf("dest@example.com"), subject = "Hi", body = "Body"),
        )

        val allSucceeded = repository.flushOutbox()

        assertFalse(allSucceeded)
        assertEquals(OutboxStatus.FAILED, outboxDao.items[id]?.status)
        assertEquals("connection refused", outboxDao.items[id]?.errorMessage)
    }

    @Test
    fun `a failed send does not block other queued messages from being attempted`() = runTest {
        val failingId = repository.queueMessageForSending(
            accountId,
            ComposeDraft(to = listOf("a@example.com"), subject = "A", body = "Body"),
        )
        val succeedingId = repository.queueMessageForSending(
            accountId,
            ComposeDraft(to = listOf("b@example.com"), subject = "B", body = "Body"),
        )
        smtpSender.enqueueResult(Result.failure(RuntimeException("temporary failure")))
        smtpSender.enqueueResult(Result.success(Unit))

        val allSucceeded = repository.flushOutbox()

        assertFalse(allSucceeded)
        assertEquals(OutboxStatus.FAILED, outboxDao.items[failingId]?.status)
        assertEquals(OutboxStatus.SENT, outboxDao.items[succeedingId]?.status)
    }

    @Test
    fun `a failed outbox item is retried on the next flush`() = runTest {
        val id = repository.queueMessageForSending(
            accountId,
            ComposeDraft(to = listOf("a@example.com"), subject = "A", body = "Body"),
        )
        smtpSender.enqueueResult(Result.failure(RuntimeException("temporary failure")))
        repository.flushOutbox()
        assertEquals(OutboxStatus.FAILED, outboxDao.items[id]?.status)

        smtpSender.enqueueResult(Result.success(Unit))
        val allSucceeded = repository.flushOutbox()

        assertTrue(allSucceeded)
        assertEquals(OutboxStatus.SENT, outboxDao.items[id]?.status)
    }
}
