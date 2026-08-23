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
    val ccAddresses: String?,
    val sentDateEpochMillis: Long?,
    val receivedDateEpochMillis: Long?,
    val isRead: Boolean,
    val isFlagged: Boolean,
    val hasAttachments: Boolean,
    // Always a genuine text/plain part when the message has one — never HTML-stripped-to-text —
    // so the list preview and plain-text fallback view are both a clean read, not tag-soup.
    val bodyPreview: String,
    val bodyText: String?,
    // Raw HTML, untouched, for WebView rendering — null when the message has no HTML part.
    val bodyHtml: String?,
    // Server's \Answered flag — drives the reply-indicator icon on list rows. Defaults false so
    // existing call sites that construct a MessageEntity without it still compile.
    val isAnswered: Boolean = false,
)
