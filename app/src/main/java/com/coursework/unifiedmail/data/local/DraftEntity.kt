package com.coursework.unifiedmail.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val to: String,
    val cc: String,
    val bcc: String,
    val subject: String,
    // The rich-text HTML form of the body (see RichText.toHtml/fromHtml) — preserves formatting
    // across a save/reload, unlike storing plain text alone.
    val bodyHtml: String,
    // Set only for a forward of an HTML message — the original's raw HTML, carried verbatim
    // since the rich-text editor (and so bodyHtml above) can't represent it. See
    // ComposeViewModel.prefillFromSource/send.
    val quotedHtml: String? = null,
    // Set only for a reply/reply-all — the quoted original, kept out of bodyHtml/bodyRuns so it
    // renders read-only below a divider (see ComposeScreen) instead of as editable text mixed in
    // with the new message. See ComposeViewModel.prefillFromSource/send.
    val quotedText: String? = null,
    val inReplyToMessageIdHeader: String?,
    val referencesHeader: String?,
    val updatedAtEpochMillis: Long,
)
