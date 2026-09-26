package com.example.forwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestTemplateTest {

    private val entries = listOf(
        MessageTemplate.DigestEntry("HBL", "OTP 1111", 1_700_000_000_000L, MessageSource.SMS),
        MessageTemplate.DigestEntry("UBL", "OTP 2222", 1_700_000_060_000L, MessageSource.SMS),
        MessageTemplate.DigestEntry("+9230012", "Missed call", 1_700_000_120_000L, MessageSource.CALL)
    )

    @Test
    fun `the subject counts the messages`() {
        assertTrue(MessageTemplate.digestSubject("Bank alerts", 3).contains("3 messages"))
        assertTrue(MessageTemplate.digestSubject("Bank alerts", 1).contains("1 message"))
        assertFalse(MessageTemplate.digestSubject("Bank alerts", 1).contains("1 messages"))
    }

    @Test
    fun `every collected message appears in the body`() {
        val body = MessageTemplate.digestBody("Bank alerts", entries, includeBranding = false)
        entries.forEach { entry ->
            assertTrue("missing ${entry.sender}", body.contains(entry.sender))
            assertTrue("missing ${entry.body}", body.contains(entry.body))
        }
    }

    @Test
    fun `the digest names the rule that collected it`() {
        assertTrue(
            MessageTemplate.digestBody("Bank alerts", entries, false).contains("Bank alerts")
        )
    }

    @Test
    fun `branding is omitted when the user turns it off`() {
        assertFalse(MessageTemplate.digestBody("R", entries, false).contains("PenduCoder"))
        assertTrue(MessageTemplate.digestBody("R", entries, true).contains("PenduCoder"))
    }

    @Test
    fun `each source is labelled so a mixed digest reads clearly`() {
        val body = MessageTemplate.digestBody("R", entries, false)
        assertTrue(body.contains("Text message"))
        assertTrue(body.contains("Missed call"))
    }

    @Test
    fun `source labels cover every kind`() {
        assertEquals("Text message", MessageSource.label(MessageSource.SMS))
        assertEquals("Picture message", MessageSource.label(MessageSource.MMS))
        assertEquals("App notification", MessageSource.label(MessageSource.NOTIFICATION))
        assertEquals("Missed call", MessageSource.label(MessageSource.CALL))
    }
}
