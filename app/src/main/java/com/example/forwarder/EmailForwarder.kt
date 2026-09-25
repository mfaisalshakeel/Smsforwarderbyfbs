package com.example.forwarder

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal SMTP client used for the "app password" login method.
 *
 * Correctness notes, all of which were defects in the first implementation:
 *  - the greeting and every response may be multi-line, so all reads are multi-line aware;
 *  - AUTH mechanism is chosen from what the server advertises rather than assumed;
 *  - the message is dot-stuffed before DATA so a line starting with `.` cannot truncate it;
 *  - the body is base64 encoded and headers are RFC 2047 encoded, so non-ASCII survives;
 *  - writes go through a Writer that surfaces I/O errors instead of swallowing them.
 */
class EmailForwarder {

    private val gmailApiForwarder = GmailApiForwarder()

    suspend fun sendViaGmailApi(
        context: android.content.Context,
        fromEmail: String,
        fromName: String,
        toEmail: String,
        subject: String,
        body: String,
        timestamp: Long
    ): ForwardResult = gmailApiForwarder.sendEmail(
        context = context,
        senderEmail = fromEmail,
        fromName = fromName,
        toEmail = toEmail,
        subject = subject,
        body = body,
        timestamp = timestamp
    )

    suspend fun sendEmail(
        host: String,
        port: Int,
        username: String,
        password: String,
        useTls: Boolean,
        fromName: String,
        toEmail: String,
        subject: String,
        body: String,
        timestamp: Long
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext ForwardResult(false, errorMessage = "SMTP host is empty.")
        }
        if (!isValidEmail(toEmail)) {
            return@withContext ForwardResult(false, errorMessage = "Recipient email '$toEmail' is invalid.")
        }

        var socket: Socket? = null
        try {
            val isImplicitSsl = port == 465
            socket = if (isImplicitSsl) {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket().also {
                    it.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                }
            } else {
                Socket().also { it.connect(InetSocketAddress(host, port), TIMEOUT_MS) }
            }
            socket.soTimeout = TIMEOUT_MS

            var reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            var writer: Writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            // Greeting is frequently multi-line (220-host ESMTP ...).
            readResponse(reader, 220)

            var capabilities = ehlo(reader, writer)

            if (!isImplicitSsl && useTls) {
                if (!capabilities.contains("STARTTLS", ignoreCase = true)) {
                    throw SmtpException("Server does not support STARTTLS on port $port. Try port 465.")
                }
                sendLine(writer, "STARTTLS")
                readResponse(reader, 220)

                val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, host, port, true) as SSLSocket
                sslSocket.soTimeout = TIMEOUT_MS
                sslSocket.startHandshake()
                socket = sslSocket

                reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), Charsets.UTF_8))
                writer = BufferedWriter(OutputStreamWriter(sslSocket.getOutputStream(), Charsets.UTF_8))
                capabilities = ehlo(reader, writer)
            }

            if (username.isNotBlank()) {
                authenticate(reader, writer, capabilities, username, password)
            }

            val fromAddress = if (username.contains("@")) username else "sms-forwarder@$host"

            sendLine(writer, "MAIL FROM:<$fromAddress>")
            readResponse(reader, 250)

            sendLine(writer, "RCPT TO:<${MimeBuilder.sanitizeHeader(toEmail)}>")
            readResponse(reader, 250)

            sendLine(writer, "DATA")
            readResponse(reader, 354)

            val message = MimeBuilder.buildMessage(
                fromName = fromName,
                fromEmail = fromAddress,
                toEmail = toEmail,
                subject = subject,
                body = body,
                timestamp = timestamp
            )
            writer.write(MimeBuilder.dotStuff(MimeBuilder.normaliseLineEndings(message)))
            writer.write("\r\n.\r\n")
            writer.flush()
            readResponse(reader, 250)

            runCatching {
                sendLine(writer, "QUIT")
                readResponse(reader, 221)
            }

            ForwardResult(
                success = true,
                responseDetails = "Delivered to $toEmail via $host:$port"
            )
        } catch (e: SmtpException) {
            ForwardResult(success = false, errorMessage = e.message)
        } catch (e: Exception) {
            ForwardResult(
                success = false,
                errorMessage = "SMTP error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun ehlo(reader: BufferedReader, writer: Writer): String {
        sendLine(writer, "EHLO localhost")
        return try {
            readResponse(reader, 250)
        } catch (e: SmtpException) {
            // Pre-ESMTP servers only understand HELO.
            sendLine(writer, "HELO localhost")
            readResponse(reader, 250)
        }
    }

    private fun authenticate(
        reader: BufferedReader,
        writer: Writer,
        capabilities: String,
        username: String,
        password: String
    ) {
        val authLine = capabilities.lines().firstOrNull { it.contains("AUTH", ignoreCase = true) }.orEmpty()
        val supportsPlain = authLine.contains("PLAIN", ignoreCase = true)
        val supportsLogin = authLine.contains("LOGIN", ignoreCase = true)

        when {
            supportsPlain -> {
                val credential = "\u0000$username\u0000$password"
                val encoded = Base64.encodeToString(credential.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                sendLine(writer, "AUTH PLAIN $encoded")
                readResponse(reader, 235)
            }
            supportsLogin || authLine.isEmpty() -> {
                sendLine(writer, "AUTH LOGIN")
                readResponse(reader, 334)
                sendLine(writer, Base64.encodeToString(username.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                readResponse(reader, 334)
                sendLine(writer, Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                readResponse(reader, 235)
            }
            else -> throw SmtpException(
                "This server only offers ${authLine.substringAfter("AUTH").trim()}. " +
                    "Use the Google one-tap method instead."
            )
        }
    }

    private fun sendLine(writer: Writer, line: String) {
        writer.write(line)
        writer.write("\r\n")
        writer.flush()
    }

    /**
     * Reads a full SMTP reply. A reply is a run of `NNN-text` lines terminated by `NNN text`,
     * so anything that reads a single line breaks on the many servers that answer multi-line.
     */
    private fun readResponse(reader: BufferedReader, expectedCode: Int): String {
        val builder = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: throw SmtpException("Server closed the connection unexpectedly.")
            builder.append(line).append('\n')
            if (line.length < 4 || line[3] != '-') {
                if (!line.startsWith(expectedCode.toString())) {
                    throw SmtpException(describeFailure(expectedCode, line))
                }
                break
            }
        }
        return builder.toString()
    }

    private fun describeFailure(expectedCode: Int, line: String): String = when {
        line.startsWith("535") ->
            "Login rejected. For Gmail you need a 16-character App Password, not your normal password."
        line.startsWith("534") ->
            "Google requires an App Password for this account. Enable 2-step verification, then create one."
        line.startsWith("550") || line.startsWith("553") ->
            "The server rejected the recipient address: $line"
        line.startsWith("421") || line.startsWith("451") ->
            "The mail server is temporarily unavailable: $line"
        else -> "SMTP expected $expectedCode but the server said: $line"
    }

    private fun isValidEmail(value: String): Boolean {
        val trimmed = value.trim()
        val at = trimmed.indexOf('@')
        return at > 0 && trimmed.indexOf('.', at) > at + 1 && !trimmed.contains(' ')
    }

    private class SmtpException(message: String) : Exception(message)

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
