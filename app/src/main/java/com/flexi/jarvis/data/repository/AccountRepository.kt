package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType

interface AccountRepository {
    suspend fun currentAccount(): Account?
    suspend fun signInWithEmail(email: String, password: String): Result<Account>
    suspend fun signUpWithEmail(email: String, password: String, name: String): Result<Account>
    suspend fun signInWithGoogle(idToken: String): Result<Account>
    suspend fun linkFlexiMasterclassAccount(masterclassToken: String): Result<Account>
    suspend fun signOut()
}

/** Local preview implementation; replace with authenticated Supabase/backend calls. */
class PreviewAccountRepository : AccountRepository {
    private var account: Account? = Account(
        id = "preview-user",
        displayName = "Flexi Student",
        type = AccountType.FREE
    )
    override suspend fun currentAccount() = account
    override suspend fun signInWithEmail(email: String, password: String) =
        Result.failure<Account>(NotImplementedError("Connect the secure auth backend."))
    override suspend fun signUpWithEmail(email: String, password: String, name: String) =
        Result.failure<Account>(NotImplementedError("Connect the secure auth backend."))
    override suspend fun signInWithGoogle(idToken: String) =
        Result.failure<Account>(NotImplementedError("Connect Google OAuth through the backend."))
    override suspend fun linkFlexiMasterclassAccount(masterclassToken: String) =
        Result.failure<Account>(NotImplementedError("Connect the existing Flexi Masterclass identity provider."))
    override suspend fun signOut() { account = null }
}
