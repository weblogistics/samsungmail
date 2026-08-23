package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FolderDao {
    // IGNORE, not REPLACE: refreshing the folder list must not clobber lastSyncedUid on
    // folders that already have sync progress.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(folders: List<FolderEntity>)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun getForAccount(accountId: String): List<FolderEntity>

    @Query("UPDATE folders SET lastSyncedUid = :uid WHERE id = :id")
    suspend fun updateLastSyncedUid(id: String, uid: Long)

    /** Resets every cached folder's sync watermark for an account so the next sync of each one is a full re-fetch — see MailRepository.resetSyncProgress. */
    @Query("UPDATE folders SET lastSyncedUid = 0 WHERE accountId = :accountId")
    suspend fun resetLastSyncedUid(accountId: String)

    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}
