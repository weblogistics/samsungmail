package com.coursework.unifiedmail.data.remote

import com.coursework.unifiedmail.data.local.MailSecurity

/**
 * Standard well-known ports for each security type, plus a small set of hardcoded host guesses
 * for major providers whose IMAP/SMTP hosts don't follow any guessable pattern (Gmail, Outlook,
 * Yahoo, iCloud, AOL). This is not a real autoconfig protocol (Mozilla ISPDB/Thunderbird
 * autoconfig would need a network round trip this app doesn't otherwise make just for account
 * setup) — just a best-effort starting point the user's own Test Connection step still verifies.
 */
object MailServerDefaults {
    fun imapPort(security: MailSecurity): Int = when (security) {
        MailSecurity.SSL_TLS -> 993
        MailSecurity.STARTTLS -> 143
        MailSecurity.NONE -> 143
    }

    fun smtpPort(security: MailSecurity): Int = when (security) {
        MailSecurity.SSL_TLS -> 465
        MailSecurity.STARTTLS -> 587
        MailSecurity.NONE -> 25
    }

    data class ServerGuess(
        val imapHost: String,
        val imapPort: Int,
        val imapSecurity: MailSecurity,
        val smtpHost: String,
        val smtpPort: Int,
        val smtpSecurity: MailSecurity,
    )

    private val knownProviders: Map<String, ServerGuess> = mapOf(
        "gmail.com" to ServerGuess("imap.gmail.com", 993, MailSecurity.SSL_TLS, "smtp.gmail.com", 465, MailSecurity.SSL_TLS),
        "googlemail.com" to ServerGuess("imap.gmail.com", 993, MailSecurity.SSL_TLS, "smtp.gmail.com", 465, MailSecurity.SSL_TLS),
        "outlook.com" to ServerGuess("outlook.office365.com", 993, MailSecurity.SSL_TLS, "smtp.office365.com", 587, MailSecurity.STARTTLS),
        "hotmail.com" to ServerGuess("outlook.office365.com", 993, MailSecurity.SSL_TLS, "smtp.office365.com", 587, MailSecurity.STARTTLS),
        "live.com" to ServerGuess("outlook.office365.com", 993, MailSecurity.SSL_TLS, "smtp.office365.com", 587, MailSecurity.STARTTLS),
        "msn.com" to ServerGuess("outlook.office365.com", 993, MailSecurity.SSL_TLS, "smtp.office365.com", 587, MailSecurity.STARTTLS),
        "yahoo.com" to ServerGuess("imap.mail.yahoo.com", 993, MailSecurity.SSL_TLS, "smtp.mail.yahoo.com", 465, MailSecurity.SSL_TLS),
        "yahoo.co.uk" to ServerGuess("imap.mail.yahoo.com", 993, MailSecurity.SSL_TLS, "smtp.mail.yahoo.com", 465, MailSecurity.SSL_TLS),
        "icloud.com" to ServerGuess("imap.mail.me.com", 993, MailSecurity.SSL_TLS, "smtp.mail.me.com", 587, MailSecurity.STARTTLS),
        "me.com" to ServerGuess("imap.mail.me.com", 993, MailSecurity.SSL_TLS, "smtp.mail.me.com", 587, MailSecurity.STARTTLS),
        "mac.com" to ServerGuess("imap.mail.me.com", 993, MailSecurity.SSL_TLS, "smtp.mail.me.com", 587, MailSecurity.STARTTLS),
        "aol.com" to ServerGuess("imap.aol.com", 993, MailSecurity.SSL_TLS, "smtp.aol.com", 465, MailSecurity.SSL_TLS),
    )

    /**
     * Exact match for a known provider, else a generic imap.<domain>/smtp.<domain> guess — works
     * for a lot of smaller/business domains but isn't guaranteed, unlike the hardcoded entries.
     */
    fun guessForEmail(emailAddress: String): ServerGuess? {
        val domain = emailAddress.substringAfter('@', missingDelimiterValue = "").trim().lowercase()
        if (domain.isEmpty() || !domain.contains('.')) return null
        knownProviders[domain]?.let { return it }
        return ServerGuess(
            imapHost = "imap.$domain",
            imapPort = 993,
            imapSecurity = MailSecurity.SSL_TLS,
            smtpHost = "smtp.$domain",
            smtpPort = 587,
            smtpSecurity = MailSecurity.STARTTLS,
        )
    }
}
