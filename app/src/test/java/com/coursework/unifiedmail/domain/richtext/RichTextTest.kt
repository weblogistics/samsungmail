package com.coursework.unifiedmail.domain.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun `fromPlainText and plainTextOf round-trip`() {
        val runs = RichText.fromPlainText("Hello world")
        assertEquals("Hello world", RichText.plainTextOf(runs))
    }

    @Test
    fun `applyStyle splits a run at the selection boundaries`() {
        val runs = listOf(TextRun("Hello world"))
        val styled = RichText.applyStyle(runs, 6, 11) { it.copy(bold = true) }

        assertEquals("Hello world", RichText.plainTextOf(styled))
        assertEquals(
            listOf(TextRun("Hello "), TextRun("world", bold = true)),
            styled,
        )
    }

    @Test
    fun `applyStyle merges adjacent runs with identical formatting`() {
        val runs = listOf(TextRun("Hello ", bold = true), TextRun("world"))
        val styled = RichText.applyStyle(runs, 6, 11) { it.copy(bold = true) }

        assertEquals(listOf(TextRun("Hello world", bold = true)), styled)
    }

    @Test
    fun `toggleBold adds bold when the selection isn't uniformly bold`() {
        val runs = listOf(TextRun("Hello world"))
        val toggled = RichText.toggleBold(runs, 0, 11)

        assertTrue(toggled.all { it.bold })
    }

    @Test
    fun `toggleBold removes bold when the selection is already uniformly bold`() {
        val runs = listOf(TextRun("Hello world", bold = true))
        val toggled = RichText.toggleBold(runs, 0, 11)

        assertTrue(toggled.none { it.bold })
        assertEquals("Hello world", RichText.plainTextOf(toggled))
    }

    @Test
    fun `toggleBold on a partially-bold selection makes the whole selection bold, not off`() {
        val runs = listOf(TextRun("Hello ", bold = true), TextRun("world"))
        val toggled = RichText.toggleBold(runs, 0, 11)

        assertTrue(toggled.all { it.bold })
    }

    @Test
    fun `reconcileEdit handles a pure insertion at the end`() {
        val oldRuns = listOf(TextRun("Hello"))
        val newRuns = RichText.reconcileEdit(oldRuns, "Hello", "Hello world", PendingStyle())

        assertEquals("Hello world", RichText.plainTextOf(newRuns))
    }

    @Test
    fun `reconcileEdit inserted text carries the pending style`() {
        val oldRuns = listOf(TextRun("Hello "))
        val newRuns = RichText.reconcileEdit(oldRuns, "Hello ", "Hello world", PendingStyle(bold = true))

        assertEquals("Hello world", RichText.plainTextOf(newRuns))
        assertEquals(TextRun("world", bold = true), newRuns.last())
        assertFalse(newRuns.first().bold)
    }

    @Test
    fun `reconcileEdit handles a deletion in the middle`() {
        val oldRuns = listOf(TextRun("Hello brave world"))
        val newRuns = RichText.reconcileEdit(oldRuns, "Hello brave world", "Hello world", PendingStyle())

        assertEquals("Hello world", RichText.plainTextOf(newRuns))
    }

    @Test
    fun `reconcileEdit preserves formatting of untouched runs`() {
        val oldRuns = listOf(TextRun("Hello ", bold = true), TextRun("world"))
        // Insert "!" at the very end — should not disturb the bold run at all.
        val newRuns = RichText.reconcileEdit(oldRuns, "Hello world", "Hello world!", PendingStyle())

        assertEquals("Hello world!", RichText.plainTextOf(newRuns))
        assertTrue(newRuns.first().bold)
    }

    @Test
    fun `toggleBullet adds then removes a bullet on the first line`() {
        val runs = listOf(TextRun("Buy milk"))
        val bulleted = RichText.toggleBullet(runs, cursor = 0)
        assertEquals("• Buy milk", RichText.plainTextOf(bulleted))

        val unbulleted = RichText.toggleBullet(bulleted, cursor = 0)
        assertEquals("Buy milk", RichText.plainTextOf(unbulleted))
    }

    @Test
    fun `toggleBullet targets only the line containing the cursor`() {
        val runs = listOf(TextRun("First line\nSecond line"))
        val cursorOnSecondLine = "First line\n".length
        val bulleted = RichText.toggleBullet(runs, cursor = cursorOnSecondLine)

        assertEquals("First line\n• Second line", RichText.plainTextOf(bulleted))
    }

    @Test
    fun `toHtml escapes text and nests bold, italic and underline consistently`() {
        val runs = listOf(TextRun("<script>", bold = true, italic = true))
        assertEquals("<b><i>&lt;script&gt;</i></b>", RichText.toHtml(runs))
    }

    @Test
    fun `toHtml converts newlines to br`() {
        val runs = listOf(TextRun("line one\nline two"))
        assertEquals("line one<br>line two", RichText.toHtml(runs))
    }

    @Test
    fun `toggleNumbering adds then removes a numbered prefix on the first line`() {
        val runs = listOf(TextRun("Buy milk"))
        val numbered = RichText.toggleNumbering(runs, cursor = 0)
        assertEquals("1. Buy milk", RichText.plainTextOf(numbered))

        val unnumbered = RichText.toggleNumbering(numbered, cursor = 0)
        assertEquals("Buy milk", RichText.plainTextOf(unnumbered))
    }

    @Test
    fun `toggleNumbering continues the count from preceding numbered lines`() {
        val runs = listOf(TextRun("1. First\nSecond"))
        val cursorOnSecondLine = "1. First\n".length
        val numbered = RichText.toggleNumbering(runs, cursor = cursorOnSecondLine)

        assertEquals("1. First\n2. Second", RichText.plainTextOf(numbered))
    }

    @Test
    fun `toggleStrikethrough adds then removes strikethrough`() {
        val runs = listOf(TextRun("Hello world"))
        val toggled = RichText.toggleStrikethrough(runs, 0, 11)
        assertTrue(toggled.all { it.strikethrough })

        val untoggled = RichText.toggleStrikethrough(toggled, 0, 11)
        assertTrue(untoggled.none { it.strikethrough })
    }

    @Test
    fun `setLink applies and clears a link url over the selection`() {
        val runs = listOf(TextRun("Hello world"))
        val linked = RichText.setLink(runs, 0, 5, "https://example.com")
        assertEquals("https://example.com", linked.first().linkUrl)
        assertEquals("Hello world", RichText.plainTextOf(linked))

        val unlinked = RichText.setLink(linked, 0, 5, null)
        assertTrue(unlinked.all { it.linkUrl == null })
    }

    @Test
    fun `toHtml wraps a link and nests other formatting inside the anchor`() {
        val runs = listOf(TextRun("click", bold = true, linkUrl = "https://example.com"))
        assertEquals("<a href=\"https://example.com\"><b>click</b></a>", RichText.toHtml(runs))
    }

    @Test
    fun `fromHtml round-trips plain and mixed-formatting runs through toHtml`() {
        val runs = listOf(
            TextRun("Hello "),
            TextRun("bold", bold = true),
            TextRun(" and "),
            TextRun("linked", linkUrl = "https://example.com"),
            TextRun("\nnext line"),
        )
        val roundTripped = RichText.fromHtml(RichText.toHtml(runs))
        assertEquals(RichText.plainTextOf(runs), RichText.plainTextOf(roundTripped))
        assertEquals(runs, roundTripped)
    }

    @Test
    fun `fromHtml unescapes entities and strips the wrapping tags`() {
        val runs = RichText.fromHtml("<b>&lt;script&gt;</b>")
        assertEquals(listOf(TextRun("<script>", bold = true)), runs)
    }

    @Test
    fun `fromHtml of empty string yields no runs`() {
        assertEquals(emptyList<TextRun>(), RichText.fromHtml(""))
    }
}
