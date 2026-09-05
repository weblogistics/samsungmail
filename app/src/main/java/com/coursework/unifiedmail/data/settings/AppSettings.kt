package com.coursework.unifiedmail.data.settings

data class AppSettings(
    // Names match Samsung Email's own "swipe left" / "swipe right" settings terminology.
    // swipeRightAction = SwipeToDismissBoxValue.StartToEnd, swipeLeftAction = EndToStart.
    val swipeRightAction: SwipeAction = SwipeAction.TOGGLE_READ,
    val swipeLeftAction: SwipeAction = SwipeAction.REMOVE,
    // ON_ARRIVAL_MINUTES means real-time push (IMAP IDLE) rather than periodic polling.
    val syncIntervalMinutes: Long = 15L,
    val confirmBeforeDelete: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // null = Unified Inbox is the home screen; an account id = that account's Inbox is.
    val defaultViewAccountId: String? = null,
    // How far back a folder's very first sync reaches (delta syncs afterwards are always
    // UID-based and ignore this). ALL_MAIL_SYNC_WINDOW_DAYS means no date bound at all.
    val syncWindowDays: Int = 30,
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
    val messageTextSize: MessageTextSize = MessageTextSize.MEDIUM,
    // Raw "accountId::folderFullName" entries, most-recent-first — see
    // SettingsRepository.recordMoveFolderUsage/recentMoveFoldersFor. Kept as composite strings
    // here rather than a richer type since this is the only place that needs to parse them.
    val recentMoveFolders: List<String> = emptyList(),
    // Raw "accountId::folderFullName<SEP>count" entries (see MOVE_FOLDER_COUNT_SEPARATOR),
    // tracking how often each destination has been chosen via "Move to" — independent of
    // recency, so a folder used constantly-but-not-recently still surfaces.
    val moveFolderUseCounts: List<String> = emptyList(),
    // DUAL_PANE_DISABLED_WIDTH_DP means never show the list+detail split, regardless of screen
    // width — see rememberIsWideScreen().
    val dualPaneMinWidthDp: Int = DEFAULT_DUAL_PANE_MIN_WIDTH_DP,
    // The list pane's width in the two-pane layout — user-draggable (see SplitPaneDivider),
    // persisted here so it survives navigating away and back / restarting the app.
    val listPaneWidthDp: Int = DEFAULT_LIST_PANE_WIDTH_DP,
    // How long the "Undo" snackbar after a move/archive/delete stays up before auto-dismissing.
    val undoDurationSeconds: Int = DEFAULT_UNDO_DURATION_SECONDS,
    // Whether related messages (same IMAP thread — see ConversationThreading) are grouped into
    // one row in the message list. Off shows every message as its own row instead.
    val threadedConversations: Boolean = true,
    // Whether a new-mail notification shows the sender/subject/snippet (see
    // NotificationHelper.showNewMailNotification) or just a bare "N new messages" count — off is
    // the more private choice for a shared or lock-screen-visible device.
    val showNotificationPreview: Boolean = true,
) {
    val isPushSync: Boolean get() = syncIntervalMinutes == ON_ARRIVAL_MINUTES

    /** This account's recently-used "Move to" destinations, most-recent-first. */
    fun recentMoveFoldersFor(accountId: String): List<String> {
        val prefix = "$accountId::"
        return recentMoveFolders.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    }

    /** This account's "Move to" destinations ordered by how often they've been chosen, most-used-first. */
    fun mostUsedMoveFoldersFor(accountId: String): List<String> {
        val prefix = "$accountId::"
        return moveFolderUseCounts
            .filter { it.startsWith(prefix) }
            .mapNotNull { entry ->
                val body = entry.removePrefix(prefix)
                val separatorIndex = body.lastIndexOf(MOVE_FOLDER_COUNT_SEPARATOR)
                if (separatorIndex < 0) return@mapNotNull null
                val fullName = body.substring(0, separatorIndex)
                val count = body.substring(separatorIndex + MOVE_FOLDER_COUNT_SEPARATOR.length).toIntOrNull() ?: return@mapNotNull null
                fullName to count
            }
            .sortedByDescending { (_, count) -> count }
            .map { (fullName, _) -> fullName }
    }

    companion object {
        const val ON_ARRIVAL_MINUTES = 0L
        const val ALL_MAIL_SYNC_WINDOW_DAYS = 0
        const val MOVE_FOLDER_COUNT_SEPARATOR = "@@"
        const val DUAL_PANE_DISABLED_WIDTH_DP = 0
        const val DEFAULT_DUAL_PANE_MIN_WIDTH_DP = 600
        const val DEFAULT_LIST_PANE_WIDTH_DP = 400
        // Matches Material's own SnackbarDuration.Long baseline (10s), which this setting
        // replaces — see the showSnackbarFor() helper used for the undo snackbar.
        const val DEFAULT_UNDO_DURATION_SECONDS = 10
    }
}
