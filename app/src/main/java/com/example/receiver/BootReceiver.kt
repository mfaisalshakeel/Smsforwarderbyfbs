package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.SmsForwarderApplication
import com.example.forwarder.ForwardScheduler
import com.example.service.ForwarderForegroundService

/**
 * Restarts the forwarding engine after a reboot or an app update, and drains anything that was
 * still queued when the device went down.
 *
 * The app declared RECEIVE_BOOT_COMPLETED but had no receiver, so nothing came back after a
 * restart until the user opened the app by hand.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        Log.i(TAG, "Restarting the forwarding engine after $action")
        try {
            ForwarderForegroundService.start(context)

            val app = context.applicationContext as? SmsForwarderApplication ?: return
            ForwardScheduler.enqueueImmediateDrain(
                context = context,
                requireUnmeteredNetwork = app.settingsRepository.getSettings().retryOnlyOnWifi
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart after $action", e)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
