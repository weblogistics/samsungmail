package com.coursework.unifiedmail.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SyncOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accounts = accountRepository.getAllAccountsOnce()

        // Concurrent per account: one slow/unreachable server shouldn't hold up every other
        // account's sync (same reasoning as UnifiedInboxViewModel.sync()).
        val anyFailure = coroutineScope {
            accounts
                .map { account -> async { account to mailRepository.syncAccount(account.id) } }
                .awaitAll()
                .also { results ->
                    // Only the unattended periodic path notifies — a foreground manual refresh
                    // means the user is already looking at the list.
                    for ((account, result) in results) {
                        if (result is SyncOutcome.Success && result.newMessageCount > 0 && account.notificationsEnabled) {
                            notificationHelper.showNewMailNotification(account.id, account.displayName, result.newMessageCount)
                        }
                    }
                }
                .any { (_, result) -> result is SyncOutcome.Failure }
        }

        // Backstop for outbox items queued while the app was killed before their own
        // network-constrained send request could be enqueued (see SendOutboxWorker).
        val outboxOk = mailRepository.flushOutbox()

        return if (anyFailure || !outboxOk) Result.retry() else Result.success()
    }
}
