package com.coursework.unifiedmail.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import com.coursework.unifiedmail.domain.richtext.PendingStyle
import com.coursework.unifiedmail.domain.richtext.TextRun

/**
 * The compose body field: a small, real (not markdown-syntax) rich-text editor built on
 * Compose's own AnnotatedString/TextFieldValue — see ComposeViewModel's RichText usage for why
 * this isn't a WebView/contenteditable editor. Bold/italic/underline/strikethrough, a bulleted or
 * numbered line prefix, and a plain-URL link — no colors, font sizes, or nested lists.
 */
@Composable
fun RichTextEditorField(
    runs: List<TextRun>,
    selection: TextRange,
    pendingStyle: PendingStyle,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onToggleBullet: () -> Unit,
    onToggleNumbering: () -> Unit,
    onInsertLinkClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            IconToggleButton(checked = pendingStyle.bold, onCheckedChange = { onToggleBold() }) {
                Icon(Icons.Filled.FormatBold, contentDescription = "Bold")
            }
            IconToggleButton(checked = pendingStyle.italic, onCheckedChange = { onToggleItalic() }) {
                Icon(Icons.Filled.FormatItalic, contentDescription = "Italic")
            }
            IconToggleButton(checked = pendingStyle.underline, onCheckedChange = { onToggleUnderline() }) {
                Icon(Icons.Filled.FormatUnderlined, contentDescription = "Underline")
            }
            IconToggleButton(checked = pendingStyle.strikethrough, onCheckedChange = { onToggleStrikethrough() }) {
                Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Strikethrough")
            }
            // Bullets/numbering aren't tracked in pendingStyle (they're a line prefix, not a run
            // style), so these buttons never show as "checked" — each is a one-shot action.
            IconToggleButton(checked = false, onCheckedChange = { onToggleBullet() }) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bulleted list")
            }
            IconToggleButton(checked = false, onCheckedChange = { onToggleNumbering() }) {
                Icon(Icons.Filled.FormatListNumbered, contentDescription = "Numbered list")
            }
            IconButton(onClick = onInsertLinkClick) {
                Icon(Icons.Filled.Link, contentDescription = "Insert link")
            }
        }
        TextField(
            value = TextFieldValue(annotatedString = runs.toAnnotatedString(), selection = selection),
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun List<TextRun>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    for (run in this@toAnnotatedString) {
        val start = length
        append(run.text)
        val decorations = listOfNotNull(
            TextDecoration.Underline.takeIf { run.underline },
            TextDecoration.LineThrough.takeIf { run.strikethrough },
        )
        if (run.bold || run.italic || decorations.isNotEmpty() || run.linkUrl != null) {
            addStyle(
                SpanStyle(
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    textDecoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null,
                    color = if (run.linkUrl != null) Color(0xFF1A73E8) else Color.Unspecified,
                ),
                start,
                length,
            )
        }
    }
}
