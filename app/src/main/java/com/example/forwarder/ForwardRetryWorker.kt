package com.example.forwarder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.SmsForwarderApplication

/**
 * Drains the retry queue. Every forward that failed for a transient reason (no connectivity,
 * a 5xx, an expired token) lands here instead of being lost, which was the behaviour before.
 */
class ForwardRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SmsForwarderApplication ?: return Result.success()

        return try {
            val outcome = app.forwardingManager.drainRetryQueue()
            // Ask WorkManager to run us again only while something is still waiting.
            if (outcome.remaining > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Retry pass failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "ForwardRetryWorker"
    }
}
