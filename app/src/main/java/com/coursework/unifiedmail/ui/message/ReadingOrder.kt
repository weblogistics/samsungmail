package com.coursework.unifiedmail.ui.message

import javax.inject.Inject
import javax.inject.Singleton

/** Identifies one cached message — the same triple the message-detail route is built from. */
data class MessageKey(val accountId: String, val folderKey: String, val uid: Long)

/**
 * The order of the list a message was last opened from (an inbox/folder list, search results or
 * a thread), so the message screen can step to the previous/next one exactly as the user saw them
 * listed. Set by the list just before it opens a message; deliberately in-memory only — after
 * process death, or for a message opened some other way (e.g. a notification), the current
 * message simply isn't in it and the previous/next buttons don't show.
 */
@Singleton
class ReadingOrder @Inject constructor() {
    @Volatile
    private var keys: List<MessageKey> = emptyList()

    fun set(keys: List<MessageKey>) {
        this.keys = keys
    }

    /** The messages listed directly above and below [key], or nulls if it isn't in the list (or is at an end). */
    fun neighbours(key: MessageKey): Pair<MessageKey?, MessageKey?> {
        val current = keys
        val index = current.indexOf(key)
        if (index < 0) return null to null
        return current.getOrNull(index - 1) to current.getOrNull(index + 1)
    }
}
