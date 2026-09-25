package com.example.forwarder

import android.content.Context
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.preferences.ForwarderSettings
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.ForwardingRuleRepository
import com.example.data.repository.SmsLogRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ForwardingManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: ForwardingRuleRepository,
    private val smsLogRepository: SmsLogRepository,
    private val notificationHelper: NotificationHelper
) {
    private val emailForwarder = EmailForwarder()

    suspend fun forwardIncomingSms(
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        simSlot: Int = 0
    ): List<SmsLogEntity> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val createdLogs = mutableListOf<SmsLogEntity>()

        if (!settings.isForwarderEnabled) {
            val log = SmsLogEntity(
                sender = sender,
                body = body,
                receivedAt = timestamp,
                destinationType = "EMAIL",
                destinationTarget = "ALL",
                status = "SKIPPED",
                errorMessage = "Forwarding engine is paused",
                source = "SMS",
                simSlot = simSlot
            )
            val id = smsLogRepository.insertLog(log)
            createdLogs.add(log.copy(id = id))
            return@withContext createdLogs
        }

        val rules = ruleRepository.getEnabledRules().filter { it.forwardSms }
        if (rules.isEmpty()) {
            val log = SmsLogEntity(
                sender = sender,
                body = body,
                receivedAt = timestamp,
                destinationType = "EMAIL",
                destinationTarget = "None",
                status = "SKIPPED",
                errorMessage = "No active SMS forwarding rules configured. Add a filter rule with recipient email.",
                source = "SMS",
                simSlot = simSlot
            )
            val id = smsLogRepository.insertLog(log)
            createdLogs.add(log.copy(id = id))
            return@withContext createdLogs
        }

        var anySuccess = false
        val summaries = mutableListOf<String>()

        for (rule in rules) {
            // Check SIM slot filter
            if (rule.simSlot != 0 && simSlot != 0 && rule.simSlot != simSlot) {
                continue
            }

            // Check sender filter
            if (!matchesSenderFilter(sender, rule.senderFilterType, rule.senderFilterValue)) {
                continue
            }

            // Check content filter
            if (!matchesContentFilter(body, rule.contentFilterType, rule.contentFilterValue)) {
                continue
            }

            // Target recipient email
            val recipientEmail = rule.recipientEmail.trim()
            if (recipientEmail.isBlank()) continue

            // Check if sender account is linked
            if (!settings.isSenderAccountConfigured) {
                val log = SmsLogEntity(
                    sender = sender,
                    body = body,
                    receivedAt = timestamp,
                    destinationType = "EMAIL",
                    destinationTarget = recipientEmail,
                    status = "FAILED",
                    errorMessage = "Google / Email account not linked. Go to Settings to link your sender email.",
                    source = "SMS",
                    simSlot = simSlot,
                    ruleName = rule.name
                )
                val id = smsLogRepository.insertLog(log)
                createdLogs.add(log.copy(id = id))
                summaries.add("${rule.name}: Sender account not linked")
                continue
            }

            // Prepare email content with SIM indicator
            val simIndicator = if (simSlot > 0) "[SIM $simSlot] " else ""
            val formattedSubject = "$simIndicator[SMS Alert] From $sender"
            val formattedBody = buildString {
                append("📩 Incoming SMS Received\n")
                append("----------------------------------------\n")
                append("Rule: ${rule.name}\n")
                append("From: $sender\n")
                if (simSlot > 0) append("SIM: SIM $simSlot\n")
                append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}\n\n")
                append("Message:\n")
                append(body)
                append("\n----------------------------------------\n")
                append("Forwarded automatically by SMS & Notification Forwarder.\n")
                append("Developed by PenduCoder • https://penducoder.com\n")
            }

            val result = sendConfiguredEmail(
                settings = settings,
                toEmail = recipientEmail,
                senderNumber = sender,
                formattedBody = formattedBody,
                timestamp = timestamp
            )

            val log = SmsLogEntity(
                sender = sender,
                body = body,
                receivedAt = timestamp,
                destinationType = "EMAIL",
                destinationTarget = recipientEmail,
                status = if (result.success) "SUCCESS" else "FAILED",
                errorMessage = result.errorMessage,
                responsePayload = result.responseDetails,
                forwardedAt = if (result.success) System.currentTimeMillis() else null,
                source = "SMS",
                simSlot = simSlot,
                ruleName = rule.name
            )
            val id = smsLogRepository.insertLog(log)
            createdLogs.add(log.copy(id = id))

            if (result.success) {
                anySuccess = true
                summaries.add("Sent to $recipientEmail")
            } else {
                summaries.add("Failed to $recipientEmail (${result.errorMessage})")
            }
        }

        if (settings.notifyOnForward && summaries.isNotEmpty()) {
            notificationHelper.showForwardedNotification(
                sender = sender,
                body = body,
                destinationsSummary = summaries.joinToString("; "),
                allSuccess = anySuccess
            )
        }

        createdLogs
    }

    suspend fun forwardIncomingNotification(
        appName: String,
        packageName: String,
        title: String,
        text: String,
        timestamp: Long = System.currentTimeMillis()
    ): List<SmsLogEntity> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val createdLogs = mutableListOf<SmsLogEntity>()

        if (!settings.isForwarderEnabled) return@withContext emptyList()

        val rules = ruleRepository.getEnabledRules().filter { it.forwardNotifications }
        if (rules.isEmpty()) return@withContext emptyList()

        val combinedSender = "$appName ($title)"
        val fullContent = if (title.isNotBlank()) "[$title]\n$text" else text

        var anySuccess = false
        val summaries = mutableListOf<String>()

        for (rule in rules) {
            // Check app / sender filter
            if (!matchesSenderFilter(appName, rule.senderFilterType, rule.senderFilterValue) &&
                !matchesSenderFilter(packageName, rule.senderFilterType, rule.senderFilterValue)
            ) {
                continue
            }

            // Check content filter
            if (!matchesContentFilter(fullContent, rule.contentFilterType, rule.contentFilterValue)) {
                continue
            }

            val recipientEmail = rule.recipientEmail.trim()
            if (recipientEmail.isBlank()) continue

            if (!settings.isSenderAccountConfigured) {
                val log = SmsLogEntity(
                    sender = appName,
                    body = fullContent,
                    receivedAt = timestamp,
                    destinationType = "EMAIL",
                    destinationTarget = recipientEmail,
                    status = "FAILED",
                    errorMessage = "Google / Email account not linked in Settings",
                    source = "NOTIFICATION",
                    simSlot = 0,
                    ruleName = rule.name
                )
                val id = smsLogRepository.insertLog(log)
                createdLogs.add(log.copy(id = id))
                continue
            }

            val formattedBody = buildString {
                append("🔔 App Notification Received\n")
                append("----------------------------------------\n")
                append("Rule: ${rule.name}\n")
                append("App: $appName ($packageName)\n")
                append("Title: $title\n")
                append("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}\n\n")
                append("Content:\n")
                append(text)
                append("\n----------------------------------------\n")
                append("Forwarded automatically by SMS & Notification Forwarder.\n")
                append("Developed by PenduCoder • https://penducoder.com\n")
            }

            val result = sendConfiguredEmail(
                settings = settings,
                toEmail = recipientEmail,
                senderNumber = appName,
                formattedBody = formattedBody,
                timestamp = timestamp
            )

            val log = SmsLogEntity(
                sender = appName,
                body = fullContent,
                receivedAt = timestamp,
                destinationType = "EMAIL",
                destinationTarget = recipientEmail,
                status = if (result.success) "SUCCESS" else "FAILED",
                errorMessage = result.errorMessage,
                responsePayload = result.responseDetails,
                forwardedAt = if (result.success) System.currentTimeMillis() else null,
                source = "NOTIFICATION",
                simSlot = 0,
                ruleName = rule.name
            )
            val id = smsLogRepository.insertLog(log)
            createdLogs.add(log.copy(id = id))

            if (result.success) {
                anySuccess = true
                summaries.add("Sent to $recipientEmail")
            }
        }

        if (settings.notifyOnForward && summaries.isNotEmpty()) {
            notificationHelper.showForwardedNotification(
                sender = appName,
                body = fullContent,
                destinationsSummary = summaries.joinToString("; "),
                allSuccess = anySuccess
            )
        }

        createdLogs
    }

    suspend fun retryForwarding(logId: Long): ForwardResult = withContext(Dispatchers.IO) {
        val log = smsLogRepository.getLogById(logId)
            ?: return@withContext ForwardResult(false, errorMessage = "Log not found")
        val settings = settingsRepository.getSettings()

        if (!settings.isSenderAccountConfigured) {
            return@withContext ForwardResult(false, errorMessage = "Sender Google/Email account is not configured in Settings.")
        }

        val result = sendConfiguredEmail(
            settings = settings,
            toEmail = log.destinationTarget,
            senderNumber = log.sender,
            formattedBody = log.body,
            timestamp = log.receivedAt
        )

        val updated = log.copy(
            status = if (result.success) "SUCCESS" else "FAILED",
            errorMessage = result.errorMessage,
            responsePayload = result.responseDetails,
            retryCount = log.retryCount + 1,
            forwardedAt = if (result.success) System.currentTimeMillis() else log.forwardedAt
        )
        smsLogRepository.updateLog(updated)
        result
    }

    suspend fun testSenderAccount(
        emailAccount: String,
        appPassword: String,
        host: String,
        port: Int,
        tls: Boolean,
        testRecipient: String,
        authMethod: String = "GOOGLE_OAUTH"
    ): ForwardResult = withContext(Dispatchers.IO) {
        val testBody = "Congratulations! Your Google Account / Email is connected and forwarding is working properly.\n\nDeveloped by PenduCoder • https://penducoder.com"
        if (authMethod == "GOOGLE_OAUTH") {
            emailForwarder.sendViaGmailApi(
                context = context,
                fromEmail = emailAccount,
                fromName = "SMS Forwarder Test",
                toEmail = testRecipient,
                senderNumber = "TEST_SYSTEM",
                smsBody = testBody,
                timestamp = System.currentTimeMillis()
            )
        } else {
            emailForwarder.sendEmail(
                host = host,
                port = port,
                username = emailAccount,
                password = appPassword,
                useTls = tls,
                fromName = "SMS Forwarder Test",
                toEmail = testRecipient,
                senderNumber = "TEST_SYSTEM",
                smsBody = testBody,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private suspend fun sendConfiguredEmail(
        settings: ForwarderSettings,
        toEmail: String,
        senderNumber: String,
        formattedBody: String,
        timestamp: Long
    ): ForwardResult {
        return if (settings.authMethod == "GOOGLE_OAUTH") {
            emailForwarder.sendViaGmailApi(
                context = context,
                fromEmail = settings.senderEmailAccount,
                fromName = settings.senderDisplayName,
                toEmail = toEmail,
                senderNumber = senderNumber,
                smsBody = formattedBody,
                timestamp = timestamp
            )
        } else {
            emailForwarder.sendEmail(
                host = settings.smtpHost,
                port = settings.smtpPort,
                username = settings.senderEmailAccount,
                password = settings.senderAppPassword,
                useTls = settings.smtpUseTls,
                fromName = settings.senderDisplayName,
                toEmail = toEmail,
                senderNumber = senderNumber,
                smsBody = formattedBody,
                timestamp = timestamp
            )
        }
    }

    private fun matchesSenderFilter(sender: String, type: String, value: String): Boolean {
        if (type == "ANY" || value.isBlank()) return true
        val cleanSender = sender.trim().lowercase()
        val targets = value.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (targets.isEmpty()) return true

        return when (type) {
            "EXACT" -> targets.any { cleanSender == it }
            "CONTAINS" -> targets.any { cleanSender.contains(it) || it.contains(cleanSender) }
            else -> true
        }
    }

    private fun matchesContentFilter(body: String, type: String, value: String): Boolean {
        if (type == "ANY" || value.isBlank()) return true
        val lowerBody = body.lowercase()
        val keywords = value.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return true

        return when (type) {
            "CONTAINS" -> keywords.any { lowerBody.contains(it) }
            else -> true
        }
    }
}
