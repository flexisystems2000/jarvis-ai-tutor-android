package com.flexi.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin authenticated configuration for the Cloudflare Live gateway. */
data class LiveSessionConfig(
    val endpoint: String,
    val accessToken: String,
    val tutorName: String,
    val language: String
)

class LiveSessionApi(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String?
) {
    suspend fun create(tutorName: String = "JARVIS", language: String = "English"): LiveSessionConfig =
        withContext(Dispatchers.IO) {
            require(baseUrl.isNotBlank()) { "The Cloudflare Live service URL is not configured." }
            val bearer = accessTokenProvider()?.takeIf { it.isNotBlank() }
                ?: error("Please sign in before starting a JARVIS Live call.")
            val websocketBase = when {
                baseUrl.startsWith("https://", ignoreCase = true) -> "wss://" + baseUrl.substring(8)
                baseUrl.startsWith("http://", ignoreCase = true) -> "ws://" + baseUrl.substring(7)
                baseUrl.startsWith("wss://", ignoreCase = true) || baseUrl.startsWith("ws://", ignoreCase = true) -> baseUrl
                else -> error("The Cloudflare Live service URL must use HTTPS.")
            }
            LiveSessionConfig(
                endpoint = websocketBase.trimEnd('/') + "/live",
                accessToken = bearer,
                tutorName = tutorName,
                language = language
            )
        }
}
