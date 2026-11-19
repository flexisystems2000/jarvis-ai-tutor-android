package com.flexi.jarvis.network

data class TutorRequest(
    val conversationId: String?,
    val message: String,
    val tutorName: String,
    val languageCode: String,
    val webSearch: Boolean
)

data class TutorReply(
    val text: String,
    val citations: List<String> = emptyList()
)

interface AiTutorApi {
    suspend fun ask(request: TutorRequest): Result<TutorReply>
}

/**
 * Production implementation should call your authenticated backend.
 * Do not place Gemini/OpenAI/API provider keys in the Android app.
 */
class UnconfiguredAiTutorApi : AiTutorApi {
    override suspend fun ask(request: TutorRequest) =
        Result.failure<TutorReply>(IllegalStateException("AI backend has not been connected yet."))
}
