package com.flexi.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class TutorTurn(
    val role: String,
    val content: String
)

data class TutorRequest(
    val conversationId: String?,
    val message: String,
    val tutorName: String,
    val languageCode: String,
    val webSearch: Boolean,
    val history: List<TutorTurn> = emptyList()
)

data class TutorReply(
    val text: String,
    val citations: List<String> = emptyList()
)

interface AiTutorApi {
    suspend fun ask(request: TutorRequest): Result<TutorReply>
}

/**
 * Calls the authenticated Cloudflare Worker. Permanent Gemini keys remain
 * server-side; the Android client sends only the signed-in Supabase access token.
 */
class CloudflareAiTutorApi(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String?
) : AiTutorApi {

    override suspend fun ask(request: TutorRequest): Result<TutorReply> = runCatching {
        val endpoint = baseUrl.trim().trimEnd('/')
        require(endpoint.startsWith("https://")) {
            "The AI service URL is not configured."
        }

        val token = accessTokenProvider()
            ?: throw IOException("Please sign in again before using JARVIS AI.")

        val turns = request.history
            .filter { it.content.isNotBlank() }
            .map {
                JSONObject()
                    .put(
                        "role",
                        if (it.role.equals("assistant", true) ||
                            it.role.equals("model", true)
                        ) "model" else "user"
                    )
                    .put("content", it.content)
            }
            .toMutableList()

        if (turns.isEmpty() || !turns.last().optString("content").equals(request.message)) {
            turns += JSONObject().put("role", "user").put("content", request.message)
        }

        val body = JSONObject()
            .put("messages", JSONArray(turns))
            .put("webSearch", request.webSearch)
            .put("tutorName", request.tutorName)
            .put("languageCode", request.languageCode)
            .put("conversationId", request.conversationId)

        withContext(Dispatchers.IO) {
            val connection = (URL("$endpoint/chat").openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 20_000
                connection.readTimeout = 120_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    val detail = runCatching {
                        val json = JSONObject(raw)
                        json.optString("detail").ifBlank {
                            json.optString("error").ifBlank { json.optString("message") }
                        }
                    }.getOrNull().orEmpty()
                    throw IOException(
                        detail.ifBlank { "JARVIS AI request failed (HTTP $status)." }.take(500)
                    )
                }

                val json = JSONObject(raw)
                // The Cloudflare /chat endpoint returns its generated answer in "reply".
                // Keep the other formats for backward compatibility with older workers.
                val answer = json.optString("reply").ifBlank {
                    json.optString("text").ifBlank {
                        json.optString("response").ifBlank {
                            json.optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.let { parts ->
                                    (0 until parts.length()).joinToString("") { index ->
                                        parts.optJSONObject(index)?.optString("text").orEmpty()
                                    }
                                }
                                .orEmpty()
                        }
                    }
                }.trim()

                if (answer.isBlank()) {
                    throw IOException("JARVIS returned an empty response. Please try again.")
                }

                val citationsJson = json.optJSONArray("citations")
                val citations = if (citationsJson == null) emptyList() else {
                    (0 until citationsJson.length()).mapNotNull {
                        citationsJson.optString(it).takeIf(String::isNotBlank)
                    }
                }

                TutorReply(answer, citations)
            } finally {
                connection.disconnect()
            }
        }
    }
}
