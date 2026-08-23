package com.coursework.unifiedmail.data.settings

/**
 * REMOVE moves the message to the account's Trash folder on the server (a real IMAP move — see
 * MailRepository.moveMessageToTrash), not just a local-cache removal. ARCHIVE is the same idea
 * against the Archive folder. TOGGLE_FLAG stars/unstars (\Flagged) rather than deleting/moving.
 */
enum class SwipeAction {
    NONE,
    TOGGLE_READ,
    REMOVE,
    ARCHIVE,
    TOGGLE_FLAG,
}
