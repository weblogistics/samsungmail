package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val fullName: String,
    val displayName: String,
    // High-water mark for incremental IMAP sync: only messages with UID > this are re-fetched.
    val lastSyncedUid: Long = 0,
    // Server folder fullName of this folder's parent, derived from the IMAP hierarchy
    // delimiter at sync time — null means top-level (no parent). See domain.folders.FolderTree.
    val parentFullName: String? = null,
)
