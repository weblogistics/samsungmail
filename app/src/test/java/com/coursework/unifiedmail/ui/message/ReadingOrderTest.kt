package com.coursework.unifiedmail.ui.message

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {

    private val a = MessageKey("acc", "INBOX", 1)
    private val b = MessageKey("acc", "INBOX", 2)
    private val c = MessageKey("other", "INBOX", 1)

    @Test
    fun `neighbours are the messages listed directly above and below`() {
        val order = ReadingOrder().apply { set(listOf(a, b, c)) }

        assertEquals(a to c, order.neighbours(b))
        assertEquals(null to b, order.neighbours(a))
        assertEquals(b to null, order.neighbours(c))
    }

    @Test
    fun `a message not in the recorded list has no neighbours`() {
        val order = ReadingOrder().apply { set(listOf(a, b)) }

        assertEquals(null to null, order.neighbours(c))
        assertEquals(null to null, ReadingOrder().neighbours(a))
    }
}
