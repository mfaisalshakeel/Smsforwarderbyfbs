package com.example.forwarder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class PhoneForwarder(private val context: Context) {

    fun sendSms(
        targetPhone: String,
        sender: String,
        body: String,
        template: String
    ): ForwardResult {
        // Check SEND_SMS permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ForwardResult(
                success = false,
                errorMessage = "SEND_SMS permission not granted. Please grant permission in app settings."
            )
        }

        if (targetPhone.isBlank()) {
            return ForwardResult(
                success = false,
                errorMessage = "Target phone number is empty."
            )
        }

        val formattedMessage = template
            .replace("{sender}", sender)
            .replace("{body}", body)
            .replace("{time}", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(formattedMessage)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetPhone, null, formattedMessage, null, null)
            }

            ForwardResult(
                success = true,
                responseDetails = "Sent $formattedMessage (${parts.size} part(s)) to $targetPhone"
            )
        } catch (e: Exception) {
            ForwardResult(
                success = false,
                errorMessage = "Failed to send SMS: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}

data class ForwardResult(
    val success: Boolean,
    val responseDetails: String? = null,
    val errorMessage: String? = null
)
