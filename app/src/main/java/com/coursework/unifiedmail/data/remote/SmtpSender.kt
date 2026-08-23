package com.coursework.unifiedmail.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.activation.DataHandler
import javax.inject.Inject
import javax.mail.Message
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

data class OutgoingAttachment(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray,
)

data class OutgoingMessage(
    val fromAddress: String,
    val fromPersonal: String?,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    // The rich-text HTML sibling of `body` — when present, sent as multipart/alternative with
    // `body` as the required plain-text fallback part. Null sends plain-text-only.
    val bodyHtml: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
    val attachments: List<OutgoingAttachment> = emptyList(),
)

/** Behind an interface so MailRepository can be unit-tested against a fake instead of real SMTP. */
interface SmtpSender {
    suspend fun send(config: SmtpConfig, message: OutgoingMessage): Result<Unit>
}

class SmtpSenderImpl @Inject constructor() : SmtpSender {

    override suspend fun send(config: SmtpConfig, message: OutgoingMessage): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.smtpProperties(config.security))
            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(message.fromAddress, message.fromPersonal))
                message.to.forEach { addRecipient(Message.RecipientType.TO, InternetAddress(it)) }
                message.cc.forEach { addRecipient(Message.RecipientType.CC, InternetAddress(it)) }
                message.bcc.forEach { addRecipient(Message.RecipientType.BCC, InternetAddress(it)) }
                subject = message.subject
                if (message.attachments.isEmpty()) {
                    val textPart = buildTextPart(message.body, message.bodyHtml)
                    setContent(textPart.content, textPart.contentType)
                } else {
                    val mixed = MimeMultipart("mixed")
                    mixed.addBodyPart(buildTextPart(message.body, message.bodyHtml))
                    for (attachment in message.attachments) {
                        mixed.addBodyPart(
                            MimeBodyPart().apply {
                                dataHandler = DataHandler(
                                    ByteArrayDataSource(attachment.bytes, attachment.mimeType ?: "application/octet-stream"),
                                )
                                fileName = attachment.fileName
                                disposition = Part.ATTACHMENT
                            },
                        )
                    }
                    setContent(mixed)
                }
                sentDate = Date()
                message.inReplyTo?.let { setHeader("In-Reply-To", it) }
                message.references?.let { setHeader("References", it) }
            }

            val transport = session.getTransport(MailSessionFactory.smtpProtocol(config.security))
            transport.connect(config.host, config.port, config.username, config.password)
            try {
                transport.sendMessage(mimeMessage, mimeMessage.allRecipients)
            } finally {
                transport.close()
            }
        }
    }

    /** multipart/alternative(plain, html) when [html] is present, otherwise a plain text/plain part — the required plain-text fallback either way. */
    private fun buildTextPart(body: String, html: String?): MimeBodyPart = MimeBodyPart().apply {
        if (html != null) {
            val alternative = MimeMultipart("alternative")
            alternative.addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
            alternative.addBodyPart(MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") })
            setContent(alternative)
        } else {
            setText(body, "UTF-8")
        }
    }
}
