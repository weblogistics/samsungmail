package com.coursework.unifiedmail.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.coursework.unifiedmail.MainActivity
import com.coursework.unifiedmail.R
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.settings.AppSettingsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsProvider: AppSettingsProvider,
) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "New mail", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun createIdleServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(IDLE_CHANNEL_ID, "Mail push connection", NotificationManager.IMPORTANCE_MIN)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Required ongoing notification for the "On arrival" push foreground service. */
    fun buildIdleServiceNotification(): Notification =
        NotificationCompat.Builder(context, IDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Listening for new mail")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    /**
     * Posts one notification per message in [newMessages] (each independently dismissible, and
     * tappable straight to that message), plus a group summary so they still read as "3 new in
     * Work Account" when collapsed in the shade rather than flooding it with unrelated-looking
     * entries. No-ops if the POST_NOTIFICATIONS permission was never granted (not requested, or
     * denied), or if [newMessages] is empty.
     *
     * Previously this posted a single notification per *account*, re-`notify()`-ing the same id
     * (and re-alerting — sound/vibration again) for every subsequent message, which also silently
     * replaced whatever the last notify() showed: a second email arriving overwrote the first's
     * notification entirely rather than adding to it, so the first was never actually seen. Each
     * message getting its own stable id (and never being re-notified once posted, since a delta
     * sync only ever reports a given message as "new" exactly once) fixes both at once.
     */
    suspend fun showNewMailNotification(accountId: String, accountDisplayName: String, newMessages: List<MessageEntity>) {
        if (newMessages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val showPreview = settingsProvider.currentSettings().showNotificationPreview
        val groupKey = "account_$accountId"
        for (message in newMessages) {
            showMessageNotification(accountId, accountDisplayName, message, showPreview, groupKey)
        }
        showGroupSummary(accountId, accountDisplayName, groupKey)
    }

    private fun showMessageNotification(
        accountId: String,
        accountDisplayName: String,
        message: MessageEntity,
        showPreview: Boolean,
        groupKey: String,
    ) {
        // Stable per message (not per account) — see the class doc above for why that matters.
        val notificationId = message.id.hashCode()

        // A distinct Intent.action per MainActivity-targeting PendingIntent below (not just
        // different extras) — two PendingIntents sharing a requestCode and component are the
        // same PendingIntent as far as Android's concerned unless their action/data/categories
        // differ too, so without this, tapping the notification body and tapping "Reply" would
        // collapse into whichever one was created last.
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = "open:${message.id}"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_DEEPLINK_ACCOUNT_ID, accountId)
            putExtra(MainActivity.EXTRA_DEEPLINK_FOLDER_KEY, message.folderName)
            putExtra(MainActivity.EXTRA_DEEPLINK_UID, message.uid)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val replyIntent = Intent(context, MainActivity::class.java).apply {
            action = "reply:${message.id}"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_DEEPLINK_ACCOUNT_ID, accountId)
            putExtra(MainActivity.EXTRA_DEEPLINK_FOLDER_KEY, message.folderName)
            putExtra(MainActivity.EXTRA_DEEPLINK_UID, message.uid)
            putExtra(MainActivity.EXTRA_DEEPLINK_REPLY, true)
        }
        val replyPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setGroup(groupKey)
            .addAction(0, "Reply", replyPendingIntent)
            .addAction(0, "Archive", actionPendingIntent(NotificationActionReceiver.ACTION_ARCHIVE, accountId, message, notificationId))
            .addAction(0, "Trash", actionPendingIntent(NotificationActionReceiver.ACTION_TRASH, accountId, message, notificationId))

        if (showPreview) {
            val sender = message.fromPersonal?.takeIf { it.isNotBlank() } ?: message.fromAddress ?: accountDisplayName
            val subject = message.subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
            val snippet = message.bodyPreview.takeIf { it.isNotBlank() }
            builder.setContentTitle(sender).setContentText(subject)
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(sender)
                    .bigText(if (snippet != null) "$subject\n$snippet" else subject),
            )
        } else {
            builder.setContentTitle(accountDisplayName).setContentText("New message")
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun actionPendingIntent(action: String, accountId: String, message: MessageEntity, notificationId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            // Distinct Intent.action (not just an extra) so two PendingIntents for the same
            // message/notificationId — Archive vs Trash — don't collide and overwrite each
            // other's extras, which request-code reuse alone wouldn't prevent.
            this.action = "$action:${message.id}"
            putExtra(NotificationActionReceiver.EXTRA_ACCOUNT_ID, accountId)
            putExtra(NotificationActionReceiver.EXTRA_FOLDER_KEY, message.folderName)
            putExtra(NotificationActionReceiver.EXTRA_UID, message.uid)
            putExtra(NotificationActionReceiver.EXTRA_ACTION, action)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The collapsed "N new in Account" summary a group of [showMessageNotification] posts bundle under — see showNewMailNotification's doc. */
    private fun showGroupSummary(accountId: String, accountDisplayName: String, groupKey: String) {
        val notificationId = accountId.hashCode()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_DEEPLINK_ACCOUNT_ID, accountId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadIntent = Intent(context, MarkAllReadReceiver::class.java).apply {
            putExtra(MarkAllReadReceiver.EXTRA_ACCOUNT_ID, accountId)
            putExtra(MarkAllReadReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setContentTitle(accountDisplayName)
            .setContentText("New mail")
            .setGroup(groupKey)
            .setGroupSummary(true)
            // The summary gets re-posted (its count effectively) every time another message in
            // the same batch is added — it must never itself make noise doing that; each real
            // message already alerted once via its own, separate notification above.
            .setOnlyAlertOnce(true)
            .addAction(0, "Mark all read", markReadPendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private companion object {
        const val CHANNEL_ID = "new_mail"
        const val IDLE_CHANNEL_ID = "mail_idle_push"
    }
}
