package com.flexi.jarvis.network

import com.flexi.jarvis.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/** Authenticated REST access to the user's own Stage 3 Supabase rows. */
class SupabaseBackendApi(private val supabase: SupabaseClient) {

    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    private suspend fun accessToken(): String =
        supabase.auth.currentAccessTokenOrNull()
            ?: throw IllegalStateException("Your session has expired. Please sign in again.")

    suspend fun listConversations(userId: String): JSONArray = request(
        method = "GET",
        path = "/rest/v1/conversations?select=id,user_id,title,created_at,updated_at,last_message_at" +
            "&user_id=eq.${enc(userId)}&archived_at=is.null&order=last_message_at.desc"
    ) as JSONArray

    suspend fun createConversation(userId: String, title: String): JSONObject {
        val rows = request(
            method = "POST",
            path = "/rest/v1/conversations?select=id,user_id,title,created_at,updated_at,last_message_at",
            body = JSONObject().put("user_id", userId).put("title", title.take(120)),
            prefer = "return=representation"
        ) as JSONArray
        return rows.optJSONObject(0) ?: error("Supabase did not return the new conversation.")
    }

    suspend fun getMessages(userId: String, conversationId: String): JSONArray = request(
        method = "GET",
        path = "/rest/v1/messages?select=id,conversation_id,user_id,role,content,citations,metadata,created_at" +
            "&user_id=eq.${enc(userId)}&conversation_id=eq.${enc(conversationId)}&order=created_at.asc"
    ) as JSONArray

    suspend fun addMessage(
        userId: String,
        conversationId: String,
        role: String,
        content: String,
        citations: List<String> = emptyList(),
        imageUrl: String? = null
    ): JSONObject {
        val metadata = JSONObject()
        if (!imageUrl.isNullOrBlank()) metadata.put("image_url", imageUrl)
        val row = JSONObject()
            .put("user_id", userId)
            .put("conversation_id", conversationId)
            .put("role", role)
            .put("content", content)
            .put("citations", JSONArray(citations))
            .put("metadata", metadata)
        val rows = request(
            method = "POST",
            path = "/rest/v1/messages?select=id,conversation_id,user_id,role,content,citations,created_at",
            body = row,
            prefer = "return=representation"
        ) as JSONArray
        val saved = rows.optJSONObject(0) ?: error("Supabase did not confirm that the message was saved.")
        val now = Instant.now().toString()
        request(
            method = "PATCH",
            path = "/rest/v1/conversations?id=eq.${enc(conversationId)}&user_id=eq.${enc(userId)}",
            body = JSONObject().put("last_message_at", now).put("updated_at", now),
            prefer = "return=minimal"
        )
        return saved
    }

    suspend fun updateConversationTitle(userId: String, conversationId: String, title: String) {
        request(
            method = "PATCH",
            path = "/rest/v1/conversations?id=eq.${enc(conversationId)}&user_id=eq.${enc(userId)}",
            body = JSONObject().put("title", title.take(120)).put("updated_at", Instant.now().toString()),
            prefer = "return=minimal"
        )
    }

    suspend fun deleteConversation(userId: String, conversationId: String) {
        request(
            method = "DELETE",
            path = "/rest/v1/conversations?id=eq.${enc(conversationId)}&user_id=eq.${enc(userId)}",
            prefer = "return=minimal"
        )
    }

    suspend fun getPreferences(userId: String): JSONObject? {
        val rows = request(
            method = "GET",
            path = "/rest/v1/user_preferences?select=user_id,theme,tutor_name,language_code,web_search_enabled" +
                "&user_id=eq.${enc(userId)}&limit=1"
        ) as JSONArray
        return rows.optJSONObject(0)
    }

    suspend fun savePreferences(userId: String, values: JSONObject) {
        val body = JSONObject()
            .put("user_id", userId)
            .put("theme", values.optString("theme", "SYSTEM"))
            .put("tutor_name", values.optString("tutor_name", "JARVIS"))
            .put("language_code", values.optString("language_code", "en"))
            .put("web_search_enabled", values.optBoolean("web_search_enabled", true))
        request(
            method = "POST",
            path = "/rest/v1/user_preferences?on_conflict=user_id",
            body = body,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        prefer: String? = null
    ): Any? {
        val token = accessToken()
        return withContext(Dispatchers.IO) {
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                if (prefer != null) setRequestProperty("Prefer", prefer)
            }
            try {
                if (body != null) {
                    connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching { JSONObject(response).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: response.take(300)
                    throw IllegalStateException("Supabase request failed (HTTP $status): $detail")
                }
                when {
                    response.isBlank() -> null
                    response.trimStart().startsWith("[") -> JSONArray(response)
                    response.trimStart().startsWith("{") -> JSONObject(response)
                    else -> response
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
