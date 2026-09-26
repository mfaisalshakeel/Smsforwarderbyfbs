package com.example.forwarder

import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.MatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The filter surface that decides whether a message is forwarded at all. */
class RuleMatcherTest {

    private fun rule(
        senderType: String = MatchType.ANY,
        senderValue: String = "",
        senderExclude: String = "",
        contentType: String = MatchType.ANY,
        contentValue: String = "",
        contentExclude: String = "",
        packages: String = "",
        forwardSms: Boolean = true,
        forwardMms: Boolean = true,
        forwardMissedCalls: Boolean = true,
        forwardNotifications: Boolean = true,
        simSlot: Int = 0,
        scheduleEnabled: Boolean = false,
        start: Int = 0,
        end: Int = 1439,
        days: String = "1,2,3,4,5,6,7"
    ) = ForwardingRuleEntity(
        id = 1,
        name = "Test",
        recipientEmail = "to@example.com",
        senderFilterType = senderType,
        senderFilterValue = senderValue,
        senderExcludeValue = senderExclude,
        contentFilterType = contentType,
        contentFilterValue = contentValue,
        contentExcludeValue = contentExclude,
        appPackages = packages,
        forwardSms = forwardSms,
        forwardMms = forwardMms,
        forwardMissedCalls = forwardMissedCalls,
        forwardNotifications = forwardNotifications,
        simSlot = simSlot,
        scheduleEnabled = scheduleEnabled,
        scheduleStartMinute = start,
        scheduleEndMinute = end,
        scheduleDays = days
    )

    private fun smsContext(sender: String = "HBL", body: String = "Your OTP is 1234", simSlot: Int = 0) =
        MessageContext(sender = sender, body = body, timestamp = 0L, source = "SMS", simSlot = simSlot)

    private fun notificationContext(
        app: String = "WhatsApp",
        pkg: String = "com.whatsapp",
        title: String = "Ahmed",
        body: String = "Hello"
    ) = MessageContext(
        sender = app, body = body, timestamp = 0L, source = "NOTIFICATION",
        appName = app, packageName = pkg, title = title
    )

    // ---------------------------------------------------------------- include matching

    @Test
    fun `any type matches everything`() {
        assertTrue(RuleMatcher.matchesValue("anything", MatchType.ANY, "ignored"))
    }

    @Test
    fun `contains matches a substring of the candidate`() {
        assertTrue(RuleMatcher.matchesValue("HBL-Bank", MatchType.CONTAINS, "hbl"))
    }

    /**
     * Regression: the original filter also tested `needle.contains(candidate)`, so a filter of
     * "whatsapp business" matched a sender literally called "app".
     */
    @Test
    fun `contains does not match in reverse`() {
        assertFalse(RuleMatcher.matchesValue("app", MatchType.CONTAINS, "whatsapp business"))
    }

    @Test
    fun `exact requires the whole value`() {
        assertTrue(RuleMatcher.matchesValue("HBL", MatchType.EXACT, "hbl"))
        assertFalse(RuleMatcher.matchesValue("HBL-Bank", MatchType.EXACT, "hbl"))
    }

    @Test
    fun `starts with anchors at the beginning`() {
        assertTrue(RuleMatcher.matchesValue("+923001234567", MatchType.STARTS_WITH, "+9230"))
        assertFalse(RuleMatcher.matchesValue("+923001234567", MatchType.STARTS_WITH, "1234"))
    }

    @Test
    fun `regex matches a pattern`() {
        assertTrue(RuleMatcher.matchesValue("Your code is 4821", MatchType.REGEX, "\\d{4}"))
        assertFalse(RuleMatcher.matchesValue("No digits here", MatchType.REGEX, "\\d{4}"))
    }

    @Test
    fun `an invalid regex does not match rather than crashing`() {
        assertFalse(RuleMatcher.matchesValue("anything", MatchType.REGEX, "([unclosed"))
    }

    @Test
    fun `comma separated values are treated as alternatives`() {
        assertTrue(RuleMatcher.matchesValue("UBL", MatchType.EXACT, "HBL, UBL, MCB"))
    }

    // ---------------------------------------------------------------- exclusions

    @Test
    fun `exclusion beats the include filter`() {
        val decision = RuleMatcher.evaluate(
            rule(senderType = MatchType.CONTAINS, senderValue = "bank", senderExclude = "spambank"),
            smsContext(sender = "SpamBank"),
            minuteOfDay = 600,
            isoDay = 3
        )
        assertTrue(decision is RuleMatcher.Decision.Skip)
    }

    @Test
    fun `content exclusion vetoes a match`() {
        val decision = RuleMatcher.evaluate(
            rule(contentExclude = "advertisement"),
            smsContext(body = "Great advertisement offer"),
            minuteOfDay = 600,
            isoDay = 3
        )
        assertTrue(decision is RuleMatcher.Decision.Skip)
    }

    // ---------------------------------------------------------------- app selection

    @Test
    fun `blank package list means every app`() {
        assertTrue(RuleMatcher.matchesApp("com.anything", rule(packages = "")))
    }

    @Test
    fun `only selected packages match`() {
        val r = rule(packages = "com.whatsapp,org.telegram.messenger")
        assertTrue(RuleMatcher.matchesApp("com.whatsapp", r))
        assertFalse(RuleMatcher.matchesApp("com.facebook.katana", r))
    }

    @Test
    fun `notification from an unselected app is skipped`() {
        val decision = RuleMatcher.evaluate(
            rule(packages = "com.whatsapp"),
            notificationContext(pkg = "com.facebook.katana"),
            minuteOfDay = 600,
            isoDay = 3
        )
        assertTrue(decision is RuleMatcher.Decision.Skip)
    }

    // ---------------------------------------------------------------- sources

    @Test
    fun `a rule that ignores sms skips an sms`() {
        val decision = RuleMatcher.evaluate(
            rule(forwardSms = false),
            smsContext(),
            minuteOfDay = 600,
            isoDay = 3
        )
        assertTrue(decision is RuleMatcher.Decision.Skip)
    }

    @Test
    fun `a missed call is skipped unless the rule asks for one`() {
        val callCtx = MessageContext(
            sender = "+923001234567", body = "Missed call", timestamp = 0L,
            source = MessageSource.CALL
        )
        assertTrue(
            RuleMatcher.evaluate(rule(forwardMissedCalls = false), callCtx, 600, 3)
                is RuleMatcher.Decision.Skip
        )
        assertEquals(
            RuleMatcher.Decision.Match,
            RuleMatcher.evaluate(rule(forwardMissedCalls = true), callCtx, 600, 3)
        )
    }

    /** A call carries no text, so a keyword filter would otherwise drop every one. */
    @Test
    fun `a keyword filter does not apply to missed calls`() {
        val callCtx = MessageContext(
            sender = "+923001234567", body = "Missed call", timestamp = 0L,
            source = MessageSource.CALL
        )
        assertEquals(
            RuleMatcher.Decision.Match,
            RuleMatcher.evaluate(
                rule(contentType = MatchType.CONTAINS, contentValue = "otp"),
                callCtx, 600, 3
            )
        )
    }

    @Test
    fun `an mms is skipped unless the rule asks for one`() {
        val mmsCtx = MessageContext(
            sender = "+923001234567", body = "Look at this", timestamp = 0L,
            source = MessageSource.MMS
        )
        assertTrue(
            RuleMatcher.evaluate(rule(forwardMms = false), mmsCtx, 600, 3)
                is RuleMatcher.Decision.Skip
        )
        assertEquals(
            RuleMatcher.Decision.Match,
            RuleMatcher.evaluate(rule(forwardMms = true), mmsCtx, 600, 3)
        )
    }

    @Test
    fun `sim filter only applies when the sim is known`() {
        val r = rule(simSlot = 2)
        // Slot 0 means "we could not tell", and must not silently drop the message.
        assertEquals(
            RuleMatcher.Decision.Match,
            RuleMatcher.evaluate(r, smsContext(simSlot = 0), 600, 3)
        )
        assertTrue(RuleMatcher.evaluate(r, smsContext(simSlot = 1), 600, 3) is RuleMatcher.Decision.Skip)
        assertEquals(
            RuleMatcher.Decision.Match,
            RuleMatcher.evaluate(r, smsContext(simSlot = 2), 600, 3)
        )
    }

    // ---------------------------------------------------------------- schedule

    @Test
    fun `a normal window includes its endpoints`() {
        assertTrue(RuleMatcher.isWithinWindow(540, 540, 1020))
        assertTrue(RuleMatcher.isWithinWindow(1020, 540, 1020))
        assertFalse(RuleMatcher.isWithinWindow(1021, 540, 1020))
    }

    @Test
    fun `a window that crosses midnight is one window not an empty one`() {
        // 22:00 to 07:00
        assertTrue(RuleMatcher.isWithinWindow(23 * 60, 22 * 60, 7 * 60))
        assertTrue(RuleMatcher.isWithinWindow(2 * 60, 22 * 60, 7 * 60))
        assertFalse(RuleMatcher.isWithinWindow(12 * 60, 22 * 60, 7 * 60))
    }

    @Test
    fun `schedule respects the selected days`() {
        val r = rule(scheduleEnabled = true, days = "1,2,3,4,5")
        assertTrue(RuleMatcher.matchesSchedule(r, 600, 3))
        assertFalse(RuleMatcher.matchesSchedule(r, 600, 6))
    }

    @Test
    fun `a disabled schedule always matches`() {
        assertTrue(RuleMatcher.matchesSchedule(rule(scheduleEnabled = false), 0, 7))
    }

    // ---------------------------------------------------------------- full evaluation

    @Test
    fun `a fully matching sms is forwarded`() {
        val decision = RuleMatcher.evaluate(
            rule(
                senderType = MatchType.CONTAINS, senderValue = "hbl",
                contentType = MatchType.CONTAINS, contentValue = "otp"
            ),
            smsContext(sender = "HBL-Bank", body = "Your OTP is 1234"),
            minuteOfDay = 600,
            isoDay = 3
        )
        assertEquals(RuleMatcher.Decision.Match, decision)
    }

    @Test
    fun `notification sender filter also checks the package name`() {
        val decision = RuleMatcher.evaluate(
            rule(senderType = MatchType.CONTAINS, senderValue = "com.whatsapp"),
            notificationContext(app = "WhatsApp", pkg = "com.whatsapp"),
            minuteOfDay = 600,
            isoDay = 3
        )
        assertEquals(RuleMatcher.Decision.Match, decision)
    }

    @Test
    fun `sunday maps to iso day seven`() {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        }
        assertEquals(7, RuleMatcher.isoDayOfWeek(calendar))
    }

    @Test
    fun `monday maps to iso day one`() {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        }
        assertEquals(1, RuleMatcher.isoDayOfWeek(calendar))
    }
}
