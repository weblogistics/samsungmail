package com.coursework.unifiedmail

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.coursework.unifiedmail.data.settings.SettingsRepository
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
        val intervalMinutes = runBlocking { settingsRepository.settings.first().syncIntervalMinutes }
        SyncScheduler.schedulePeriodicSync(this, intervalMinutes)
    }
}
