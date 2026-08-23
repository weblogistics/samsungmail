package com.coursework.unifiedmail.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.coursework.unifiedmail.data.settings.AppSettings.Companion.DUAL_PANE_DISABLED_WIDTH_DP

/**
 * [minWidthDp] is user-configurable (Settings > Two-pane layout, [AppSettings.dualPaneMinWidthDp])
 * — [DUAL_PANE_DISABLED_WIDTH_DP] turns the split off entirely regardless of screen width. The
 * default, 600dp, is Android's standard large-screen breakpoint (the `sw600dp` resource qualifier
 * / Material's medium window-size-class boundary) — matches an unfolded foldable or a small tablet.
 */
@Composable
fun rememberIsWideScreen(minWidthDp: Int): Boolean =
    minWidthDp != DUAL_PANE_DISABLED_WIDTH_DP && LocalConfiguration.current.screenWidthDp >= minWidthDp
