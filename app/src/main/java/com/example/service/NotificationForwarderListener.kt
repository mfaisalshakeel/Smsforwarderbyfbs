package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.SmsForwarderApplication
import com.example.util.AppInfoHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Captures app notifications and hands them to the forwarding engine.
 *
 * Three things here were previously missing and between them made notification forwarding
 * either silent or unusable:
 *  - the service never asked to be rebound after Android unbound it, so forwarding stopped
 *    permanently until the user toggled the permission by hand;
 *  - ongoing, group-summary and service notifications were forwarded, so a music player or a
 *    download bar produced an email every time it redrew;
 *  - nothing suppressed repeats, so a chat app re-posting the same notification sent duplicates.
 */
class NotificationForwarderListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Short-lived in-memory guard so obvious repeats never reach the database. */
    private val recentlySeen = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_RECENT_ENTRIES
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Android unbinds listeners routinely. Without this the app silently stops forwarding.
        Log.w(TAG, "Notification listener disconnected; requesting rebind")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                requestRebind(ComponentName(applicationContext, NotificationForwarderListener::class.java))
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val app = applicationContext as? SmsForwarderApplication ?: return
        val settings = app.settingsRepository.settingsFlow.value
        if (!settings.isForwarderEnabled) return

        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        if (shouldIgnore(sbn, notification, settings.skipOngoingNotifications, settings.skipGroupSummaries)) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val content = if (!bigText.isNullOrBlank()) bigText else text
        if (title.isBlank() && content.isBlank()) return

        if (isDuplicate("$packageName|$title|$content", settings.duplicateWindowSeconds)) return

        serviceScope.launch {
            try {
                // Resolving the app label hits PackageManager, so it stays off the main thread.
                val appName = AppInfoHelper.labelFor(applicationContext, packageName)
                app.forwardingManager.forwardIncomingNotification(
                    appName = appName,
                    packageName = packageName,
                    title = title,
                    text = content,
                    timestamp = sbn.postTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward a notification from $packageName", e)
            }
        }
    }

    /** Filters out the notification kinds that are noise rather than messages. */
    private fun shouldIgnore(
        sbn: StatusBarNotification,
        notification: Notification,
        skipOngoing: Boolean,
        skipGroupSummaries: Boolean
    ): Boolean {
        val flags = notification.flags

        if (skipOngoing) {
            // Music players, downloads, navigation and anything else that sits in the shade.
            if (sbn.isOngoing) return true
            if (flags and Notification.FLAG_ONGOING_EVENT != 0) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                flags and Notification.FLAG_FOREGROUND_SERVICE != 0
            ) {
                return true
            }
        }

        // The collapsed "3 new messages" header duplicates the individual notifications below it.
        if (skipGroupSummaries && flags and Notification.FLAG_GROUP_SUMMARY != 0) return true

        // Local-only notifications are explicitly marked "do not mirror off this device".
        if (flags and Notification.FLAG_LOCAL_ONLY != 0) return true

        // Progress bars redraw constantly and carry no message.
        val extras = notification.extras
        if (extras != null && extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return true

        return false
    }

    private fun isDuplicate(key: String, windowSeconds: Int): Boolean {
        if (windowSeconds <= 0) return false
        val now = System.currentTimeMillis()
        val windowMillis = windowSeconds * 1000L
        synchronized(recentlySeen) {
            val previous = recentlySeen[key]
            if (previous != null && now - previous < windowMillis) return true
            recentlySeen[key] = now
        }
        return false
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "NotifForwarder"
        const val MAX_RECENT_ENTRIES = 200
    }
}
