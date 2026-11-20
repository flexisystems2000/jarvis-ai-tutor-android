
package com.flexi.jarvis.data.repository

import android.util.Patterns
import com.flexi.jarvis.data.repository.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType

class PasswordRecoveryRepository(
    private val supabase: SupabaseClient
) {

    suspend fun sendRecoveryCode(email: String): Result<Unit> =
        runCatching {
            val cleanEmail = email.trim()

            require(cleanEmail.isNotBlank()) {
                "Enter your email address."
            }

            require(Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                "Enter a valid email address."
            }

            supabase.auth.resetPasswordForEmail(
                email = cleanEmail
            )
        }

    suspend fun verifyRecoveryCode(
        email: String,
        code: String
    ): Result<Unit> = runCatching {
        val cleanEmail = email.trim()
        val cleanCode = code.trim()

        require(cleanEmail.isNotBlank()) {
            "Your email address is missing."
        }

        require(cleanCode.isNotBlank()) {
            "Enter the recovery code sent to your email."
        }

        require(cleanCode.length == 6 && cleanCode.all { it.isDigit() }) {
            "Enter the complete 6-digit recovery code."
        }

        supabase.auth.verifyEmailOtp(
            type = OtpType.Email.RECOVERY,
            email = cleanEmail,
            token = cleanCode
        )

        requireNotNull(supabase.auth.currentAccessTokenOrNull()) {
            "The code was accepted, but a recovery session could not be established. Please request another code."
        }
    }

    suspend fun updatePassword(
        newPassword: String
    ): Result<Unit> = runCatching {
        require(newPassword.length >= 8) {
            "Your new password must contain at least 8 characters."
        }

        requireNotNull(supabase.auth.currentUserOrNull()) {
            "Your recovery session has expired. Please request another recovery code."
        }

        supabase.auth.updateUser {
            password = newPassword
        }

        supabase.auth.signOut()
    }
}
