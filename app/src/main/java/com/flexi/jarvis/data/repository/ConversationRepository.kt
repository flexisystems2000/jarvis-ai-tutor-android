package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.ChatMessage
import com.flexi.jarvis.data.model.Conversation
import com.flexi.jarvis.network.SupabaseBackendApi
import org.json.JSONArray
import org.json.JSONObject

class ConversationRepository(private val api: SupabaseBackendApi) {
    suspend fun list(userId: String): List<Conversation> =
        api.listConversations(userId).toList().map { it.toConversation() }

    suspend fun create(userId: String, title: String): Conversation =
        api.createConversation(userId, title).toConversation()

    suspend fun messages(userId: String, conversationId: String): List<ChatMessage> =
        api.getMessages(userId, conversationId).toList().map { it.toChatMessage() }

    suspend fun addMessage(
        userId: String,
        conversationId: String,
        role: String,
        content: String,
        citations: List<String> = emptyList()
    ): ChatMessage = api.addMessage(userId, conversationId, role, content, citations).toChatMessage()

    suspend fun rename(userId: String, conversationId: String, title: String) =
        api.updateConversationTitle(userId, conversationId, title)

    suspend fun delete(userId: String, conversationId: String) =
        api.deleteConversation(userId, conversationId)

    private fun JSONArray.toList(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.toConversation() = Conversation(
        id = getString("id"),
        userId = getString("user_id"),
        title = optString("title").ifBlank { "New conversation" },
        createdAt = optString("created_at"),
        updatedAt = optString("updated_at"),
        lastMessageAt = optString("last_message_at").ifBlank { optString("updated_at") }
    )

    private fun JSONObject.toChatMessage() = ChatMessage(
        id = getString("id"),
        conversationId = getString("conversation_id"),
        userId = getString("user_id"),
        role = getString("role"),
        content = optString("content"),
        citations = optJSONArray("citations").toStringList(),
        createdAt = optString("created_at")
    )

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }
}
