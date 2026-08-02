package com.coursework.unifiedmail.data.settings

/**
 * Deliberately limited to what's actually backed by real behavior today — no move/archive/spam,
 * since there's no IMAP write support for those yet. REMOVE is local-cache-only (see
 * MailRepository.removeMessageLocally), not a server-side delete.
 */
enum class SwipeAction {
    NONE,
    TOGGLE_READ,
    REMOVE,
}
