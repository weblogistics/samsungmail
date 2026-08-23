package com.coursework.unifiedmail.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.coursework.unifiedmail.data.repository.MailRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the "Mark all read" action button on a new-mail notification — see NotificationHelper. */
@AndroidEntryPoint
class MarkAllReadReceiver : BroadcastReceiver() {

    @Inject
    lateinit var mailRepository: MailRepository

    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mailRepository.markAllReadInFolder(accountId, MailRepository.INBOX_FOLDER_KEY)
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
