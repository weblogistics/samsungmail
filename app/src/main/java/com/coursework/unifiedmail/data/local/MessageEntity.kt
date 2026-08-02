package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["accountId", "folderName"]), Index(value = ["conversationId"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val folderName: String,
    val uid: Long,
    val messageIdHeader: String?,
    // See ConversationThreading — derived from References/In-Reply-To/subject at sync time.
    val conversationId: String,
    val subject: String?,
    val fromAddress: String?,
    val fromPersonal: String?,
    val toAddresses: String?,
    val sentDateEpochMillis: Long?,
    val receivedDateEpochMillis: Long?,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val hasAttachments: Boolean,
    val bodyPreview: String,
    val bodyText: String?,
)
