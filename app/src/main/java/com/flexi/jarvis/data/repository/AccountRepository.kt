package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

interface AccountRepository {
    suspend fun currentAccount(): Account?
    suspend fun signInWithEmail(email: String, password: String): Result<Account>
    suspend fun signUpWithEmail(email: String, password: String, name: String): Result<Account>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Account>
    suspend fun linkFlexiMasterclassAccount(verificationToken: String): Result<Account>
    suspend fun signOut()
}

class FirebaseAccountRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AccountRepository {
    override suspend fun currentAccount(): Account? {
        val user = auth.currentUser ?: return null
        val token = user.getIdToken(false).await()
        val isMasterclass = token.claims["flexiMasterclass"] == true
        return Account(
            id = user.uid,
            displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Student",
            email = user.email,
            type = if (isMasterclass) AccountType.FLEXI_MASTERCLASS else AccountType.FREE,
            isFlexiMasterclassLinked = isMasterclass
        )
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Account> =
        runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            requireNotNull(currentAccount())
        }

    override suspend fun signUpWithEmail(email: String, password: String, name: String): Result<Account> =
        runCatching {
            require(password.length >= 8) { "Use a password with at least 8 characters." }
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim()).build()
            result.user?.updateProfile(profile)?.await()
            requireNotNull(currentAccount())
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Account> = runCatching {
        require(idToken.isNotBlank()) { "Google sign-in did not return an ID token." }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        requireNotNull(currentAccount())
    }

    override suspend fun linkFlexiMasterclassAccount(verificationToken: String): Result<Account> =
        Result.failure(
            IllegalStateException(
                "Flexi Masterclass verification needs the secure Flexi Lessons server verifier. " +
                    "The current Flexi Lessons login only matches Firestore email and WhatsApp values in the browser; " +
                    "JARVIS will not use that insecure client-side check or grant Masterclass access without server verification."
            )
        )

    override suspend fun signOut() {
        auth.signOut()
    }
}
