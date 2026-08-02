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
    val inReplyToMessageIdHeader: String?,
    val referencesHeader: String?,
    val status: OutboxStatus,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
)
