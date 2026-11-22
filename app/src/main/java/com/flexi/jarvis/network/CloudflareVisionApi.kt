package com.flexi.jarvis.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends camera frames to the authenticated Cloudflare vision route. */
class CloudflareVisionApi(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String?
) {
    suspend fun analyzeVideoFrame(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        require(jpeg.isNotEmpty() && jpeg.size <= 2 * 1024 * 1024) { "Video frame is empty or too large." }
        val bearer = accessTokenProvider()?.takeIf { it.isNotBlank() }
            ?: error("Please sign in before sharing video with JARVIS.")
        val connection = (URL(baseUrl.trimEnd('/') + "/vision").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = JSONObject()
                .put("imageBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                .put("prompt", "Describe the important visible details for a live tutoring conversation. Be concise and mention readable text when clear.")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = JSONObject(response.ifBlank { "{}" })
            if (status !in 200..299) error(json.optString("detail").ifBlank { "Video analysis failed (HTTP $status)." })
            json.optString("description").takeIf { it.isNotBlank() } ?: error("JARVIS could not analyse this camera frame.")
        } finally {
            connection.disconnect()
        }
    }
}
