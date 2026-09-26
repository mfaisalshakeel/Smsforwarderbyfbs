package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.SmsForwarderApplication
import com.example.util.CallLogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Detects a missed call.
 *
 * The platform does not broadcast "missed call" directly, so the state machine is inferred:
 * RINGING followed by IDLE with no OFFHOOK in between means nobody picked up. The number and
 * contact name are then read back from the call log, which is the only reliable source on
 * Android 10 and above.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val app = context.applicationContext as? SmsForwarderApplication ?: return
        if (!app.settingsRepository.getSettings().isForwarderEnabled) return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallState.ringingSince = System.currentTimeMillis()
                CallState.wasAnswered = false
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallState.wasAnswered = true
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val ringingSince = CallState.ringingSince
                val answered = CallState.wasAnswered
                CallState.ringingSince = 0L
                CallState.wasAnswered = false

                if (ringingSince == 0L || answered) return
                handleMissedCall(context, app, ringingSince)
            }
        }
    }

    private fun handleMissedCall(
        context: Context,
        app: SmsForwarderApplication,
        ringingSince: Long
    ) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                withTimeout(PROCESSING_TIMEOUT_MS) {
                    // The call-log row is written a moment after the call ends, so poll briefly
                    // rather than reading once and finding nothing.
                    var call = CallLogHelper.latestMissedCall(context, ringingSince)
                    var attempts = 0
                    while (call == null && attempts < LOOKUP_ATTEMPTS) {
                        delay(LOOKUP_DELAY_MS)
                        call = CallLogHelper.latestMissedCall(context, ringingSince)
                        attempts++
                    }

                    app.forwardingManager.forwardMissedCall(
                        number = call?.number.orEmpty(),
                        displayName = call?.displayName ?: "Unknown number",
                        timestamp = call?.receivedAt ?: ringingSince
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not forward a missed call", e)
            } finally {
                runCatching { pendingResult.finish() }
                scope.cancel()
            }
        }
    }

    /** Shared across receiver instances; Android may create a new one per broadcast. */
    private object CallState {
        @Volatile
        var ringingSince: Long = 0L

        @Volatile
        var wasAnswered: Boolean = false
    }

    private companion object {
        const val TAG = "CallReceiver"
        const val PROCESSING_TIMEOUT_MS = 9_000L
        const val LOOKUP_ATTEMPTS = 6
        const val LOOKUP_DELAY_MS = 700L
    }
}
