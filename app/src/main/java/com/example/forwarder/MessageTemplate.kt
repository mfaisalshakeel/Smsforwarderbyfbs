package com.example.forwarder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The kinds of event the app can forward. */
object MessageSource {
    const val SMS = "SMS"
    const val MMS = "MMS"
    const val NOTIFICATION = "NOTIFICATION"
    const val CALL = "CALL"

    /** Label shown in the history list and the log detail sheet. */
    fun label(source: String): String = when (source) {
        MMS -> "Picture message"
        NOTIFICATION -> "App notification"
        CALL -> "Missed call"
        else -> "Text message"
    }
}

/** Everything a template or a default message body can refer to. */
data class MessageContext(
    val sender: String,
    val body: String,
    val timestamp: Long,
    /** "SMS" or "NOTIFICATION". */
    val source: String,
    val simSlot: Int = 0,
    val simName: String = "",
    val appName: String = "",
    val packageName: String = "",
    val title: String = "",
    val ruleName: String = "",
    /** Number of attachments on an MMS, or 0. */
    val attachmentCount: Int = 0
) {
    val isSms: Boolean get() = source == MessageSource.SMS
    val isMms: Boolean get() = source == MessageSource.MMS
    val isNotification: Boolean get() = source == MessageSource.NOTIFICATION
    val isCall: Boolean get() = source == MessageSource.CALL
}

/**
 * Renders the subject and body of a forwarded message, either from the user's own template
 * or from the built-in layout.
 */
object MessageTemplate {

    /** Placeholders offered in the rule editor, shown to the user as chips. */
    val PLACEHOLDERS = listOf(
        "{sender}", "{message}", "{time}", "{date}",
        "{app}", "{title}", "{sim}", "{rule}"
    )

    const val DEFAULT_SMS_SUBJECT = "[SMS] {sender}"
    const val DEFAULT_NOTIFICATION_SUBJECT = "[{app}] {title}"
    const val DEFAULT_SHORT_BODY = "From: {sender}\n{message}\n\n{time}"

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

    fun render(template: String, ctx: MessageContext): String =
        template
            .replace("{sender}", ctx.sender)
            .replace("{message}", ctx.body)
            .replace("{body}", ctx.body)
            .replace("{time}", formatTime(ctx.timestamp))
            .replace("{date}", formatDate(ctx.timestamp))
            .replace("{app}", ctx.appName.ifBlank { ctx.sender })
            .replace("{package}", ctx.packageName)
            .replace("{title}", ctx.title)
            .replace("{sim}", if (ctx.simSlot > 0) ctx.simName.ifBlank { "SIM ${ctx.simSlot}" } else "")
            .replace("{rule}", ctx.ruleName)

    /** Subject line used when the rule has no custom template. */
    fun defaultSubject(ctx: MessageContext): String {
        val simPrefix = if (ctx.simSlot > 0) "[${ctx.simName.ifBlank { "SIM ${ctx.simSlot}" }}] " else ""
        return when {
            ctx.isNotification -> {
                val title = ctx.title.ifBlank { "New notification" }
                "$simPrefix[${ctx.appName.ifBlank { ctx.sender }}] $title"
            }
            ctx.isCall -> "$simPrefix[Missed call] ${ctx.sender}"
            ctx.isMms -> "$simPrefix[MMS] ${ctx.sender}"
            else -> "$simPrefix[SMS] ${ctx.sender}"
        }
    }

    /** Full message body used when the rule has no custom template. */
    fun defaultBody(ctx: MessageContext, includeBranding: Boolean): String = buildString {
        val divider = "------------------------------"
        if (ctx.isNotification) {
            append("App notification").append('\n')
            append(divider).append('\n')
            append("App: ").append(ctx.appName)
            if (ctx.packageName.isNotBlank()) append(" (").append(ctx.packageName).append(')')
            append('\n')
            if (ctx.title.isNotBlank()) append("Title: ").append(ctx.title).append('\n')
        } else {
            append(
                when {
                    ctx.isCall -> "Missed call"
                    ctx.isMms -> "Incoming picture message"
                    else -> "Incoming SMS"
                }
            ).append('\n')
            append(divider).append('\n')
            append("From: ").append(ctx.sender).append('\n')
            if (ctx.isMms && ctx.attachmentCount > 0) {
                append("Attachments: ").append(ctx.attachmentCount)
                    .append(" (not included in this message)").append('\n')
            }
            if (ctx.simSlot > 0) {
                append("SIM: ").append(ctx.simName.ifBlank { "SIM ${ctx.simSlot}" }).append('\n')
            }
        }
        if (ctx.ruleName.isNotBlank()) append("Rule: ").append(ctx.ruleName).append('\n')
        append("Time: ").append(formatTime(ctx.timestamp)).append('\n')
        append('\n')
        append(ctx.body)
        append('\n').append(divider).append('\n')
        append("Forwarded automatically by SMS & Notification Forwarder.")
        if (includeBranding) {
            append('\n').append("Developed by PenduCoder - https://penducoder.com")
        }
    }

    /** Compact single-message body for SMS, Telegram and webhook destinations. */
    fun compactBody(ctx: MessageContext): String = buildString {
        when {
            ctx.isNotification -> {
                append('[').append(ctx.appName.ifBlank { ctx.sender }).append("] ")
                if (ctx.title.isNotBlank()) append(ctx.title).append(": ")
            }
            ctx.isCall -> {
                append("Missed call from ").append(ctx.sender)
                if (ctx.simSlot > 0) append(" (").append(ctx.simName.ifBlank { "SIM ${ctx.simSlot}" }).append(')')
                append('\n')
            }
            else -> {
                append(if (ctx.isMms) "MMS from " else "SMS from ").append(ctx.sender)
                if (ctx.simSlot > 0) append(" (").append(ctx.simName.ifBlank { "SIM ${ctx.simSlot}" }).append(')')
                append('\n')
            }
        }
        append(ctx.body)
    }

    /** Subject for a batch of messages collected by a digest rule. */
    fun digestSubject(ruleName: String, count: Int): String =
        "[Digest] $count message${if (count == 1) "" else "s"} - $ruleName"

    /**
     * One combined message for a digest rule, newest last so it reads in the order the
     * messages arrived.
     */
    fun digestBody(
        ruleName: String,
        entries: List<DigestEntry>,
        includeBranding: Boolean
    ): String = buildString {
        val divider = "------------------------------"
        append(entries.size).append(" message").append(if (entries.size == 1) "" else "s")
            .append(" collected by \"").append(ruleName).append("\"").append('\n')
        append(divider).append('\n')
        entries.forEachIndexed { index, entry ->
            append('\n').append(index + 1).append(". ")
            append(MessageSource.label(entry.source)).append(" from ").append(entry.sender).append('\n')
            append("   ").append(formatTime(entry.receivedAt)).append('\n')
            entry.body.lineSequence().forEach { line -> append("   ").append(line).append('\n') }
        }
        append('\n').append(divider).append('\n')
        append("Forwarded automatically by SMS & Notification Forwarder.")
        if (includeBranding) {
            append('\n').append("Developed by PenduCoder - https://penducoder.com")
        }
    }

    /** One collected message inside a digest. */
    data class DigestEntry(
        val sender: String,
        val body: String,
        val receivedAt: Long,
        val source: String
    )
}
