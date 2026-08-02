package com.coursework.unifiedmail.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

// A fixed, deliberately un-themed palette: these badge accounts by identity, so they need to
// stay visually distinct from each other regardless of the active Material theme/dark mode.
private val AccountPalette = listOf(
    Color(0xFFE57373),
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFBA68C8),
    Color(0xFF4DB6AC),
    Color(0xFFF06292),
    Color(0xFFA1887F),
)

/** Deterministic color per account id, so the same account always gets the same badge color. */
fun colorForKey(key: String): Color = AccountPalette[key.hashCode().absoluteValue % AccountPalette.size]
