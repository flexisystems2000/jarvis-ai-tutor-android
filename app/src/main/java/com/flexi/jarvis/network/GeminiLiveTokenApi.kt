package com.flexi.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

data class GeminiLiveToken(
    val token: String,
    val model: String,
    val apiVersion: String,
    val tutorName: String = "JARVIS",
    val language: String = "English"
)

class GeminiLiveTokenApi(
    private val baseUrl: String,
    private val accessToken: suspend () -> String?
) {
    suspend fun analyzeVideoFrame(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        require(jpeg.isNotEmpty() && jpeg.size <= 2 * 1024 * 1024) { "Video frame is empty or too large." }
        val bearer = accessToken()?.takeIf { it.isNotBlank() }
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
        } finally { connection.disconnect() }
    }

    suspend fun create(tutorName: String = "JARVIS", language: String = "English"): GeminiLiveToken =
        withContext(Dispatchers.IO) {
            val bearer = accessToken()?.takeIf { it.isNotBlank() }
                ?: error("Please sign in before starting a JARVIS Live call.")
            val endpoint = baseUrl.trimEnd('/') + "/live-token"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $bearer")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                val body = JSONObject()
                    .put("tutorName", tutorName)
                    .put("language", language)
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = JSONObject(response.ifBlank { "{}" })
                if (status !in 200..299) {
                    error(json.optString("detail", "Could not start a JARVIS Live session (HTTP $status)."))
                }
                val token = json.optString("token")
                if (token.isBlank()) error("The Live service returned an empty session token.")
                GeminiLiveToken(
                    token = token,
                    model = json.optString("model", "gemini-3.8-live"),
                    apiVersion = json.optString("apiVersion", "v1alpha"),
                    tutorName = json.optString("tutorName", tutorName),
                    language = json.optString("language", language)
                )
            } finally {
                connection.disconnect()
            }
        }
}
