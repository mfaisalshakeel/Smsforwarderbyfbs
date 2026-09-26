package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

class NotificationHelper(private val context: Context) {

    companion object {
        /** Low-importance channel for the always-on service notification. */
        const val SERVICE_CHANNEL_ID = "sms_forwarder_service"
        /** Default-importance channel for "message forwarded" alerts. */
        const val ALERT_CHANNEL_ID = "sms_forwarder_alerts"

        const val SERVICE_NOTIFICATION_ID = 1001
        /**
         * A single reused id, so twenty forwarded messages update one notification instead of
         * stacking twenty separate ones in the shade.
         */
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val GROUP_KEY = "com.example.FORWARDED"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.channel_service_desc)
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
            }
        )
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The persistent notification that keeps the forwarding service alive. */
    fun buildServiceNotification(statusText: String) =
        NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forward)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(contentIntent())
            .build()

    /** Refreshes the ongoing service notification without needing a permission check at each call site. */
    fun updateServiceNotification(statusText: String) {
        notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(statusText))
    }

    fun showForwardedNotification(
        sender: String,
        body: String,
        destinationsSummary: String,
        allSuccess: Boolean
    ) {
        val title = if (allSuccess) {
            context.getString(R.string.notify_forwarded_title, sender)
        } else {
            context.getString(R.string.notify_problem_title, sender)
        }

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_forward)
            .setContentTitle(title)
            .setContentText(destinationsSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\n$destinationsSummary"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent())
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .build()

        notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS; nothing to do.
        }
    }
}
