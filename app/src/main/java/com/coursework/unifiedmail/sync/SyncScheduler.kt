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
    private const val SYNC_INTERVAL_MINUTES = 15L

    // Foreground/user-triggered inbox syncs (e.g. the Inbox screen's refresh button) call
    // MailRepository.syncAccount() directly instead of going through WorkManager, since that
    // gives the ViewModel a plain suspend-function result to drive UI state from. WorkManager is
    // used here for the unattended periodic schedule.
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
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
