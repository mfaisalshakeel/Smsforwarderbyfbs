package com.example.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.example.SmsForwarderApplication
import com.example.util.MmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Watches the MMS provider and forwards each new picture message once.
 *
 * Lives for as long as the foreground service does. A watermark of the highest id already
 * seen keeps it from re-forwarding old messages after a restart.
 */
class MmsWatcher(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var lastSeenId = 0L
    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) return
        if (!MmsReader.hasPermission(context)) {
            Log.i(TAG, "READ_SMS not granted; not watching for MMS")
            return
        }

        // Anything already in the inbox predates this session and must not be forwarded.
        scope.launch { lock.withLock { lastSeenId = MmsReader.latestMessageId(context) } }

        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                scope.launch { drain() }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                Telephony.Mms.CONTENT_URI,
                true,
                contentObserver
            )
            observer = contentObserver
            Log.i(TAG, "Watching the MMS provider")
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch the MMS provider", e)
        }
    }

    fun stop() {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
        scope.cancel()
    }

    private suspend fun drain() {
        // The provider fires as soon as the row appears, before the parts are written, so give
        // Android a moment to finish assembling the message.
        delay(SETTLE_DELAY_MS)

        lock.withLock {
            val app = context.applicationContext as? SmsForwarderApplication ?: return
            if (!app.settingsRepository.getSettings().isForwarderEnabled) return

            val messages = MmsReader.messagesAfter(context, lastSeenId)
            if (messages.isEmpty()) return

            for (message in messages) {
                lastSeenId = maxOf(lastSeenId, message.id)
                val body = listOf(message.subject, message.text)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                if (body.isBlank() && message.attachmentCount == 0) continue

                try {
                    app.forwardingManager.forwardIncomingMms(
                        sender = message.sender,
                        body = body.ifBlank { "(no text)" },
                        timestamp = message.receivedAt,
                        attachmentCount = message.attachmentCount
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Could not forward MMS ${message.id}", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MmsWatcher"
        const val SETTLE_DELAY_MS = 2_500L
    }
}
