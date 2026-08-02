package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.ui.theme.colorForKey

/** Initial-letter avatar, color-keyed by sender — shared by the message list and detail screens. */
@Composable
fun SenderAvatar(senderLabel: String, size: Dp = 44.dp) {
    val color = colorForKey(senderLabel)
    val initial = senderLabel.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White
        Text(text = initial, color = onColor, fontWeight = FontWeight.Bold)
    }
}
