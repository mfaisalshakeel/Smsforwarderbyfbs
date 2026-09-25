package com.example.forwarder

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When Google needs the user to approve the `gmail.send` scope, the auth library hands back a
 * recovery [Intent]. Forwarding happens in the background where no Activity is available, so
 * the intent is parked here and the UI launches it the next time the app is opened.
 *
 * Previously this intent was discarded, which left the account permanently unable to send with
 * no way to fix it from inside the app.
 */
object GoogleAuthRecovery {

    private val _pendingConsent = MutableStateFlow<Intent?>(null)
    val pendingConsent: StateFlow<Intent?> = _pendingConsent.asStateFlow()

    fun publish(intent: Intent?) {
        if (intent != null) _pendingConsent.value = intent
    }

    fun clear() {
        _pendingConsent.value = null
    }
}
