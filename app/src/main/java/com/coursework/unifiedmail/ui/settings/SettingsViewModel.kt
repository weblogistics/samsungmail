package com.coursework.unifiedmail.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.ListDensity
import com.coursework.unifiedmail.data.settings.MessageTextSize
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.data.settings.SwipeAction
import com.coursework.unifiedmail.data.settings.ThemeMode
import com.coursework.unifiedmail.sync.MailIdleService
import com.coursework.unifiedmail.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    accountRepository: AccountRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSwipeRightAction(action: SwipeAction) {
        viewModelScope.launch { settingsRepository.setSwipeRightAction(action) }
    }

    fun setSwipeLeftAction(action: SwipeAction) {
        viewModelScope.launch { settingsRepository.setSwipeLeftAction(action) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeDelete(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultViewAccountId(accountId: String?) {
        viewModelScope.launch { settingsRepository.setDefaultViewAccountId(accountId) }
    }

    fun setSyncWindowDays(days: Int) {
        viewModelScope.launch { settingsRepository.setSyncWindowDays(days) }
    }

    fun setListDensity(density: ListDensity) {
        viewModelScope.launch { settingsRepository.setListDensity(density) }
    }

    fun setMessageTextSize(size: MessageTextSize) {
        viewModelScope.launch { settingsRepository.setMessageTextSize(size) }
    }

    fun setDualPaneMinWidthDp(widthDp: Int) {
        viewModelScope.launch { settingsRepository.setDualPaneMinWidthDp(widthDp) }
    }

    fun setUndoDurationSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setUndoDurationSeconds(seconds) }
    }

    fun setThreadedConversations(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setThreadedConversations(enabled) }
    }

    fun setShowNotificationPreview(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowNotificationPreview(enabled) }
    }

    fun setSenderFirstInList(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSenderFirstInList(enabled) }
    }

    fun setSyncIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            settingsRepository.setSyncIntervalMinutes(minutes)
            // WorkManager can't schedule a 0-minute period — "on arrival" is handled by push
            // (see MailIdleService); the periodic worker still runs at the default interval as
            // a backstop even in that mode.
            val workManagerInterval = if (minutes == AppSettings.ON_ARRIVAL_MINUTES) {
                SyncScheduler.DEFAULT_SYNC_INTERVAL_MINUTES
            } else {
                minutes
            }
            SyncScheduler.schedulePeriodicSync(appContext, workManagerInterval)
            if (minutes == AppSettings.ON_ARRIVAL_MINUTES) {
                MailIdleService.start(appContext)
                requestIgnoreBatteryOptimizations()
            } else {
                MailIdleService.stop(appContext)
            }
        }
    }

    /**
     * "As they arrive" depends on a foreground service holding an IMAP IDLE connection open
     * indefinitely — exactly the kind of background work OEM battery managers (One UI, ColorOS,
     * MIUI, ...) are prone to silently kill, which is the single most common reason push sync
     * "just stops working" with no error anywhere. Asks the user to whitelist the app the moment
     * they turn the feature on, while the context for why is obvious, rather than leaving them to
     * find Android's own battery settings on their own after noticing mail stopped arriving.
     * No-ops quietly if already exempted or if the OEM doesn't support the standard dialog.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return
        if (powerManager.isIgnoringBatteryOptimizations(appContext.packageName)) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { appContext.startActivity(intent) }
    }
}
