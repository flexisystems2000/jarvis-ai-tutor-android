package com.flexi.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
                    model = json.optString("model", "gemini-3.1-flash-live-preview"),
                    apiVersion = json.optString("apiVersion", "v1alpha"),
                    tutorName = json.optString("tutorName", tutorName),
                    language = json.optString("language", language)
                )
            } finally {
                connection.disconnect()
            }
        }
}
