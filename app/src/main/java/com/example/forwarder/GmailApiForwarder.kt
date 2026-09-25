package com.example.forwarder

import android.accounts.Account
import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GmailApiForwarder {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GmailApiForwarder"
        const val GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.send"
        private const val GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
    }

    /**
     * Sends an email via Google's official Gmail REST API using OAuth 2.0 authorization.
     * No App Password or SMTP configuration required - just 1-click Google permission!
     */
    suspend fun sendEmail(
        context: Context,
        senderEmail: String,
        fromName: String,
        toEmail: String,
        senderNumber: String,
        bodyContent: String,
        timestamp: Long
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (senderEmail.isBlank()) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Google Account is not connected. Please connect your Google account in Settings."
            )
        }
        if (toEmail.isBlank() || !toEmail.contains("@")) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Recipient email '$toEmail' is invalid."
            )
        }

        // 1. Retrieve OAuth Access Token for the selected Google Account
        val accessTokenResult = getAccessToken(context, senderEmail)
        if (!accessTokenResult.isSuccess) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = accessTokenResult.errorMessage
            )
        }
        val token = accessTokenResult.token!!

        // 2. Build RFC 2822 Email Message
        val rfc2822 = buildRfc2822Message(
            fromName = fromName,
            fromEmail = senderEmail,
            toEmail = toEmail,
            senderNumber = senderNumber,
            bodyContent = bodyContent,
            timestamp = timestamp
        )

        // 3. Base64 URL-safe encode the message
        val encodedRawMessage = Base64.encodeToString(
            rfc2822.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )

        // 4. Send via Gmail REST API
        val jsonPayload = JSONObject().apply {
            put("raw", encodedRawMessage)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(GMAIL_SEND_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Email sent successfully via Gmail API to $toEmail")
                    ForwardResult(
                        success = true,
                        responseDetails = "Sent via Gmail API (OAuth 2.0) to $toEmail"
                    )
                } else {
                    Log.e(TAG, "Gmail API error: ${response.code} - $responseBodyStr")
                    // If token expired, clear it from cache so next attempt refreshes
                    if (response.code == 401) {
                        try {
                            GoogleAuthUtil.clearToken(context, token)
                        } catch (_: Exception) {}
                    }
                    val parsedError = try {
                        val obj = JSONObject(responseBodyStr)
                        obj.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                    } catch (_: Exception) {
                        "HTTP ${response.code}: $responseBodyStr"
                    }
                    ForwardResult(
                        success = false,
                        errorMessage = "Gmail API Error ($parsedError)"
                    )
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error sending email via Gmail API", e)
            ForwardResult(
                success = false,
                errorMessage = "Network error: ${e.localizedMessage ?: e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in GmailApiForwarder", e)
            ForwardResult(
                success = false,
                errorMessage = "Error: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private data class TokenResult(
        val isSuccess: Boolean,
        val token: String? = null,
        val errorMessage: String = ""
    )

    private fun getAccessToken(context: Context, email: String): TokenResult {
        return try {
            val account = Account(email, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, GMAIL_SCOPE)
            TokenResult(isSuccess = true, token = token)
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User approval required for Gmail API: ${e.message}")
            TokenResult(
                isSuccess = false,
                errorMessage = "Google permission required. Tap 'Connect with Google' to grant 1-click email permission."
            )
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "GoogleAuthException getting token", e)
            TokenResult(
                isSuccess = false,
                errorMessage = "Google Auth error: ${e.localizedMessage ?: e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting Google token", e)
            TokenResult(
                isSuccess = false,
                errorMessage = "Could not get Google Token for $email: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private fun buildRfc2822Message(
        fromName: String,
        fromEmail: String,
        toEmail: String,
        senderNumber: String,
        bodyContent: String,
        timestamp: Long
    ): String {
        val formattedDate = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US)
            .format(Date(timestamp))
        val displayDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timestamp))

        val subject = "[SMS Alert] New message from $senderNumber"

        return buildString {
            append("From: $fromName <$fromEmail>\r\n")
            append("To: <$toEmail>\r\n")
            append("Subject: $subject\r\n")
            append("Date: $formattedDate\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("\r\n")
            append("📩 Incoming Message Received\n")
            append("----------------------------------------\n")
            append("From: $senderNumber\n")
            append("Time: $displayDate\n\n")
            append("Message:\n")
            append(bodyContent)
            append("\n----------------------------------------\n")
            append("Forwarded automatically via Gmail 1-Click OAuth • SMS Forwarder\n")
            append("Developed by PenduCoder • https://penducoder.com\r\n")
        }
    }
}
