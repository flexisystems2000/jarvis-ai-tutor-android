
package com.flexi.jarvis.core.errors

import io.github.jan.supabase.auth.exception.AuthRestException

enum class JarvisErrorCategory {
    AUTHENTICATION,
    DATABASE,
    REST_API,
    STORAGE,
    EDGE_FUNCTION,
    NETWORK,
    UNKNOWN
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

        // Supabase Kotlin Auth errors expose their server code directly.
        val authError = causes
            .filterIsInstance<AuthRestException>()
            .firstOrNull()

        if (authError != null) {
            return fromCode(
                code = authError.errorCode.value,
                category = JarvisErrorCategory.AUTHENTICATION
            )
        }

        val message = causes.joinToString(" ") {
            it.message.orEmpty()
        }

        val extractedCode = extractStructuredCode(message)

        if (extractedCode != null) {
            return fromCode(extractedCode)
        }

        val status = extractHttpStatus(message)

        if (status != null) {
            return fromHttpStatus(status)
        }

        val exceptionName = error::class.simpleName.orEmpty()

        if (
            exceptionName.contains("UnknownHost", ignoreCase = true) ||
            exceptionName.contains("ConnectException", ignoreCase = true) ||
            exceptionName.contains("SocketTimeout", ignoreCase = true)
        ) {
            return JarvisAppError(
                code = "NETWORK_UNAVAILABLE",
                message = "Unable to connect. Check your internet connection and try again.",
                category = JarvisErrorCategory.NETWORK,
                retryable = true
            )
        }

        return JarvisAppError(
            code = "UNKNOWN_ERROR",
            message = "Something went wrong. Please try again. If the problem continues, contact support.",
            category = JarvisErrorCategory.UNKNOWN,
            retryable = true
        )
    }

    fun fromCode(
        code: String,
        category: JarvisErrorCategory? = null
    ): JarvisAppError {
        val normalized = code.trim().uppercase()

        val resolvedCategory = category ?: categoryFor(normalized)

        val message = when (normalized) {

            // Supabase Auth
            "INVALID_CREDENTIALS" ->
                "Your login details are incorrect. Check your password or use Google sign-in."

            "EMAIL_EXISTS", "USER_ALREADY_EXISTS" ->
                "An account with this email already exists. Please sign in instead."

            "EMAIL_NOT_CONFIRMED" ->
                "Please verify your email address before signing in."

            "PHONE_NOT_CONFIRMED" ->
                "Please verify your phone number before continuing."

            "WEAK_PASSWORD" ->
                "Your password is too weak. Choose a stronger password."

            "SAME_PASSWORD" ->
                "Your new password must be different from your current password."

            "EMAIL_ADDRESS_INVALID" ->
                "Please enter a valid email address."

            "EMAIL_ADDRESS_NOT_AUTHORIZED" ->
                "Email delivery is not configured for this recipient. Please contact support."

            "EMAIL_PROVIDER_DISABLED" ->
                "Email and password registration is currently unavailable."

            "PHONE_PROVIDER_DISABLED" ->
                "Phone authentication is currently unavailable."

            "SIGNUP_DISABLED" ->
                "New account registration is temporarily disabled."

            "PROVIDER_DISABLED", "OAUTH_PROVIDER_NOT_SUPPORTED" ->
                "This sign-in method is currently unavailable."

            "OTP_EXPIRED", "FLOW_STATE_EXPIRED", "FLOW_STATE_NOT_FOUND" ->
                "This sign-in link or verification session has expired. Please try again."

            "REFRESH_TOKEN_NOT_FOUND",
            "REFRESH_TOKEN_ALREADY_USED",
            "SESSION_EXPIRED",
            "SESSION_NOT_FOUND" ->
                "Your session has expired. Please sign in again."

            "USER_NOT_FOUND" ->
                "We could not find an account matching those details."

            "USER_BANNED" ->
                "This account is currently restricted. Please contact support."

            "OVER_EMAIL_SEND_RATE_LIMIT", "OVER_SMS_SEND_RATE_LIMIT" ->
                "Too many verification requests. Please wait before trying again."

            "OVER_REQUEST_RATE_LIMIT" ->
                "You have made too many requests. Please wait a little and try again."

            "BAD_CODE_VERIFIER", "BAD_OAUTH_STATE", "BAD_OAUTH_CALLBACK" ->
                "The sign-in process could not be verified. Please restart sign-in."

            "IDENTITY_ALREADY_EXISTS" ->
                "This sign-in identity is already linked to another account."

            "IDENTITY_NOT_FOUND" ->
                "The requested linked identity could not be found."

            "INSUFFICIENT_AAL" ->
                "Additional security verification is required before continuing."

            "MFA_VERIFICATION_FAILED" ->
                "The security verification code is incorrect. Please try again."

            "CAPTCHA_FAILED" ->
                "Security verification failed. Please try again."

            "NO_AUTHORIZATION", "BAD_JWT" ->
                "Your authorization is invalid or has expired. Please sign in again."

            "REQUEST_TIMEOUT" ->
                "The request took too long. Please try again."

            "CONFLICT" ->
                "Another operation is updating this account. Please wait and try again."

            "EMAIL_PROVIDER_DISABLED", "PROVIDER_DISABLED" ->
                "This authentication method is not enabled. Please use another sign-in method."

            // PostgreSQL SQLSTATE
            "23502" ->
                "A required database field is missing."

            "23503" ->
                "This operation references a record that does not exist."

            "23505" ->
                "This record already exists. Please check your information."

            "23514" ->
                "Some submitted information does not meet the database requirements."

            "22P02" ->
                "Some information has an invalid format."

            "22001" ->
                "Some submitted text is longer than allowed."

            "22003" ->
                "A submitted number is outside the allowed range."

            "42501" ->
                "You do not have permission to perform this action."

            "42P01", "PGRST205" ->
                "A required database resource is unavailable. Please contact support."

            "42703", "PGRST204" ->
                "A required database field is unavailable. Please contact support."

            "42883", "PGRST202" ->
                "A required database operation is unavailable. Please contact support."

            "PGRST000", "PGRST001", "PGRST002", "PGRST003" ->
                "The database service is temporarily unavailable. Please try again."

            "PGRST100", "PGRST102", "PGRST103", "PGRST107" ->
                "The request contains invalid information. Please try again."

            "PGRST116" ->
                "The requested record could not be found or the result was ambiguous."

            "PGRST200", "PGRST201" ->
                "The requested database relationship is unavailable."

            "PGRST300", "PGRST301", "PGRST302", "PGRST303" ->
                "The authorization token could not be verified. Please sign in again."

            // Storage
            "NOSUCHBUCKET", "NOSUCHKEY" ->
                "The requested file or storage location could not be found."

            "ENTITYTOOLARGE" ->
                "This file is too large to upload."

            "INVALIDMIMETYPE" ->
                "This file type is not supported."

            "ACCESSDENIED", "UNAUTHORIZED" ->
                "You do not have permission to access this file."

            "RESOURCEALREADYEXISTS", "KEYALREADYEXISTS" ->
                "A file with this name already exists."

            "DATABASETIMEOUT", "LOCKTIMEOUT" ->
                "The file service is busy. Please try again."

            // Edge Functions
            "BOOT_ERROR", "LOAD_FUNCTION_ERROR", "EDGE_FUNCTION_ERROR" ->
                "A JARVIS service could not start correctly. Please try again later."

            "IDLE_TIMEOUT" ->
                "The service took too long to respond. Please try again."

            "WORKER_RESOURCE_LIMIT", "WORKER_LIMIT", "546" ->
                "The service is temporarily busy. Please try again shortly."

            "NOT_FOUND" ->
                "The requested service could not be found."

            else -> "Something went wrong. Please try again. If the problem continues, contact support."
        }

        val retryable = normalized in setOf(
            "REQUEST_TIMEOUT",
            "OVER_REQUEST_RATE_LIMIT",
            "OVER_EMAIL_SEND_RATE_LIMIT",
            "OVER_SMS_SEND_RATE_LIMIT",
            "PGRST000",
            "PGRST001",
            "PGRST002",
            "PGRST003",
            "DATABASETIMEOUT",
            "LOCKTIMEOUT",
            "IDLE_TIMEOUT",
            "WORKER_RESOURCE_LIMIT",
            "WORKER_LIMIT",
            "546",
            "NETWORK_UNAVAILABLE",
            "CONFLICT"
        )

        return JarvisAppError(
            code = normalized,
            message = message,
            category = resolvedCategory,
            retryable = retryable
        )
    }

    fun fromHttpStatus(status: Int): JarvisAppError {
        val message = when (status) {
            400 -> "The request contains invalid information."
            401 -> "Your session is invalid or expired. Please sign in again."
            403 -> "You do not have permission to perform this action."
            404 -> "The requested service or resource could not be found."
            408 -> "The request timed out. Please try again."
            413 -> "The submitted content is too large."
            422 -> "The request could not be processed. Check your information."
            429 -> "Too many requests. Please wait before trying again."
            in 500..599 -> "JARVIS is temporarily experiencing a server problem. Please try again later."
            else -> "The request failed. Please try again."
        }

        return JarvisAppError(
            code = "HTTP_$status",
            message = message,
            category = JarvisErrorCategory.UNKNOWN,
            retryable = status == 408 || status == 429 || status >= 500,
            httpStatus = status
        )
    }

    private fun categoryFor(code: String): JarvisErrorCategory {
        return when {
            code.startsWith("PGRST") ->
                JarvisErrorCategory.REST_API

            code.matches(Regex("[0-9A-Z]{5}")) ->
                JarvisErrorCategory.DATABASE

            code in STORAGE_CODES ->
                JarvisErrorCategory.STORAGE

            code in EDGE_CODES ->
                JarvisErrorCategory.EDGE_FUNCTION

            code.startsWith("HTTP_") ->
                JarvisErrorCategory.UNKNOWN

            else ->
                JarvisErrorCategory.AUTHENTICATION
        }
    }

    private fun extractStructuredCode(message: String): String? {
        val patterns = listOf(
            Regex("(?i)[\"'](?:error_code|code|sqlstate)[\"']\\s*:\\s*[\"']([A-Z0-9_]+)[\"']"),
            Regex("(?i)SQLSTATE\\s*[:= ]\\s*([0-9A-Z]{5})"),
            Regex("\\b(PGRST\\d{3})\\b")
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(message)?.groupValues?.getOrNull(1)
        }
    }

    private fun extractHttpStatus(message: String): Int? {
        val patterns = listOf(
            Regex("(?i)(?:HTTP|statusCode|status)\\s*[:= ]\\s*(\\d{3})"),
            Regex("(?i)response\\s+with\\s+status\\s+(\\d{3})")
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(message)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
    }

    private val STORAGE_CODES = setOf(
        "NOSUCHBUCKET",
        "NOSUCHKEY",
        "ENTITYTOOLARGE",
        "INVALIDMIMETYPE",
        "ACCESSDENIED",
        "UNAUTHORIZED",
        "RESOURCEALREADYEXISTS",
        "KEYALREADYEXISTS",
        "DATABASETIMEOUT",
        "LOCKTIMEOUT"
    )

    private val EDGE_CODES = setOf(
        "BOOT_ERROR",
        "LOAD_FUNCTION_ERROR",
        "EDGE_FUNCTION_ERROR",
        "IDLE_TIMEOUT",
        "WORKER_RESOURCE_LIMIT",
        "WORKER_LIMIT",
        "NOT_FOUND"
    )
}
