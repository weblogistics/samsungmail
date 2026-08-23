package com.coursework.unifiedmail.domain.richtext

/** A styled run of text — a compose body is `List<TextRun>`; concatenating `text` gives the plain string. */
data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    // Non-null marks this run as a hyperlink over the given URL — set via RichText.setLink, not
    // a toggle like the other styles (a link needs a URL from the user, not just an on/off flip).
    val linkUrl: String? = null,
)

/** The formatting that newly-typed text should carry when the selection is collapsed (no range to restyle). */
data class PendingStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
)

/**
 * A small, explicit rich-text model — Compose has no built-in rich-text editing widget, and a
 * WebView/contenteditable editor was deliberately ruled out: it would need JavaScript re-enabled
 * right where `HtmlMessageBody` disables it on purpose, since email HTML (which seeds a reply's
 * quoted content) is untrusted. Scoped to bold/italic/underline/strikethrough, a literal
 * bulleted or numbered line prefix, and a plain-URL link — no colors, font sizes, or nested lists.
 *
 * Pure, framework-free logic only (no Compose/Android types) so it's plain-JUnit-testable, same
 * as ConversationThreading/FolderTree. `start`/`end` offsets throughout use the same
 * [start, end) convention as Compose's own `TextRange` (`start <= end`; `start == end` = collapsed).
 */
object RichText {

    private const val BULLET_PREFIX = "• "
    private val NUMBER_PREFIX_REGEX = Regex("^\\d+\\. ")

    fun fromPlainText(text: String): List<TextRun> = if (text.isEmpty()) emptyList() else listOf(TextRun(text))

    fun plainTextOf(runs: List<TextRun>): String = runs.joinToString("") { it.text }

    /** Splits any run overlapping [start, end) at the boundaries and applies [transform] to the covered piece(s). */
    fun applyStyle(runs: List<TextRun>, start: Int, end: Int, transform: (TextRun) -> TextRun): List<TextRun> {
        if (start >= end) return runs
        val result = mutableListOf<TextRun>()
        var offset = 0
        for (run in runs) {
            val runStart = offset
            val runEnd = offset + run.text.length
            offset = runEnd
            if (runEnd <= start || runStart >= end) {
                result.add(run)
                continue
            }
            val overlapStart = maxOf(runStart, start) - runStart
            val overlapEnd = minOf(runEnd, end) - runStart
            val before = run.text.substring(0, overlapStart)
            val overlap = run.text.substring(overlapStart, overlapEnd)
            val after = run.text.substring(overlapEnd)
            if (before.isNotEmpty()) result.add(run.copy(text = before))
            if (overlap.isNotEmpty()) result.add(transform(run.copy(text = overlap)))
            if (after.isNotEmpty()) result.add(run.copy(text = after))
        }
        return mergeAdjacent(result)
    }

    /** Extracts [start, end) as its own run list — used to splice the run list around an edit. */
    private fun sliceRuns(runs: List<TextRun>, start: Int, end: Int): List<TextRun> {
        if (start >= end) return emptyList()
        val result = mutableListOf<TextRun>()
        var offset = 0
        for (run in runs) {
            val runStart = offset
            val runEnd = offset + run.text.length
            offset = runEnd
            if (runEnd <= start || runStart >= end) continue
            val pieceStart = maxOf(runStart, start) - runStart
            val pieceEnd = minOf(runEnd, end) - runStart
            val piece = run.text.substring(pieceStart, pieceEnd)
            if (piece.isNotEmpty()) result.add(run.copy(text = piece))
        }
        return result
    }

    private fun mergeAdjacent(runs: List<TextRun>): List<TextRun> {
        val nonEmpty = runs.filter { it.text.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()
        val result = mutableListOf(nonEmpty.first())
        for (run in nonEmpty.drop(1)) {
            val last = result.last()
            if (last.bold == run.bold && last.italic == run.italic && last.underline == run.underline &&
                last.strikethrough == run.strikethrough && last.linkUrl == run.linkUrl
            ) {
                result[result.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                result.add(run)
            }
        }
        return result
    }

    private fun isUniformlyStyled(runs: List<TextRun>, start: Int, end: Int, has: (TextRun) -> Boolean): Boolean {
        if (start >= end) return false
        var offset = 0
        var coveredAny = false
        for (run in runs) {
            val runStart = offset
            val runEnd = offset + run.text.length
            offset = runEnd
            if (runEnd <= start || runStart >= end) continue
            coveredAny = true
            if (!has(run)) return false
        }
        return coveredAny
    }

    /** Toggles by first checking whether [start, end) is already uniformly styled — add if not, remove if so. */
    private fun toggle(runs: List<TextRun>, start: Int, end: Int, has: (TextRun) -> Boolean, with: (TextRun, Boolean) -> TextRun): List<TextRun> {
        val allStyled = isUniformlyStyled(runs, start, end, has)
        return applyStyle(runs, start, end) { with(it, !allStyled) }
    }

    fun toggleBold(runs: List<TextRun>, start: Int, end: Int): List<TextRun> =
        toggle(runs, start, end, { it.bold }, { r, v -> r.copy(bold = v) })

    fun toggleItalic(runs: List<TextRun>, start: Int, end: Int): List<TextRun> =
        toggle(runs, start, end, { it.italic }, { r, v -> r.copy(italic = v) })

    fun toggleUnderline(runs: List<TextRun>, start: Int, end: Int): List<TextRun> =
        toggle(runs, start, end, { it.underline }, { r, v -> r.copy(underline = v) })

    fun toggleStrikethrough(runs: List<TextRun>, start: Int, end: Int): List<TextRun> =
        toggle(runs, start, end, { it.strikethrough }, { r, v -> r.copy(strikethrough = v) })

    /** Sets (non-null [url]) or clears (null) the link over [start, end) — not a toggle, since adding a link needs a URL from the caller. */
    fun setLink(runs: List<TextRun>, start: Int, end: Int, url: String?): List<TextRun> =
        applyStyle(runs, start, end) { it.copy(linkUrl = url) }

    /**
     * Bridges a plain TextField's onValueChange back into the run model: finds the differing
     * middle slice between [oldText] and [newText] via common prefix/suffix, keeps runs outside
     * it untouched, and replaces the slice with a single new run carrying [pendingStyle] — so
     * text typed right after tapping Bold comes out bold.
     */
    fun reconcileEdit(oldRuns: List<TextRun>, oldText: String, newText: String, pendingStyle: PendingStyle): List<TextRun> {
        if (oldText == newText) return oldRuns

        val maxCommon = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < maxCommon && oldText[prefix] == newText[prefix]) prefix++

        val maxSuffix = maxCommon - prefix
        var suffix = 0
        while (suffix < maxSuffix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++

        val oldMiddleEnd = oldText.length - suffix
        val insertedText = newText.substring(prefix, newText.length - suffix)

        val before = sliceRuns(oldRuns, 0, prefix)
        val after = sliceRuns(oldRuns, oldMiddleEnd, oldText.length)
        val inserted = if (insertedText.isEmpty()) {
            emptyList()
        } else {
            listOf(TextRun(insertedText, pendingStyle.bold, pendingStyle.italic, pendingStyle.underline, pendingStyle.strikethrough))
        }
        return mergeAdjacent(before + inserted + after)
    }

    /**
     * Inserts/removes a literal "• " prefix on the line containing [cursor] — toggle is based on
     * whether that line already starts with the bullet. Scoped to a single line; bullets are
     * literal characters (here and in [toHtml]), not semantic `<ul>/<li>` markup.
     */
    fun toggleBullet(runs: List<TextRun>, cursor: Int): List<TextRun> {
        val text = plainTextOf(runs)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val hasBullet = text.startsWith(BULLET_PREFIX, lineStart)
        return if (hasBullet) {
            mergeAdjacent(sliceRuns(runs, 0, lineStart) + sliceRuns(runs, lineStart + BULLET_PREFIX.length, text.length))
        } else {
            val bulletRun = listOf(TextRun(BULLET_PREFIX))
            mergeAdjacent(sliceRuns(runs, 0, lineStart) + bulletRun + sliceRuns(runs, lineStart, text.length))
        }
    }

    /**
     * Inserts/removes a literal "N. " prefix on the line containing [cursor] — same literal,
     * single-line-scoped mechanism as [toggleBullet] (not semantic `<ol>/<li>` markup). The
     * number is computed once at toggle time from how many numbered lines immediately precede
     * this one, so continuing a list picks up the next number; it does not renumber later if
     * lines above are added, removed, or reordered.
     */
    fun toggleNumbering(runs: List<TextRun>, cursor: Int): List<TextRun> {
        val text = plainTextOf(runs)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        val existingMatch = NUMBER_PREFIX_REGEX.find(text.substring(lineStart, lineEnd))
        return if (existingMatch != null) {
            val prefixLength = existingMatch.value.length
            mergeAdjacent(sliceRuns(runs, 0, lineStart) + sliceRuns(runs, lineStart + prefixLength, text.length))
        } else {
            val number = precedingNumberedLineCount(text, lineStart) + 1
            val numberRun = listOf(TextRun("$number. "))
            mergeAdjacent(sliceRuns(runs, 0, lineStart) + numberRun + sliceRuns(runs, lineStart, text.length))
        }
    }

    private fun precedingNumberedLineCount(text: String, lineStart: Int): Int {
        var count = 0
        var end = lineStart - 1
        while (end >= 0) {
            val start = text.lastIndexOf('\n', end - 1) + 1
            if (!NUMBER_PREFIX_REGEX.containsMatchIn(text.substring(start, end))) break
            count++
            end = start - 1
        }
        return count
    }

    /** Nested `<b>/<i>/<u>/<s>/<a>`, escaped text, `\n` -> `<br>` — the HTML MIME part sent alongside the plain-text fallback. */
    fun toHtml(runs: List<TextRun>): String = runs.joinToString("") { run ->
        var wrapped = escapeHtml(run.text).replace("\n", "<br>")
        if (run.strikethrough) wrapped = "<s>$wrapped</s>"
        if (run.underline) wrapped = "<u>$wrapped</u>"
        if (run.italic) wrapped = "<i>$wrapped</i>"
        if (run.bold) wrapped = "<b>$wrapped</b>"
        run.linkUrl?.let { url -> wrapped = "<a href=\"${escapeHtml(url)}\">$wrapped</a>" }
        wrapped
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /**
     * Inverse of [toHtml] — parses only the exact tag vocabulary that method emits (nested
     * `<b>/<i>/<u>/<s>/<a href="...">`, `<br>`, and escaped text). Safe to hand-roll rather than
     * needing a real HTML parser because this only ever runs on HTML this same object generated
     * (a saved draft's own round-trip through [MailRepository]'s DraftEntity) — never on
     * untrusted mail content, which stays confined to the read-only WebView in HtmlMessageBody.
     */
    fun fromHtml(html: String): List<TextRun> {
        data class OpenTag(val name: String, val href: String?)

        val stack = mutableListOf<OpenTag>()
        val runs = mutableListOf<TextRun>()
        val text = StringBuilder()

        fun flush() {
            if (text.isEmpty()) return
            runs.add(
                TextRun(
                    text = unescapeHtml(text.toString()),
                    bold = stack.any { it.name == "b" },
                    italic = stack.any { it.name == "i" },
                    underline = stack.any { it.name == "u" },
                    strikethrough = stack.any { it.name == "s" },
                    linkUrl = stack.firstOrNull { it.name == "a" }?.href,
                ),
            )
            text.clear()
        }

        var i = 0
        while (i < html.length) {
            if (html[i] != '<') {
                text.append(html[i])
                i++
                continue
            }
            val end = html.indexOf('>', i)
            if (end == -1) {
                text.append(html[i])
                i++
                continue
            }
            val tag = html.substring(i + 1, end)
            flush()
            when {
                tag == "br" -> runs.add(TextRun("\n"))
                tag.startsWith("/") -> stack.removeLastOrNull()
                tag == "a" || tag.startsWith("a ") -> {
                    val href = Regex("href=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)?.let(::unescapeHtml)
                    stack.add(OpenTag("a", href))
                }
                else -> stack.add(OpenTag(tag, null))
            }
            i = end + 1
        }
        flush()
        return mergeAdjacent(runs)
    }

    // Order matters: the specific entities must be matched before "&amp;" — escapeHtml replaces
    // "&" first when encoding, so e.g. a literal "&" became "&amp;" while "<" became "&lt;", and
    // unescaping has to reverse that in the opposite order to avoid double-unescaping "&amp;lt;".
    private fun unescapeHtml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
