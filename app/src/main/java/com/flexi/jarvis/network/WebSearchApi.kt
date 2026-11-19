package com.flexi.jarvis.network

data class WebSearchResult(val title: String, val snippet: String, val url: String)

interface WebSearchApi {
    suspend fun search(query: String, languageCode: String): Result<List<WebSearchResult>>
}

/** Search requests must be proxied through a backend to protect provider credentials. */
class UnconfiguredWebSearchApi : WebSearchApi {
    override suspend fun search(query: String, languageCode: String) =
        Result.failure<List<WebSearchResult>>(IllegalStateException("Web search backend has not been connected yet."))
}
