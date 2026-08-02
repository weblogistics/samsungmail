package com.coursework.unifiedmail.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

data class OutgoingMessage(
    val fromAddress: String,
    val fromPersonal: String?,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val inReplyTo: String? = null,
    val references: String? = null,
)

class SmtpSender @Inject constructor() {

    suspend fun send(config: SmtpConfig, message: OutgoingMessage): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.smtpProperties(config.security))
            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(message.fromAddress, message.fromPersonal))
                message.to.forEach { addRecipient(Message.RecipientType.TO, InternetAddress(it)) }
                message.cc.forEach { addRecipient(Message.RecipientType.CC, InternetAddress(it)) }
                message.bcc.forEach { addRecipient(Message.RecipientType.BCC, InternetAddress(it)) }
                subject = message.subject
                setText(message.body, "UTF-8")
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
}
