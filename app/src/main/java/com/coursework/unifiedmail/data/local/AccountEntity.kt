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
    val notificationsEnabled: Boolean = true,
    // Server folder fullName (not the local key) — null falls back to MailRepository's
    // name-based guessing (e.g. a folder named "Trash"/"Deleted"). Lets an account whose server
    // uses a non-obvious folder name (or has both a "Trash" and a "Deleted Items") be configured
    // explicitly instead.
    val trashFolderFullName: String? = null,
    val archiveFolderFullName: String? = null,
    val spamFolderFullName: String? = null,
    val signature: String? = null,
    val createdAtEpochMillis: Long,
)
