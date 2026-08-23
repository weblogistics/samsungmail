package com.coursework.unifiedmail.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** One-shot current settings — narrower than [SettingsRepository] so callers that only need a
 * snapshot (not the live [Flow]) can be unit-tested against a fake instead of a real DataStore. */
interface AppSettingsProvider {
    suspend fun currentSettings(): AppSettings
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppSettingsProvider {
    private object Keys {
        val SWIPE_LEFT = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right_action")
        val SYNC_INTERVAL_MINUTES = longPreferencesKey("sync_interval_minutes")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_VIEW_ACCOUNT_ID = stringPreferencesKey("default_view_account_id")
        val SYNC_WINDOW_DAYS = intPreferencesKey("sync_window_days")
        val LIST_DENSITY = stringPreferencesKey("list_density")
        val MESSAGE_TEXT_SIZE = stringPreferencesKey("message_text_size")
        val RECENT_MOVE_FOLDERS = stringPreferencesKey("recent_move_folders")
        val MOVE_FOLDER_USE_COUNTS = stringPreferencesKey("move_folder_use_counts")
        val DUAL_PANE_MIN_WIDTH_DP = intPreferencesKey("dual_pane_min_width_dp")
        val LIST_PANE_WIDTH_DP = intPreferencesKey("list_pane_width_dp")
        val UNDO_DURATION_SECONDS = intPreferencesKey("undo_duration_seconds")
        val THREADED_CONVERSATIONS = booleanPreferencesKey("threaded_conversations")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            swipeRightAction = prefs[Keys.SWIPE_RIGHT]?.toEnumOrNull<SwipeAction>() ?: defaults.swipeRightAction,
            swipeLeftAction = prefs[Keys.SWIPE_LEFT]?.toEnumOrNull<SwipeAction>() ?: defaults.swipeLeftAction,
            syncIntervalMinutes = prefs[Keys.SYNC_INTERVAL_MINUTES] ?: defaults.syncIntervalMinutes,
            confirmBeforeDelete = prefs[Keys.CONFIRM_BEFORE_DELETE] ?: defaults.confirmBeforeDelete,
            themeMode = prefs[Keys.THEME_MODE]?.toEnumOrNull<ThemeMode>() ?: defaults.themeMode,
            defaultViewAccountId = prefs[Keys.DEFAULT_VIEW_ACCOUNT_ID],
            syncWindowDays = prefs[Keys.SYNC_WINDOW_DAYS] ?: defaults.syncWindowDays,
            listDensity = prefs[Keys.LIST_DENSITY]?.toEnumOrNull<ListDensity>() ?: defaults.listDensity,
            messageTextSize = prefs[Keys.MESSAGE_TEXT_SIZE]?.toEnumOrNull<MessageTextSize>() ?: defaults.messageTextSize,
            recentMoveFolders = prefs[Keys.RECENT_MOVE_FOLDERS]?.split(RECENT_MOVE_FOLDERS_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
            moveFolderUseCounts = prefs[Keys.MOVE_FOLDER_USE_COUNTS]?.split(RECENT_MOVE_FOLDERS_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
            dualPaneMinWidthDp = prefs[Keys.DUAL_PANE_MIN_WIDTH_DP] ?: defaults.dualPaneMinWidthDp,
            listPaneWidthDp = prefs[Keys.LIST_PANE_WIDTH_DP] ?: defaults.listPaneWidthDp,
            undoDurationSeconds = prefs[Keys.UNDO_DURATION_SECONDS] ?: defaults.undoDurationSeconds,
            threadedConversations = prefs[Keys.THREADED_CONVERSATIONS] ?: defaults.threadedConversations,
        )
    }

    override suspend fun currentSettings(): AppSettings = settings.first()

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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** `null` clears the override, making Unified Inbox the home screen again. */
    suspend fun setDefaultViewAccountId(accountId: String?) {
        context.settingsDataStore.edit {
            if (accountId == null) it.remove(Keys.DEFAULT_VIEW_ACCOUNT_ID) else it[Keys.DEFAULT_VIEW_ACCOUNT_ID] = accountId
        }
    }

    suspend fun setSyncWindowDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.SYNC_WINDOW_DAYS] = days }
    }

    suspend fun setListDensity(density: ListDensity) {
        context.settingsDataStore.edit { it[Keys.LIST_DENSITY] = density.name }
    }

    suspend fun setMessageTextSize(size: MessageTextSize) {
        context.settingsDataStore.edit { it[Keys.MESSAGE_TEXT_SIZE] = size.name }
    }

    suspend fun setDualPaneMinWidthDp(widthDp: Int) {
        context.settingsDataStore.edit { it[Keys.DUAL_PANE_MIN_WIDTH_DP] = widthDp }
    }

    suspend fun setListPaneWidthDp(widthDp: Int) {
        context.settingsDataStore.edit { it[Keys.LIST_PANE_WIDTH_DP] = widthDp }
    }

    suspend fun setUndoDurationSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.UNDO_DURATION_SECONDS] = seconds }
    }

    suspend fun setThreadedConversations(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.THREADED_CONVERSATIONS] = enabled }
    }

    /**
     * Records a "Move to" destination for [accountId] against both tracks the folder picker
     * draws on: recency (moved to the front, see [AppSettings.recentMoveFoldersFor]) and
     * frequency (count incremented, see [AppSettings.mostUsedMoveFoldersFor]) — one is "used
     * just now", the other is "used a lot", and they surface different folders in practice.
     */
    suspend fun recordMoveFolderUsage(accountId: String, folderFullName: String) {
        context.settingsDataStore.edit { prefs ->
            val entry = "$accountId::$folderFullName"

            val recentCurrent = prefs[Keys.RECENT_MOVE_FOLDERS]?.split(RECENT_MOVE_FOLDERS_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            val recentUpdated = (listOf(entry) + recentCurrent.filterNot { it == entry }).take(MAX_RECENT_MOVE_FOLDERS)
            prefs[Keys.RECENT_MOVE_FOLDERS] = recentUpdated.joinToString(RECENT_MOVE_FOLDERS_SEPARATOR)

            val countPrefix = "$entry${AppSettings.MOVE_FOLDER_COUNT_SEPARATOR}"
            val countsCurrent = prefs[Keys.MOVE_FOLDER_USE_COUNTS]?.split(RECENT_MOVE_FOLDERS_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            val existingCount = countsCurrent.firstOrNull { it.startsWith(countPrefix) }
                ?.removePrefix(countPrefix)?.toIntOrNull() ?: 0
            val countsUpdated = (countsCurrent.filterNot { it.startsWith(countPrefix) } + "$countPrefix${existingCount + 1}")
                .sortedByDescending { it.substringAfterLast(AppSettings.MOVE_FOLDER_COUNT_SEPARATOR).toIntOrNull() ?: 0 }
                .take(MAX_MOVE_FOLDER_USE_COUNTS)
            prefs[Keys.MOVE_FOLDER_USE_COUNTS] = countsUpdated.joinToString(RECENT_MOVE_FOLDERS_SEPARATOR)
        }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        runCatching { java.lang.Enum.valueOf(T::class.java, this) }.getOrNull()

    private companion object {
        const val RECENT_MOVE_FOLDERS_SEPARATOR = "|||"
        // Capped across all accounts combined, not per-account — a handful of recent
        // destinations is all a "recently used" section is meant to surface anyway.
        const val MAX_RECENT_MOVE_FOLDERS = 12
        const val MAX_MOVE_FOLDER_USE_COUNTS = 40
    }
}
