package com.coursework.unifiedmail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.coursework.unifiedmail.data.settings.SwipeAction
import com.coursework.unifiedmail.ui.components.SectionLabel

private val SYNC_INTERVAL_OPTIONS = listOf(15L, 30L, 60L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    var showSwipeRightDialog by remember { mutableStateOf(false) }
    var showSwipeLeftDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }

    if (showSwipeRightDialog) {
        SwipeActionPickerDialog(
            title = "Swipe right",
            current = settings.swipeRightAction,
            onDismiss = { showSwipeRightDialog = false },
            onSelect = {
                viewModel.setSwipeRightAction(it)
                showSwipeRightDialog = false
            },
        )
    }
    if (showSwipeLeftDialog) {
        SwipeActionPickerDialog(
            title = "Swipe left",
            current = settings.swipeLeftAction,
            onDismiss = { showSwipeLeftDialog = false },
            onSelect = {
                viewModel.setSwipeLeftAction(it)
                showSwipeLeftDialog = false
            },
        )
    }
    if (showSyncIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showSyncIntervalDialog = false },
            title = { Text("Sync interval") },
            text = {
                Column {
                    SYNC_INTERVAL_OPTIONS.forEach { minutes ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSyncIntervalMinutes(minutes)
                                    showSyncIntervalDialog = false
                                },
                        ) {
                            RadioButton(selected = minutes == settings.syncIntervalMinutes, onClick = null)
                            Text("$minutes minutes")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSyncIntervalDialog = false }) { Text("Cancel") }
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
        Column(modifier = Modifier.padding(padding)) {
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
                value = "${settings.syncIntervalMinutes} minutes",
                onClick = { showSyncIntervalDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionLabel(text = "General", modifier = Modifier.padding(16.dp))
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

@Composable
private fun SwipeActionPickerDialog(
    title: String,
    current: SwipeAction,
    onDismiss: () -> Unit,
    onSelect: (SwipeAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SwipeAction.entries.forEach { action ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) },
                    ) {
                        RadioButton(selected = action == current, onClick = null)
                        Text(action.displayLabel())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun SwipeAction.displayLabel(): String = when (this) {
    SwipeAction.NONE -> "Off"
    SwipeAction.TOGGLE_READ -> "Mark read/unread"
    SwipeAction.REMOVE -> "Remove from device"
}
