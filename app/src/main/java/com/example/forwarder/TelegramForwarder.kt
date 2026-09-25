package com.example.forwarder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Delivers a message through a Telegram bot. */
class TelegramForwarder {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String
    ): ForwardResult = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Telegram bot token is missing. Create a bot with @BotFather and paste its token."
            )
        }
        if (chatId.isBlank()) {
            return@withContext ForwardResult(
                success = false,
                errorMessage = "Telegram chat ID is missing. Message your bot, then use @userinfobot to find your ID."
            )
        }

        val form = FormBody.Builder()
            .add("chat_id", chatId.trim())
            // Telegram caps a message at 4096 characters.
            .add("text", text.take(4096))
            .add("disable_web_page_preview", "true")
            .build()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot${botToken.trim()}/sendMessage")
            .post(form)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ForwardResult(success = true, responseDetails = "Delivered to Telegram chat $chatId")
                } else {
                    val description = runCatching {
                        JSONObject(bodyText).optString("description")
                    }.getOrNull().orEmpty().ifBlank { "HTTP ${response.code}" }
                    ForwardResult(
                        success = false,
                        errorMessage = "Telegram: $description",
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
                errorMessage = "Telegram error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }
}
