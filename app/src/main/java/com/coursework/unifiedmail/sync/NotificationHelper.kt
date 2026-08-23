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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
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

    /** No-ops if the POST_NOTIFICATIONS permission was never granted (not requested, or denied). */
    fun showNewMailNotification(accountId: String, accountDisplayName: String, newMessageCount: Int) {
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

        val contentText = if (newMessageCount == 1) "1 new message" else "$newMessageCount new messages"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(accountDisplayName)
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Mark all read", markReadPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private companion object {
        const val CHANNEL_ID = "new_mail"
        const val IDLE_CHANNEL_ID = "mail_idle_push"
    }
}
