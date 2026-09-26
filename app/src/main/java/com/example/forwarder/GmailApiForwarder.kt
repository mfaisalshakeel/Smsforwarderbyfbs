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
import java.util.concurrent.TimeUnit

/** Sends mail through the Gmail REST API using the one-tap Google account. */
class GmailApiForwarder {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GmailApiForwarder"
        const val GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.send"
        private const val GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
    }

    suspend fun sendEmail(
        context: Context,
        senderEmail: String,
        fromName: String,
        toEmail: String,
        subject: String,
        body: String,
        timestamp: Long
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (senderEmail.isBlank()) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "No Google account connected. Connect one in Setup."
            )
        }
        if (toEmail.isBlank() || !toEmail.contains("@")) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Recipient email '$toEmail' is invalid."
            )
        }

        val tokenResult = getAccessToken(context, senderEmail)
        val token = tokenResult.token
            ?: return@withContext ForwardResult(
                success = false,
                errorMessage = tokenResult.errorMessage,
                isRetryable = tokenResult.isRetryable
            )

        val rfc5322 = MimeBuilder.buildMessage(
            fromName = fromName,
            fromEmail = senderEmail,
            toEmail = toEmail,
            subject = subject,
            body = body,
            timestamp = timestamp
        )
        val encodedRawMessage = Base64.encodeToString(
            rfc5322.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )

        val payload = JSONObject().put("raw", encodedRawMessage).toString()
        val request = Request.Builder()
            .url(GMAIL_SEND_URL)
            .addHeader("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext ForwardResult(
                        success = true,
                        responseDetails = "Delivered to $toEmail via Gmail API"
                    )
                }

                if (response.code == 401) {
                    // The cached token has expired; drop it so the next attempt fetches a fresh one.
                    runCatching { GoogleAuthUtil.clearToken(context, token) }
                }

                val message = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "HTTP ${response.code}" }

                ForwardResult(
                    success = false,
                    errorMessage = "Gmail API: $message",
                    // 401 clears the token, 403/429 are quota, 5xx are transient - all worth retrying.
                    isRetryable = response.code == 401 || response.code == 429 || response.code >= 500
                )
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error sending via Gmail API", e)
            ForwardResult(
                success = false,
                errorMessage = "No internet connection (${e.localizedMessage ?: "network error"})",
                isRetryable = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error sending via Gmail API", e)
            ForwardResult(success = false, errorMessage = "Error: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Checks the account can actually obtain a `gmail.send` token, without sending a message.
     *
     * Run right after connecting an account so a missing OAuth client is reported there and
     * then, instead of silently failing later on the first real message.
     */
    suspend fun verifyAccess(context: Context, senderEmail: String): ForwardResult =
        withContext(Dispatchers.IO) {
            if (senderEmail.isBlank()) {
                return@withContext ForwardResult(
                    success = false,
                    errorMessage = "No Google account selected."
                )
            }
            val result = getAccessToken(context, senderEmail)
            if (result.token != null) {
                ForwardResult(
                    success = true,
                    responseDetails = "Google authorised $senderEmail to send mail."
                )
            } else {
                ForwardResult(
                    success = false,
                    errorMessage = result.errorMessage,
                    isRetryable = result.isRetryable
                )
            }
        }

    private data class TokenResult(
        val token: String? = null,
        val errorMessage: String = "",
        val isRetryable: Boolean = false
    )

    private fun getAccessToken(context: Context, email: String): TokenResult = try {
        TokenResult(token = GoogleAuthUtil.getToken(context, Account(email, "com.google"), GMAIL_SCOPE))
    } catch (e: UserRecoverableAuthException) {
        // Park the consent screen so the UI can show it; without this the account can never
        // be repaired from inside the app.
        GoogleAuthRecovery.publish(e.intent)
        TokenResult(
            errorMessage = "Google needs you to approve sending mail. Open the app and tap " +
                "\"Grant Gmail permission\".",
            isRetryable = false
        )
    } catch (e: GoogleAuthException) {
        Log.e(TAG, "Google auth failed", e)
        TokenResult(errorMessage = explainAuthFailure(e.message.orEmpty()))
    } catch (e: IOException) {
        TokenResult(
            errorMessage = "Could not reach Google to refresh the sign-in token.",
            isRetryable = true
        )
    } catch (e: Exception) {
        Log.e(TAG, "Unable to obtain Google token", e)
        TokenResult(errorMessage = explainAuthFailure(e.message.orEmpty()))
    }

    /**
     * Turns Google's opaque auth codes into something actionable.
     *
     * Sending through the Gmail API needs an OAuth client registered in Google Cloud for this
     * exact package name and signing certificate. Without one, Play Services refuses the token
     * and the only clue is a bare code like UNREGISTERED_ON_API_CONSOLE.
     */
    private fun explainAuthFailure(raw: String): String = when {
        raw.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) ||
            raw.contains("INVALID_AUDIENCE", ignoreCase = true) ||
            raw.contains("INVALID_CLIENT", ignoreCase = true) ->
            "This app is not registered with Google for sending mail. An OAuth client for its " +
                "package name and signing certificate has to be created in Google Cloud first " +
                "(see GOOGLE_SETUP.md). Use the App password method instead for now."

        raw.contains("INVALID_SCOPE", ignoreCase = true) ->
            "The Gmail API is not enabled on the Google Cloud project behind this app, or " +
                "gmail.send is not listed on its consent screen."

        raw.contains("ServiceDisabled", ignoreCase = true) ||
            raw.contains("AccountNotPresent", ignoreCase = true) ->
            "Google has disabled API access for this account. Try another account, or use the " +
                "App password method."

        raw.contains("NetworkError", ignoreCase = true) ->
            "Could not reach Google to sign in. Check the connection and try again."

        raw.isBlank() -> "Google refused the sign-in without giving a reason. If this app has " +
            "not been registered in Google Cloud yet, use the App password method."

        else -> "Google refused the sign-in ($raw). If it mentions the app being unregistered, " +
            "see GOOGLE_SETUP.md, or use the App password method instead."
    }
}
