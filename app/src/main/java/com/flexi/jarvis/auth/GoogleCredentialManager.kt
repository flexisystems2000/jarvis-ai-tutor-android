
package com.flexi.jarvis.auth

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.flexi.jarvis.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

data class GoogleIdTokenResult(
    val idToken: String,
    val nonce: String
)

class GoogleCredentialManager(private val activity: Activity) {

    suspend fun getGoogleIdToken(): GoogleIdTokenResult {
        val nonce = createNonce()

        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val response = CredentialManager.create(activity)
            .getCredential(activity, request)

        val credential = response.credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Google did not return a supported sign-in credential.")
        }

        val googleCredential =
            GoogleIdTokenCredential.createFrom(credential.data)

        return GoogleIdTokenResult(
            idToken = googleCredential.idToken,
            nonce = nonce
        )
    }

    private fun createNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)

        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
