package com.coursework.unifiedmail.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.coursework.unifiedmail.data.repository.MailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Triggered immediately after a message is queued for sending; see [SyncScheduler.enqueueOutboxSend]. */
@HiltWorker
class SendOutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mailRepository: MailRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (mailRepository.flushOutbox()) Result.success() else Result.retry()
}
