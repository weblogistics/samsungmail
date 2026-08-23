package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A single-choice picker dialog — shared by Settings and per-account folder pickers. */
@Composable
fun <T> RadioOptionDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    // An optional extra action below the option list — e.g. "Empty trash now" on the Trash
    // folder picker, so a related maintenance action is reachable right where the folder is
    // configured, not only elsewhere in the account screen.
    extraContent: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        headlineContent = { Text(labelFor(option)) },
                    )
                }
                extraContent?.let {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    it()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
