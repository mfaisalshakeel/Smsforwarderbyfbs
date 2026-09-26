package com.example.forwarder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Queues the retry worker. Work is coalesced into a single named job so a burst of failures
 * does not schedule dozens of wake-ups.
 */
object ForwardScheduler {

    private const val RETRY_WORK_NAME = "sms_forwarder_retry"
    private const val DIGEST_WORK_NAME = "sms_forwarder_digest"

    /** Exponential backoff, capped so a long outage does not push the next try hours away. */
    fun backoffMillis(attempt: Int): Long {
        val seconds = (BASE_DELAY_SECONDS shl attempt.coerceIn(0, 6)).coerceAtMost(MAX_DELAY_SECONDS)
        return seconds * 1000L
    }

    fun enqueueRetry(context: Context, delayMillis: Long, requireUnmeteredNetwork: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<ForwardRetryWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RETRY_WORK_NAME,
            // REPLACE so the soonest pending item decides when the queue next runs.
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Schedules the digest pass. Uses KEEP rather than REPLACE so a later message joining an
     * open batch does not push the already-scheduled send further out.
     */
    fun enqueueDigest(context: Context, delayMillis: Long, requireUnmeteredNetwork: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            DIGEST_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /** Called on boot and on app start so nothing is stranded in the queue. */
    fun enqueueImmediateDrain(context: Context, requireUnmeteredNetwork: Boolean) {
        enqueueRetry(context, 0L, requireUnmeteredNetwork)
    }

    private const val BASE_DELAY_SECONDS = 30L
    private const val MAX_DELAY_SECONDS = 3600L
}
