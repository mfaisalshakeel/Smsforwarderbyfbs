package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat

/** A missed call read back from the system call log. */
data class MissedCall(
    val number: String,
    val contactName: String,
    val receivedAt: Long
) {
    /** Contact name when the number is known, otherwise the number itself. */
    val displayName: String get() = contactName.ifBlank { number.ifBlank { "Unknown number" } }
}

/**
 * Resolves a missed call.
 *
 * `EXTRA_INCOMING_NUMBER` stopped being delivered without `READ_CALL_LOG` on Android 10, and
 * even where it is present it does not say whether the call was answered. So the phone-state
 * receiver only detects *that* a call ended unanswered, and the details are read back here.
 */
object CallLogHelper {

    private const val TAG = "CallLogHelper"
    /** The log row is written shortly after the call ends, so allow a little slack. */
    private const val LOOKBACK_MILLIS = 60_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

    /** The newest missed call recorded since [since], or null if there is none yet. */
    fun latestMissedCall(context: Context, since: Long): MissedCall? {
        if (!hasPermission(context)) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE
                ),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
                arrayOf(
                    CallLog.Calls.MISSED_TYPE.toString(),
                    (since - LOOKBACK_MILLIS).toString()
                ),
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                MissedCall(
                    number = cursor.getString(0).orEmpty(),
                    contactName = cursor.getString(1).orEmpty(),
                    receivedAt = cursor.getLong(2)
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CALL_LOG not granted")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the call log", e)
            null
        }
    }
}
