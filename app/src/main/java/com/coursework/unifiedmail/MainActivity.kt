package com.coursework.unifiedmail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.data.settings.ThemeMode
import com.coursework.unifiedmail.ui.nav.MailNavGraph
import com.coursework.unifiedmail.ui.theme.UnifiedMailTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // A notification tap takes priority over the configured default view — the whole point
        // of tapping a "new mail in X" notification is landing on that account's Inbox, even if
        // a different account (or Unified Inbox) is normally the default.
        val deepLinkAccountId = intent?.getStringExtra(EXTRA_DEEPLINK_ACCOUNT_ID)
        // Same tradeoff as MailApplication.onCreate: a single fast local DataStore read at
        // startup, blocked on briefly here since there's no reasonable async alternative for
        // "which account, if any, should auto-open on top of Unified Inbox" this early.
        val startAccountId = deepLinkAccountId
            ?: runBlocking { settingsRepository.settings.first() }.defaultViewAccountId
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            UnifiedMailTheme(darkTheme = darkTheme) {
                MailNavGraph(startAccountId = startAccountId)
            }
        }
    }

    // Declared in the manifest since Milestone 1, but declaring it alone doesn't grant it on
    // API 33+ — it's a runtime permission there and new-mail notifications silently never show
    // without this request actually being made.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_DEEPLINK_ACCOUNT_ID = "deeplink_account_id"
    }
}
