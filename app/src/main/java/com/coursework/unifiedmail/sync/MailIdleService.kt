package com.coursework.unifiedmail.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.remote.MailSessionFactory
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SyncOutcome
import com.sun.mail.imap.IMAPFolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent

/**
 * Foreground service that keeps one persistent IMAP IDLE connection open per account for
 * "On arrival" sync, instead of relying on WorkManager's minimum ~15-minute periodic floor.
 * Push is a single global on/off for every account (not per-account) — a deliberate scope cut
 * for this pass. The 15-minute WorkManager periodic sync (see [SyncScheduler]) stays enabled
 * as a backstop even while this runs, since a dropped IDLE connection can go unnoticed.
 *
 * Unverified beyond compilation/code review: no device or emulator is available in this
 * environment to soak-test a long-lived IMAP connection against a real server.
 */
@AndroidEntryPoint
class MailIdleService : Service() {

    @Inject lateinit var accountRepository: AccountRepository

    @Inject lateinit var mailRepository: MailRepository

    @Inject lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createIdleServiceChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, notificationHelper.buildIdleServiceNotification())
        serviceScope.launch {
            val accounts = accountRepository.getAllAccountsOnce()
            accounts.map { account -> launch { runIdleLoop(account) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Connects, issues IMAP IDLE in a loop, and syncs whenever the server reports new mail.
     * Reconnects with exponential backoff on any failure. Rather than trying to interrupt a
     * blocking idle() call precisely at the 25-minute mark, a watchdog just force-closes the
     * store — that unblocks idle() with an IOException, which the catch block below turns into
     * a clean reconnect. Simpler than fighting JavaMail's blocking API, and the outcome (a fresh
     * connection before servers typically drop IDLE around the RFC 2177-suggested 29 minutes) is
     * the same either way.
     */
    private suspend fun CoroutineScope.runIdleLoop(account: AccountEntity) {
        var backoffMs = INITIAL_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            var store: Store? = null
            var watchdog: Job? = null
            try {
                val password = accountRepository.getPassword(account.id)
                    ?: error("Missing saved credentials for ${account.displayName}")

                val session = Session.getInstance(MailSessionFactory.imapProperties(account.imapSecurity))
                store = session.getStore(MailSessionFactory.imapProtocol(account.imapSecurity))
                store.connect(account.imapHost, account.imapPort, account.username, password)

                val folder = store.getFolder("INBOX") as IMAPFolder
                folder.open(Folder.READ_ONLY)

                var newMailSeen = false
                folder.addMessageCountListener(object : MessageCountAdapter() {
                    override fun messagesAdded(event: MessageCountEvent) {
                        newMailSeen = true
                    }
                })

                backoffMs = INITIAL_BACKOFF_MS

                val storeToClose = store
                watchdog = launch {
                    delay(IDLE_CYCLE_MS)
                    runCatching { storeToClose.close() }
                }

                val cycleDeadline = System.currentTimeMillis() + IDLE_CYCLE_MS
                while (currentCoroutineContext().isActive && System.currentTimeMillis() < cycleDeadline) {
                    folder.idle()
                    if (newMailSeen) {
                        newMailSeen = false
                        val outcome = mailRepository.syncFolder(account.id, MailRepository.INBOX_FOLDER_KEY)
                        if (outcome is SyncOutcome.Success && outcome.newMessageCount > 0 && account.notificationsEnabled) {
                            notificationHelper.showNewMailNotification(account.id, account.displayName, outcome.newMessageCount)
                        }
                    }
                }
            } catch (e: Exception) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            } finally {
                watchdog?.cancel()
                runCatching { store?.close() }
            }
        }
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val IDLE_CYCLE_MS = 25 * 60 * 1000L
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MailIdleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MailIdleService::class.java))
        }
    }
}
