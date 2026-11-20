
package com.flexi.jarvis.core.errors

import io.github.jan.supabase.auth.exception.AuthRestException

enum class JarvisErrorCategory {
    AUTHENTICATION, DATABASE, REST_API, STORAGE,
    EDGE_FUNCTION, NETWORK, UNKNOWN
}

data class JarvisAppError(
    val code: String,
    val message: String,
    val category: JarvisErrorCategory,
    val retryable: Boolean = false,
    val httpStatus: Int? = null
)

object JarvisErrorHandler {

    fun handle(error: Throwable): JarvisAppError {
        val causes = generateSequence(error) { it.cause }.toList()

        val authError = causes.filterIsInstance<AuthRestException>()
            .firstOrNull()

        if (authError != null) {
            return fromCode(
                authError.errorCode?.value ?: "AUTH_ERROR",
                JarvisErrorCategory.AUTHENTICATION
            )
        }

        val details = causes.joinToString(" ") { it.message.orEmpty() }

        extractCode(details)?.let { return fromCode(it) }
        extractStatus(details)?.let { return fromHttpStatus(it) }

        val names = causes.joinToString(" ") {
            it::class.simpleName.orEmpty()
        }

        if (listOf(
                "UnknownHost",
                "ConnectException",
                "SocketTimeout",
                "IOException"
            ).any { names.contains(it, ignoreCase = true) }
        ) {
            return JarvisAppError(
                "NETWORK_UNAVAILABLE",
                "Unable to connect. Check your internet connection and try again.",
                JarvisErrorCategory.NETWORK,
                true
            )
        }

        return JarvisAppError(
            "UNKNOWN_ERROR",
            "Something went wrong. Please try again. If the problem continues, contact support.",
            JarvisErrorCategory.UNKNOWN,
            true
        )
    }

    fun fromCode(
        code: String,
        category: JarvisErrorCategory? = null
    ): JarvisAppError {
        val normalized = code.trim().uppercase().ifBlank {
            "UNKNOWN_ERROR"
        }

        val message = when (normalized) {
            "INVALID_CREDENTIALS" ->
                "Your login details are incorrect."

            "EMAIL_EXISTS", "USER_ALREADY_EXISTS" ->
                "An account with this email already exists. Please sign in."

            "EMAIL_NOT_CONFIRMED" ->
                "Please verify your email address before signing in."

            "WEAK_PASSWORD" ->
                "Your password is too weak. Choose a stronger password."

            "EMAIL_ADDRESS_INVALID" ->
                "Please enter a valid email address."

            "EMAIL_PROVIDER_DISABLED", "PROVIDER_DISABLED",
            "OAUTH_PROVIDER_NOT_SUPPORTED" ->
                "This sign-in method is not enabled."

            "SIGNUP_DISABLED" ->
                "New account registration is temporarily disabled."

            "OTP_EXPIRED", "FLOW_STATE_EXPIRED" ->
                "Your verification link or session has expired."

            "SESSION_EXPIRED", "SESSION_NOT_FOUND",
            "REFRESH_TOKEN_NOT_FOUND" ->
                "Your session has expired. Please sign in again."

            "USER_NOT_FOUND" ->
                "We could not find an account matching those details."

            "OVER_EMAIL_SEND_RATE_LIMIT",
            "OVER_REQUEST_RATE_LIMIT" ->
                "Too many requests. Please wait and try again."

            "23502" -> "A required database field is missing."
            "23503" -> "A referenced database record does not exist."
            "23505" -> "This record already exists."
            "23514" -> "Some information does not meet database requirements."
            "42501" -> "You do not have permission to perform this action."
            "PGRST116" -> "The requested record could not be found."
            "PGRST000", "PGRST001", "PGRST002", "PGRST003" ->
                "The database service is temporarily unavailable."

            "NOSUCHBUCKET", "NOSUCHKEY" ->
                "The requested file or storage location was not found."

            "ENTITYTOOLARGE" ->
                "This file is too large to upload."

            "INVALIDMIMETYPE" ->
                "This file type is not supported."

            "BOOT_ERROR", "LOAD_FUNCTION_ERROR",
            "EDGE_FUNCTION_ERROR" ->
                "A JARVIS service could not start correctly."

            "REQUEST_TIMEOUT" ->
                "The request took too long. Please try again."

            "NETWORK_UNAVAILABLE" ->
                "Unable to connect. Check your internet connection."

            else ->
                "Something went wrong. Please try again. If the problem continues, contact support."
        }

        val resolvedCategory = category ?: when {
            normalized.startsWith("PGRST") ->
                JarvisErrorCategory.REST_API

            normalized.matches(Regex("[0-9A-Z]{5}")) ->
                JarvisErrorCategory.DATABASE

            normalized.startsWith("HTTP_") ->
                JarvisErrorCategory.UNKNOWN

            normalized == "NETWORK_UNAVAILABLE" ->
                JarvisErrorCategory.NETWORK

            else -> JarvisErrorCategory.AUTHENTICATION
        }

        val retryable = normalized in setOf(
            "REQUEST_TIMEOUT",
            "OVER_REQUEST_RATE_LIMIT",
            "OVER_EMAIL_SEND_RATE_LIMIT",
            "PGRST000", "PGRST001", "PGRST002", "PGRST003",
            "NETWORK_UNAVAILABLE"
        )

        return JarvisAppError(
            normalized,
            message,
            resolvedCategory,
            retryable
        )
    }

    fun fromHttpStatus(status: Int): JarvisAppError {
        val message = when (status) {
            400 -> "The request contains invalid information."
            401 -> "Your session is invalid or expired."
            403 -> "You do not have permission to perform this action."
            404 -> "The requested resource could not be found."
            408 -> "The request timed out."
            413 -> "The submitted content is too large."
            429 -> "Too many requests. Please wait."
            in 500..599 ->
                "JARVIS is experiencing a server problem. Please try again later."
            else -> "The request failed. Please try again."
        }

        return JarvisAppError(
            "HTTP_$status",
            message,
            JarvisErrorCategory.UNKNOWN,
            status == 408 || status == 429 || status in 500..599,
            status
        )
    }

    private fun extractCode(message: String): String? {
        val patterns = listOf(
            Regex("(?i)[\"'](?:error_code|code|sqlstate)[\"']\\s*:\\s*[\"']([A-Z0-9_]+)[\"']"),
            Regex("(?i)SQLSTATE\\s*[:= ]\\s*([0-9A-Z]{5})"),
            Regex("\\b(PGRST\\d{3})\\b")
        )

        return patterns.firstNotNullOfOrNull {
            it.find(message)?.groupValues?.getOrNull(1)
        }
    }

    private fun extractStatus(message: String): Int? {
        val patterns = listOf(
            Regex("(?i)(?:HTTP|statusCode|status)\\s*[:= ]\\s*(\\d{3})"),
            Regex("(?i)response\\s+with\\s+status\\s+(\\d{3})")
        )

        return patterns.firstNotNullOfOrNull {
            it.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }
}
