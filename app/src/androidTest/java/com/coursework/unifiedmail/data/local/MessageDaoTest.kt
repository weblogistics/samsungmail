package com.coursework.unifiedmail.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the conversation-grouping/search queries against a real (in-memory) SQLite database
 * — the GROUP BY + MAX(...) bare-column behavior those queries rely on is genuine SQLite
 * semantics that only a real database engine can verify, not something a JVM unit test with a
 * fake DAO can meaningfully check.
 */
@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = db.messageDao()
        accountDao = db.accountDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun account(id: String, isActive: Boolean = true) = AccountEntity(
        id = id,
        displayName = id,
        emailAddress = "$id@example.com",
        username = id,
        imapHost = "imap.example.com",
        imapPort = 993,
        imapSecurity = MailSecurity.SSL_TLS,
        smtpHost = "smtp.example.com",
        smtpPort = 587,
        smtpSecurity = MailSecurity.STARTTLS,
        isActive = isActive,
        createdAtEpochMillis = 0L,
    )

    private fun message(
        id: String,
        accountId: String,
        uid: Long,
        conversationId: String,
        sentAt: Long,
        subject: String = "Subject $id",
    ) = MessageEntity(
        id = id,
        accountId = accountId,
        folderName = "INBOX",
        uid = uid,
        messageIdHeader = "<$id@x>",
        conversationId = conversationId,
        subject = subject,
        fromAddress = "from@example.com",
        fromPersonal = "From",
        toAddresses = null,
        ccAddresses = null,
        sentDateEpochMillis = sentAt,
        receivedDateEpochMillis = sentAt,
        isRead = false,
        isFlagged = false,
        hasAttachments = false,
        bodyPreview = "preview",
        bodyText = "body for $subject",
        bodyHtml = null,
    )

    @Test
    fun observeConversations_collapsesSameConversationToLatestMessage() = runBlocking {
        accountDao.insert(account("acc1"))
        messageDao.upsertAll(
            listOf(
                message("m1", "acc1", 1, "conv-a", sentAt = 100),
                message("m2", "acc1", 2, "conv-a", sentAt = 200),
                message("m3", "acc1", 3, "conv-b", sentAt = 150),
            ),
        )

        val conversations = messageDao.observeConversations("acc1", "INBOX").first()

        assertEquals(2, conversations.size)
        val convA = conversations.first { it.latestMessage.conversationId == "conv-a" }
        assertEquals("m2", convA.latestMessage.id)
        assertEquals(2, convA.messageCount)
        val convB = conversations.first { it.latestMessage.conversationId == "conv-b" }
        assertEquals(1, convB.messageCount)
    }

    @Test
    fun observeUnifiedConversations_onlyIncludesActiveAccounts() = runBlocking {
        accountDao.insert(account("active", isActive = true))
        accountDao.insert(account("inactive", isActive = false))
        messageDao.upsertAll(
            listOf(
                message("m1", "active", 1, "conv-a", sentAt = 100),
                message("m2", "inactive", 1, "conv-b", sentAt = 200),
            ),
        )

        val conversations = messageDao.observeUnifiedConversations("INBOX").first()

        assertEquals(1, conversations.size)
        assertEquals("active", conversations.first().latestMessage.accountId)
    }

    @Test
    fun searchUnified_matchesSubjectCaseInsensitively() = runBlocking {
        accountDao.insert(account("acc1"))
        messageDao.upsertAll(
            listOf(
                message("m1", "acc1", 1, "conv-a", sentAt = 100, subject = "Quarterly Budget Review"),
                message("m2", "acc1", 2, "conv-b", sentAt = 200, subject = "Lunch plans"),
            ),
        )

        val results = messageDao.searchUnified("INBOX", "budget", hasAttachmentOnly = false, fromQuery = "").first()

        assertEquals(1, results.size)
        assertEquals("m1", results.first().latestMessage.id)
    }

    @Test
    fun markRead_updatesOnlyTheTargetedMessage() = runBlocking {
        accountDao.insert(account("acc1"))
        messageDao.upsertAll(
            listOf(
                message("m1", "acc1", 1, "conv-a", sentAt = 100),
                message("m2", "acc1", 2, "conv-b", sentAt = 200),
            ),
        )

        messageDao.markRead("m1", true)

        assertEquals(true, messageDao.getByUid("acc1", "INBOX", 1)?.isRead)
        assertEquals(false, messageDao.getByUid("acc1", "INBOX", 2)?.isRead)
    }

    @Test
    fun deleteById_removesOnlyThatMessage() = runBlocking {
        accountDao.insert(account("acc1"))
        messageDao.upsertAll(
            listOf(
                message("m1", "acc1", 1, "conv-a", sentAt = 100),
                message("m2", "acc1", 2, "conv-b", sentAt = 200),
            ),
        )

        messageDao.deleteById("m1")

        assertNull(messageDao.getByUid("acc1", "INBOX", 1))
        assertEquals("m2", messageDao.getByUid("acc1", "INBOX", 2)?.id)
    }
}
