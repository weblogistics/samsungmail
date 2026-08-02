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

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND folderName = :folderName AND uid = :uid")
    suspend fun getByUid(accountId: String, folderName: String, uid: Long): MessageEntity?

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :id")
    suspend fun markRead(id: String, isRead: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    // The bare `messages.*` columns are pulled from whichever row produced MAX(...) within each
    // conversationId group — documented SQLite behavior for a single min()/max() aggregate query,
    // not something Room-specific. That's what makes "latest message per conversation" cheap here.
    @Query(
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
            "WHERE messages.accountId = :accountId AND messages.folderName = :folderName " +
            "GROUP BY messages.conversationId " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun observeConversations(accountId: String, folderName: String): Flow<List<ConversationSummary>>

    @Query(
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
            "INNER JOIN accounts ON messages.accountId = accounts.id " +
            "WHERE messages.folderName = :folderName AND accounts.isActive = 1 " +
            "GROUP BY messages.conversationId " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun observeUnifiedConversations(folderName: String): Flow<List<ConversationSummary>>

    @Query(
        "SELECT messages.*, COUNT(*) as messageCount FROM messages " +
            "INNER JOIN accounts ON messages.accountId = accounts.id " +
            "WHERE messages.folderName = :folderName AND accounts.isActive = 1 " +
            "AND (messages.subject LIKE '%' || :query || '%' " +
            "  OR messages.fromAddress LIKE '%' || :query || '%' " +
            "  OR messages.fromPersonal LIKE '%' || :query || '%' " +
            "  OR messages.bodyText LIKE '%' || :query || '%') " +
            "GROUP BY messages.conversationId " +
            "ORDER BY MAX(COALESCE(messages.sentDateEpochMillis, messages.receivedDateEpochMillis)) DESC",
    )
    fun searchUnified(folderName: String, query: String): Flow<List<ConversationSummary>>
}
