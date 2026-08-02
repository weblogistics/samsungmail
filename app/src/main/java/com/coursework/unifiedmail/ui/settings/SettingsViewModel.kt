package com.coursework.unifiedmail.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.data.settings.SwipeAction
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setSwipeRightAction(action: SwipeAction) {
        viewModelScope.launch { settingsRepository.setSwipeRightAction(action) }
    }

    fun setSwipeLeftAction(action: SwipeAction) {
        viewModelScope.launch { settingsRepository.setSwipeLeftAction(action) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeDelete(enabled) }
    }

    fun setSyncIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            settingsRepository.setSyncIntervalMinutes(minutes)
            SyncScheduler.schedulePeriodicSync(appContext, minutes)
        }
    }
}
