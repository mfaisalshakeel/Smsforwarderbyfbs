package com.example.forwarder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebhookForwarder {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun sendWebhook(
        url: String,
        format: String,
        customHeaders: String,
        sender: String,
        body: String,
        timestamp: Long
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Webhook URL is empty."
            )
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Invalid Webhook URL. Must begin with http:// or https://"
            )
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val jsonPayload = when (format.uppercase()) {
            "DISCORD" -> {
                JSONObject().apply {
                    put("content", "📩 **SMS Received**\n**From:** `$sender`\n**Message:** $body\n**Time:** <t:${timestamp / 1000}:f>")
                }.toString()
            }
            "SLACK" -> {
                JSONObject().apply {
                    put("text", "📩 *SMS Received from:* `$sender`\n>${body.replace("\n", "\n>")}")
                }.toString()
            }
            else -> {
                // Standard JSON
                JSONObject().apply {
                    put("event", "sms_received")
                    put("sender", sender)
                    put("message", body)
                    put("timestamp", timestamp)
                    put("formatted_time", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp)))
                }.toString()
            }
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody(jsonMediaType))

        // Parse custom headers
        if (customHeaders.isNotBlank()) {
            val lines = customHeaders.lines()
            for (line in lines) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        requestBuilder.addHeader(key, value)
                    }
                }
            }
        }

        try {
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val code = response.code
                val responseSnippet = response.body?.string()?.take(500) ?: ""
                if (response.isSuccessful) {
                    ForwardResult(
                        success = true,
                        responseDetails = "HTTP $code: ${if (responseSnippet.isBlank()) "OK" else responseSnippet}"
                    )
                } else {
                    ForwardResult(
                        success = false,
                        errorMessage = "HTTP $code error: $responseSnippet"
                    )
                }
            }
        } catch (e: Exception) {
            ForwardResult(
                success = false,
                errorMessage = "Network error: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
