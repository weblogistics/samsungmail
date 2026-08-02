package com.coursework.unifiedmail.domain.threading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConversationThreadingTest {

    @Test
    fun `root and reply converge on the same conversation`() {
        val root = ConversationThreading.computeConversationId(
            messageIdHeader = "<root@x>",
            inReplyToHeader = null,
            referencesHeader = null,
            subject = "Hello",
        )
        val reply = ConversationThreading.computeConversationId(
            messageIdHeader = "<reply@x>",
            inReplyToHeader = "<root@x>",
            referencesHeader = "<root@x>",
            subject = "Re: Hello",
        )

        assertEquals(root, reply)
        assertEquals("mid:<root@x>", root)
    }

    @Test
    fun `three-message chain all converge on the original root`() {
        val root = ConversationThreading.computeConversationId(
            messageIdHeader = "<a@x>",
            inReplyToHeader = null,
            referencesHeader = null,
            subject = "Plans",
        )
        val secondReply = ConversationThreading.computeConversationId(
            messageIdHeader = "<b@x>",
            inReplyToHeader = "<a@x>",
            referencesHeader = "<a@x>",
            subject = "Re: Plans",
        )
        // Third message's References accumulates the whole chain, oldest first.
        val thirdReply = ConversationThreading.computeConversationId(
            messageIdHeader = "<c@x>",
            inReplyToHeader = "<b@x>",
            referencesHeader = "<a@x> <b@x>",
            subject = "Re: Re: Plans",
        )

        assertEquals(root, secondReply)
        assertEquals(root, thirdReply)
    }

    @Test
    fun `references takes priority over in-reply-to`() {
        val id = ConversationThreading.computeConversationId(
            messageIdHeader = "<c@x>",
            inReplyToHeader = "<b@x>",
            referencesHeader = "<a@x> <b@x>",
            subject = "Re: Plans",
        )
        assertEquals("mid:<a@x>", id)
    }

    @Test
    fun `message with no threading headers keys off its own message id`() {
        val id = ConversationThreading.computeConversationId(
            messageIdHeader = "<solo@x>",
            inReplyToHeader = null,
            referencesHeader = null,
            subject = "Standalone",
        )
        assertEquals("mid:<solo@x>", id)
    }

    @Test
    fun `missing message id falls back to normalized subject`() {
        val id = ConversationThreading.computeConversationId(
            messageIdHeader = null,
            inReplyToHeader = null,
            referencesHeader = null,
            subject = "Re: Fwd: Weekly update",
        )
        assertEquals("subj:weekly update", id)
    }

    @Test
    fun `subject fallback strips repeated re and fwd prefixes case-insensitively`() {
        val id = ConversationThreading.computeConversationId(
            messageIdHeader = null,
            inReplyToHeader = null,
            referencesHeader = null,
            subject = "RE: Fwd: re: Budget review",
        )
        assertEquals("subj:budget review", id)
    }

    @Test
    fun `completely empty message falls back to a stable unknown key`() {
        val id = ConversationThreading.computeConversationId(
            messageIdHeader = null,
            inReplyToHeader = null,
            referencesHeader = null,
            subject = null,
        )
        assertEquals("mid:unknown", id)
    }

    @Test
    fun `a real message-id can never collide with a subject-derived key`() {
        val subjectDerived = ConversationThreading.computeConversationId(
            messageIdHeader = null,
            inReplyToHeader = null,
            referencesHeader = null,
            subject = "unknown",
        )
        assertNotEquals("mid:unknown", subjectDerived)
        assertEquals("subj:unknown", subjectDerived)
    }
}
