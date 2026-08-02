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
}
