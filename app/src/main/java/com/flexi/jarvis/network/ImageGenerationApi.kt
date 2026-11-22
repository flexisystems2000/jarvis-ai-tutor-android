package com.flexi.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class GeneratedImage(
    val url: String,
    val usedToday: Int?,
    val dailyLimit: Int?
)

/** Calls the authenticated Cloudflare image Worker; provider credentials never enter the APK. */
class ImageGenerationApi(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String?
) {
    suspend fun generate(prompt: String): GeneratedImage = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank() && prompt.length <= 4000) {
            "Please provide a shorter image description."
        }
        val endpoint = baseUrl.trim().trimEnd('/')
        require(endpoint.startsWith("https://")) {
            "The image service URL is not configured."
        }
        val token = accessTokenProvider()
            ?: throw IOException("Please sign in again before generating an image.")
        val connection = (URL("$endpoint/v1/images/generations").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 110_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val body = JSONObject()
                .put("model", "novastack-1.0-imagen")
                .put("prompt", prompt)
                .put("n", 1)
                .put("size", "1024x1024")
            connection.outputStream.use {
                it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (status !in 200..299) {
                val error = json?.optJSONObject("error")
                val message = error?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: json?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Image generation failed (HTTP $status)."
                throw IOException(message.take(400))
            }
            val imageUrl = json?.optJSONArray("data")
                ?.optJSONObject(0)
                ?.optString("url")
                .orEmpty()
            if (!imageUrl.startsWith("https://")) {
                throw IOException("The image service returned an invalid image link.")
            }
            val usage = json?.optJSONObject("usage")
            GeneratedImage(
                url = imageUrl,
                usedToday = if (usage?.has("used") == true) usage.optInt("used") else null,
                dailyLimit = if (usage?.has("limit") == true) usage.optInt("limit") else null
            )
        } finally {
            connection.disconnect()
        }
    }
}