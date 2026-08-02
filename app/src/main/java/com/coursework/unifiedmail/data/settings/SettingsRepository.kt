package com.coursework.unifiedmail.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SWIPE_LEFT = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right_action")
        val SYNC_INTERVAL_MINUTES = longPreferencesKey("sync_interval_minutes")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            swipeRightAction = prefs[Keys.SWIPE_RIGHT]?.toSwipeActionOrNull() ?: defaults.swipeRightAction,
            swipeLeftAction = prefs[Keys.SWIPE_LEFT]?.toSwipeActionOrNull() ?: defaults.swipeLeftAction,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL_MINUTES] ?: defaults.syncIntervalMinutes,
            confirmBeforeDelete = prefs[Keys.CONFIRM_BEFORE_DELETE] ?: defaults.confirmBeforeDelete,
        )
    }

    suspend fun setSwipeRightAction(action: SwipeAction) {
        context.settingsDataStore.edit { it[Keys.SWIPE_RIGHT] = action.name }
    }

    suspend fun setSwipeLeftAction(action: SwipeAction) {
        context.settingsDataStore.edit { it[Keys.SWIPE_LEFT] = action.name }
    }

    suspend fun setSyncIntervalMinutes(minutes: Long) {
        context.settingsDataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setConfirmBeforeDelete(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CONFIRM_BEFORE_DELETE] = enabled }
    }

    private fun String.toSwipeActionOrNull(): SwipeAction? = runCatching { SwipeAction.valueOf(this) }.getOrNull()
}
