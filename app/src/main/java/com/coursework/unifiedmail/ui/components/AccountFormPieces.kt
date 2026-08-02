package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.local.MailSecurity

/** Shared pieces of the Add/Edit account forms. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
fun SecurityChipRow(selected: MailSecurity, onSelected: (MailSecurity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MailSecurity.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(option.label()) },
            )
        }
    }
}

private fun MailSecurity.label(): String = when (this) {
    MailSecurity.NONE -> "None"
    MailSecurity.SSL_TLS -> "SSL/TLS"
    MailSecurity.STARTTLS -> "STARTTLS"
}
