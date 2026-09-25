package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Where a matched message is delivered. */
object DestinationType {
    const val EMAIL = "EMAIL"
    const val SMS = "SMS"
    const val WEBHOOK = "WEBHOOK"
    const val TELEGRAM = "TELEGRAM"
}

/** How a sender / content filter is evaluated. */
object MatchType {
    const val ANY = "ANY"
    const val CONTAINS = "CONTAINS"
    const val EXACT = "EXACT"
    const val STARTS_WITH = "STARTS_WITH"
    const val REGEX = "REGEX"
}

@Entity(tableName = "forwarding_rules")
data class ForwardingRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Forwarding Rule",
    val isEnabled: Boolean = true,

    // ---- Destination -------------------------------------------------------
    val destinationType: String = DestinationType.EMAIL,
    /** Comma-separated recipient addresses. Used when [destinationType] is EMAIL. */
    val recipientEmail: String = "",
    /** Phone number, webhook URL or Telegram chat id, depending on [destinationType]. */
    val destinationTarget: String = "",
    /** JSON, DISCORD or SLACK. Used when [destinationType] is WEBHOOK. */
    val webhookFormat: String = "JSON",
    /** One `Header: value` per line. Used when [destinationType] is WEBHOOK. */
    val webhookHeaders: String = "",
    /** Bot token. Used when [destinationType] is TELEGRAM. */
    val telegramBotToken: String = "",

    // ---- Sources -----------------------------------------------------------
    val forwardSms: Boolean = true,
    val forwardNotifications: Boolean = false,
    /** Comma-separated package names chosen in the app picker. Blank means every app. */
    val appPackages: String = "",
    /** 0 = any SIM, 1 = SIM 1, 2 = SIM 2. */
    val simSlot: Int = 0,

    // ---- Filters -----------------------------------------------------------
    val senderFilterType: String = MatchType.ANY,
    val senderFilterValue: String = "",
    /** Comma-separated senders that must never match, whatever the include filter says. */
    val senderExcludeValue: String = "",
    val contentFilterType: String = MatchType.ANY,
    val contentFilterValue: String = "",
    /** Comma-separated keywords that veto a match. */
    val contentExcludeValue: String = "",

    // ---- Schedule ----------------------------------------------------------
    val scheduleEnabled: Boolean = false,
    /** Minutes from midnight. A start after the end means the window crosses midnight. */
    val scheduleStartMinute: Int = 0,
    val scheduleEndMinute: Int = 1439,
    /** Comma-separated ISO-8601 day numbers, Monday = 1 .. Sunday = 7. */
    val scheduleDays: String = "1,2,3,4,5,6,7",

    // ---- Message template --------------------------------------------------
    val useCustomTemplate: Boolean = false,
    val subjectTemplate: String = "",
    val bodyTemplate: String = "",

    val createdAt: Long = System.currentTimeMillis()
) {
    /** The addresses/targets this rule delivers to, already split and trimmed. */
    val targets: List<String>
        get() = when (destinationType) {
            DestinationType.EMAIL -> recipientEmail
            else -> destinationTarget
        }.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Package names selected in the app picker; empty means "every app". */
    val selectedPackages: List<String>
        get() = appPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** A short, human-readable description of where this rule delivers. */
    val destinationSummary: String
        get() = targets.joinToString(", ").ifBlank { "Not configured" }

    val isConfigured: Boolean
        get() = targets.isNotEmpty() &&
            (destinationType != DestinationType.TELEGRAM || telegramBotToken.isNotBlank())
}
