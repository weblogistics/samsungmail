package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OutboxStatus {
    PENDING,
    SENT,
    FAILED,
}

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val toAddresses: String,
    val ccAddresses: String?,
    val bccAddresses: String?,
    val subject: String,
    val body: String,
    // The rich-text HTML sibling of `body` — null sends plain-text-only (defensive fallback;
    // not expected once compose always populates it). See SmtpSenderImpl.
    val bodyHtml: String? = null,
    val inReplyToMessageIdHeader: String?,
    val referencesHeader: String?,
    val status: OutboxStatus,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
)
