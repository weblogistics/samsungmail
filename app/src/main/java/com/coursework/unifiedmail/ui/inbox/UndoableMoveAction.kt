package com.coursework.unifiedmail.ui.inbox

import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.MoveOutcome
import com.coursework.unifiedmail.data.repository.UndoMoveOutcome

/**
 * A just-completed archive/trash move the user can still reverse via a Snackbar's Undo action.
 * Holds every message the move covered — several when a whole thread row was swiped. All of them
 * come from one account and folder (a conversation never spans either).
 */
data class UndoableMoveAction(
    val messages: List<MessageEntity>,
    val originalFolderKey: String,
    val destinationFolderKey: String,
    val label: String,
) {
    val accountId: String get() = messages.first().accountId
}

/**
 * Runs [move] on each of [messages]. Returns an Undo-able action covering the ones that moved
 * (null if none did) and the distinct failure reasons for the rest. [label] is the snackbar text,
 * e.g. "Moved to trash", with the message count appended when more than one moved.
 */
suspend fun moveMessages(
    messages: List<MessageEntity>,
    label: String,
    move: suspend (MessageEntity) -> MoveOutcome,
): Pair<UndoableMoveAction?, List<String>> {
    val moved = mutableListOf<MessageEntity>()
    val failures = mutableListOf<String>()
    var destinationFolderKey: String? = null
    for (message in messages) {
        when (val result = move(message)) {
            is MoveOutcome.Success -> {
                moved += message
                destinationFolderKey = result.destinationFolderKey
            }
            is MoveOutcome.Failure -> failures += result.reason
        }
    }
    val action = destinationFolderKey?.let { destination ->
        val fullLabel = if (moved.size > 1) "$label (${moved.size} messages)" else label
        UndoableMoveAction(moved, moved.first().folderName, destination, fullLabel)
    }
    return action to failures.distinct()
}

/** Reverses every message in [action] — see MailRepository.undoMove. Returns the distinct failure reasons, if any. */
suspend fun MailRepository.undoMoves(action: UndoableMoveAction): List<String> =
    action.messages.mapNotNull { message ->
        val result = undoMove(message.accountId, action.originalFolderKey, action.destinationFolderKey, message.messageIdHeader)
        (result as? UndoMoveOutcome.Failure)?.reason
    }.distinct()
