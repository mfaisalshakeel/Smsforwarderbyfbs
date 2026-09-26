package com.example.forwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTemplateTest {

    private val smsContext = MessageContext(
        sender = "HBL",
        body = "Your OTP is 1234",
        timestamp = 0L,
        source = "SMS",
        simSlot = 1,
        simName = "Jazz",
        ruleName = "Bank alerts"
    )

    private val notificationContext = MessageContext(
        sender = "WhatsApp",
        body = "See you at 5",
        timestamp = 0L,
        source = "NOTIFICATION",
        appName = "WhatsApp",
        packageName = "com.whatsapp",
        title = "Ahmed"
    )

    @Test
    fun `placeholders are substituted`() {
        val rendered = MessageTemplate.render("{sender}: {message}", smsContext)
        assertEquals("HBL: Your OTP is 1234", rendered)
    }

    @Test
    fun `message and body are interchangeable`() {
        assertEquals(
            MessageTemplate.render("{message}", smsContext),
            MessageTemplate.render("{body}", smsContext)
        )
    }

    @Test
    fun `the sim placeholder uses the carrier name when known`() {
        assertEquals("Jazz", MessageTemplate.render("{sim}", smsContext))
    }

    @Test
    fun `the sim placeholder is blank when the sim is unknown`() {
        assertEquals("", MessageTemplate.render("{sim}", smsContext.copy(simSlot = 0)))
    }

    @Test
    fun `notification subject names the app and the title`() {
        val subject = MessageTemplate.defaultSubject(notificationContext)
        assertTrue(subject.contains("WhatsApp"))
        assertTrue(subject.contains("Ahmed"))
    }

    @Test
    fun `sms subject names the sender`() {
        assertTrue(MessageTemplate.defaultSubject(smsContext).contains("HBL"))
    }

    @Test
    fun `branding is omitted when the user turns it off`() {
        val withBranding = MessageTemplate.defaultBody(smsContext, includeBranding = true)
        val without = MessageTemplate.defaultBody(smsContext, includeBranding = false)
        assertTrue(withBranding.contains("PenduCoder"))
        assertFalse(without.contains("PenduCoder"))
    }

    @Test
    fun `the body always contains the original message`() {
        assertTrue(MessageTemplate.defaultBody(smsContext, false).contains("Your OTP is 1234"))
        assertTrue(MessageTemplate.compactBody(notificationContext).contains("See you at 5"))
    }

    @Test
    fun `every advertised placeholder is actually supported`() {
        val template = MessageTemplate.PLACEHOLDERS.joinToString(" ")
        val rendered = MessageTemplate.render(template, smsContext)
        MessageTemplate.PLACEHOLDERS.forEach { placeholder ->
            assertFalse("$placeholder was not substituted", rendered.contains(placeholder))
        }
    }
}
