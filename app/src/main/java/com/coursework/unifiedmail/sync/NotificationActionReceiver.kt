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

/**
 * Handles the per-message "Archive"/"Trash" quick actions on a new-mail notification — see
 * NotificationHelper. One receiver for both (branching on [EXTRA_ACTION]) rather than two,
 * since they're otherwise identical: move the one message, then dismiss its notification.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var mailRepository: MailRepository

    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        val folderKey = intent.getStringExtra(EXTRA_FOLDER_KEY) ?: return
        val uid = intent.getLongExtra(EXTRA_UID, -1L).takeIf { it >= 0 } ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Best-effort, same as MarkAllReadReceiver — there's no UI reachable from a
                // background receiver to surface a failure to, and a failed move here is no
                // worse than one triggered from the inbox's own swipe action already is.
                when (action) {
                    ACTION_ARCHIVE -> mailRepository.moveMessageToArchive(accountId, folderKey, uid)
                    ACTION_TRASH -> mailRepository.moveMessageToTrash(accountId, folderKey, uid)
                }
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
        const val EXTRA_FOLDER_KEY = "folder_key"
        const val EXTRA_UID = "uid"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val ACTION_ARCHIVE = "archive"
        const val ACTION_TRASH = "trash"
    }
}
