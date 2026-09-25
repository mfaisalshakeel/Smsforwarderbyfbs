package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.SmsForwarderApplication
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive so forwarding survives Doze, app standby and aggressive OEM task
 * killers. Without it the app only worked while it happened to be in memory, which is the
 * single biggest reason a forwarder "stops working after a few hours".
 */
class ForwarderForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as? SmsForwarderApplication
        if (app == null) {
            stopSelf()
            return
        }
        notificationHelper = app.notificationHelper

        startInForeground(getString(R.string.service_status_starting))

        // Keep the notification text in step with the engine's actual state.
        serviceScope.launch {
            app.settingsRepository.settingsFlow.collectLatest { settings ->
                val status = when {
                    !settings.isForwarderEnabled -> getString(R.string.service_status_paused)
                    !settings.isSenderAccountConfigured -> getString(R.string.service_status_no_account)
                    else -> getString(R.string.service_status_active)
                }
                runCatching { notificationHelper.updateServiceNotification(status) }
            }
        }
    }

    private fun startInForeground(statusText: String) {
        val notification = notificationHelper.buildServiceNotification(statusText)
        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            // Android 12+ refuses a foreground start from the background in some states.
            // Forwarding still works while the app is alive, so this must not crash.
            Log.w(TAG, "Could not enter the foreground", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ask the platform to recreate us if the process is killed.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ForwarderService"

        /** Starts the service unless the user has turned the always-on option off. */
        fun start(context: Context) {
            val app = context.applicationContext as? SmsForwarderApplication ?: return
            val settings = app.settingsRepository.getSettings()
            if (!settings.keepServiceAlive || !settings.isForwarderEnabled) {
                stop(context)
                return
            }
            val intent = Intent(context, ForwarderForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start the forwarding service", e)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ForwarderForegroundService::class.java))
            }
        }
    }
}
