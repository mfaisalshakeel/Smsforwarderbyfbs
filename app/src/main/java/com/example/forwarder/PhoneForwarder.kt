package com.example.forwarder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Forwards a message to another phone number as an SMS. */
class PhoneForwarder(private val context: Context) {

    suspend fun sendSms(
        targetPhone: String,
        body: String
    ): ForwardResult = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "The Send SMS permission is not granted. Enable it in Setup."
            )
        }
        if (targetPhone.isBlank()) {
            return@withContext ForwardResult(success = false, errorMessage = "Target phone number is empty.")
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetPhone, null, body, null, null)
            }

            ForwardResult(
                success = true,
                responseDetails = "Sent to $targetPhone (${parts.size} part${if (parts.size == 1) "" else "s"})"
            )
        } catch (e: Exception) {
            ForwardResult(
                success = false,
                errorMessage = "Could not send SMS: ${e.localizedMessage ?: e.javaClass.simpleName}",
                isRetryable = true
            )
        }
    }
}
