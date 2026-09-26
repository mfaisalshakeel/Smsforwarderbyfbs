package com.example.forwarder

import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds RFC 5322 messages that survive the trip intact.
 *
 * The previous implementation interpolated raw values straight into headers and wrote the body
 * as bare 8-bit text, which corrupted every non-English message, truncated any message
 * containing a line starting with `.`, and allowed header injection through the sender name.
 */
object MimeBuilder {

    private const val CRLF = "\r\n"
    /** Encoded words must stay under 75 characters including the `=?UTF-8?B?...?=` wrapper. */
    private const val MAX_ENCODED_WORD_BYTES = 42
    private const val BASE64_LINE_LENGTH = 76

    /**
     * Removes CR, LF and NUL so a value cannot terminate the header it sits in and inject
     * new ones (a `Bcc:` smuggled through the sender's display name, for example).
     */
    fun sanitizeHeader(value: String): String =
        value.replace("\r", " ").replace("\n", " ").replace("\u0000", "").trim()

    /**
     * RFC 2047 encodes a header value when it contains anything outside printable US-ASCII.
     * Without this, an Urdu, Arabic, Chinese or emoji subject arrives as mojibake.
     */
    fun encodeHeaderValue(value: String): String {
        val clean = sanitizeHeader(value)
        if (clean.isEmpty()) return ""
        val needsEncoding = clean.any { it.code < 32 || it.code > 126 }
        if (!needsEncoding) return clean

        return buildList {
            val chunk = StringBuilder()
            var chunkBytes = 0
            for (ch in clean) {
                val charBytes = ch.toString().toByteArray(Charsets.UTF_8).size
                if (chunkBytes + charBytes > MAX_ENCODED_WORD_BYTES) {
                    add(encodeWord(chunk.toString()))
                    chunk.setLength(0)
                    chunkBytes = 0
                }
                chunk.append(ch)
                chunkBytes += charBytes
            }
            if (chunk.isNotEmpty()) add(encodeWord(chunk.toString()))
        }.joinToString("$CRLF ")
    }

    private fun encodeWord(text: String): String {
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "=?UTF-8?B?$encoded?="
    }

    /** Formats an address as `Display Name <user@example.com>`, encoding the name if needed. */
    fun formatAddress(displayName: String, email: String): String {
        val address = sanitizeHeader(email)
        val clean = sanitizeHeader(displayName)
        if (clean.isEmpty()) return "<$address>"
        val encoded = encodeHeaderValue(clean)
        // An encoded word must not be quoted, but a plain name must be: quoting stops a colon
        // or comma inside the name from being read as address syntax.
        return if (encoded == clean) {
            "\"${clean.replace("\"", "'")}\" <$address>"
        } else {
            "$encoded <$address>"
        }
    }

    /** Base64-encodes the body and wraps it to the 76-character line limit. */
    fun encodeBody(body: String): String {
        val encoded = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return encoded.chunked(BASE64_LINE_LENGTH).joinToString(CRLF)
    }

    /**
     * Builds the complete message. The body is base64 encoded, so it contains no bare `.`
     * lines and needs no dot-stuffing — but [dotStuff] is still applied by the SMTP
     * transport as a defence in depth.
     */
    fun buildMessage(
        fromName: String,
        fromEmail: String,
        toEmail: String,
        subject: String,
        body: String,
        timestamp: Long
    ): String {
        val date = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(Date(timestamp))
        return buildString {
            append("From: ").append(formatAddress(fromName, fromEmail)).append(CRLF)
            append("To: <").append(sanitizeHeader(toEmail)).append(">").append(CRLF)
            append("Subject: ").append(encodeHeaderValue(subject)).append(CRLF)
            append("Date: ").append(date).append(CRLF)
            append("Message-ID: <").append(System.nanoTime()).append(".")
                .append(sanitizeHeader(fromEmail).substringAfter('@', "smsforwarder.local"))
                .append(">").append(CRLF)
            append("MIME-Version: 1.0").append(CRLF)
            append("Content-Type: text/plain; charset=UTF-8").append(CRLF)
            append("Content-Transfer-Encoding: base64").append(CRLF)
            append("X-Mailer: SMS & Notification Forwarder").append(CRLF)
            append("Auto-Submitted: auto-generated").append(CRLF)
            append(CRLF)
            append(encodeBody(body)).append(CRLF)
        }
    }

    /**
     * RFC 5321 dot-stuffing. A line consisting of a single `.` ends the DATA command, so any
     * line that starts with `.` must be doubled or the message is silently truncated and the
     * remainder is interpreted as SMTP commands.
     */
    fun dotStuff(message: String): String =
        message.replace("$CRLF.", "$CRLF..").let { if (it.startsWith(".")) ".$it" else it }

    /** Normalises bare LF to CRLF, which several SMTP servers reject outright. */
    fun normaliseLineEndings(message: String): String =
        message.replace("\r\n", "\n").replace("\r", "\n").replace("\n", CRLF)
}
