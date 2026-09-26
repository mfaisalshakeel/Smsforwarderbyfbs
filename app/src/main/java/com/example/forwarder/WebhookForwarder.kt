package com.example.forwarder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Posts a message to an HTTP endpoint, optionally shaped for Discord or Slack. */
class WebhookForwarder {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendWebhook(
        url: String,
        format: String,
        customHeaders: String,
        ctx: MessageContext
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext ForwardResult(success = false, errorMessage = "Webhook URL is empty.")
        }
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Webhook URL must start with https:// or http://"
            )
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val summary = MessageTemplate.compactBody(ctx)

        val payload = when (format.uppercase()) {
            "DISCORD" -> JSONObject()
                .put("content", summary.take(1900))
                .toString()
            "SLACK" -> JSONObject()
                .put("text", summary.take(3000))
                .toString()
            else -> JSONObject().apply {
                put("event", if (ctx.isNotification) "notification_received" else "sms_received")
                put("sender", ctx.sender)
                put("message", ctx.body)
                put("timestamp", ctx.timestamp)
                if (ctx.isNotification) {
                    put("app", ctx.appName)
                    put("package", ctx.packageName)
                    put("title", ctx.title)
                } else if (ctx.simSlot > 0) {
                    put("sim_slot", ctx.simSlot)
                    put("sim_name", ctx.simName)
                }
                put("rule", ctx.ruleName)
            }.toString()
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMediaType))

        customHeaders.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                // A header value containing CR/LF would split the request.
                if (key.isNotEmpty() && value.isNotEmpty() && !key.contains(' ')) {
                    runCatching { requestBuilder.addHeader(key, value) }
                }
            }
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val snippet = response.body?.string()?.take(300).orEmpty()
                if (response.isSuccessful) {
                    ForwardResult(
                        success = true,
                        responseDetails = "HTTP ${response.code}${if (snippet.isBlank()) "" else ": $snippet"}"
                    )
                } else {
                    ForwardResult(
                        success = false,
                        errorMessage = "HTTP ${response.code}: $snippet",
                        isRetryable = response.code == 429 || response.code >= 500
                    )
                }
            }
        } catch (e: IOException) {
            ForwardResult(
                success = false,
                errorMessage = "No internet connection (${e.localizedMessage ?: "network error"})",
                isRetryable = true
            )
        } catch (e: Exception) {
            ForwardResult(
                success = false,
                errorMessage = "Webhook error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }
}
