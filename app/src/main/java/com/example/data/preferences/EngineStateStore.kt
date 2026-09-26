package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the forwarding engine has actually been doing.
 *
 * Every field is a fact recorded by the running engine, not a setting. The app uses these to
 * tell the user honestly whether background forwarding is alive, instead of assuming it is
 * because a switch is on.
 */
data class EngineState(
    /** The service believes it is running. Cleared when it is destroyed. */
    val serviceRunning: Boolean = false,
    /** Last time the service proved it was alive. */
    val lastHeartbeatAt: Long = 0L,
    /** Last time a message or call actually reached the engine. */
    val lastEventAt: Long = 0L,
    val lastEventSource: String = "",
    /** Last successful delivery. */
    val lastDeliveryAt: Long = 0L,
    /** Last time the engine started up, including after a reboot. */
    val lastStartedAt: Long = 0L,
    /** Set by the boot receiver, so the user can see that restart-on-boot really works. */
    val lastBootRestartAt: Long = 0L,
    /** Why the service last stopped, when it stopped itself. */
    val lastStopReason: String = ""
) {
    /**
     * A heartbeat older than this means the process was killed without the service getting a
     * chance to clear its flag — the exact failure this whole mechanism exists to catch.
     */
    fun isHeartbeatStale(now: Long): Boolean =
        lastHeartbeatAt > 0L && now - lastHeartbeatAt > STALE_AFTER_MILLIS

    companion object {
        /** The service beats every 10 minutes; allow a couple of missed beats before alarming. */
        const val HEARTBEAT_INTERVAL_MILLIS = 10L * 60L * 1000L
        const val STALE_AFTER_MILLIS = 25L * 60L * 1000L
    }
}

class EngineStateStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun current(): EngineState = _state.value

    private fun load() = EngineState(
        serviceRunning = prefs.getBoolean(KEY_RUNNING, false),
        lastHeartbeatAt = prefs.getLong(KEY_HEARTBEAT, 0L),
        lastEventAt = prefs.getLong(KEY_EVENT_AT, 0L),
        lastEventSource = prefs.getString(KEY_EVENT_SOURCE, "").orEmpty(),
        lastDeliveryAt = prefs.getLong(KEY_DELIVERY_AT, 0L),
        lastStartedAt = prefs.getLong(KEY_STARTED_AT, 0L),
        lastBootRestartAt = prefs.getLong(KEY_BOOT_AT, 0L),
        lastStopReason = prefs.getString(KEY_STOP_REASON, "").orEmpty()
    )

    private fun update(block: EngineState.() -> EngineState) {
        val updated = _state.value.block()
        prefs.edit()
            .putBoolean(KEY_RUNNING, updated.serviceRunning)
            .putLong(KEY_HEARTBEAT, updated.lastHeartbeatAt)
            .putLong(KEY_EVENT_AT, updated.lastEventAt)
            .putString(KEY_EVENT_SOURCE, updated.lastEventSource)
            .putLong(KEY_DELIVERY_AT, updated.lastDeliveryAt)
            .putLong(KEY_STARTED_AT, updated.lastStartedAt)
            .putLong(KEY_BOOT_AT, updated.lastBootRestartAt)
            .putString(KEY_STOP_REASON, updated.lastStopReason)
            .apply()
        _state.value = updated
    }

    fun recordServiceStarted() = update {
        copy(
            serviceRunning = true,
            lastStartedAt = System.currentTimeMillis(),
            lastHeartbeatAt = System.currentTimeMillis(),
            lastStopReason = ""
        )
    }

    fun recordServiceStopped(reason: String) = update {
        copy(serviceRunning = false, lastStopReason = reason)
    }

    fun recordHeartbeat() = update { copy(lastHeartbeatAt = System.currentTimeMillis()) }

    fun recordEvent(source: String) = update {
        copy(lastEventAt = System.currentTimeMillis(), lastEventSource = source)
    }

    fun recordDelivery() = update { copy(lastDeliveryAt = System.currentTimeMillis()) }

    fun recordBootRestart() = update { copy(lastBootRestartAt = System.currentTimeMillis()) }

    private companion object {
        const val PREFS_NAME = "sms_forwarder_engine_state"
        const val KEY_RUNNING = "service_running"
        const val KEY_HEARTBEAT = "last_heartbeat"
        const val KEY_EVENT_AT = "last_event_at"
        const val KEY_EVENT_SOURCE = "last_event_source"
        const val KEY_DELIVERY_AT = "last_delivery_at"
        const val KEY_STARTED_AT = "last_started_at"
        const val KEY_BOOT_AT = "last_boot_restart_at"
        const val KEY_STOP_REASON = "last_stop_reason"
    }
}
