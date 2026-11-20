
package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val EMAIL_VERIFICATION_REDIRECT =
    "com.flexi.jarvis://auth/callback"

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

    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun signInWithGoogle(
        idToken: String,
        nonce: String
    ): Result<Account>

    suspend fun linkFlexiMasterclassAccount(
        token: String
    ): Result<Account>

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

        supabase.auth.resetPasswordForEmail(email.trim())
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

    override suspend fun linkFlexiMasterclassAccount(
        token: String
    ): Result<Account> {
        return Result.failure(
            IllegalStateException(
                "Flexi Masterclass verification is waiting for the " +
                    "secure Flexi Lessons server verifier. No Masterclass " +
                    "benefits will be granted until verification is completed."
            )
        )
    }

    override suspend fun signOut() {
        supabase.auth.signOut()

        supabase.auth.sessionStatus.first {
            it is SessionStatus.NotAuthenticated
        }
    }
}
