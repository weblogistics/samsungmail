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
     * No-ops if the POST_NOTIFICATIONS permission was never granted (not requested, or denied),
     * or if [newMessages] is empty. [newMessages] drives the sender/subject/snippet preview when
     * AppSettings.showNotificationPreview is on (see [buildContent]) — off (or no message data
     * available) falls back to a bare "N new messages" count, same as before that setting existed.
     */
    suspend fun showNewMailNotification(accountId: String, accountDisplayName: String, newMessages: List<MessageEntity>) {
        if (newMessages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notificationId = accountId.hashCode()

        // Deep-links straight to this account's Inbox (see MainActivity/MailNavGraph) rather than
        // just opening the app to whatever it last showed — the point of a per-account
        // notification is knowing which mailbox to look at.
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

        val showPreview = settingsProvider.currentSettings().showNotificationPreview
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Mark all read", markReadPendingIntent)
        buildContent(builder, accountDisplayName, newMessages, showPreview)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /** Bare count, unless [showPreview] is on — then sender/subject (one message) or an inbox-style sender/subject list (several). */
    private fun buildContent(builder: NotificationCompat.Builder, accountDisplayName: String, newMessages: List<MessageEntity>, showPreview: Boolean) {
        val count = newMessages.size
        val countText = if (count == 1) "1 new message" else "$count new messages"

        if (!showPreview) {
            builder.setContentTitle(accountDisplayName).setContentText(countText)
            return
        }

        if (count == 1) {
            val message = newMessages.single()
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
            builder.setContentTitle(accountDisplayName).setContentText(countText)
            val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(accountDisplayName).setSummaryText(accountDisplayName)
            for (message in newMessages.take(MAX_INBOX_STYLE_LINES)) {
                val sender = message.fromPersonal?.takeIf { it.isNotBlank() } ?: message.fromAddress ?: "Unknown sender"
                val subject = message.subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
                inboxStyle.addLine("$sender: $subject")
            }
            builder.setStyle(inboxStyle)
        }
    }

    private companion object {
        const val CHANNEL_ID = "new_mail"
        const val IDLE_CHANNEL_ID = "mail_idle_push"
        // Matches the common "show a handful, then just the count" convention (Gmail does the
        // same) rather than growing the notification unboundedly for a big batch of new mail.
        const val MAX_INBOX_STYLE_LINES = 5
    }
}
