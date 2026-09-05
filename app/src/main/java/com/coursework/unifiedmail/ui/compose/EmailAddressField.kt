package com.coursework.unifiedmail.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.data.repository.EmailContact
import com.coursework.unifiedmail.ui.components.FlatTextField
import com.coursework.unifiedmail.ui.theme.Spacing

/**
 * A comma/semicolon-separated address field (To/Cc/Bcc) with inline autocomplete drawn from
 * [suggestions] — see MailRepository.getAutocompleteContacts, which builds that list from the
 * account's own Sent folder. Matching is against whatever's typed after the last separator (the
 * address currently being composed), not the whole field, so earlier already-picked recipients
 * don't get re-matched on every keystroke.
 *
 * Deliberately not built on Material3's ExposedDropdownMenuBox: that widget expects a fixed set
 * of options for a single value, not a running list appended to a free-typed, multi-value field.
 * A plain inline suggestions panel below the field — pushing later content down rather than
 * floating over it — needs no extra positioning logic and behaves the same across API/library
 * versions.
 */
@Composable
fun EmailAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<EmailContact>,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    // The token is whatever's typed after the last comma/semicolon — the address currently being
    // composed. rawToken (untrimmed) is what a match gets spliced in over; trimmedToken is what
    // actually gets matched against, so a stray leading space after a separator doesn't break
    // matching without also being carried into the splice math.
    val rawToken = value.substringAfterLast(',').substringAfterLast(';')
    val trimmedToken = rawToken.trim()
    val matches = if (trimmedToken.length < 2) {
        emptyList()
    } else {
        suggestions.filter { contact ->
            contact.address.contains(trimmedToken, ignoreCase = true) ||
                contact.displayName?.contains(trimmedToken, ignoreCase = true) == true
        }.take(MAX_SUGGESTIONS)
    }
    val expanded = isFocused && matches.isNotEmpty()

    Column(modifier = modifier) {
        FlatTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = { Text("comma-separated addresses") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
        )
        if (expanded) {
            Surface(
                tonalElevation = 3.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    matches.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val prefixEnd = value.length - rawToken.length
                                    onValueChange(value.substring(0, prefixEnd) + contact.formatted + ", ")
                                }
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        ) {
                            Column {
                                if (!contact.displayName.isNullOrBlank()) {
                                    Text(contact.displayName, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    contact.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 5
