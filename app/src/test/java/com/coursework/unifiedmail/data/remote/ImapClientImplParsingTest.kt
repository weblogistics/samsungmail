package com.coursework.unifiedmail.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * ImapClientImpl.parseBody/isAttachmentPart have never run against a real MIME tree (only ever
 * exercised via FakeImapClient in MailRepositoryTest, which bypasses this parsing entirely) —
 * diagnostic coverage for the report that attachments aren't detected on a real account.
 * Builds a message the same way a real client (e.g. Gmail) would, serializes it to raw RFC822
 * bytes and reparses it, to reproduce exactly what JavaMail hands back for a real IMAP fetch.
 */
class ImapClientImplParsingTest {

    private val session = Session.getDefaultInstance(Properties())

    private fun reparsed(build: MimeMessage.() -> Unit): MimeMessage {
        val original = MimeMessage(session).apply(build)
        val out = ByteArrayOutputStream()
        original.writeTo(out)
        return MimeMessage(session, ByteArrayInputStream(out.toByteArray()))
    }

    private fun invokeParseBody(part: Part): Triple<String?, String?, List<ImapAttachmentInfo>> {
        val client = ImapClientImpl()
        val method = ImapClientImpl::class.java.getDeclaredMethod("parseBody", Part::class.java)
        method.isAccessible = true
        val result = method.invoke(client, part)
        val resultClass = result.javaClass
        val plainText = resultClass.getDeclaredField("plainText").apply { isAccessible = true }.get(result) as String?
        val html = resultClass.getDeclaredField("html").apply { isAccessible = true }.get(result) as String?
        @Suppress("UNCHECKED_CAST")
        val attachments = resultClass.getDeclaredField("attachments").apply { isAccessible = true }.get(result) as List<ImapAttachmentInfo>
        return Triple(plainText, html, attachments)
    }

    /** Mirrors [invokeParseBody] for the private, reflection-only download-time counterpart. */
    private fun invokeFindAttachmentPart(part: Part, targetIndex: Int): Part? {
        val client = ImapClientImpl()
        val method = ImapClientImpl::class.java.getDeclaredMethod(
            "findAttachmentPart", Part::class.java, Int::class.javaPrimitiveType, IntArray::class.java,
        )
        method.isAccessible = true
        return method.invoke(client, part, targetIndex, IntArray(1)) as Part?
    }

    @Test
    fun `a Content-Disposition attachment part is detected`() {
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Photo"
            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(MimeBodyPart().apply { setText("See attached", "UTF-8") })
            multipart.addBodyPart(
                MimeBodyPart().apply {
                    setContent(ByteArray(10), "image/jpeg")
                    fileName = "photo.jpg"
                    disposition = Part.ATTACHMENT
                },
            )
            setContent(multipart)
        }

        val (plainText, _, attachments) = invokeParseBody(message)

        assertEquals("See attached", plainText)
        assertEquals(1, attachments.size)
        assertEquals("photo.jpg", attachments.single().fileName)
    }

    @Test
    fun `an attachment nested under multipart alternative plus mixed is still detected`() {
        // Common real-world shape: multipart/mixed(multipart/alternative(plain, html), attachment)
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Photo with HTML body"
            val alternative = MimeMultipart("alternative")
            alternative.addBodyPart(MimeBodyPart().apply { setText("Plain body", "UTF-8") })
            alternative.addBodyPart(MimeBodyPart().apply { setContent("<p>HTML body</p>", "text/html; charset=UTF-8") })
            val alternativePart = MimeBodyPart().apply { setContent(alternative) }

            val mixed = MimeMultipart("mixed")
            mixed.addBodyPart(alternativePart)
            mixed.addBodyPart(
                MimeBodyPart().apply {
                    setContent(ByteArray(10), "application/pdf")
                    fileName = "notes.pdf"
                    disposition = Part.ATTACHMENT
                },
            )
            setContent(mixed)
        }

        val (plainText, html, attachments) = invokeParseBody(message)

        assertEquals("Plain body", plainText)
        assertTrue(html?.contains("HTML body") == true)
        assertEquals(1, attachments.size)
        assertEquals("notes.pdf", attachments.single().fileName)
    }

    @Test
    fun `an inline cid image is embedded as a data URI and not listed as a downloadable attachment`() {
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Signature with logo"
            val related = MimeMultipart("related")
            related.addBodyPart(MimeBodyPart().apply { setContent("<p>Hi</p><img src=\"cid:logo123\">", "text/html; charset=UTF-8") })
            related.addBodyPart(
                MimeBodyPart().apply {
                    setContent(byteArrayOf(1, 2, 3, 4), "image/png")
                    contentID = "<logo123>"
                },
            )
            setContent(related)
        }

        val (_, html, attachments) = invokeParseBody(message)

        assertTrue(attachments.isEmpty())
        assertTrue(html?.contains("cid:logo123") == false)
        assertTrue(html?.startsWith("<p>Hi</p><img src=\"data:image/png;base64,") == true)
    }

    @Test
    fun `multiple inline cid images resolve correctly even when one id is a prefix of another`() {
        // resolveInlineImages matches all cid references in one regex pass; if the alternation
        // isn't ordered longest-id-first, "cid:img1" can match inside "cid:img12" and leave a
        // stray "2" (the unmatched tail of the longer id) sitting in the output HTML.
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Two inline images"
            val related = MimeMultipart("related")
            related.addBodyPart(
                MimeBodyPart().apply {
                    setContent(
                        "<img src=\"cid:img12\"><img src=\"cid:img1\">",
                        "text/html; charset=UTF-8",
                    )
                },
            )
            related.addBodyPart(
                MimeBodyPart().apply {
                    setContent(byteArrayOf(1, 2, 3), "image/png")
                    contentID = "<img12>"
                },
            )
            related.addBodyPart(
                MimeBodyPart().apply {
                    setContent(byteArrayOf(4, 5, 6), "image/png")
                    contentID = "<img1>"
                },
            )
            setContent(related)
        }

        val (_, html, attachments) = invokeParseBody(message)

        assertTrue(attachments.isEmpty())
        requireNotNull(html)
        assertTrue(html.contains("cid:img") == false)
        val img12Data = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val img1Data = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(byteArrayOf(4, 5, 6))
        assertTrue(html.contains("<img src=\"$img12Data\">"))
        assertTrue(html.contains("<img src=\"$img1Data\">"))
    }

    @Test
    fun `an HTML-only message falls back to visible text, not the markup itself`() {
        // Common real-world shape for a marketing/newsletter email: no text/plain alternative at
        // all, and a sizeable inline stylesheet ahead of the actual visible content — previously
        // the CSS rules leaked straight into the plain-text fallback used by the list preview.
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Newsletter"
            setContent(
                "<html><head><style>body { color: #333; font-family: Arial; }</style></head>" +
                    "<body><!--[if mso]>ignored<![endif]--><p>Hello&nbsp;there &amp; welcome</p></body></html>",
                "text/html; charset=UTF-8",
            )
        }

        val (plainText, _, _) = invokeParseBody(message)

        assertEquals("Hello there & welcome", plainText)
    }

    @Test
    fun `an attachment with no Content-Disposition but a filename is still detected`() {
        // Some senders omit Content-Disposition and rely on the filename/Content-Type alone.
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "No disposition header"
            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(MimeBodyPart().apply { setText("Body", "UTF-8") })
            multipart.addBodyPart(
                MimeBodyPart().apply {
                    setContent(ByteArray(10), "application/octet-stream")
                    fileName = "data.bin"
                },
            )
            setContent(multipart)
        }

        val (_, _, attachments) = invokeParseBody(message)

        assertEquals(1, attachments.size)
        assertEquals("data.bin", attachments.single().fileName)
    }

    @Test
    fun `a plain-text part that is itself HTML-escaped markup yields readable text, not escaped tags`() {
        // Some senders' mail-merge tools HTML-escape their own HTML source into the "plain text"
        // alternative instead of writing real content — i.e. the raw MIME content is literally
        // "&lt;html&gt;&lt;body&gt;...", with no real tags to strip until that's decoded. Real-world
        // report: this showed up verbatim as "<html><body><span>" in the message list preview.
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Escaped HTML plain-text part"
            setText(
                "&lt;html&gt;&lt;body&gt;&lt;span&gt;Your records are ready&lt;/span&gt;&lt;/body&gt;&lt;/html&gt;",
                "UTF-8",
            )
        }

        val (plainText, _, _) = invokeParseBody(message)

        assertEquals("Your records are ready", plainText)
    }

    @Test
    fun `downloading attachment index 0 returns the real attachment, not a filenamed inline cid image`() {
        // Real-world report: an email with both an inline logo (Content-ID'd, referenced via
        // cid: in the HTML) and a real PDF attachment — the PDF showed as attachment index 0 (the
        // image is excluded from the list, see the cid-image test above), but downloading index 0
        // actually returned the image's bytes. Cause: the inline image here also carries a
        // filename, which real mail clients commonly do even for inline parts — satisfying
        // isAttachmentPart the same way a real attachment does — so findAttachmentPart's counting
        // has to skip it exactly like parseBody's walk does, or the two drift apart.
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Image and PDF"
            val related = MimeMultipart("related")
            related.addBodyPart(MimeBodyPart().apply { setContent("<p>Hi</p><img src=\"cid:logo123\">", "text/html; charset=UTF-8") })
            related.addBodyPart(
                MimeBodyPart().apply {
                    setContent(byteArrayOf(1, 2, 3, 4), "image/png")
                    contentID = "<logo123>"
                    fileName = "image001.png"
                },
            )

            val mixed = MimeMultipart("mixed")
            mixed.addBodyPart(MimeBodyPart().apply { setContent(related) })
            mixed.addBodyPart(
                MimeBodyPart().apply {
                    setContent(ByteArray(10), "application/pdf")
                    fileName = "notes.pdf"
                    disposition = Part.ATTACHMENT
                },
            )
            setContent(mixed)
        }

        val (_, _, attachments) = invokeParseBody(message)
        assertEquals(1, attachments.size)
        assertEquals("notes.pdf", attachments.single().fileName)

        val resolved = invokeFindAttachmentPart(message, 0)

        assertEquals("notes.pdf", resolved?.fileName)
    }

    @Test
    fun `a forwarded email attached as message-rfc822 is listed as an attachment instead of silently vanishing`() {
        // Real-world report: forwarding an email as a real attachment (not inline-quoted text —
        // common from Outlook and support/escalation workflows) has no multipart/text/html
        // content of its own, so recursing into it like any other unrecognized part found nothing
        // and the forwarded message disappeared entirely with no error. Often has no filename of
        // its own either, which previously would have reproduced the "random app chooser" bug
        // already fixed for PDFs with unreliable metadata.
        val forwarded = MimeMessage(session).apply {
            setFrom("original-sender@example.com")
            subject = "Original subject"
            setText("Original body", "UTF-8")
        }
        val message = reparsed {
            setFrom("sender@example.com")
            subject = "Fwd: something"
            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(MimeBodyPart().apply { setText("See the forwarded message below", "UTF-8") })
            multipart.addBodyPart(MimeBodyPart().apply { setContent(forwarded, "message/rfc822") })
            setContent(multipart)
        }

        val (_, _, attachments) = invokeParseBody(message)

        assertEquals(1, attachments.size)
        assertEquals("forwarded-message.eml", attachments.single().fileName)
    }
}
