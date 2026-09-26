package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Lifecycle of a single forwarding attempt. */
object LogStatus {
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val PENDING = "PENDING"
    /** Collected by a digest rule, waiting to be sent as part of a batch. */
    const val BATCHED = "BATCHED"
    const val SKIPPED = "SKIPPED"
}

@Entity(
    tableName = "sms_logs",
    indices = [
        Index(value = ["receivedAt"]),
        Index(value = ["status"]),
        Index(value = ["contentHash"])
    ]
)
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val destinationType: String,
    val destinationTarget: String,
    val status: String,
    val errorMessage: String? = null,
    val responsePayload: String? = null,
    val retryCount: Int = 0,
    val forwardedAt: Long? = null,
    /** "SMS" or "NOTIFICATION". */
    val source: String = "SMS",
    val simSlot: Int = 0,
    val ruleName: String? = null,

    // ---- Added in schema v3 ------------------------------------------------
    /** The rule that produced this entry, so a retry can re-run the same rule. */
    val ruleId: Long? = null,
    /** Package name for notification-sourced entries. */
    val packageName: String? = null,
    /** Stable hash of sender + body, used to suppress duplicate notifications. */
    val contentHash: String = "",
    /** The exact subject that was sent, so a retry reproduces the original message. */
    val renderedSubject: String? = null,
    /** The exact body that was sent, so a retry reproduces the original message. */
    val renderedBody: String? = null,
    /** When the retry worker should try again; null once the entry is settled. */
    val nextAttemptAt: Long? = null
) {
    val isRetryable: Boolean
        get() = status == LogStatus.FAILED || status == LogStatus.PENDING

    /** Waiting in a digest batch rather than having failed. */
    val isBatched: Boolean
        get() = status == LogStatus.BATCHED
}
