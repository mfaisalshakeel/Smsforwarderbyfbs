package com.example.forwarder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.SmsForwarderApplication

/**
 * Sends the digest batches that have come due.
 *
 * Digest rules collect their matches instead of sending each one, so this worker is what
 * actually delivers them. It reschedules itself for the next batch rather than polling.
 */
class DigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SmsForwarderApplication ?: return Result.success()

        return try {
            val outcome = app.forwardingManager.drainDigests()
            val nextDueAt = outcome.nextDueAt
            if (nextDueAt != null) {
                ForwardScheduler.enqueueDigest(
                    context = applicationContext,
                    delayMillis = nextDueAt - System.currentTimeMillis(),
                    requireUnmeteredNetwork = app.settingsRepository.getSettings().retryOnlyOnWifi
                )
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Digest pass failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "DigestWorker"
    }
}
