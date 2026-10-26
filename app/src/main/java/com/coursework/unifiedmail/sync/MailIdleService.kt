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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps one persistent IMAP IDLE connection open per account for
 * "On arrival" sync, instead of relying on WorkManager's minimum ~15-minute periodic floor.
 * Push is a single global on/off for every account (not per-account) — a deliberate scope cut
 * for this pass. The 15-minute WorkManager periodic sync (see [SyncScheduler]) stays enabled
 * as a backstop even while this runs, since a dropped IDLE connection can go unnoticed.
 *
 * Reacts to the account list live (see [reconcileAccountJobs]) rather than snapshotting it once
 * in [onCreate] — the previous one-shot fetch meant an account added after this service was
 * already running got no IDLE loop at all until the next full service restart (toggling the
 * setting off/on, or killing and relaunching the app), and a removed account's loop just kept
 * retrying with now-invalid credentials indefinitely.
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

    // One entry per account currently being idled. accountStores holds whichever Store a given
    // account's loop is *currently* connected through (replaced on every reconnect) so onDestroy
    // can force-close every live socket immediately — coroutine cancellation alone has no effect
    // on a thread blocked inside JavaMail's synchronous idle() call, so without this, stopping
    // the service could leave a connection (and a possible sync+notify cycle) alive for up to
    // IDLE_CYCLE_MS afterward, until the per-loop watchdog eventually catches up on its own.
    private val accountJobs = ConcurrentHashMap<String, Job>()
    private val accountStores = ConcurrentHashMap<String, Store>()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createIdleServiceChannel()
        startForeground(FOREGROUND_NOTIFICATION_ID, notificationHelper.buildIdleServiceNotification())
        serviceScope.launch {
            accountRepository.observeAccounts().collect { accounts -> reconcileAccountJobs(accounts) }
        }
    }

    /** Starts an IDLE loop for any account that doesn't have one yet, and stops one for any account that's gone. */
    private fun reconcileAccountJobs(accounts: List<AccountEntity>) {
        val currentIds = accounts.map { it.id }.toSet()
        for (removedId in accountJobs.keys - currentIds) {
            accountJobs.remove(removedId)?.cancel()
            accountStores.remove(removedId)?.let { runCatching { it.close() } }
        }
        for (account in accounts) {
            if (!accountJobs.containsKey(account.id)) {
                accountJobs[account.id] = serviceScope.launch { runIdleLoop(account) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        for (store in accountStores.values) {
            runCatching { store.close() }
        }
        accountStores.clear()
        accountJobs.clear()
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
                accountStores[account.id] = store

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
                            val recent = mailRepository.getRecentMessages(account.id, MailRepository.INBOX_FOLDER_KEY, outcome.newMessageCount)
                            notificationHelper.showNewMailNotification(account.id, account.displayName, recent)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Must propagate, not be treated as a retry-able connection failure below — this
                // is the service (or this one account) actually being told to stop.
                throw e
            } catch (e: Exception) {
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            } finally {
                watchdog?.cancel()
                runCatching { store?.close() }
                accountStores.remove(account.id, store)
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
