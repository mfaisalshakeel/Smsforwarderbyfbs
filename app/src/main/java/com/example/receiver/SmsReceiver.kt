package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.SmsForwarderApplication
import com.example.util.SimHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? SmsForwarderApplication
        if (app == null) {
            Log.e(TAG, "Application context is not SmsForwarderApplication")
            return
        }

        val simInfo = SimHelper.fromIntent(context, intent)
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read the incoming SMS", e)
            null
        }
        if (messages.isNullOrEmpty()) return

        // Parts of one multipart SMS share a sender AND a timestamp; two separate messages that
        // arrive together do not. Grouping on both stops unrelated messages being glued into one.
        val grouped = messages.groupBy { part ->
            (part.displayOriginatingAddress ?: UNKNOWN_SENDER) to part.timestampMillis
        }

        val pendingResult = goAsync()
        // A receiver gets roughly ten seconds; own the scope so it is cancelled with the work.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                withTimeout(PROCESSING_TIMEOUT_MS) {
                    for ((key, parts) in grouped) {
                        val (sender, timestamp) = key
                        val fullBody = parts.joinToString("") { it.displayMessageBody.orEmpty() }
                        if (fullBody.isBlank()) continue

                        app.forwardingManager.forwardIncomingSms(
                            sender = sender,
                            body = fullBody,
                            timestamp = timestamp,
                            simSlot = simInfo.slot,
                            simName = simInfo.displayName
                        )
                    }
                }
            } catch (e: Exception) {
                // A slow network must not take the broadcast down; the retry queue owns delivery.
                Log.e(TAG, "Error while forwarding an incoming SMS", e)
            } finally {
                runCatching { pendingResult.finish() }
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
        const val UNKNOWN_SENDER = "Unknown"
        const val PROCESSING_TIMEOUT_MS = 9_000L
    }
}
