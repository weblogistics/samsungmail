package com.coursework.unifiedmail.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.coursework.unifiedmail.ui.theme.Spacing

/**
 * Placeholder rows shown while a folder's very first sync is still in flight — distinguishes
 * "this is loading" from "this folder is genuinely empty", which a blank list + spinner doesn't.
 */
@Composable
fun MessageListSkeleton(rowCount: Int = 5, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.3f)

    Column(modifier = modifier) {
        repeat(rowCount) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(placeholderColor))
                Column(modifier = Modifier.padding(start = Spacing.md)) {
                    Box(modifier = Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(placeholderColor))
                    Box(
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .width(120.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                }
            }
        }
    }
}
