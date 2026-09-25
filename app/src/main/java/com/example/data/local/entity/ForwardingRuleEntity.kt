package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarding_rules")
data class ForwardingRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Email Forwarding Rule",
    val isEnabled: Boolean = true,
    val recipientEmail: String,
    val forwardSms: Boolean = true,
    val forwardNotifications: Boolean = false,
    val simSlot: Int = 0, // 0 = Any SIM, 1 = SIM 1, 2 = SIM 2
    val senderFilterType: String = "ANY", // "ANY", "CONTAINS", "EXACT"
    val senderFilterValue: String = "",
    val contentFilterType: String = "ANY", // "ANY", "CONTAINS"
    val contentFilterValue: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
