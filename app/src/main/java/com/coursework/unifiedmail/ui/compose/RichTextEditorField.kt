package com.coursework.unifiedmail.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.domain.richtext.PendingStyle
import com.coursework.unifiedmail.domain.richtext.TextRun
import kotlinx.coroutines.launch

/**
 * The compose body field: a small, real (not markdown-syntax) rich-text editor built on
 * Compose's own AnnotatedString/TextFieldValue — see ComposeViewModel's RichText usage for why
 * this isn't a WebView/contenteditable editor. Bold/italic/underline/strikethrough, a bulleted or
 * numbered line prefix, and a plain-URL link — no colors, font sizes, or nested lists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    // The default "scroll the focused field into view" behavior tracks the field's own bounds,
    // not the cursor's line — for a field that grows as you type, pressing Enter can land the new
    // (empty) line a frame behind that, under the keyboard, until something else nudges a rescroll.
    // Recomputing the cursor's own rect on every layout pass and requesting exactly that into view
    // keeps it synced with what's actually on screen instead of relying on that lag-prone default.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // A little breathing room below the cursor line so it doesn't end up sitting flush against
    // the keyboard's top edge.
    val cursorMarginPx = with(density) { 24.dp.toPx() }

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
        val fieldValue = TextFieldValue(annotatedString = runs.toAnnotatedString(), selection = selection)
        val interactionSource = remember { MutableInteractionSource() }
        val colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        )
        // Material3's TextField (in the version this project is on) doesn't expose onTextLayout,
        // which is what's needed below to track the cursor's own rect — so this is built directly
        // on BasicTextField, styled to match via TextFieldDefaults.DecorationBox rather than
        // losing the app's usual field look.
        BasicTextField(
            value = fieldValue,
            onValueChange = onValueChange,
            onTextLayout = { layoutResult: TextLayoutResult ->
                val cursorIndex = selection.end.coerceIn(0, layoutResult.layoutInput.text.length)
                val cursorRect = layoutResult.getCursorRect(cursorIndex)
                val target = Rect(cursorRect.left, cursorRect.top, cursorRect.right, cursorRect.bottom + cursorMarginPx)
                coroutineScope.launch { bringIntoViewRequester.bringIntoView(target) }
            },
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = true,
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester),
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = fieldValue.text,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = false,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    colors = colors,
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                )
            },
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
