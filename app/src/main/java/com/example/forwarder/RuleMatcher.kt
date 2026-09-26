package com.example.forwarder

import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.MatchType
import java.util.Calendar

/**
 * Decides whether a message matches a rule.
 *
 * Kept free of Android types so the whole filter surface can be unit tested.
 */
object RuleMatcher {

    /** Why a rule did not fire, so the log can tell the user something useful. */
    sealed interface Decision {
        data object Match : Decision
        data class Skip(val reason: String) : Decision
    }

    fun splitValues(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Include matching. Unlike the original implementation this is one-directional: a filter of
     * "whatsapp business" no longer matches a sender called "app".
     */
    fun matchesValue(candidate: String, type: String, rawValue: String): Boolean {
        if (type == MatchType.ANY || rawValue.isBlank()) return true
        val needles = splitValues(rawValue)
        if (needles.isEmpty()) return true
        val subject = candidate.trim().lowercase()

        return when (type) {
            MatchType.EXACT -> needles.any { subject == it.lowercase() }
            MatchType.STARTS_WITH -> needles.any { subject.startsWith(it.lowercase()) }
            MatchType.CONTAINS -> needles.any { subject.contains(it.lowercase()) }
            MatchType.REGEX -> needles.any { pattern ->
                runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(candidate) }
                    .getOrDefault(false)
            }
            else -> true
        }
    }

    /** Exclusions always win over the include filter. */
    fun isExcluded(candidate: String, rawExcludes: String): Boolean {
        val needles = splitValues(rawExcludes)
        if (needles.isEmpty()) return false
        val subject = candidate.trim().lowercase()
        return needles.any { subject.contains(it.lowercase()) }
    }

    /** Blank package list means "every app". */
    fun matchesApp(packageName: String, rule: ForwardingRuleEntity): Boolean {
        val selected = rule.selectedPackages
        return selected.isEmpty() || selected.any { it.equals(packageName, ignoreCase = true) }
    }

    /**
     * True when [minuteOfDay] falls inside the window, handling windows that wrap past midnight
     * (22:00 to 07:00 is a single window, not an empty one).
     */
    fun isWithinWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean =
        if (startMinute <= endMinute) {
            minuteOfDay in startMinute..endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay <= endMinute
        }

    /** [isoDay] is Monday = 1 .. Sunday = 7. */
    fun matchesSchedule(rule: ForwardingRuleEntity, minuteOfDay: Int, isoDay: Int): Boolean {
        if (!rule.scheduleEnabled) return true
        val days = splitValues(rule.scheduleDays).mapNotNull { it.toIntOrNull() }
        if (days.isNotEmpty() && isoDay !in days) return false
        return isWithinWindow(minuteOfDay, rule.scheduleStartMinute, rule.scheduleEndMinute)
    }

    /** Full evaluation for one rule against one message. */
    fun evaluate(
        rule: ForwardingRuleEntity,
        ctx: MessageContext,
        minuteOfDay: Int,
        isoDay: Int
    ): Decision {
        if (!rule.acceptsSource(ctx.source)) {
            return Decision.Skip("Rule does not watch ${MessageSource.label(ctx.source).lowercase()}s")
        }

        if (ctx.isNotification && !matchesApp(ctx.packageName, rule)) {
            return Decision.Skip("App not selected in this rule")
        }

        if (!ctx.isNotification && rule.simSlot != 0 && ctx.simSlot != 0 && rule.simSlot != ctx.simSlot) {
            return Decision.Skip("Message arrived on a different SIM")
        }

        if (!matchesSchedule(rule, minuteOfDay, isoDay)) {
            return Decision.Skip("Outside this rule's schedule")
        }

        // Notification senders are matched against both the app label and the package name,
        // since a user may have typed either.
        val senderCandidates = if (ctx.isNotification) {
            listOf(ctx.appName, ctx.packageName, ctx.title)
        } else {
            listOf(ctx.sender)
        }.filter { it.isNotBlank() }

        if (senderCandidates.any { isExcluded(it, rule.senderExcludeValue) }) {
            return Decision.Skip("Sender is on this rule's exclude list")
        }
        if (senderCandidates.none { matchesValue(it, rule.senderFilterType, rule.senderFilterValue) }) {
            return Decision.Skip("Sender does not match this rule's filter")
        }

        // A missed call carries no text, so a keyword filter would silently drop every one.
        if (!ctx.isCall) {
            if (isExcluded(ctx.body, rule.contentExcludeValue)) {
                return Decision.Skip("Message contains an excluded keyword")
            }
            if (!matchesValue(ctx.body, rule.contentFilterType, rule.contentFilterValue)) {
                return Decision.Skip("Message does not contain this rule's keywords")
            }
        }

        return Decision.Match
    }

    /** Minutes since midnight for [calendar], defaulting to now. */
    fun minuteOfDay(calendar: Calendar = Calendar.getInstance()): Int =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    /** Converts [Calendar.DAY_OF_WEEK] (Sunday = 1) to ISO-8601 (Monday = 1, Sunday = 7). */
    fun isoDayOfWeek(calendar: Calendar = Calendar.getInstance()): Int =
        when (val day = calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 7
            else -> day - 1
        }
}
