package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One inbox row: the newest message of a conversation plus totals across all of its messages, so
 * the row can show e.g. "unread" when only an older message in the thread is unread.
 */
data class ConversationSummary(
    @Embedded val latestMessage: MessageEntity,
    val messageCount: Int,
    val unreadCount: Int,
    val flaggedCount: Int,
    val attachmentCount: Int,
) {
    val isThread: Boolean get() = messageCount > 1
}

// Every MessageEntity column except the bodies, which come back NULL. Lists, threads and
// notifications only need the preview, and a body can be large: HTML plus inlined images is
// bounded by ImapClient, but rows synced before that bound could exceed Android's ~2 MB
// CursorWindow, which makes the *whole query* fail. Only getByUid (the message view) reads bodies.
private const val MESSAGE_COLUMNS_NO_BODY =
    "messages.id, messages.accountId, messages.folderName, messages.uid, messages.messageIdHeader, " +
        "messages.conversationId, messages.subject, messages.fromAddress, messages.fromPersonal, " +
        "messages.toAddresses, messages.ccAddresses, messages.sentDateEpochMillis, " +
        "messages.receivedDateEpochMillis, messages.isRead, messages.isFlagged, messages.hasAttachments, " +
        "messages.bodyPreview, messages.isAnswered, NULL AS bodyText, NULL AS bodyHtml"

// Shared by every grouped query below. Only SUM/COUNT are added alongside the single MAX in each
// ORDER BY — a second min()/max() aggregate would break SQLite's guarantee that the bare
// message columns come from the newest row (see observeConversations).
private const val CONVERSATION_COLUMNS =
    "$MESSAGE_COLUMNS_NO_BODY, COUNT(*) as messageCount, " +
        "SUM(CASE WHEN messages.isRead = 0 THEN 1 ELSE 0 END) as unreadCount, " +
        "SUM(messages.isFlagged) as flaggedCount, " +
        "SUM(messages.hasAttachments) as attachmentCount"

data class AccountUnreadCount(val accountId: String, val unreadCount: Int)

/** Projection for MailRepository.getAutocompleteContacts — just the two columns actually addresses live in. */
data class AddressColumns(val toAddresses: String?, val ccAddresses: String?)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid = :uid")
    suspend fun getByUid(accountId: String, folderName: String, uid: Long): MessageEntity?

    /** [getByUid] without the HTML body — the fallback when a row is too large to read whole (see MailRepository.getMessage). */
    @Query(
        "SELECT messages.id, messages.accountId, messages.folderName, messages.uid, messages.messageIdHeader, " +
            "messages.conversationId, messages.subject, messages.fromAddress, messages.fromPersonal, " +
            "messages.toAddresses, messages.ccAddresses, messages.sentDateEpochMillis, " +
            "messages.receivedDateEpochMillis, messages.isRead, messages.isFlagged, messages.hasAttachments, " +
            "messages.bodyPreview, messages.isAnswered, messages.bodyText, NULL AS bodyHtml " +
            "FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid = :uid",
    )
    suspend fun getByUidWithoutHtml(accountId: String, folderName: String, uid: Long): MessageEntity?

    /** Feeds MailRepository.getAutocompleteContacts — every To/Cc pair cached for one account's Sent folder. */
    @Query("SELECT toAddresses, ccAddresses FROM messages WHERE accountId = :accountId AND folderName = :folderName")
    suspend fun getAddressColumns(accountId: String, folderName: String): List<AddressColumns>

    /** Lowest cached UID in this folder — the boundary "load older mail" pages backward from. */
    @Query("SELECT MIN(uid) FROM messages WHERE accountId = :accountId AND folderName = :folderName")
    suspend fun getMinUid(accountId: String, folderName: String): Long?

    /** The [limit] most-recently-dated cached messages — see MailRepository.getRecentMessages, used to build a new-mail notification's preview. */
    @Query(
        "SELECT $MESSAGE_COLUMNS_NO_BODY FROM messages WHERE accountId = :accountId AND folderName = :folderName " +
            "ORDER BY COALESCE(sentDateEpochMillis, receivedDateEpochMillis) DESC LIMIT :limit",
    )
    suspend fun getRecent(accountId: String, folderName: String, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :id")
    suspend fun markRead(id: String, isRead: Boolean)

    @Query("SELECT $MESSAGE_COLUMNS_NO_BODY FROM messages WHERE accountId = :accountId AND folderName = :folderName AND isRead = 0")
    suspend fun getUnread(accountId: String, folderName: String): List<MessageEntity>

    /** Unread inbox count per account — drives the drawer's per-account and Combined view badges. */
    @Query(
        "SELECT accountId, COUNT(*) as unreadCount FROM messages " +
            "WHERE folderName = :folderName AND isRead = 0 GROUP BY accountId",
    )
    fun observeUnreadCountsByAccount(folderName: String): Flow<List<AccountUnreadCount>>

    @Query("UPDATE messages SET isFlagged = :isFlagged WHERE id = :id")
    suspend fun markFlagged(id: String, isFlagged: Boolean)

    @Query("UPDATE messages SET isAnswered = :isAnswered WHERE id = :id")
    suspend fun markAnswered(id: String, isAnswered: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    /** Same WHERE clause as [deleteMissing] — call first to know which messages' attachment rows/cached files need cleaning up before they're gone. */
    @Query("SELECT id FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid NOT IN (:presentUids)")
    suspend fun getIdsMissing(accountId: String, folderName: String, presentUids: List<Long>): List<String>

    /** Prunes local rows for messages no longer present on the server (deleted/moved elsewhere). */
    @Query("DELETE FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid NOT IN (:presentUids)")
    suspend fun deleteMissing(accountId: String, folderName: String, presentUids: List<Long>)

    // The bare `messages.*` columns are pulled from whichever row produced MAX(...) within each
    // group — documented SQLite behavior for a single min()/max() aggregate query, not something
    // Room-specific. That's what makes "latest message per conversation" cheap here. [threaded]
    // false groups by the message's own (unique) id instead of its conversationId, so every
    // message becomes its own one-row "conversation" — see AppSettings.threadedConversations.
    @Query(
        "SELECT $CONVERSATION_COLUMNS FROM messages " +
            "WHERE messages.accountId = :accountId AND messages.folderName = :folderName " +
            "GROUP BY CASE WHEN :threaded = 1 THEN messages.conversationId ELSE messages.id END " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun observeConversations(accountId: String, folderName: String, threaded: Boolean): Flow<List<ConversationSummary>>

    /** Every message in one thread, oldest first — for the thread-expansion screen. */
    @Query(
        "SELECT $MESSAGE_COLUMNS_NO_BODY FROM messages WHERE accountId = :accountId AND folderName = :folderName " +
            "AND conversationId = :conversationId " +
            "ORDER BY COALESCE(sentDateEpochMillis, receivedDateEpochMillis) ASC",
    )
    fun observeConversationMessages(accountId: String, folderName: String, conversationId: String): Flow<List<MessageEntity>>

    /** One-shot form of [observeConversationMessages] — what a whole-thread swipe or bulk action acts on. */
    @Query(
        "SELECT $MESSAGE_COLUMNS_NO_BODY FROM messages WHERE accountId = :accountId AND folderName = :folderName " +
            "AND conversationId = :conversationId " +
            "ORDER BY COALESCE(sentDateEpochMillis, receivedDateEpochMillis) ASC",
    )
    suspend fun getConversationMessages(accountId: String, folderName: String, conversationId: String): List<MessageEntity>

    @Query(
        "SELECT $CONVERSATION_COLUMNS FROM messages " +
            "INNER JOIN accounts ON messages.accountId = accounts.id " +
            "WHERE messages.folderName = :folderName AND accounts.isActive = 1 " +
            "GROUP BY CASE WHEN :threaded = 1 THEN messages.conversationId ELSE messages.id END " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun observeUnifiedConversations(folderName: String, threaded: Boolean): Flow<List<ConversationSummary>>

    // hasAttachmentOnly/unreadOnly/flaggedOnly/fromQuery use the "flag = 0 OR condition" pattern
    // to make each filter optional from a single query rather than needing a family of
    // near-duplicate @Query methods.
    @Query(
        "SELECT $CONVERSATION_COLUMNS FROM messages " +
            "INNER JOIN accounts ON messages.accountId = accounts.id " +
            "WHERE messages.folderName = :folderName AND accounts.isActive = 1 " +
            "AND (:query = '' OR messages.subject LIKE '%' || :query || '%' " +
            "  OR messages.fromAddress LIKE '%' || :query || '%' " +
            "  OR messages.fromPersonal LIKE '%' || :query || '%' " +
            "  OR messages.bodyText LIKE '%' || :query || '%') " +
            "AND (:hasAttachmentOnly = 0 OR messages.hasAttachments = 1) " +
            "AND (:unreadOnly = 0 OR messages.isRead = 0) " +
            "AND (:flaggedOnly = 0 OR messages.isFlagged = 1) " +
            "AND (:fromQuery = '' OR messages.fromAddress LIKE '%' || :fromQuery || '%' OR messages.fromPersonal LIKE '%' || :fromQuery || '%') " +
            "GROUP BY CASE WHEN :threaded = 1 THEN messages.conversationId ELSE messages.id END " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun searchUnified(
        folderName: String,
        query: String,
        hasAttachmentOnly: Boolean,
        unreadOnly: Boolean,
        flaggedOnly: Boolean,
        fromQuery: String,
        threaded: Boolean,
    ): Flow<List<ConversationSummary>>

    /** Same filter shape as [searchUnified], scoped to one account/folder instead of every active account's given folder. */
    @Query(
        "SELECT $CONVERSATION_COLUMNS FROM messages " +
            "WHERE messages.accountId = :accountId AND messages.folderName = :folderName " +
            "AND (:query = '' OR messages.subject LIKE '%' || :query || '%' " +
            "  OR messages.fromAddress LIKE '%' || :query || '%' " +
            "  OR messages.fromPersonal LIKE '%' || :query || '%' " +
            "  OR messages.bodyText LIKE '%' || :query || '%') " +
            "AND (:hasAttachmentOnly = 0 OR messages.hasAttachments = 1) " +
            "AND (:unreadOnly = 0 OR messages.isRead = 0) " +
            "AND (:flaggedOnly = 0 OR messages.isFlagged = 1) " +
            "GROUP BY CASE WHEN :threaded = 1 THEN messages.conversationId ELSE messages.id END " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun searchInFolder(
        accountId: String,
        folderName: String,
        query: String,
        hasAttachmentOnly: Boolean,
        unreadOnly: Boolean,
        flaggedOnly: Boolean,
        threaded: Boolean,
    ): Flow<List<ConversationSummary>>
}
