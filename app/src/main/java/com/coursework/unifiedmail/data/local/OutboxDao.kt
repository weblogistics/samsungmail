package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(item: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun getById(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMillis ASC")
    suspend fun getUnsent(): List<OutboxEntity>

    @Query("UPDATE outbox SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: OutboxStatus, errorMessage: String?)
}
