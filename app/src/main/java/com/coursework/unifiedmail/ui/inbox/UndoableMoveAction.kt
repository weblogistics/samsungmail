package com.coursework.unifiedmail.ui.inbox

import com.coursework.unifiedmail.data.local.MessageEntity

/** A just-completed archive/trash move the user can still reverse via a Snackbar's Undo action. */
data class UndoableMoveAction(
    val message: MessageEntity,
    val originalFolderKey: String,
    val destinationFolderKey: String,
    val label: String,
)
