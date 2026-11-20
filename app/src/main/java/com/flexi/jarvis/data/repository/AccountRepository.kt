
package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

private const val EMAIL_VERIFICATION_REDIRECT =
    "com.flexi.jarvis://auth/callback"

private const val MASTERCLASS_VERIFY_URL =
    "https://flexi-lessons-payment-api-virid.vercel.app/api/verify-masterclass"

data class MasterclassVerificationResult(
    val eligible: Boolean,
    val message: String,
    val expiresAt: String? = null,
    val displayName: String? = null,
    val code: String? = null
)

interface AccountRepository {

    suspend fun currentAccount(): Account?

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<Account>

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String
    ): Result<Account>

    suspend fun sendPasswordReset(
        email: String
    ): Result<Unit>

    suspend fun signInWithGoogle(
        idToken: String,
        nonce: String
    ): Result<Account>

    suspend fun verifyFlexiMasterclassAccount(
        whatsappNumber: String
    ): Result<MasterclassVerificationResult>

    suspend fun signOut()
}

class SupabaseAccountRepository(
    private val supabase: SupabaseClient
) : AccountRepository {

    override suspend fun currentAccount(): Account? {

        supabase.auth.sessionStatus.first {
            it != SessionStatus.Initializing
        }

        val user = supabase.auth.currentUserOrNull()
            ?: return null

        val metadata = user.userMetadata

        val fullName = metadata
            ?.get("full_name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: metadata
                ?.get("name")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Student"

        return Account(
            id = user.id,
            displayName = fullName,
            email = user.email,
            type = AccountType.FREE,
            isFlexiMasterclassLinked = false
        )
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<Account> = runCatching {

        require(email.isNotBlank()) {
            "Enter your email address."
        }

        require(password.isNotBlank()) {
            "Enter your password."
        }

        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }

        requireNotNull(currentAccount()) {
            "Sign-in succeeded but the account session could not be loaded."
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        name: String
    ): Result<Account> = runCatching {

        require(name.isNotBlank()) {
            "Enter your full name."
        }

        require(email.isNotBlank()) {
            "Enter your email address."
        }

        require(password.length >= 8) {
            "Your password must contain at least 8 characters."
        }

        val cleanName = name.trim()
        val cleanEmail = email.trim()

        supabase.auth.signUpWith(
            Email,
            redirectUrl = EMAIL_VERIFICATION_REDIRECT
        ) {

            this.email = cleanEmail
            this.password = password

            data = buildJsonObject {
                put("full_name", cleanName)
                put("name", cleanName)
            }
        }

        currentAccount() ?: Account(
            id = "pending-email-verification",
            displayName = cleanName,
            email = cleanEmail,
            type = AccountType.FREE
        )
    }

    override suspend fun sendPasswordReset(
        email: String
    ): Result<Unit> = runCatching {

        require(email.isNotBlank()) {
            "Enter your email address."
        }

        supabase.auth.resetPasswordForEmail(
            email.trim()
        )
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        nonce: String
    ): Result<Account> = runCatching {

        require(idToken.isNotBlank()) {
            "Google did not return an ID token."
        }

        require(nonce.isNotBlank()) {
            "Google sign-in nonce is missing."
        }

        supabase.auth.signInWith(IDToken) {

            this.idToken = idToken
            provider = Google
            this.nonce = nonce
        }

        requireNotNull(currentAccount()) {
            "Google authentication succeeded but the session could not be loaded."
        }
    }

    override suspend fun verifyFlexiMasterclassAccount(
        whatsappNumber: String
    ): Result<MasterclassVerificationResult> = runCatching {

        require(whatsappNumber.isNotBlank()) {
            "Enter the WhatsApp number used for your Flexi Lessons registration."
        }

        // Retrieve the active Supabase access token.
        // The token is never displayed or logged.

        val accessToken =
            supabase.auth.currentAccessTokenOrNull()
                ?: throw IllegalStateException(
                    "Your JARVIS sign-in session is missing. Please sign in again."
                )

        withContext(Dispatchers.IO) {

            val connection =
                (URL(MASTERCLASS_VERIFY_URL)
                    .openConnection() as HttpURLConnection).apply {

                    requestMethod = "POST"

                    connectTimeout = 15000
                    readTimeout = 15000

                    doOutput = true

                    setRequestProperty(
                        "Authorization",
                        "Bearer $accessToken"
                    )

                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )

                    setRequestProperty(
                        "Accept",
                        "application/json"
                    )
                }

            try {

                // Send only the WhatsApp number.
                // The server obtains the authenticated email
                // from the Supabase access token.

                val requestBody = buildJsonObject {
                    put(
                        "whatsappNumber",
                        whatsappNumber.trim()
                    )
                }.toString()

                connection.outputStream.use { output ->

                    output.write(
                        requestBody.toByteArray(Charsets.UTF_8)
                    )
                }

                val statusCode = connection.responseCode

                val responseStream =
                    if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val responseText = responseStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                if (responseText.isBlank()) {
                    throw IllegalStateException(
                        "The Masterclass server returned an empty response (HTTP $statusCode)."
                    )
                }

                val response =
                    Json.parseToJsonElement(responseText).jsonObject

                val eligible = response["eligible"]
                    ?.jsonPrimitive
                    ?.booleanOrNull ?: false

                val message = response["message"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: response["error"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                    ?: "Masterclass verification could not be completed."

                // HTTP 404 is a valid registration-not-found result.
                if (statusCode !in 200..299 && statusCode != 404) {
                    throw IllegalStateException(message)
                }

                val accountObject =
                    response["account"]?.jsonObject

                MasterclassVerificationResult(
                    eligible = eligible,
                    message = message,

                    expiresAt = response["expiresAt"]
                        ?.jsonPrimitive
                        ?.contentOrNull,

                    displayName = accountObject
                        ?.get("displayName")
                        ?.jsonPrimitive
                        ?.contentOrNull,

                    code = response["code"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                )

            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun signOut() {

        supabase.auth.signOut()

        supabase.auth.sessionStatus.first {
            it is SessionStatus.NotAuthenticated
        }
    }
}
