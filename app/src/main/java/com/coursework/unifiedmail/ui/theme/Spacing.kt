package com.coursework.unifiedmail.ui.theme

import androidx.compose.ui.unit.dp

/**
 * A small named scale for the dp literals scattered across screens (16.dp/8.dp/4.dp show up
 * ad hoc everywhere from incremental work) — used in new/touched components going forward.
 * Not yet swept across every existing screen; the values below just name what was already the
 * de facto scale rather than introducing new ones, so adopting it elsewhere is a safe follow-up.
 */
object Spacing {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
