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

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accounts = accountRepository.getAllAccountsOnce()
        var anyFailure = false
        for (account in accounts) {
            if (mailRepository.syncAccount(account.id) is SyncOutcome.Failure) {
                anyFailure = true
            }
        }
        // Backstop for outbox items queued while the app was killed before their own
        // network-constrained send request could be enqueued (see SendOutboxWorker).
        if (!mailRepository.flushOutbox()) {
            anyFailure = true
        }
        return if (anyFailure) Result.retry() else Result.success()
    }
}
