package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxAttachmentDao {
    @Insert
    suspend fun insertAll(attachments: List<OutboxAttachmentEntity>)

    @Query("SELECT * FROM outbox_attachments WHERE outboxId = :outboxId")
    suspend fun getForOutbox(outboxId: String): List<OutboxAttachmentEntity>

    @Query("DELETE FROM outbox_attachments WHERE outboxId = :outboxId")
    suspend fun deleteForOutbox(outboxId: String)
}
