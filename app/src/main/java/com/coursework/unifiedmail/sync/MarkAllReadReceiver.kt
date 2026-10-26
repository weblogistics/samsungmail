package com.coursework.unifiedmail.sync

import android.app.NotificationManager
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
                // NotificationHelper now posts one notification per message (grouped under this
                // summary), not one for the whole account — "mark all read" needs to dismiss
                // every one of them, not just the summary notificationId above, or they'd sit in
                // the shade claiming to be about messages that are no longer unread.
                cancelGroupMembers(context, accountId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun cancelGroupMembers(context: Context, accountId: String) {
        val groupKey = "account_$accountId"
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        for (statusBarNotification in notificationManager.activeNotifications) {
            if (statusBarNotification.notification.group == groupKey) {
                notificationManager.cancel(statusBarNotification.id)
            }
        }
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
