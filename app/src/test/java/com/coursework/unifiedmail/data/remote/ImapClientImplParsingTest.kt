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
}
