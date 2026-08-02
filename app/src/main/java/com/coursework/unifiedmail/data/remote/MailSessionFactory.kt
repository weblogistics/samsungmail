package com.coursework.unifiedmail.data.remote

import com.coursework.unifiedmail.data.local.MailSecurity
import java.util.Properties

/**
 * Builds the JavaMail Properties/protocol-name pairs shared by connection testing, IMAP sync,
 * and (eventually) SMTP sending, so all three agree on timeouts and TLS handling.
 */
internal object MailSessionFactory {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    fun imapProtocol(security: MailSecurity): String =
        if (security == MailSecurity.SSL_TLS) "imaps" else "imap"

    fun smtpProtocol(security: MailSecurity): String =
        if (security == MailSecurity.SSL_TLS) "smtps" else "smtp"

    fun imapProperties(security: MailSecurity): Properties = Properties().apply {
        put("mail.store.protocol", imapProtocol(security))
        put("mail.imap.starttls.enable", (security == MailSecurity.STARTTLS).toString())
        put("mail.imaps.ssl.trust", "*")
        put("mail.imap.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.imap.timeout", READ_TIMEOUT_MS.toString())
        put("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.imaps.timeout", READ_TIMEOUT_MS.toString())
    }

    fun smtpProperties(security: MailSecurity): Properties = Properties().apply {
        put("mail.transport.protocol", smtpProtocol(security))
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", (security == MailSecurity.STARTTLS).toString())
        put("mail.smtps.ssl.trust", "*")
        put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.smtp.timeout", READ_TIMEOUT_MS.toString())
        put("mail.smtps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.smtps.timeout", READ_TIMEOUT_MS.toString())
    }
}
