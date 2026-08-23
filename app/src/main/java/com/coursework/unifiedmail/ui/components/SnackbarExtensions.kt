package com.coursework.unifiedmail.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows a snackbar that auto-dismisses after exactly [durationMillis] rather than one of
 * Material's fixed [SnackbarDuration] presets — needed because the undo snackbar's timeout is
 * user-configurable (see AppSettings.undoDurationSeconds). [SnackbarHostState.showSnackbar] only
 * accepts that preset enum directly, so this requests [SnackbarDuration.Indefinite] and races it
 * against our own timer instead.
 */
suspend fun SnackbarHostState.showSnackbarFor(
    message: String,
    actionLabel: String?,
    durationMillis: Long,
): SnackbarResult = coroutineScope {
    val dismissJob = launch {
        delay(durationMillis)
        currentSnackbarData?.dismiss()
    }
    val result = showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Indefinite)
    dismissJob.cancel()
    result
}
