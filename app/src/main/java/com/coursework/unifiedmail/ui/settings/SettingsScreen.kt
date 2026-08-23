package com.coursework.unifiedmail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.settings.AppSettings.Companion.ALL_MAIL_SYNC_WINDOW_DAYS
import com.coursework.unifiedmail.data.settings.AppSettings.Companion.DUAL_PANE_DISABLED_WIDTH_DP
import com.coursework.unifiedmail.data.settings.AppSettings.Companion.ON_ARRIVAL_MINUTES
import com.coursework.unifiedmail.data.settings.ListDensity
import com.coursework.unifiedmail.data.settings.MessageTextSize
import com.coursework.unifiedmail.data.settings.SwipeAction
import com.coursework.unifiedmail.data.settings.ThemeMode
import com.coursework.unifiedmail.ui.components.RadioOptionDialog
import com.coursework.unifiedmail.ui.components.SectionLabel

private val SYNC_INTERVAL_OPTIONS = listOf(ON_ARRIVAL_MINUTES, 1L, 5L, 10L, 15L, 30L, 60L)
private val SYNC_WINDOW_OPTIONS = listOf(7, 30, 90, 365, ALL_MAIL_SYNC_WINDOW_DAYS)
// 600dp is Android's standard large-screen breakpoint (an unfolded foldable or small tablet);
// 720/840 give it more room before switching, matching larger tablets.
private val DUAL_PANE_WIDTH_OPTIONS = listOf(DUAL_PANE_DISABLED_WIDTH_DP, 600, 720, 840)
private val UNDO_DURATION_OPTIONS = listOf(3, 5, 8, 10, 15)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var showSwipeRightDialog by remember { mutableStateOf(false) }
    var showSwipeLeftDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDefaultViewDialog by remember { mutableStateOf(false) }
    var showSyncWindowDialog by remember { mutableStateOf(false) }
    var showListDensityDialog by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showDualPaneWidthDialog by remember { mutableStateOf(false) }
    var showUndoDurationDialog by remember { mutableStateOf(false) }

    if (showSwipeRightDialog) {
        RadioOptionDialog(
            title = "Swipe right",
            options = SwipeAction.entries,
            selected = settings.swipeRightAction,
            labelFor = { it.displayLabel() },
            onDismiss = { showSwipeRightDialog = false },
            onSelect = {
                viewModel.setSwipeRightAction(it)
                showSwipeRightDialog = false
            },
        )
    }
    if (showSwipeLeftDialog) {
        RadioOptionDialog(
            title = "Swipe left",
            options = SwipeAction.entries,
            selected = settings.swipeLeftAction,
            labelFor = { it.displayLabel() },
            onDismiss = { showSwipeLeftDialog = false },
            onSelect = {
                viewModel.setSwipeLeftAction(it)
                showSwipeLeftDialog = false
            },
        )
    }
    if (showSyncIntervalDialog) {
        RadioOptionDialog(
            title = "Sync interval",
            options = SYNC_INTERVAL_OPTIONS,
            selected = settings.syncIntervalMinutes,
            labelFor = { it.toSyncIntervalLabel() },
            onDismiss = { showSyncIntervalDialog = false },
            onSelect = {
                viewModel.setSyncIntervalMinutes(it)
                showSyncIntervalDialog = false
            },
        )
    }
    if (showThemeDialog) {
        RadioOptionDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            labelFor = { it.displayLabel() },
            onDismiss = { showThemeDialog = false },
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
        )
    }
    if (showSyncWindowDialog) {
        RadioOptionDialog(
            title = "Sync window",
            options = SYNC_WINDOW_OPTIONS,
            selected = settings.syncWindowDays,
            labelFor = { it.toSyncWindowLabel() },
            onDismiss = { showSyncWindowDialog = false },
            onSelect = {
                viewModel.setSyncWindowDays(it)
                showSyncWindowDialog = false
            },
        )
    }
    if (showListDensityDialog) {
        RadioOptionDialog(
            title = "List density",
            options = ListDensity.entries,
            selected = settings.listDensity,
            labelFor = { it.displayLabel() },
            onDismiss = { showListDensityDialog = false },
            onSelect = {
                viewModel.setListDensity(it)
                showListDensityDialog = false
            },
        )
    }
    if (showTextSizeDialog) {
        RadioOptionDialog(
            title = "Message text size",
            options = MessageTextSize.entries,
            selected = settings.messageTextSize,
            labelFor = { it.displayLabel() },
            onDismiss = { showTextSizeDialog = false },
            onSelect = {
                viewModel.setMessageTextSize(it)
                showTextSizeDialog = false
            },
        )
    }
    if (showDualPaneWidthDialog) {
        RadioOptionDialog(
            title = "Two-pane layout",
            options = DUAL_PANE_WIDTH_OPTIONS,
            selected = settings.dualPaneMinWidthDp,
            labelFor = { it.toDualPaneWidthLabel() },
            onDismiss = { showDualPaneWidthDialog = false },
            onSelect = {
                viewModel.setDualPaneMinWidthDp(it)
                showDualPaneWidthDialog = false
            },
        )
    }
    if (showUndoDurationDialog) {
        RadioOptionDialog(
            title = "Undo notice duration",
            options = UNDO_DURATION_OPTIONS,
            selected = settings.undoDurationSeconds,
            labelFor = { it.toUndoDurationLabel() },
            onDismiss = { showUndoDurationDialog = false },
            onSelect = {
                viewModel.setUndoDurationSeconds(it)
                showUndoDurationDialog = false
            },
        )
    }
    if (showDefaultViewDialog) {
        // null represents Unified Inbox — listed first, ahead of individual accounts.
        RadioOptionDialog(
            title = "Default view",
            options = listOf(null) + accounts.map { it.id },
            selected = settings.defaultViewAccountId,
            labelFor = { id -> accounts.firstOrNull { it.id == id }?.displayName ?: "Unified inbox" },
            onDismiss = { showDefaultViewDialog = false },
            onSelect = {
                viewModel.setDefaultViewAccountId(it)
                showDefaultViewDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionLabel(text = "Swipe actions", modifier = Modifier.padding(16.dp))
            SettingRow(
                label = "Swipe right",
                value = settings.swipeRightAction.displayLabel(),
                onClick = { showSwipeRightDialog = true },
            )
            SettingRow(
                label = "Swipe left",
                value = settings.swipeLeftAction.displayLabel(),
                onClick = { showSwipeLeftDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel(text = "Sync", modifier = Modifier.padding(16.dp))
            SettingRow(
                label = "Sync interval",
                value = settings.syncIntervalMinutes.toSyncIntervalLabel(),
                onClick = { showSyncIntervalDialog = true },
            )
            SettingRow(
                label = "Sync window",
                value = settings.syncWindowDays.toSyncWindowLabel(),
                onClick = { showSyncWindowDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel(text = "Display", modifier = Modifier.padding(16.dp))
            SettingRow(
                label = "Theme",
                value = settings.themeMode.displayLabel(),
                onClick = { showThemeDialog = true },
            )
            SettingRow(
                label = "Default view",
                value = accounts.firstOrNull { it.id == settings.defaultViewAccountId }?.displayName ?: "Unified inbox",
                onClick = { showDefaultViewDialog = true },
            )
            SettingRow(
                label = "List density",
                value = settings.listDensity.displayLabel(),
                onClick = { showListDensityDialog = true },
            )
            SettingRow(
                label = "Message text size",
                value = settings.messageTextSize.displayLabel(),
                onClick = { showTextSizeDialog = true },
            )
            SettingRow(
                label = "Two-pane layout",
                value = settings.dualPaneMinWidthDp.toDualPaneWidthLabel(),
                onClick = { showDualPaneWidthDialog = true },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Group messages into conversations")
                Switch(checked = settings.threadedConversations, onCheckedChange = viewModel::setThreadedConversations)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel(text = "General", modifier = Modifier.padding(16.dp))
            SettingRow(
                label = "Undo notice duration",
                value = settings.undoDurationSeconds.toUndoDurationLabel(),
                onClick = { showUndoDurationDialog = true },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Confirm before removing a message")
                Switch(checked = settings.confirmBeforeDelete, onCheckedChange = viewModel::setConfirmBeforeDelete)
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
    )
}

private fun SwipeAction.displayLabel(): String = when (this) {
    SwipeAction.NONE -> "Off"
    SwipeAction.TOGGLE_READ -> "Mark read/unread"
    SwipeAction.REMOVE -> "Move to trash"
    SwipeAction.ARCHIVE -> "Archive"
    SwipeAction.TOGGLE_FLAG -> "Star/unstar"
}

private fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System default"
}

private fun ListDensity.displayLabel(): String = when (this) {
    ListDensity.COMFORTABLE -> "Comfortable"
    ListDensity.COMPACT -> "Compact"
}

private fun MessageTextSize.displayLabel(): String = when (this) {
    MessageTextSize.SMALL -> "Small"
    MessageTextSize.MEDIUM -> "Medium"
    MessageTextSize.LARGE -> "Large"
    MessageTextSize.EXTRA_LARGE -> "Extra large"
}

private fun Long.toSyncIntervalLabel(): String = when {
    this == ON_ARRIVAL_MINUTES -> "On arrival"
    this == 1L -> "1 minute"
    else -> "$this minutes"
}

private fun Int.toSyncWindowLabel(): String = when {
    this == ALL_MAIL_SYNC_WINDOW_DAYS -> "All mail"
    this == 365 -> "1 year"
    else -> "$this days"
}

private fun Int.toDualPaneWidthLabel(): String = when {
    this == DUAL_PANE_DISABLED_WIDTH_DP -> "Off"
    else -> "${this}dp and wider"
}

private fun Int.toUndoDurationLabel(): String = "$this seconds"
