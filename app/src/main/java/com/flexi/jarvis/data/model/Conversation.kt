package com.flexi.jarvis.data.model

data class Conversation(
    val id: String,
    val userId: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
    val lastMessageAt: String
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val userId: String,
    val role: String,
    val content: String,
    val citations: List<String> = emptyList(),
    val createdAt: String,
    val imageUrl: String? = null
)
