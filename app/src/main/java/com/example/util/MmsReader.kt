package com.example.util

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

/** A picture/group message pulled out of the MMS provider. */
data class MmsMessage(
    val id: Long,
    val sender: String,
    val subject: String,
    val text: String,
    val receivedAt: Long,
    val attachmentCount: Int
)

/**
 * Reads MMS out of the system provider.
 *
 * MMS cannot be captured from a broadcast the way SMS can — `WAP_PUSH_DELIVER` only reaches
 * the default SMS app, and the raw PDU needs the carrier's transaction to complete before the
 * body exists. So the app watches the provider instead and reads each message once Android
 * has stored it. Only the text part is forwarded; attachments are reported by count.
 */
object MmsReader {

    private const val TAG = "MmsReader"
    private const val PART_URI = "content://mms/part"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

    /** The highest MMS id currently stored, used as the starting watermark. */
    fun latestMessageId(context: Context): Long {
        if (!hasPermission(context)) return 0L
        return try {
            context.contentResolver.query(
                Telephony.Mms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                null,
                null,
                "${Telephony.Mms._ID} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the newest MMS id", e)
            0L
        }
    }

    /** Inbox messages stored after [afterId], oldest first. */
    fun messagesAfter(context: Context, afterId: Long, limit: Int = 10): List<MmsMessage> {
        if (!hasPermission(context)) return emptyList()
        return try {
            context.contentResolver.query(
                Telephony.Mms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.SUBJECT, Telephony.Mms.DATE),
                "${Telephony.Mms._ID} > ?",
                arrayOf(afterId.toString()),
                "${Telephony.Mms._ID} ASC LIMIT $limit"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val subject = cursor.getString(1).orEmpty()
                        // The provider stores the date in seconds, not milliseconds.
                        val dateSeconds = cursor.getLong(2)
                        val parts = readParts(context, id)
                        add(
                            MmsMessage(
                                id = id,
                                sender = readSender(context, id),
                                subject = subject,
                                text = parts.first,
                                receivedAt = if (dateSeconds > 0) dateSeconds * 1000L
                                else System.currentTimeMillis(),
                                attachmentCount = parts.second
                            )
                        )
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read MMS after id $afterId", e)
            emptyList()
        }
    }

    /**
     * The sender sits in a sub-table keyed by message id. Address type 137 (PduHeaders.FROM)
     * is the originator; everything else is a recipient.
     */
    private fun readSender(context: Context, messageId: Long): String {
        val uri: Uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, messageId)
            .buildUpon().appendPath("addr").build()
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                "${Telephony.Mms.Addr.TYPE} = ?",
                arrayOf(FROM_ADDRESS_TYPE.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty().ifBlank { "Unknown" }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the sender of MMS $messageId", e)
            "Unknown"
        }
    }

    /** Returns the concatenated text parts and the number of non-text attachments. */
    private fun readParts(context: Context, messageId: Long): Pair<String, Int> {
        return try {
            context.contentResolver.query(
                Uri.parse(PART_URI),
                arrayOf("_id", "ct", "text"),
                "mid = ?",
                arrayOf(messageId.toString()),
                null
            )?.use { cursor ->
                val text = StringBuilder()
                var attachments = 0
                while (cursor.moveToNext()) {
                    val contentType = cursor.getString(1).orEmpty()
                    when {
                        contentType.startsWith("text/plain") -> {
                            cursor.getString(2)?.takeIf { it.isNotBlank() }?.let { body ->
                                if (text.isNotEmpty()) text.append('\n')
                                text.append(body)
                            }
                        }
                        // SMIL is the layout descriptor, not content the user wrote.
                        contentType.contains("smil", ignoreCase = true) -> Unit
                        contentType.isNotBlank() -> attachments++
                    }
                }
                text.toString() to attachments
            } ?: ("" to 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the parts of MMS $messageId", e)
            "" to 0
        }
    }

    /** `PduHeaders.FROM`, which is not exposed as a public constant. */
    private const val FROM_ADDRESS_TYPE = 137
}
