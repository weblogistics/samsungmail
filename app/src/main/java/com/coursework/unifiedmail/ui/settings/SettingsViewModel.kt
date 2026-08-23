package com.coursework.unifiedmail.ui.settings

import android.content.Context
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
            } else {
                MailIdleService.stop(appContext)
            }
        }
    }
}
