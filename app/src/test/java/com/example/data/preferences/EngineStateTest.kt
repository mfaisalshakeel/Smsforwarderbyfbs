package com.example.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineStateTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a recent heartbeat is not stale`() {
        assertFalse(EngineState(lastHeartbeatAt = now - 5 * 60_000L).isHeartbeatStale(now))
    }

    @Test
    fun `one missed beat is tolerated`() {
        // Doze delays timers, so a single late beat must not raise a false alarm.
        assertFalse(EngineState(lastHeartbeatAt = now - 15 * 60_000L).isHeartbeatStale(now))
    }

    @Test
    fun `a long silence is stale`() {
        assertTrue(EngineState(lastHeartbeatAt = now - 40 * 60_000L).isHeartbeatStale(now))
    }

    @Test
    fun `a never-started engine is not reported as stale`() {
        // Zero means "has not run yet", which is a different problem from "stopped responding".
        assertFalse(EngineState(lastHeartbeatAt = 0L).isHeartbeatStale(now))
    }
}
