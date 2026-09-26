package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole point of this check is to catch the case where the user believes the app is
 * running and it is not, so the "healthy" path must be hard to reach by accident.
 */
class BackgroundHealthCheckerTest {

    private val now = 1_700_000_000_000L

    private fun evaluate(
        engineEnabled: Boolean = true,
        keepServiceAlive: Boolean = true,
        serviceRunning: Boolean = true,
        heartbeatStale: Boolean = false,
        lastHeartbeatAt: Long = now - 60_000L,
        hasSmsPermission: Boolean = true,
        hasNotificationAccess: Boolean = true,
        hasCallLogPermission: Boolean = true,
        appNotificationsEnabled: Boolean = true,
        isBatteryOptimised: Boolean = false,
        manufacturerNeedsAutostart: Boolean = false,
        senderAccountConfigured: Boolean = true,
        enabledRuleCount: Int = 1,
        rulesNeedingNotifications: Int = 0,
        rulesNeedingCallLog: Int = 0,
        queuedCount: Int = 0
    ) = BackgroundHealthChecker.evaluate(
        now = now,
        engineEnabled = engineEnabled,
        keepServiceAlive = keepServiceAlive,
        serviceRunning = serviceRunning,
        lastHeartbeatAt = lastHeartbeatAt,
        heartbeatStale = heartbeatStale,
        lastEventAt = now - 120_000L,
        lastDeliveryAt = now - 120_000L,
        lastBootRestartAt = 0L,
        hasSmsPermission = hasSmsPermission,
        hasNotificationAccess = hasNotificationAccess,
        hasCallLogPermission = hasCallLogPermission,
        appNotificationsEnabled = appNotificationsEnabled,
        isBatteryOptimised = isBatteryOptimised,
        manufacturerNeedsAutostart = manufacturerNeedsAutostart,
        senderAccountConfigured = senderAccountConfigured,
        enabledRuleCount = enabledRuleCount,
        rulesNeedingNotifications = rulesNeedingNotifications,
        rulesNeedingCallLog = rulesNeedingCallLog,
        queuedCount = queuedCount
    )

    @Test
    fun `a fully configured running engine is healthy`() {
        val health = evaluate()
        assertEquals(HealthStatus.WORKING, health.status)
        assertTrue(health.issues.isEmpty())
        assertTrue(health.isWorking)
    }

    @Test
    fun `a stopped service is reported as not working`() {
        val health = evaluate(serviceRunning = false)
        assertEquals(HealthStatus.NOT_WORKING, health.status)
        assertTrue(health.issues.any { it.id == "service_down" })
        assertEquals(HealthAction.RESTART_ENGINE, health.issues.first { it.id == "service_down" }.action)
    }

    /** The killer case: the flag still says running because onDestroy never got to run. */
    @Test
    fun `a stale heartbeat is reported even while the running flag says yes`() {
        val health = evaluate(serviceRunning = true, heartbeatStale = true)
        assertEquals(HealthStatus.NOT_WORKING, health.status)
        assertTrue(health.issues.any { it.id == "heartbeat_stale" })
    }

    @Test
    fun `the heartbeat is not checked when the service is not meant to be resident`() {
        val health = evaluate(keepServiceAlive = false, serviceRunning = false, heartbeatStale = true)
        assertFalse(health.issues.any { it.id == "heartbeat_stale" })
        assertFalse(health.issues.any { it.id == "service_down" })
        assertTrue(health.issues.any { it.id == "service_disabled" })
    }

    @Test
    fun `a switched off engine blocks everything`() {
        val health = evaluate(engineEnabled = false)
        assertEquals(HealthStatus.NOT_WORKING, health.status)
        assertEquals(HealthAction.ENABLE_ENGINE, health.issues.first { it.id == "engine_off" }.action)
    }

    @Test
    fun `missing sms permission blocks`() {
        assertTrue(evaluate(hasSmsPermission = false).issues.any { it.id == "no_sms_permission" })
    }

    @Test
    fun `no rules and no account both block`() {
        val health = evaluate(enabledRuleCount = 0, senderAccountConfigured = false)
        assertEquals(HealthStatus.NOT_WORKING, health.status)
        assertTrue(health.issues.any { it.id == "no_rules" })
        assertTrue(health.issues.any { it.id == "no_account" })
    }

    @Test
    fun `blocked notifications block, because the service cannot stay up without them`() {
        val health = evaluate(appNotificationsEnabled = false)
        assertEquals(HealthStatus.NOT_WORKING, health.status)
        assertTrue(health.issues.any { it.id == "notifications_blocked" })
    }

    @Test
    fun `battery optimisation is a warning rather than a block`() {
        val health = evaluate(isBatteryOptimised = true)
        assertEquals(HealthStatus.AT_RISK, health.status)
        assertEquals(
            IssueSeverity.WARNING,
            health.issues.first { it.id == "battery_optimised" }.severity
        )
    }

    @Test
    fun `notification access is only required when a rule actually needs it`() {
        assertFalse(
            evaluate(hasNotificationAccess = false, rulesNeedingNotifications = 0)
                .issues.any { it.id == "no_notification_access" }
        )
        assertTrue(
            evaluate(hasNotificationAccess = false, rulesNeedingNotifications = 2)
                .issues.any { it.id == "no_notification_access" }
        )
    }

    @Test
    fun `call log access is only required when a rule forwards missed calls`() {
        assertFalse(
            evaluate(hasCallLogPermission = false, rulesNeedingCallLog = 0)
                .issues.any { it.id == "no_call_log" }
        )
        assertTrue(
            evaluate(hasCallLogPermission = false, rulesNeedingCallLog = 1)
                .issues.any { it.id == "no_call_log" }
        )
    }

    @Test
    fun `queued messages are surfaced without claiming the engine is broken`() {
        val health = evaluate(queuedCount = 3)
        assertEquals(HealthStatus.AT_RISK, health.status)
        assertTrue(health.issues.any { it.id == "queued" && it.title.contains("3") })
    }

    @Test
    fun `a blocking issue always outranks a warning`() {
        val health = evaluate(serviceRunning = false, isBatteryOptimised = true)
        assertEquals(HealthStatus.NOT_WORKING, health.status)
        assertEquals(1, health.blockingIssues.size)
    }

    @Test
    fun `the headline never claims it is working when it is not`() {
        assertTrue(evaluate().headline.contains("is working"))
        assertTrue(evaluate(serviceRunning = false).headline.contains("NOT working"))
        assertTrue(evaluate(isBatteryOptimised = true).headline.contains("may stop"))
    }

    @Test
    fun `age wording reads naturally at each scale`() {
        assertEquals("less than a minute", BackgroundHealthChecker.describeAge(30_000L))
        assertEquals("1 minute", BackgroundHealthChecker.describeAge(60_000L))
        assertEquals("5 minutes", BackgroundHealthChecker.describeAge(5 * 60_000L))
        assertEquals("1 hour", BackgroundHealthChecker.describeAge(60 * 60_000L))
        assertEquals("3 hours", BackgroundHealthChecker.describeAge(3 * 60 * 60_000L))
        assertEquals("2 days", BackgroundHealthChecker.describeAge(2 * 24 * 60 * 60_000L))
    }
}
