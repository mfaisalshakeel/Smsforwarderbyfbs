package com.example.forwarder

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class EmailForwarder {

    private val gmailApiForwarder = GmailApiForwarder()

    suspend fun sendViaGmailApi(
        context: android.content.Context,
        fromEmail: String,
        fromName: String,
        toEmail: String,
        senderNumber: String,
        smsBody: String,
        timestamp: Long
    ): ForwardResult {
        return gmailApiForwarder.sendEmail(
            context = context,
            senderEmail = fromEmail,
            fromName = fromName,
            toEmail = toEmail,
            senderNumber = senderNumber,
            bodyContent = smsBody,
            timestamp = timestamp
        )
    }

    suspend fun sendEmail(
        host: String,
        port: Int,
        username: String,
        password: String,
        useTls: Boolean,
        fromName: String,
        toEmail: String,
        senderNumber: String,
        smsBody: String,
        timestamp: Long
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext ForwardResult(false, errorMessage = "SMTP host is empty.")
        }
        if (toEmail.isBlank() || !toEmail.contains("@")) {
            return@withContext ForwardResult(false, errorMessage = "Recipient email is invalid.")
        }

        var socket: Socket? = null
        try {
            val isSslPort = port == 465
            socket = if (isSslPort) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val s = sslFactory.createSocket()
                s.connect(InetSocketAddress(host, port), 15000)
                s
            } else {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 15000)
                s
            }
            socket.soTimeout = 15000

            var reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            var writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)

            // Read greeting
            readResponse(reader, 220)

            // EHLO
            writer.print("EHLO localhost\r\n")
            writer.flush()
            val ehloResp = readMultilineResponse(reader, 250)

            // Upgrade to STARTTLS if on port 587 or useTls and not already SSL
            if (!isSslPort && useTls && ehloResp.contains("STARTTLS", ignoreCase = true)) {
                writer.print("STARTTLS\r\n")
                writer.flush()
                readResponse(reader, 220)

                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(socket, host, port, true) as SSLSocket
                sslSocket.soTimeout = 15000
                sslSocket.startHandshake()
                socket = sslSocket

                reader = BufferedReader(InputStreamReader(sslSocket.getInputStream(), "UTF-8"))
                writer = PrintWriter(OutputStreamWriter(sslSocket.getOutputStream(), "UTF-8"), true)

                // EHLO again after STARTTLS
                writer.print("EHLO localhost\r\n")
                writer.flush()
                readMultilineResponse(reader, 250)
            }

            // Authenticate if username provided
            if (username.isNotBlank()) {
                writer.print("AUTH LOGIN\r\n")
                writer.flush()
                readResponse(reader, 334)

                val userB64 = Base64.encodeToString(username.toByteArray(), Base64.NO_WRAP)
                writer.print("$userB64\r\n")
                writer.flush()
                readResponse(reader, 334)

                val passB64 = Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)
                writer.print("$passB64\r\n")
                writer.flush()
                readResponse(reader, 235)
            }

            // MAIL FROM
            val fromAddress = if (username.contains("@")) username else "sms-forwarder@localhost"
            writer.print("MAIL FROM:<$fromAddress>\r\n")
            writer.flush()
            readResponse(reader, 250)

            // RCPT TO
            writer.print("RCPT TO:<$toEmail>\r\n")
            writer.flush()
            readResponse(reader, 250)

            // DATA
            writer.print("DATA\r\n")
            writer.flush()
            readResponse(reader, 354)

            val formattedDate = java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                .format(java.util.Date(timestamp))
            val displayDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))

            val subject = "[SMS Alert] New message from $senderNumber"
            val messageContent = buildString {
                append("From: $fromName <$fromAddress>\r\n")
                append("To: <$toEmail>\r\n")
                append("Subject: $subject\r\n")
                append("Date: $formattedDate\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("\r\n")
                append("📩 Incoming SMS Received\n")
                append("----------------------------------------\n")
                append("Sender: $senderNumber\n")
                append("Time: $displayDate\n\n")
                append("Message:\n")
                append(smsBody)
                append("\n----------------------------------------\n")
                append("Forwarded automatically by SMS Forwarder.\n")
                append("Developed by PenduCoder • https://penducoder.com\r\n")
                append(".\r\n")
            }

            writer.print(messageContent)
            writer.flush()
            readResponse(reader, 250)

            // QUIT
            writer.print("QUIT\r\n")
            writer.flush()

            ForwardResult(
                success = true,
                responseDetails = "Email sent successfully to $toEmail via $host"
            )
        } catch (e: Exception) {
            ForwardResult(
                success = false,
                errorMessage = "SMTP error: ${e.localizedMessage ?: e.message}"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun readResponse(reader: BufferedReader, expectedCode: Int): String {
        val line = reader.readLine() ?: throw Exception("Server disconnected unexpectedly")
        if (!line.startsWith(expectedCode.toString())) {
            throw Exception("SMTP Expected $expectedCode but got: $line")
        }
        return line
    }

    private fun readMultilineResponse(reader: BufferedReader, expectedCode: Int): String {
        val sb = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            sb.append(line).append("\n")
            if (line.length >= 4 && line[3] == ' ') {
                if (!line.startsWith(expectedCode.toString())) {
                    throw Exception("SMTP Expected $expectedCode but got: $line")
                }
                break
            }
        }
        return sb.toString()
    }
}
