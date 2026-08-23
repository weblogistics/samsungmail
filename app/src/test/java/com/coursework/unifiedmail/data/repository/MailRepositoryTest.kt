package com.coursework.unifiedmail.data.repository

import com.coursework.unifiedmail.data.files.PickedAttachment
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.AttachmentEntity
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.local.MailSecurity
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.local.OutboxStatus
import com.coursework.unifiedmail.data.remote.DownloadedAttachment
import com.coursework.unifiedmail.data.remote.ImapAttachmentInfo
import com.coursework.unifiedmail.data.remote.ImapFetchResult
import com.coursework.unifiedmail.data.remote.ImapFolderInfo
import com.coursework.unifiedmail.data.remote.ImapMessageInfo
import com.coursework.unifiedmail.testutil.FakeAccountRepository
import com.coursework.unifiedmail.testutil.FakeAppSettingsProvider
import com.coursework.unifiedmail.testutil.FakeAttachmentDao
import com.coursework.unifiedmail.testutil.FakeDraftDao
import com.coursework.unifiedmail.testutil.FakeFolderDao
import com.coursework.unifiedmail.testutil.FakeImapClient
import com.coursework.unifiedmail.testutil.FakeMessageDao
import com.coursework.unifiedmail.testutil.FakeOutboxAttachmentDao
import com.coursework.unifiedmail.testutil.FakeOutboxDao
import com.coursework.unifiedmail.testutil.FakeSmtpSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MailRepositoryTest {

    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var imapClient: FakeImapClient
    private lateinit var smtpSender: FakeSmtpSender
    private lateinit var folderDao: FakeFolderDao
    private lateinit var messageDao: FakeMessageDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var attachmentDao: FakeAttachmentDao
    private lateinit var outboxAttachmentDao: FakeOutboxAttachmentDao
    private lateinit var draftDao: FakeDraftDao
    private lateinit var settingsProvider: FakeAppSettingsProvider
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
        attachmentDao = FakeAttachmentDao()
        outboxAttachmentDao = FakeOutboxAttachmentDao()
        draftDao = FakeDraftDao()
        settingsProvider = FakeAppSettingsProvider()
        // Unconfined: the fakes never suspend for real, so background work launched via
        // repositoryScope.launch (e.g. setMessageRead's server push) runs eagerly/synchronously
        // within the launch call itself, making it deterministic to assert on directly.
        repository = MailRepository(
            accountRepository, imapClient, smtpSender, folderDao, messageDao, outboxDao, attachmentDao, outboxAttachmentDao, draftDao,
            CoroutineScope(UnconfinedTestDispatcher()), settingsProvider,
        )

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
        ccAddresses = null,
        sentDateEpochMillis = uid,
        receivedDateEpochMillis = uid,
        isRead = false,
        isFlagged = false,
        hasAttachments = false,
        bodyPreview = "preview",
        bodyText = "body",
        bodyHtml = null,
    )

    private fun fakeMessageEntity(uid: Long, folderKey: String = MailRepository.INBOX_FOLDER_KEY) = MessageEntity(
        id = "$accountId|$folderKey|$uid",
        accountId = accountId,
        folderName = folderKey,
        uid = uid,
        messageIdHeader = "<$uid@x>",
        conversationId = "mid:<$uid@x>",
        subject = "Hello",
        fromAddress = "sender@example.com",
        fromPersonal = "Sender",
        toAddresses = "me@example.com",
        ccAddresses = null,
        sentDateEpochMillis = uid,
        receivedDateEpochMillis = uid,
        isRead = false,
        isFlagged = false,
        hasAttachments = false,
        bodyPreview = "preview",
        bodyText = "body",
        bodyHtml = null,
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
    fun `searchServerUnified upserts matches without touching the sync watermark`() = runTest {
        folderDao.folders["$accountId|INBOX"] = FolderEntity(
            id = "$accountId|INBOX",
            accountId = accountId,
            fullName = "INBOX",
            displayName = "INBOX",
            lastSyncedUid = 5L,
        )
        imapClient.searchResult = Result.success(listOf(fakeMessage(99, subject = "Found it")))

        val outcome = repository.searchServerUnified("found")

        assertEquals(SyncOutcome.Success(1), outcome)
        assertEquals("Found it", messageDao.messages.single { it.uid == 99L }.subject)
        assertEquals("INBOX" to "found", imapClient.lastSearchRequested)
        assertEquals(5L, folderDao.folders["$accountId|INBOX"]?.lastSyncedUid) // unchanged — not a real sync
    }

    @Test
    fun `searchServerUnified surfaces a search failure`() = runTest {
        folderDao.folders["$accountId|INBOX"] = FolderEntity(
            id = "$accountId|INBOX",
            accountId = accountId,
            fullName = "INBOX",
            displayName = "INBOX",
        )
        imapClient.searchResult = Result.failure(RuntimeException("boom"))

        val outcome = repository.searchServerUnified("found")

        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `syncFolder persists attachment metadata for messages that have any`() = runTest {
        val messageWithAttachment = fakeMessage(1).copy(
            attachments = listOf(
                ImapAttachmentInfo("photo.jpg", "image/jpeg", 1234L),
                ImapAttachmentInfo("notes.pdf", "application/pdf", 5678L),
            ),
        )
        imapClient.messagesResult = Result.success(ImapFetchResult(listOf(messageWithAttachment), newLastSyncedUid = 1))

        repository.syncAccount(accountId)

        val stored = repository.getAttachments(accountId, MailRepository.INBOX_FOLDER_KEY, 1)
        assertEquals(2, stored.size)
        assertEquals("photo.jpg", stored[0].fileName)
        assertEquals(0, stored[0].indexInMessage)
        assertEquals("notes.pdf", stored[1].fileName)
        assertEquals(1, stored[1].indexInMessage)
    }

    @Test
    fun `downloadAttachment delegates to ImapClient with the right folder and uid`() = runTest {
        folderDao.folders["$accountId|INBOX"] = FolderEntity(
            id = "$accountId|INBOX",
            accountId = accountId,
            fullName = "INBOX",
            displayName = "INBOX",
        )
        imapClient.fetchAttachmentResult = Result.success(DownloadedAttachment("photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3)))

        val result = repository.downloadAttachment(accountId, MailRepository.INBOX_FOLDER_KEY, uid = 1, attachmentIndex = 0)

        assertTrue(result.isSuccess)
        assertEquals("photo.jpg", result.getOrNull()?.fileName)
        assertEquals(1L to 0, imapClient.lastFetchAttachmentRequested)
    }

    @Test
    fun `setMessageRead updates the local row and pushes the Seen flag to the server`() = runTest {
        messageDao.messages.add(fakeMessageEntity(1))
        folderDao.folders["$accountId|INBOX"] = FolderEntity(
            id = "$accountId|INBOX",
            accountId = accountId,
            fullName = "INBOX",
            displayName = "INBOX",
        )

        repository.setMessageRead(accountId, MailRepository.INBOX_FOLDER_KEY, 1, isRead = true)

        assertTrue(messageDao.messages.first { it.uid == 1L }.isRead)
        assertEquals(Triple("INBOX", 1L, true), imapClient.lastSeenFlagRequested)
    }

    @Test
    fun `syncFolder prunes local messages no longer present on the server`() = runTest {
        messageDao.messages.add(fakeMessageEntity(1))
        messageDao.messages.add(fakeMessageEntity(2))
        imapClient.messagesResult = Result.success(
            ImapFetchResult(messages = emptyList(), newLastSyncedUid = 2, presentUids = listOf(2L)),
        )

        repository.syncAccount(accountId)

        assertEquals(listOf(2L), messageDao.messages.map { it.uid })
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
    fun `bodyHtml flows through from ComposeDraft to the outgoing message`() = runTest {
        repository.queueMessageForSending(
            accountId,
            ComposeDraft(to = listOf("dest@example.com"), subject = "Hi", body = "Plain", bodyHtml = "<b>Plain</b>"),
        )

        repository.flushOutbox()

        assertEquals("<b>Plain</b>", smtpSender.sentMessages.single().bodyHtml)
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
    fun `emptyTrash expunges the resolved Trash folder on the server and clears the local cache`() = runTest {
        accountRepository.accounts[accountId] = accountRepository.accounts.getValue(accountId).copy(trashFolderFullName = "Trash")
        imapClient.foldersResult = Result.success(listOf(ImapFolderInfo("INBOX", "INBOX"), ImapFolderInfo("Trash", "Trash")))
        folderDao.folders["$accountId|Trash"] = FolderEntity(id = "$accountId|Trash", accountId = accountId, fullName = "Trash", displayName = "Trash")
        messageDao.messages.add(fakeMessageEntity(1, folderKey = "Trash"))

        val outcome = repository.emptyTrash(accountId)

        assertEquals(SyncOutcome.Success(0), outcome)
        assertEquals("Trash", imapClient.lastEmptyFolderRequested)
        assertTrue(messageDao.messages.none { it.folderName == "Trash" })
    }

    @Test
    fun `emptyTrash fails cleanly when no Trash folder is configured or found`() = runTest {
        val outcome = repository.emptyTrash(accountId)
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `markAnswered updates the local row and pushes the Answered flag to the server`() = runTest {
        messageDao.messages.add(fakeMessageEntity(1))
        folderDao.folders["$accountId|INBOX"] = FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX")

        repository.markAnswered(accountId, MailRepository.INBOX_FOLDER_KEY, 1)

        assertTrue(messageDao.messages.first { it.uid == 1L }.isAnswered)
        assertEquals("INBOX" to 1L, imapClient.lastAnsweredFlagRequested)
    }

    @Test
    fun `searchServerFolder upserts matches for the given account and folder`() = runTest {
        folderDao.folders["$accountId|INBOX"] = FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX")
        imapClient.searchResult = Result.success(listOf(fakeMessage(7, subject = "Quarterly report")))

        val outcome = repository.searchServerFolder(accountId, MailRepository.INBOX_FOLDER_KEY, "quarterly")

        assertEquals(SyncOutcome.Success(1), outcome)
        assertEquals("Quarterly report", messageDao.messages.single { it.uid == 7L }.subject)
    }

    @Test
    fun `findSpamFolderKey prefers the configured folder over the name heuristic`() = runTest {
        accountRepository.accounts[accountId] = accountRepository.accounts.getValue(accountId).copy(spamFolderFullName = "Bulk Mail")
        folderDao.folders["$accountId|Junk"] = FolderEntity(id = "$accountId|Junk", accountId = accountId, fullName = "Junk", displayName = "Junk")

        assertEquals("Bulk Mail", repository.findSpamFolderKey(accountId))
    }

    @Test
    fun `findSpamFolderKey falls back to a name heuristic when nothing is configured`() = runTest {
        folderDao.folders["$accountId|Junk"] = FolderEntity(id = "$accountId|Junk", accountId = accountId, fullName = "Junk", displayName = "Junk")
        assertEquals("Junk", repository.findSpamFolderKey(accountId))
    }

    @Test
    fun `saveDraft then getDraft round-trips its content, and deleteDraft removes it`() = runTest {
        val content = DraftContent(accountId = accountId, to = "a@example.com", cc = "", bcc = "", subject = "Hi", bodyHtml = "<b>Hello</b>")

        repository.saveDraft("draft-1", content)
        val loaded = repository.getDraft("draft-1")

        assertEquals("a@example.com", loaded?.to)
        assertEquals("<b>Hello</b>", loaded?.bodyHtml)

        repository.deleteDraft("draft-1")
        assertEquals(null, repository.getDraft("draft-1"))
    }

    @Test
    fun `loadOlderMessages pages backward from the lowest cached uid without touching the sync watermark`() = runTest {
        folderDao.folders["$accountId|INBOX"] =
            FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX", lastSyncedUid = 50L)
        messageDao.messages.add(fakeMessageEntity(10))
        imapClient.olderMessagesResult = Result.success(listOf(fakeMessage(5), fakeMessage(6)))

        val outcome = repository.loadOlderMessages(accountId, MailRepository.INBOX_FOLDER_KEY)

        assertEquals(SyncOutcome.Success(2), outcome)
        assertEquals(10L to 50, imapClient.lastFetchOlderRequested)
        assertEquals(3, messageDao.messages.size)
        assertEquals(50L, folderDao.folders["$accountId|INBOX"]?.lastSyncedUid)
    }

    @Test
    fun `loadOlderMessages fails cleanly when the folder has never been synced`() = runTest {
        val outcome = repository.loadOlderMessages(accountId, MailRepository.INBOX_FOLDER_KEY)
        assertTrue(outcome is SyncOutcome.Failure)
    }

    @Test
    fun `undoMove finds the message in its new folder by Message-ID and moves it back`() = runTest {
        folderDao.folders["$accountId|INBOX"] = FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX")
        folderDao.folders["$accountId|Trash"] = FolderEntity(id = "$accountId|Trash", accountId = accountId, fullName = "Trash", displayName = "Trash")
        imapClient.findMessageByMessageIdResult = Result.success(fakeMessage(99))

        val outcome = repository.undoMove(accountId, originalFolderKey = MailRepository.INBOX_FOLDER_KEY, currentFolderKey = "Trash", messageIdHeader = "<99@x>")

        assertEquals(UndoMoveOutcome.Success, outcome)
        assertEquals("Trash" to "<99@x>", imapClient.lastFindMessageByMessageIdRequested)
        assertEquals(Triple("Trash", 99L, "INBOX"), imapClient.lastMoveRequested)
    }

    @Test
    fun `undoMove fails cleanly when the message has no Message-ID`() = runTest {
        val outcome = repository.undoMove(accountId, MailRepository.INBOX_FOLDER_KEY, "Trash", messageIdHeader = null)
        assertTrue(outcome is UndoMoveOutcome.Failure)
    }

    @Test
    fun `undoMove fails cleanly when the message is genuinely not found (e-g- moved again)`() = runTest {
        folderDao.folders["$accountId|Trash"] = FolderEntity(id = "$accountId|Trash", accountId = accountId, fullName = "Trash", displayName = "Trash")
        imapClient.findMessageByMessageIdResult = Result.success(null)

        val outcome = repository.undoMove(accountId, MailRepository.INBOX_FOLDER_KEY, "Trash", messageIdHeader = "<99@x>")

        assertTrue(outcome is UndoMoveOutcome.Failure)
    }

    @Test
    fun `markAllReadInFolder marks every unread message in that folder read`() = runTest {
        folderDao.folders["$accountId|INBOX"] = FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX")
        messageDao.messages.add(fakeMessageEntity(1))
        messageDao.messages.add(fakeMessageEntity(2))

        repository.markAllReadInFolder(accountId, MailRepository.INBOX_FOLDER_KEY)

        assertTrue(messageDao.messages.all { it.isRead })
    }

    @Test
    fun `resetSyncProgress zeroes every cached folder's watermark for the account`() = runTest {
        folderDao.folders["$accountId|INBOX"] =
            FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX", lastSyncedUid = 42L)
        folderDao.folders["$accountId|Archive"] =
            FolderEntity(id = "$accountId|Archive", accountId = accountId, fullName = "Archive", displayName = "Archive", lastSyncedUid = 7L)

        repository.resetSyncProgress(accountId)

        assertEquals(0L, folderDao.folders["$accountId|INBOX"]?.lastSyncedUid)
        assertEquals(0L, folderDao.folders["$accountId|Archive"]?.lastSyncedUid)
    }

    @Test
    fun `deleteAccountAndCache removes the account's cached messages, folders and attachments`() = runTest {
        messageDao.messages.add(fakeMessageEntity(1))
        folderDao.folders["$accountId|INBOX"] = FolderEntity(id = "$accountId|INBOX", accountId = accountId, fullName = "INBOX", displayName = "INBOX")
        attachmentDao.attachments.add(
            AttachmentEntity(id = "$accountId|INBOX|1|0", messageId = "$accountId|INBOX|1", indexInMessage = 0, fileName = "photo.jpg", mimeType = null, sizeBytes = null),
        )

        repository.deleteAccountAndCache(accountRepository.accounts.getValue(accountId))

        assertTrue(messageDao.messages.isEmpty())
        assertTrue(folderDao.folders.isEmpty())
        assertTrue(attachmentDao.attachments.isEmpty())
        assertTrue(accountRepository.accounts.isEmpty())
    }

    @Test
    fun `an attached file is sent and its local copy cleaned up on success`() = runTest {
        val id = repository.queueMessageForSending(accountId, ComposeDraft(to = listOf("dest@example.com"), subject = "Hi", body = "Body"))
        val tempFile = java.io.File.createTempFile("attach", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        repository.attachFilesToOutbox(id, listOf(PickedAttachment("photo.jpg", "image/jpeg", tempFile.length(), tempFile.absolutePath)))

        val allSucceeded = repository.flushOutbox()

        assertTrue(allSucceeded)
        assertEquals("photo.jpg", smtpSender.sentMessages.single().attachments.single().fileName)
        assertTrue(outboxAttachmentDao.attachments.isEmpty())
        assertFalse(tempFile.exists())
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
