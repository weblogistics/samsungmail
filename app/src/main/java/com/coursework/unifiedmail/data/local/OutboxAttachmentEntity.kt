package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_attachments", indices = [Index(value = ["outboxId"])])
data class OutboxAttachmentEntity(
    @PrimaryKey val id: String,
    val outboxId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    // A private copy of the picked file's bytes (see AttachmentStorage.copyForOutbox) made at
    // attach time — the content:// Uri from another app's picker isn't guaranteed to stay
    // readable by the time SendOutboxWorker actually runs (e.g. after the app restarts).
    val localFilePath: String,
)
