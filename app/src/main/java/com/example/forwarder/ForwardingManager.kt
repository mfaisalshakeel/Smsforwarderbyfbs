package com.example.forwarder

import android.content.Context
import com.example.data.local.entity.DestinationType
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.LogStatus
import com.example.data.local.entity.SmsLogEntity
import com.example.data.preferences.AuthMethod
import com.example.data.preferences.EngineStateStore
import com.example.data.preferences.ForwarderSettings
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.ForwardingRuleRepository
import com.example.data.repository.SmsLogRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Calendar

/** Aggregate outcome of one incoming message. */
data class ForwardOutcome(
    val logs: List<SmsLogEntity> = emptyList(),
    val delivered: Int = 0,
    val failed: Int = 0,
    val queued: Int = 0,
    /** Collected into a digest batch rather than sent straight away. */
    val batched: Int = 0,
    val skippedReason: String? = null
) {
    val attempted: Int get() = delivered + failed + queued + batched
}

/** How many entries the retry pass handled. */
data class RetryOutcome(val processed: Int, val delivered: Int, val remaining: Int)

/** How many digest batches were sent, and when the next one is due. */
data class DigestOutcome(val batchesSent: Int, val messagesSent: Int, val nextDueAt: Long?)

class ForwardingManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: ForwardingRuleRepository,
    private val smsLogRepository: SmsLogRepository,
    private val notificationHelper: NotificationHelper,
    private val engineStateStore: EngineStateStore
) {
    private val emailForwarder = EmailForwarder()
    private val webhookForwarder = WebhookForwarder()
    private val telegramForwarder = TelegramForwarder()
    private val phoneForwarder = PhoneForwarder(context)

    // ---------------------------------------------------------------- entry points

    suspend fun forwardIncomingSms(
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        simSlot: Int = 0,
        simName: String = ""
    ): ForwardOutcome = process(
        MessageContext(
            sender = sender,
            body = body,
            timestamp = timestamp,
            source = MessageSource.SMS,
            simSlot = simSlot,
            simName = simName
        )
    )

    suspend fun forwardIncomingMms(
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        attachmentCount: Int = 0,
        simSlot: Int = 0,
        simName: String = ""
    ): ForwardOutcome = process(
        MessageContext(
            sender = sender,
            body = body,
            timestamp = timestamp,
            source = MessageSource.MMS,
            simSlot = simSlot,
            simName = simName,
            attachmentCount = attachmentCount
        )
    )

    suspend fun forwardMissedCall(
        number: String,
        displayName: String,
        timestamp: Long = System.currentTimeMillis()
    ): ForwardOutcome = process(
        MessageContext(
            sender = displayName.ifBlank { number.ifBlank { "Unknown number" } },
            body = if (number.isBlank()) {
                "Missed call from a withheld number."
            } else {
                "Missed call from $number."
            },
            timestamp = timestamp,
            source = MessageSource.CALL
        )
    )

    suspend fun forwardIncomingNotification(
        appName: String,
        packageName: String,
        title: String,
        text: String,
        timestamp: Long = System.currentTimeMillis()
    ): ForwardOutcome = process(
        MessageContext(
            sender = appName,
            body = text,
            timestamp = timestamp,
            source = MessageSource.NOTIFICATION,
            appName = appName,
            packageName = packageName,
            title = title
        )
    )

    // ---------------------------------------------------------------- core pipeline

    private suspend fun process(ctx: MessageContext): ForwardOutcome = withContext(Dispatchers.IO) {
        // Recorded before any filtering, so the health screen can prove the engine is being
        // reached even when a rule then decides not to forward.
        engineStateStore.recordEvent(ctx.source)
        val settings = settingsRepository.getSettings()

        if (!settings.isForwarderEnabled) {
            return@withContext skip(ctx, "Forwarding is paused")
        }

        val now = Calendar.getInstance()
        val minuteOfDay = RuleMatcher.minuteOfDay(now)
        val isoDay = RuleMatcher.isoDayOfWeek(now)

        if (settings.quietHoursEnabled &&
            RuleMatcher.isWithinWindow(minuteOfDay, settings.quietHoursStartMinute, settings.quietHoursEndMinute)
        ) {
            return@withContext skip(ctx, "Quiet hours are active")
        }

        val contentHash = contentHash(ctx)
        if (ctx.isNotification && settings.duplicateWindowSeconds > 0) {
            val since = ctx.timestamp - settings.duplicateWindowSeconds * 1000L
            if (smsLogRepository.wasRecentlyForwarded(contentHash, since)) {
                // A duplicate is dropped silently: writing a log row for every repeat of a
                // music-player notification would be its own kind of spam.
                return@withContext ForwardOutcome(skippedReason = "Duplicate of a recent notification")
            }
        }

        val rules = ruleRepository.getEnabledRules()
        if (rules.isEmpty()) {
            return@withContext skip(ctx, "No forwarding rules are set up yet", contentHash)
        }

        val logs = mutableListOf<SmsLogEntity>()
        var delivered = 0
        var failed = 0
        var queued = 0
        var batched = 0
        val summaries = mutableListOf<String>()
        var anyRuleMatched = false
        var earliestBatchDue: Long? = null

        for (rule in rules) {
            val ruleCtx = ctx.copy(ruleName = rule.name)
            if (RuleMatcher.evaluate(rule, ruleCtx, minuteOfDay, isoDay) !is RuleMatcher.Decision.Match) continue
            anyRuleMatched = true

            if (!rule.isConfigured) {
                logs += record(
                    ruleCtx, rule, rule.destinationSummary, LogStatus.FAILED,
                    error = "This rule has no destination set.", contentHash = contentHash
                )
                failed++
                continue
            }

            val subject = renderSubject(rule, ruleCtx)
            val bodyText = renderBody(rule, ruleCtx, settings)

            // A digest rule collects instead of sending: the message is parked with the due
            // time of the batch that is already open, so everything in one window goes together.
            if (rule.digestEnabled) {
                val dueAt = smsLogRepository.getOpenBatchDueAt(rule.id)
                    ?: (System.currentTimeMillis() + rule.digestIntervalMinutes * MINUTE_MILLIS)
                earliestBatchDue = minOf(earliestBatchDue ?: dueAt, dueAt)

                for (target in rule.targets) {
                    logs += record(
                        ctx = ruleCtx,
                        rule = rule,
                        target = target,
                        status = LogStatus.BATCHED,
                        contentHash = contentHash,
                        subject = subject,
                        body = bodyText,
                        nextAttemptAt = dueAt
                    )
                    batched++
                }
                summaries += "Added to the next digest"
                continue
            }

            for (target in rule.targets) {
                val result = deliver(rule, ruleCtx, target, subject, bodyText, settings)
                val attempt = classify(result, attempt = 0, settings = settings)

                logs += record(
                    ctx = ruleCtx,
                    rule = rule,
                    target = target,
                    status = attempt.status,
                    error = result.errorMessage,
                    response = result.responseDetails,
                    contentHash = contentHash,
                    subject = subject,
                    body = bodyText,
                    nextAttemptAt = attempt.nextAttemptAt
                )

                when (attempt.status) {
                    LogStatus.SUCCESS -> {
                        delivered++
                        engineStateStore.recordDelivery()
                        summaries += "Sent to $target"
                    }
                    LogStatus.PENDING -> {
                        queued++
                        summaries += "Queued for $target"
                    }
                    else -> {
                        failed++
                        summaries += "Failed: ${result.errorMessage ?: "unknown error"}"
                    }
                }
            }
        }

        if (!anyRuleMatched) {
            return@withContext skip(ctx, "No rule matched this message", contentHash)
        }

        if (queued > 0) {
            ForwardScheduler.enqueueRetry(
                context = context,
                delayMillis = ForwardScheduler.backoffMillis(0),
                requireUnmeteredNetwork = settings.retryOnlyOnWifi
            )
        }

        earliestBatchDue?.let { dueAt ->
            ForwardScheduler.enqueueDigest(
                context = context,
                delayMillis = dueAt - System.currentTimeMillis(),
                requireUnmeteredNetwork = settings.retryOnlyOnWifi
            )
        }

        if (settings.notifyOnForward && summaries.isNotEmpty()) {
            notificationHelper.showForwardedNotification(
                sender = ctx.sender,
                body = ctx.body,
                destinationsSummary = summaries.joinToString("; "),
                allSuccess = failed == 0
            )
        }

        ForwardOutcome(
            logs = logs,
            delivered = delivered,
            failed = failed,
            queued = queued,
            batched = batched
        )
    }

    /** Sends to one target using the transport the rule asks for. */
    private suspend fun deliver(
        rule: ForwardingRuleEntity,
        ctx: MessageContext,
        target: String,
        subject: String,
        body: String,
        settings: ForwarderSettings
    ): ForwardResult = when (rule.destinationType) {
        DestinationType.SMS -> phoneForwarder.sendSms(
            targetPhone = target,
            body = body
        )

        DestinationType.WEBHOOK -> webhookForwarder.sendWebhook(
            url = target,
            format = rule.webhookFormat,
            customHeaders = rule.webhookHeaders,
            ctx = ctx
        )

        DestinationType.TELEGRAM -> telegramForwarder.sendMessage(
            botToken = rule.telegramBotToken,
            chatId = target,
            text = body
        )

        else -> sendEmail(settings, target, subject, body, ctx.timestamp)
    }

    private suspend fun sendEmail(
        settings: ForwarderSettings,
        toEmail: String,
        subject: String,
        body: String,
        timestamp: Long
    ): ForwardResult {
        if (!settings.isSenderAccountConfigured) {
            return ForwardResult(
                success = false,
                errorMessage = "No sender account linked. Open Setup and connect your Google account."
            )
        }
        return if (settings.authMethod == AuthMethod.GOOGLE_OAUTH) {
            emailForwarder.sendViaGmailApi(
                context = context,
                fromEmail = settings.senderEmailAccount,
                fromName = settings.senderDisplayName,
                toEmail = toEmail,
                subject = subject,
                body = body,
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
                subject = subject,
                body = body,
                timestamp = timestamp
            )
        }
    }

    // ---------------------------------------------------------------- retries

    /**
     * Manual "try again" from the log screen. It re-sends the exact rendered message that was
     * built the first time, so a retry can no longer deliver different content than the
     * original attempt, and it never targets the placeholder address of a skipped entry.
     */
    suspend fun retryForwarding(logId: Long): ForwardResult = withContext(Dispatchers.IO) {
        val log = smsLogRepository.getLogById(logId)
            ?: return@withContext ForwardResult(false, errorMessage = "This log entry no longer exists.")

        if (log.status == LogStatus.SKIPPED) {
            return@withContext ForwardResult(
                false,
                errorMessage = "This message was never sent, so there is nothing to retry."
            )
        }

        val settings = settingsRepository.getSettings()
        val rule = log.ruleId?.let { ruleRepository.getRuleById(it) }
            ?: return@withContext ForwardResult(
                false,
                errorMessage = "The rule behind this entry was deleted. Create it again to resend."
            )

        val ctx = contextFrom(log, rule)
        val subject = log.renderedSubject ?: renderSubject(rule, ctx)
        val body = log.renderedBody ?: renderBody(rule, ctx, settings)

        val result = deliver(rule, ctx, log.destinationTarget, subject, body, settings)
        val attempt = classify(result, attempt = log.retryCount + 1, settings = settings)

        smsLogRepository.updateLog(
            log.copy(
                status = attempt.status,
                errorMessage = result.errorMessage,
                responsePayload = result.responseDetails,
                retryCount = log.retryCount + 1,
                forwardedAt = if (result.success) System.currentTimeMillis() else log.forwardedAt,
                renderedSubject = subject,
                renderedBody = body,
                nextAttemptAt = attempt.nextAttemptAt
            )
        )

        if (attempt.status == LogStatus.PENDING) {
            ForwardScheduler.enqueueRetry(
                context = context,
                delayMillis = ForwardScheduler.backoffMillis(log.retryCount + 1),
                requireUnmeteredNetwork = settings.retryOnlyOnWifi
            )
        }
        result
    }

    /** Called by [ForwardRetryWorker]. */
    suspend fun drainRetryQueue(): RetryOutcome = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val due = smsLogRepository.getDueForRetry(System.currentTimeMillis())
        var delivered = 0

        for (log in due) {
            val rule = log.ruleId?.let { ruleRepository.getRuleById(it) }
            if (rule == null) {
                // The rule is gone, so this entry can never succeed. Settle it instead of
                // leaving it in the queue forever.
                smsLogRepository.updateLog(
                    log.copy(
                        status = LogStatus.FAILED,
                        errorMessage = "The rule behind this entry was deleted.",
                        nextAttemptAt = null
                    )
                )
                continue
            }

            val ctx = contextFrom(log, rule)
            val subject = log.renderedSubject ?: renderSubject(rule, ctx)
            val body = log.renderedBody ?: renderBody(rule, ctx, settings)

            val result = deliver(rule, ctx, log.destinationTarget, subject, body, settings)
            val attempt = classify(result, attempt = log.retryCount + 1, settings = settings)

            smsLogRepository.updateLog(
                log.copy(
                    status = attempt.status,
                    errorMessage = result.errorMessage,
                    responsePayload = result.responseDetails,
                    retryCount = log.retryCount + 1,
                    forwardedAt = if (result.success) System.currentTimeMillis() else log.forwardedAt,
                    nextAttemptAt = attempt.nextAttemptAt
                )
            )
            if (result.success) delivered++
        }

        RetryOutcome(
            processed = due.size,
            delivered = delivered,
            remaining = smsLogRepository.countQueued()
        )
    }

    /**
     * Sends every digest batch that is now due, one combined message per rule, and reports when
     * the next batch falls due so the worker can be rescheduled.
     */
    suspend fun drainDigests(): DigestOutcome = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val due = smsLogRepository.getDueDigestEntries(System.currentTimeMillis())
        if (due.isEmpty()) {
            return@withContext DigestOutcome(0, 0, smsLogRepository.getNextBatchDueAt())
        }

        var batchesSent = 0
        var messagesSent = 0

        // One batch per rule and target: a rule with two recipients sends each of them a digest.
        val batches = due.groupBy { it.ruleId to it.destinationTarget }

        for ((key, entries) in batches) {
            val (ruleId, target) = key
            val rule = ruleId?.let { ruleRepository.getRuleById(it) }
            if (rule == null) {
                entries.forEach { entry ->
                    smsLogRepository.updateLog(
                        entry.copy(
                            status = LogStatus.FAILED,
                            errorMessage = "The rule behind this digest was deleted.",
                            nextAttemptAt = null
                        )
                    )
                }
                continue
            }

            val digestEntries = entries
                .sortedBy { it.receivedAt }
                .map {
                    MessageTemplate.DigestEntry(
                        sender = it.sender,
                        body = it.body,
                        receivedAt = it.receivedAt,
                        source = it.source
                    )
                }

            val subject = MessageTemplate.digestSubject(rule.name, digestEntries.size)
            val body = MessageTemplate.digestBody(rule.name, digestEntries, settings.includeBrandingFooter)

            // The digest is one message, so it carries the newest entry's context.
            val newest = entries.maxByOrNull { it.receivedAt } ?: entries.first()
            val ctx = contextFrom(newest, rule)

            val result = deliver(rule, ctx, target, subject, body, settings)
            val settled = if (result.success) LogStatus.SUCCESS else LogStatus.FAILED
            val now = System.currentTimeMillis()

            entries.forEach { entry ->
                smsLogRepository.updateLog(
                    entry.copy(
                        status = settled,
                        errorMessage = result.errorMessage,
                        responsePayload = result.responseDetails,
                        forwardedAt = if (result.success) now else null,
                        renderedSubject = subject,
                        nextAttemptAt = null
                    )
                )
            }

            if (result.success) {
                batchesSent++
                messagesSent += entries.size
                engineStateStore.recordDelivery()
                if (settings.notifyOnForward) {
                    notificationHelper.showForwardedNotification(
                        sender = rule.name,
                        body = "${entries.size} message(s) collected",
                        destinationsSummary = "Digest sent to $target",
                        allSuccess = true
                    )
                }
            }
        }

        DigestOutcome(batchesSent, messagesSent, smsLogRepository.getNextBatchDueAt())
    }

    // ---------------------------------------------------------------- diagnostics

    /** "Send test message" from Setup. */
    suspend fun testSenderAccount(testRecipient: String): ForwardResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val target = testRecipient.ifBlank { settings.senderEmailAccount }
        sendEmail(
            settings = settings,
            toEmail = target,
            subject = "Test message from SMS & Notification Forwarder",
            body = buildString {
                append("Your sender account is connected and working.\n\n")
                append("Any SMS or notification that matches one of your rules will now be\n")
                append("forwarded to the recipients you chose.")
                if (settings.includeBrandingFooter) {
                    append("\n\nDeveloped by PenduCoder - https://penducoder.com")
                }
            },
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Confirms a just-connected Google account really can send, so a missing OAuth client is
     * reported at connect time rather than silently on the first real message.
     */
    suspend fun verifyGoogleAccount(email: String): ForwardResult =
        emailForwarder.verifyGoogleAccess(context, email)

    /** "Send test" from the rule editor, so a rule can be verified before it is saved. */
    suspend fun testRule(rule: ForwardingRuleEntity): ForwardResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val target = rule.targets.firstOrNull()
            ?: return@withContext ForwardResult(false, errorMessage = "Add a destination first.")

        val ctx = MessageContext(
            sender = "+10000000000",
            body = "This is a test message from your \"${rule.name}\" rule.",
            timestamp = System.currentTimeMillis(),
            source = MessageSource.SMS,
            ruleName = rule.name
        )
        deliver(rule, ctx, target, renderSubject(rule, ctx), renderBody(rule, ctx, settings), settings)
    }

    /** Removes settled entries older than the retention window. Returns rows deleted. */
    suspend fun applyRetention(): Int = withContext(Dispatchers.IO) {
        val days = settingsRepository.getSettings().logRetentionDays
        if (days <= 0) return@withContext 0
        smsLogRepository.deleteOlderThan(System.currentTimeMillis() - days * DAY_MILLIS)
    }

    // ---------------------------------------------------------------- helpers

    private data class Attempt(val status: String, val nextAttemptAt: Long?)

    /**
     * Decides whether a failure goes back on the queue. A wrong email address is permanent and
     * must not be retried forever; a dropped connection is temporary and must not be lost.
     */
    private fun classify(result: ForwardResult, attempt: Int, settings: ForwarderSettings): Attempt = when {
        result.success -> Attempt(LogStatus.SUCCESS, null)
        result.isRetryable && attempt < settings.maxRetryAttempts ->
            Attempt(LogStatus.PENDING, System.currentTimeMillis() + ForwardScheduler.backoffMillis(attempt))
        else -> Attempt(LogStatus.FAILED, null)
    }

    private fun renderSubject(rule: ForwardingRuleEntity, ctx: MessageContext): String =
        if (rule.useCustomTemplate && rule.subjectTemplate.isNotBlank()) {
            MessageTemplate.render(rule.subjectTemplate, ctx)
        } else {
            MessageTemplate.defaultSubject(ctx)
        }

    private fun renderBody(
        rule: ForwardingRuleEntity,
        ctx: MessageContext,
        settings: ForwarderSettings
    ): String = when {
        rule.useCustomTemplate && rule.bodyTemplate.isNotBlank() ->
            MessageTemplate.render(rule.bodyTemplate, ctx)
        // SMS and Telegram are length-limited, so they get the compact layout.
        rule.destinationType == DestinationType.SMS || rule.destinationType == DestinationType.TELEGRAM ->
            MessageTemplate.compactBody(ctx)
        else -> MessageTemplate.defaultBody(ctx, settings.includeBrandingFooter)
    }

    private fun contextFrom(log: SmsLogEntity, rule: ForwardingRuleEntity) = MessageContext(
        sender = log.sender,
        body = log.body,
        timestamp = log.receivedAt,
        source = log.source,
        simSlot = log.simSlot,
        appName = if (log.source == MessageSource.NOTIFICATION) log.sender else "",
        packageName = log.packageName.orEmpty(),
        ruleName = rule.name
    )

    private suspend fun skip(
        ctx: MessageContext,
        reason: String,
        contentHash: String = ""
    ): ForwardOutcome {
        // Notifications are far too frequent to log every non-match; SMS is not, and the user
        // needs to see why a message they were expecting never arrived.
        if (ctx.isNotification) return ForwardOutcome(skippedReason = reason)

        val log = SmsLogEntity(
            sender = ctx.sender,
            body = ctx.body,
            receivedAt = ctx.timestamp,
            destinationType = DestinationType.EMAIL,
            destinationTarget = "-",
            status = LogStatus.SKIPPED,
            errorMessage = reason,
            source = ctx.source,
            simSlot = ctx.simSlot,
            contentHash = contentHash
        )
        val id = smsLogRepository.insertLog(log)
        return ForwardOutcome(logs = listOf(log.copy(id = id)), skippedReason = reason)
    }

    private suspend fun record(
        ctx: MessageContext,
        rule: ForwardingRuleEntity,
        target: String,
        status: String,
        error: String? = null,
        response: String? = null,
        contentHash: String = "",
        subject: String? = null,
        body: String? = null,
        nextAttemptAt: Long? = null
    ): SmsLogEntity {
        val log = SmsLogEntity(
            sender = ctx.sender,
            body = ctx.body,
            receivedAt = ctx.timestamp,
            destinationType = rule.destinationType,
            destinationTarget = target,
            status = status,
            errorMessage = error,
            responsePayload = response,
            forwardedAt = if (status == LogStatus.SUCCESS) System.currentTimeMillis() else null,
            source = ctx.source,
            simSlot = ctx.simSlot,
            ruleName = rule.name,
            ruleId = rule.id,
            packageName = ctx.packageName.ifBlank { null },
            contentHash = contentHash,
            renderedSubject = subject,
            renderedBody = body,
            nextAttemptAt = nextAttemptAt
        )
        val id = smsLogRepository.insertLog(log)
        return log.copy(id = id)
    }

    /** Stable fingerprint of a message, used to suppress repeats of the same notification. */
    private fun contentHash(ctx: MessageContext): String = try {
        val raw = "${ctx.source}|${ctx.packageName}|${ctx.sender}|${ctx.title}|${ctx.body}"
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
    } catch (e: Exception) {
        ""
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val MINUTE_MILLIS = 60L * 1000L
    }
}
