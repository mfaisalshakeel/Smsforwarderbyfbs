package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.SmsForwarderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val pendingResult = goAsync()

        // Extract SIM slot if dual SIM device
        val rawSlot = intent.getIntExtra("simSlot", intent.getIntExtra("slot", intent.getIntExtra("phone", -1)))
        val simSlot = when (rawSlot) {
            0 -> 1
            1 -> 2
            else -> 0 // Unknown / Single SIM
        }

        receiverScope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (!messages.isNullOrEmpty()) {
                    val groupedBySender = messages.groupBy { it.displayOriginatingAddress ?: "Unknown" }

                    val app = context.applicationContext as? SmsForwarderApplication
                    val forwardingManager = app?.forwardingManager

                    if (forwardingManager != null) {
                        for ((sender, msgParts) in groupedBySender) {
                            val fullBody = msgParts.joinToString("") { it.displayMessageBody ?: "" }
                            val timestamp = msgParts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                            forwardingManager.forwardIncomingSms(
                                sender = sender,
                                body = fullBody,
                                timestamp = timestamp,
                                simSlot = simSlot
                            )
                        }
                    } else {
                        Log.e("SmsReceiver", "ForwardingManager is null")
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error processing received SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
