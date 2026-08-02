package com.coursework.unifiedmail.domain.threading

/**
 * Groups messages into conversations from IMAP headers, cheaply and locally — no server-side
 * THREAD extension required.
 *
 * The key invariant: a thread root and its replies must land on the *same* conversation id.
 * A reply names its root via References/In-Reply-To — so the root's own id must double as its
 * conversation id, otherwise a reply computing "mid:<root's Message-ID>" would never match a
 * root that keyed itself by anything else (subject, a random id, ...). So the priority is:
 *
 * 1. Oldest id in References (the thread root, when the chain was populated correctly).
 * 2. In-Reply-To (same-thread signal even without a full References chain).
 * 3. The message's own Message-ID — covers thread roots (no References/In-Reply-To of their
 *    own), which is the common case, since almost every real message has a Message-ID.
 * 4. Normalized subject — only reached when a message has no Message-ID at all (rare/malformed).
 *    This is a much weaker signal (can merge unrelated messages sharing a subject) but it's the
 *    only thing left to key on at that point.
 *
 * One known gap: a reply from a client that omits References/In-Reply-To *and* still sets its
 * own distinct Message-ID will key off that own id (case 3) rather than matching its root —
 * i.e. it won't be threaded, same as if this feature didn't exist. Fixing that would mean
 * defaulting everything to subject-based grouping, which trades this rare miss for the more
 * visible failure mode of merging unrelated same-subject threads — not a better trade.
 *
 * Each result is tagged with the source ("mid:"/"subj:") it was derived from, so a real
 * Message-ID can never collide with a normalized-subject string.
 */
object ConversationThreading {

    fun computeConversationId(
        messageIdHeader: String?,
        inReplyToHeader: String?,
        referencesHeader: String?,
        subject: String?,
    ): String {
        val rootFromReferences = referencesHeader
            ?.let { parseMessageIds(it) }
            ?.firstOrNull()
        if (rootFromReferences != null) return "mid:$rootFromReferences"

        val parent = inReplyToHeader?.let { parseMessageIds(it) }?.firstOrNull()
        if (parent != null) return "mid:$parent"

        val ownId = messageIdHeader?.trim()?.takeIf { it.isNotEmpty() }
        if (ownId != null) return "mid:$ownId"

        val normalizedSubject = normalizeSubject(subject)
        return if (normalizedSubject != null) "subj:$normalizedSubject" else "mid:unknown"
    }

    /** Message-IDs are whitespace-separated, each wrapped in angle brackets, e.g. "<a@x> <b@y>". */
    private fun parseMessageIds(headerValue: String): List<String> =
        Regex("<[^<>]+>").findAll(headerValue).map { it.value }.toList()

    private fun normalizeSubject(subject: String?): String? {
        val stripped = subject
            ?.trim()
            ?.replace(Regex("^((re|fwd?)\\s*:\\s*)+", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.lowercase()
        return stripped?.takeIf { it.isNotEmpty() }
    }
}
