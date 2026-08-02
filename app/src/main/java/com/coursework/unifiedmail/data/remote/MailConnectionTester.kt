package com.coursework.unifiedmail.data.remote

import com.coursework.unifiedmail.data.local.MailSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.mail.Session

sealed class MailTestResult {
    data object Success : MailTestResult()
    data class Failure(val reason: String) : MailTestResult()
}

data class ImapConfig(
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val username: String,
    val password: String,
)

data class SmtpConfig(
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val username: String,
    val password: String,
)

/**
 * Verifies IMAP/SMTP credentials before an account is saved, so setup failures surface
 * immediately (bad password, unreachable host, wrong TLS mode) instead of on the first sync.
 */
class MailConnectionTester @Inject constructor() {

    suspend fun testImapLogin(config: ImapConfig): MailTestResult = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.imapProperties(config.security))
            val store = session.getStore(MailSessionFactory.imapProtocol(config.security))
            store.connect(config.host, config.port, config.username, config.password)
            store.close()
        }.fold(
            onSuccess = { MailTestResult.Success },
            onFailure = { MailTestResult.Failure(describeMailError(it)) },
        )
    }

    suspend fun testSmtpLogin(config: SmtpConfig): MailTestResult = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(MailSessionFactory.smtpProperties(config.security))
            val transport = session.getTransport(MailSessionFactory.smtpProtocol(config.security))
            transport.connect(config.host, config.port, config.username, config.password)
            transport.close()
        }.fold(
            onSuccess = { MailTestResult.Success },
            onFailure = { MailTestResult.Failure(describeMailError(it)) },
        )
    }
}
