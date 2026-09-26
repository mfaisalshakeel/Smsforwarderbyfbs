package com.example.util

/** Overall verdict on whether background forwarding is actually working. */
enum class HealthStatus {
    /** Everything needed is in place and the engine has proved it is alive. */
    WORKING,

    /** It will probably work, but something makes it likely to stop. */
    AT_RISK,

    /** It is not working right now. Messages are being missed. */
    NOT_WORKING
}

enum class IssueSeverity { BLOCKING, WARNING }

/** Something the user can do about an issue. The UI maps these to handlers. */
enum class HealthAction {
    ENABLE_ENGINE,
    GRANT_SMS_PERMISSION,
    GRANT_NOTIFICATION_ACCESS,
    GRANT_CALL_LOG,
    FIX_BATTERY,
    ENABLE_APP_NOTIFICATIONS,
    OPEN_SETUP,
    CREATE_RULE,
    RESTART_ENGINE,
    OPEN_AUTOSTART_HELP
}

data class HealthIssue(
    val id: String,
    val severity: IssueSeverity,
    val title: String,
    val detail: String,
    val actionLabel: String? = null,
    val action: HealthAction? = null
)

data class BackgroundHealth(
    val status: HealthStatus,
    val issues: List<HealthIssue>,
    val lastHeartbeatAt: Long,
    val lastEventAt: Long,
    val lastDeliveryAt: Long,
    val lastBootRestartAt: Long
) {
    val isWorking: Boolean get() = status == HealthStatus.WORKING

    /** One line for the top of the dashboard. */
    val headline: String
        get() = when (status) {
            HealthStatus.WORKING -> "Background forwarding is working"
            HealthStatus.AT_RISK -> "Background forwarding may stop"
            HealthStatus.NOT_WORKING -> "Background forwarding is NOT working"
        }

    val blockingIssues: List<HealthIssue> get() = issues.filter { it.severity == IssueSeverity.BLOCKING }
}

/**
 * Works out whether the app is genuinely forwarding in the background.
 *
 * Kept as a pure function over plain inputs so every branch can be unit tested. A switch being
 * on is not evidence of anything — the point of this check is to catch the cases where the
 * user believes the app is running and it is not.
 */
object BackgroundHealthChecker {

    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun evaluate(
        now: Long,
        engineEnabled: Boolean,
        keepServiceAlive: Boolean,
        serviceRunning: Boolean,
        lastHeartbeatAt: Long,
        heartbeatStale: Boolean,
        lastEventAt: Long,
        lastDeliveryAt: Long,
        lastBootRestartAt: Long,
        hasSmsPermission: Boolean,
        hasNotificationAccess: Boolean,
        hasCallLogPermission: Boolean,
        appNotificationsEnabled: Boolean,
        isBatteryOptimised: Boolean,
        manufacturerNeedsAutostart: Boolean,
        senderAccountConfigured: Boolean,
        enabledRuleCount: Int,
        rulesNeedingNotifications: Int,
        rulesNeedingCallLog: Int,
        queuedCount: Int
    ): BackgroundHealth {
        val issues = mutableListOf<HealthIssue>()

        if (!engineEnabled) {
            issues += HealthIssue(
                id = "engine_off",
                severity = IssueSeverity.BLOCKING,
                title = "Forwarding is switched off",
                detail = "Nothing will be forwarded until you turn it back on.",
                actionLabel = "Turn on",
                action = HealthAction.ENABLE_ENGINE
            )
        }

        if (!hasSmsPermission) {
            issues += HealthIssue(
                id = "no_sms_permission",
                severity = IssueSeverity.BLOCKING,
                title = "The app cannot read incoming messages",
                detail = "Without the SMS permission Android never tells the app a message arrived.",
                actionLabel = "Grant",
                action = HealthAction.GRANT_SMS_PERMISSION
            )
        }

        if (!senderAccountConfigured) {
            issues += HealthIssue(
                id = "no_account",
                severity = IssueSeverity.BLOCKING,
                title = "No sending account connected",
                detail = "Messages are being detected but there is nowhere to send them from.",
                actionLabel = "Set up",
                action = HealthAction.OPEN_SETUP
            )
        }

        if (enabledRuleCount == 0) {
            issues += HealthIssue(
                id = "no_rules",
                severity = IssueSeverity.BLOCKING,
                title = "No active rules",
                detail = "A rule decides what gets forwarded and where. Nothing happens without one.",
                actionLabel = "Create",
                action = HealthAction.CREATE_RULE
            )
        }

        // The engine is supposed to be resident but is not, or stopped proving it is alive.
        if (engineEnabled && keepServiceAlive) {
            if (!serviceRunning) {
                issues += HealthIssue(
                    id = "service_down",
                    severity = IssueSeverity.BLOCKING,
                    title = "The background service is not running",
                    detail = "Android has stopped the app. Messages that arrive now will be missed.",
                    actionLabel = "Restart",
                    action = HealthAction.RESTART_ENGINE
                )
            } else if (heartbeatStale) {
                issues += HealthIssue(
                    id = "heartbeat_stale",
                    severity = IssueSeverity.BLOCKING,
                    title = "The background service stopped responding",
                    detail = "It last checked in " + describeAge(now - lastHeartbeatAt) +
                        " ago. The process was probably killed.",
                    actionLabel = "Restart",
                    action = HealthAction.RESTART_ENGINE
                )
            }
        }

        if (engineEnabled && !keepServiceAlive) {
            issues += HealthIssue(
                id = "service_disabled",
                severity = IssueSeverity.WARNING,
                title = "Running in the background is switched off",
                detail = "Forwarding only works while the app is open. Turn this on in Setup " +
                    "so messages are forwarded when the app is closed.",
                actionLabel = "Open setup",
                action = HealthAction.OPEN_SETUP
            )
        }

        if (!appNotificationsEnabled) {
            issues += HealthIssue(
                id = "notifications_blocked",
                severity = IssueSeverity.BLOCKING,
                title = "Notifications are blocked for this app",
                detail = "Android needs to show the ongoing notification to let the app keep " +
                    "running. With notifications blocked it will be shut down.",
                actionLabel = "Allow",
                action = HealthAction.ENABLE_APP_NOTIFICATIONS
            )
        }

        if (isBatteryOptimised) {
            issues += HealthIssue(
                id = "battery_optimised",
                severity = IssueSeverity.WARNING,
                title = "Battery optimisation is on",
                detail = "Android will freeze the app after a while and you will miss messages.",
                actionLabel = "Fix",
                action = HealthAction.FIX_BATTERY
            )
        }

        if (manufacturerNeedsAutostart) {
            issues += HealthIssue(
                id = "oem_autostart",
                severity = IssueSeverity.WARNING,
                title = "This phone needs autostart enabled",
                detail = "Xiaomi, Oppo, Vivo, Realme and similar phones close background apps " +
                    "even when Android allows them. Enable autostart for this app in your " +
                    "phone's security settings.",
                actionLabel = "How",
                action = HealthAction.OPEN_AUTOSTART_HELP
            )
        }

        if (rulesNeedingNotifications > 0 && !hasNotificationAccess) {
            issues += HealthIssue(
                id = "no_notification_access",
                severity = IssueSeverity.BLOCKING,
                title = "Notification access is off",
                detail = "$rulesNeedingNotifications rule(s) forward app notifications, but the " +
                    "app is not allowed to read them.",
                actionLabel = "Grant",
                action = HealthAction.GRANT_NOTIFICATION_ACCESS
            )
        }

        if (rulesNeedingCallLog > 0 && !hasCallLogPermission) {
            issues += HealthIssue(
                id = "no_call_log",
                severity = IssueSeverity.BLOCKING,
                title = "Call log access is off",
                detail = "$rulesNeedingCallLog rule(s) forward missed calls, which needs " +
                    "permission to read the call log.",
                actionLabel = "Grant",
                action = HealthAction.GRANT_CALL_LOG
            )
        }

        if (queuedCount > 0) {
            issues += HealthIssue(
                id = "queued",
                severity = IssueSeverity.WARNING,
                title = "$queuedCount message(s) waiting to send",
                detail = "They will go out automatically once the connection is back.",
                actionLabel = null,
                action = null
            )
        }

        val status = when {
            issues.any { it.severity == IssueSeverity.BLOCKING } -> HealthStatus.NOT_WORKING
            issues.isNotEmpty() -> HealthStatus.AT_RISK
            else -> HealthStatus.WORKING
        }

        return BackgroundHealth(
            status = status,
            issues = issues,
            lastHeartbeatAt = lastHeartbeatAt,
            lastEventAt = lastEventAt,
            lastDeliveryAt = lastDeliveryAt,
            lastBootRestartAt = lastBootRestartAt
        )
    }

    /** "3 minutes", "2 hours", "4 days" — used in the health copy and the diagnostics list. */
    fun describeAge(millis: Long): String {
        if (millis < 0) return "a moment"
        val minutes = millis / 60_000L
        return when {
            minutes < 1 -> "less than a minute"
            minutes < 60 -> "$minutes minute${plural(minutes)}"
            minutes < 60 * 24 -> {
                val hours = minutes / 60
                "$hours hour${plural(hours)}"
            }
            else -> {
                val days = minutes / (60 * 24)
                "$days day${plural(days)}"
            }
        }
    }

    private fun plural(value: Long) = if (value == 1L) "" else "s"
}
