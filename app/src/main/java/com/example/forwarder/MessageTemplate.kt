package com.example.forwarder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val ruleName: String = ""
) {
    val isNotification: Boolean get() = source == "NOTIFICATION"
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
        return if (ctx.isNotification) {
            val title = ctx.title.ifBlank { "New notification" }
            "$simPrefix[${ctx.appName.ifBlank { ctx.sender }}] $title"
        } else {
            "$simPrefix[SMS] ${ctx.sender}"
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
            append("Incoming SMS").append('\n')
            append(divider).append('\n')
            append("From: ").append(ctx.sender).append('\n')
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
        if (ctx.isNotification) {
            append('[').append(ctx.appName.ifBlank { ctx.sender }).append("] ")
            if (ctx.title.isNotBlank()) append(ctx.title).append(": ")
        } else {
            append("SMS from ").append(ctx.sender)
            if (ctx.simSlot > 0) append(" (").append(ctx.simName.ifBlank { "SIM ${ctx.simSlot}" }).append(')')
            append('\n')
        }
        append(ctx.body)
    }
}
