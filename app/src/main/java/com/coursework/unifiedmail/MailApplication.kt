package com.coursework.unifiedmail

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.sync.MailIdleService
import com.coursework.unifiedmail.sync.NotificationHelper
import com.coursework.unifiedmail.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class MailApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        // A single fast local DataStore read at startup — acceptable to block briefly on here,
        // there's no reasonable async alternative for "what interval should the very first
        // periodic work request use" this early in the process lifecycle.
        val settings = runBlocking { settingsRepository.settings.first() }
        // WorkManager can't schedule a 0-minute period, and it stays enabled as a backstop even
        // when push is active — so "on arrival" still gets a real (default-interval) polling
        // fallback underneath it, not just the IDLE connection alone.
        val workManagerInterval = if (settings.isPushSync) SyncScheduler.DEFAULT_SYNC_INTERVAL_MINUTES else settings.syncIntervalMinutes
        SyncScheduler.schedulePeriodicSync(this, workManagerInterval)
        if (settings.isPushSync) {
            MailIdleService.start(this)
        }
    }
}
