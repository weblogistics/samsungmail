package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY indexInMessage ASC")
    suspend fun getForMessage(messageId: String): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)

    // messageId is composed as "accountId|folderKey|uid" (see MailRepository.messageId) — no
    // accountId column of its own on this table, so a prefix match is how account-scoped cleanup
    // finds its rows.
    @Query("DELETE FROM attachments WHERE messageId LIKE :accountId || '|%'")
    suspend fun deleteForAccount(accountId: String)
}
