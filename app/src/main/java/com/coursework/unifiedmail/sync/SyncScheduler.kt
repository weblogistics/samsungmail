package com.coursework.unifiedmail.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "periodic_mail_sync"
    private const val SEND_OUTBOX_WORK_NAME = "send_outbox"
    const val DEFAULT_SYNC_INTERVAL_MINUTES = 15L

    // Foreground/user-triggered inbox syncs (e.g. the Inbox screen's refresh button) call
    // MailRepository.syncAccount() directly instead of going through WorkManager, since that
    // gives the ViewModel a plain suspend-function result to drive UI state from. WorkManager is
    // used here for the unattended periodic schedule.
    //
    // UPDATE (not KEEP): this is re-invoked whenever the user changes the sync interval setting,
    // and the new interval needs to actually take effect rather than being ignored because a
    // periodic work request with this unique name already exists.
    fun schedulePeriodicSync(context: Context, intervalMinutes: Long = DEFAULT_SYNC_INTERVAL_MINUTES) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    // Sending goes through WorkManager (unlike sync) so a message composed while offline isn't
    // lost: the network constraint holds the job until connectivity returns, even across process
    // death, without the compose screen needing to watch for connectivity itself.
    fun enqueueOutboxSend(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SendOutboxWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SEND_OUTBOX_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
