package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ConversationSummary(
    @Embedded val latestMessage: MessageEntity,
    val messageCount: Int,
)

data class AccountUnreadCount(val accountId: String, val unreadCount: Int)

/** Projection for MailRepository.getAutocompleteContacts — just the two columns actually addresses live in. */
data class AddressColumns(val toAddresses: String?, val ccAddresses: String?)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid = :uid")
    suspend fun getByUid(accountId: String, folderName: String, uid: Long): MessageEntity?

    /** Feeds MailRepository.getAutocompleteContacts — every To/Cc pair cached for one account's Sent folder. */
    @Query("SELECT toAddresses, ccAddresses FROM messages WHERE accountId = :accountId AND folderName = :folderName")
    suspend fun getAddressColumns(accountId: String, folderName: String): List<AddressColumns>

    /** Lowest cached UID in this folder — the boundary "load older mail" pages backward from. */
    @Query("SELECT MIN(uid) FROM messages WHERE accountId = :accountId AND folderName = :folderName")
    suspend fun getMinUid(accountId: String, folderName: String): Long?

    /** The [limit] most-recently-dated cached messages — see MailRepository.getRecentMessages, used to build a new-mail notification's preview. */
    @Query(
        "SELECT * FROM messages WHERE accountId = :accountId AND folderName = :folderName " +
            "ORDER BY COALESCE(sentDateEpochMillis, receivedDateEpochMillis) DESC LIMIT :limit",
    )
    suspend fun getRecent(accountId: String, folderName: String, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :id")
    suspend fun markRead(id: String, isRead: Boolean)

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND folderName = :folderName AND isRead = 0")
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

    /** Prunes local rows for messages no longer present on the server (deleted/moved elsewhere). */
    @Query("DELETE FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid NOT IN (:presentUids)")
    suspend fun deleteMissing(accountId: String, folderName: String, presentUids: List<Long>)

    // The bare `messages.*` columns are pulled from whichever row produced MAX(...) within each
    // group — documented SQLite behavior for a single min()/max() aggregate query, not something
    // Room-specific. That's what makes "latest message per conversation" cheap here. [threaded]
    // false groups by the message's own (unique) id instead of its conversationId, so every
    // message becomes its own one-row "conversation" — see AppSettings.threadedConversations.
    @Query(
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
            "WHERE messages.accountId = :accountId AND messages.folderName = :folderName " +
            "GROUP BY CASE WHEN :threaded = 1 THEN messages.conversationId ELSE messages.id END " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun observeConversations(accountId: String, folderName: String, threaded: Boolean): Flow<List<ConversationSummary>>

    /** Every message in one thread, oldest first — for the thread-expansion screen. */
    @Query(
        "SELECT * FROM messages WHERE accountId = :accountId AND folderName = :folderName " +
            "AND conversationId = :conversationId " +
            "ORDER BY COALESCE(sentDateEpochMillis, receivedDateEpochMillis) ASC",
    )
    fun observeConversationMessages(accountId: String, folderName: String, conversationId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
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
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
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
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
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
