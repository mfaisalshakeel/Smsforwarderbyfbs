package com.example.util

import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.preferences.ForwarderSettings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serialises rules and preferences so a user can move to a new phone, or recover after a
 * reinstall, without rebuilding every rule by hand.
 *
 * Credentials are deliberately excluded: an exported file is an ordinary document the user may
 * email to themselves, and it must never carry a mail password or a bot token.
 */
object BackupManager {

    private const val VERSION = 1

    fun exportToJson(rules: List<ForwardingRuleEntity>, settings: ForwarderSettings): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val ruleArray = JSONArray()
        rules.forEach { rule ->
            ruleArray.put(
                JSONObject().apply {
                    put("name", rule.name)
                    put("isEnabled", rule.isEnabled)
                    put("destinationType", rule.destinationType)
                    put("recipientEmail", rule.recipientEmail)
                    put("destinationTarget", rule.destinationTarget)
                    put("webhookFormat", rule.webhookFormat)
                    put("webhookHeaders", rule.webhookHeaders)
                    // telegramBotToken is a credential and is intentionally not exported.
                    put("forwardSms", rule.forwardSms)
                    put("forwardNotifications", rule.forwardNotifications)
                    put("appPackages", rule.appPackages)
                    put("simSlot", rule.simSlot)
                    put("senderFilterType", rule.senderFilterType)
                    put("senderFilterValue", rule.senderFilterValue)
                    put("senderExcludeValue", rule.senderExcludeValue)
                    put("contentFilterType", rule.contentFilterType)
                    put("contentFilterValue", rule.contentFilterValue)
                    put("contentExcludeValue", rule.contentExcludeValue)
                    put("scheduleEnabled", rule.scheduleEnabled)
                    put("scheduleStartMinute", rule.scheduleStartMinute)
                    put("scheduleEndMinute", rule.scheduleEndMinute)
                    put("scheduleDays", rule.scheduleDays)
                    put("useCustomTemplate", rule.useCustomTemplate)
                    put("subjectTemplate", rule.subjectTemplate)
                    put("bodyTemplate", rule.bodyTemplate)
                }
            )
        }
        root.put("rules", ruleArray)

        root.put(
            "settings",
            JSONObject().apply {
                put("senderDisplayName", settings.senderDisplayName)
                put("smtpHost", settings.smtpHost)
                put("smtpPort", settings.smtpPort)
                put("smtpUseTls", settings.smtpUseTls)
                put("notifyOnForward", settings.notifyOnForward)
                put("keepServiceAlive", settings.keepServiceAlive)
                put("skipOngoingNotifications", settings.skipOngoingNotifications)
                put("skipGroupSummaries", settings.skipGroupSummaries)
                put("duplicateWindowSeconds", settings.duplicateWindowSeconds)
                put("maxRetryAttempts", settings.maxRetryAttempts)
                put("retryOnlyOnWifi", settings.retryOnlyOnWifi)
                put("quietHoursEnabled", settings.quietHoursEnabled)
                put("quietHoursStartMinute", settings.quietHoursStartMinute)
                put("quietHoursEndMinute", settings.quietHoursEndMinute)
                put("logRetentionDays", settings.logRetentionDays)
                put("themeMode", settings.themeMode)
                put("dynamicColorEnabled", settings.dynamicColorEnabled)
                put("includeBrandingFooter", settings.includeBrandingFooter)
            }
        )
        return root.toString(2)
    }

    data class ImportResult(
        val rules: List<ForwardingRuleEntity>,
        val settings: ForwarderSettings?,
        val error: String? = null
    )

    fun importFromJson(json: String, current: ForwarderSettings): ImportResult = try {
        val root = JSONObject(json)
        val ruleArray = root.optJSONArray("rules") ?: JSONArray()
        val rules = (0 until ruleArray.length()).mapNotNull { index ->
            ruleArray.optJSONObject(index)?.let { obj ->
                ForwardingRuleEntity(
                    name = obj.optString("name", "Imported rule"),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    destinationType = obj.optString("destinationType", "EMAIL"),
                    recipientEmail = obj.optString("recipientEmail", ""),
                    destinationTarget = obj.optString("destinationTarget", ""),
                    webhookFormat = obj.optString("webhookFormat", "JSON"),
                    webhookHeaders = obj.optString("webhookHeaders", ""),
                    forwardSms = obj.optBoolean("forwardSms", true),
                    forwardNotifications = obj.optBoolean("forwardNotifications", false),
                    appPackages = obj.optString("appPackages", ""),
                    simSlot = obj.optInt("simSlot", 0),
                    senderFilterType = obj.optString("senderFilterType", "ANY"),
                    senderFilterValue = obj.optString("senderFilterValue", ""),
                    senderExcludeValue = obj.optString("senderExcludeValue", ""),
                    contentFilterType = obj.optString("contentFilterType", "ANY"),
                    contentFilterValue = obj.optString("contentFilterValue", ""),
                    contentExcludeValue = obj.optString("contentExcludeValue", ""),
                    scheduleEnabled = obj.optBoolean("scheduleEnabled", false),
                    scheduleStartMinute = obj.optInt("scheduleStartMinute", 0),
                    scheduleEndMinute = obj.optInt("scheduleEndMinute", 1439),
                    scheduleDays = obj.optString("scheduleDays", "1,2,3,4,5,6,7"),
                    useCustomTemplate = obj.optBoolean("useCustomTemplate", false),
                    subjectTemplate = obj.optString("subjectTemplate", ""),
                    bodyTemplate = obj.optString("bodyTemplate", "")
                )
            }
        }

        val settingsObj = root.optJSONObject("settings")
        val settings = settingsObj?.let {
            current.copy(
                senderDisplayName = it.optString("senderDisplayName", current.senderDisplayName),
                smtpHost = it.optString("smtpHost", current.smtpHost),
                smtpPort = it.optInt("smtpPort", current.smtpPort),
                smtpUseTls = it.optBoolean("smtpUseTls", current.smtpUseTls),
                notifyOnForward = it.optBoolean("notifyOnForward", current.notifyOnForward),
                keepServiceAlive = it.optBoolean("keepServiceAlive", current.keepServiceAlive),
                skipOngoingNotifications = it.optBoolean("skipOngoingNotifications", current.skipOngoingNotifications),
                skipGroupSummaries = it.optBoolean("skipGroupSummaries", current.skipGroupSummaries),
                duplicateWindowSeconds = it.optInt("duplicateWindowSeconds", current.duplicateWindowSeconds),
                maxRetryAttempts = it.optInt("maxRetryAttempts", current.maxRetryAttempts),
                retryOnlyOnWifi = it.optBoolean("retryOnlyOnWifi", current.retryOnlyOnWifi),
                quietHoursEnabled = it.optBoolean("quietHoursEnabled", current.quietHoursEnabled),
                quietHoursStartMinute = it.optInt("quietHoursStartMinute", current.quietHoursStartMinute),
                quietHoursEndMinute = it.optInt("quietHoursEndMinute", current.quietHoursEndMinute),
                logRetentionDays = it.optInt("logRetentionDays", current.logRetentionDays),
                themeMode = it.optString("themeMode", current.themeMode),
                dynamicColorEnabled = it.optBoolean("dynamicColorEnabled", current.dynamicColorEnabled),
                includeBrandingFooter = it.optBoolean("includeBrandingFooter", current.includeBrandingFooter)
            )
        }

        if (rules.isEmpty() && settings == null) {
            ImportResult(emptyList(), null, "That file does not contain any rules.")
        } else {
            ImportResult(rules, settings)
        }
    } catch (e: Exception) {
        ImportResult(emptyList(), null, "That file is not a valid backup (${e.localizedMessage}).")
    }

    /** Suggested file name for a backup, e.g. `sms-forwarder-backup-2026-09-25.json`. */
    fun suggestedBackupName(): String =
        "sms-forwarder-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"

    fun suggestedCsvName(): String =
        "sms-forwarder-logs-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv"

    /** RFC 4180 CSV of the forwarding history. */
    fun exportLogsToCsv(logs: List<SmsLogEntity>): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            append("Received,Source,Sender,Rule,Destination Type,Destination,Status,Attempts,Error,Message\n")
            logs.forEach { log ->
                append(escape(formatter.format(Date(log.receivedAt)))).append(',')
                append(escape(log.source)).append(',')
                append(escape(log.sender)).append(',')
                append(escape(log.ruleName.orEmpty())).append(',')
                append(escape(log.destinationType)).append(',')
                append(escape(log.destinationTarget)).append(',')
                append(escape(log.status)).append(',')
                append(log.retryCount).append(',')
                append(escape(log.errorMessage.orEmpty())).append(',')
                append(escape(log.body)).append('\n')
            }
        }
    }

    private fun escape(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\r\n", " ").replace("\n", " ") + "\""
}
