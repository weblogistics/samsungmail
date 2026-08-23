package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun getById(id: String): DraftEntity?

    @Query("SELECT * FROM drafts ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)
}
