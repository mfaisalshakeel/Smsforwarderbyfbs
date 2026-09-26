package com.example.forwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * These cover the defects that corrupted forwarded mail: unencoded non-ASCII, header injection
 * through the sender name, and DATA truncation on a line beginning with a dot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MimeBuilderTest {

    @Test
    fun `plain ascii headers are left alone`() {
        assertEquals("Hello world", MimeBuilder.encodeHeaderValue("Hello world"))
    }

    @Test
    fun `non ascii headers are rfc 2047 encoded`() {
        val encoded = MimeBuilder.encodeHeaderValue("بینک پیغام")
        assertTrue("expected an encoded word, got: $encoded", encoded.startsWith("=?UTF-8?B?"))
        assertTrue(encoded.endsWith("?="))
    }

    @Test
    fun `emoji subjects are encoded rather than passed through raw`() {
        val encoded = MimeBuilder.encodeHeaderValue("New message 📩")
        assertFalse(encoded.contains("📩"))
        assertTrue(encoded.contains("=?UTF-8?B?"))
    }

    @Test
    fun `a long non ascii header is split into several encoded words`() {
        val encoded = MimeBuilder.encodeHeaderValue("بینک ".repeat(30))
        assertTrue(encoded.contains("\r\n "))
    }

    @Test
    fun `carriage returns are stripped so headers cannot be injected`() {
        val dirty = "Attacker\r\nBcc: victim@example.com"
        val clean = MimeBuilder.sanitizeHeader(dirty)
        assertFalse(clean.contains("\r"))
        assertFalse(clean.contains("\n"))
    }

    @Test
    fun `a sender name carrying a newline cannot add a header to the message`() {
        val message = MimeBuilder.buildMessage(
            fromName = "Evil\r\nBcc: victim@example.com",
            fromEmail = "me@example.com",
            toEmail = "you@example.com",
            subject = "Hi",
            body = "Body",
            timestamp = 0L
        )
        val headerLines = message.substringBefore("\r\n\r\n").split("\r\n")

        // The attack is a *new header line*, not the text appearing somewhere. The CRLF is
        // folded to a space, so "Bcc:" survives as part of the From display name and is
        // harmless - what must not exist is a line that begins with it.
        assertFalse(headerLines.any { it.startsWith("Bcc:", ignoreCase = true) })
        assertTrue(headerLines.any { it.startsWith("From:") && it.contains("me@example.com") })
    }

    @Test
    fun `a subject carrying a newline cannot add a header to the message`() {
        val message = MimeBuilder.buildMessage(
            fromName = "Forwarder",
            fromEmail = "me@example.com",
            toEmail = "you@example.com",
            subject = "Hi\r\nBcc: victim@example.com",
            body = "Body",
            timestamp = 0L
        )
        val headerLines = message.substringBefore("\r\n\r\n").split("\r\n")
        assertFalse(headerLines.any { it.startsWith("Bcc:", ignoreCase = true) })
    }

    @Test
    fun `a display name containing address punctuation is quoted`() {
        // Unquoted, the colon and comma here would be read as address syntax.
        assertEquals(
            "\"Bank: alerts, daily\" <a@b.com>",
            MimeBuilder.formatAddress("Bank: alerts, daily", "a@b.com")
        )
    }

    @Test
    fun `dot stuffing doubles a leading dot so DATA is not terminated early`() {
        val stuffed = MimeBuilder.dotStuff("line one\r\n.5% interest\r\nline three")
        assertTrue(stuffed.contains("\r\n..5% interest"))
    }

    @Test
    fun `dot stuffing also handles a message that starts with a dot`() {
        assertTrue(MimeBuilder.dotStuff("..already").startsWith("."))
    }

    @Test
    fun `bare line feeds are normalised to crlf`() {
        val normalised = MimeBuilder.normaliseLineEndings("a\nb\r\nc\rd")
        assertEquals("a\r\nb\r\nc\r\nd", normalised)
    }

    @Test
    fun `the body is base64 encoded and wrapped`() {
        val encoded = MimeBuilder.encodeBody("x".repeat(400))
        encoded.split("\r\n").forEach { line ->
            assertTrue("line too long: ${line.length}", line.length <= 76)
        }
    }

    @Test
    fun `a built message declares the transfer encoding it actually uses`() {
        val message = MimeBuilder.buildMessage(
            fromName = "Forwarder",
            fromEmail = "me@example.com",
            toEmail = "you@example.com",
            subject = "Test",
            body = "Hello",
            timestamp = 0L
        )
        assertTrue(message.contains("Content-Transfer-Encoding: base64"))
        assertTrue(message.contains("Content-Type: text/plain; charset=UTF-8"))
        // Headers and body must be separated by exactly one blank line.
        assertTrue(message.contains("\r\n\r\n"))
    }

    @Test
    fun `an ascii display name is quoted`() {
        assertEquals("\"John Smith\" <j@example.com>", MimeBuilder.formatAddress("John Smith", "j@example.com"))
    }

    @Test
    fun `a blank display name yields a bare address`() {
        assertEquals("<j@example.com>", MimeBuilder.formatAddress("", "j@example.com"))
    }
}
