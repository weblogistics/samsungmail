package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "attachments", indices = [Index(value = ["messageId"])])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    // Position among attachments found during the sync-time body walk (ImapClientImpl.parseBody)
    // — used to relocate the same part when downloading, since bytes aren't cached at sync time.
    // No formal Room ForeignKey to messages — same lightweight string-id convention used
    // elsewhere in this schema (FolderEntity/MessageEntity composite ids).
    val indexInMessage: Int,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)
