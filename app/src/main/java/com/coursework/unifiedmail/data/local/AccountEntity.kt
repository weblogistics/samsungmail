package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MailSecurity {
    NONE,
    SSL_TLS,
    STARTTLS,
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val emailAddress: String,
    val username: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: MailSecurity,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: MailSecurity,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
)
