package com.coursework.unifiedmail.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coursework.unifiedmail.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts the "On arrival" push connection after a device reboot, since a foreground service
 * does not survive one on its own. RECEIVE_BOOT_COMPLETED has been declared in the manifest
 * since Milestone 1 but was unused until this.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isPushEnabled = settingsRepository.settings.first().isPushSync
                if (isPushEnabled) {
                    MailIdleService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
