package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val destinationType: String, // "EMAIL", "PHONE", "WEBHOOK", "NONE"
    val destinationTarget: String, // e.g. recipient email address
    val status: String, // "SUCCESS", "FAILED", "PENDING", "SKIPPED"
    val errorMessage: String? = null,
    val responsePayload: String? = null,
    val retryCount: Int = 0,
    val forwardedAt: Long? = null,
    val source: String = "SMS", // "SMS" or "NOTIFICATION"
    val simSlot: Int = 0, // 0 = Any/Unknown, 1 = SIM 1, 2 = SIM 2
    val ruleName: String? = null
)
