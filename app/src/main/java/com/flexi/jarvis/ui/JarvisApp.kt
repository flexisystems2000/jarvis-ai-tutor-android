
package com.flexi.jarvis.ui

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.flexi.jarvis.JarvisApplication
import com.flexi.jarvis.auth.GoogleCredentialManager
import com.flexi.jarvis.core.errors.JarvisErrorHandler
import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.repository.DataStorePreferencesRepository
import com.flexi.jarvis.data.repository.SupabaseAccountRepository
import com.flexi.jarvis.ui.screens.AccountScreen
import com.flexi.jarvis.ui.screens.CbtScreen
import com.flexi.jarvis.ui.screens.HistoryScreen
import com.flexi.jarvis.ui.screens.PlansScreen
import com.flexi.jarvis.ui.screens.SettingsScreen
import com.flexi.jarvis.ui.screens.TutorScreen
import com.flexi.jarvis.ui.theme.JarvisTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

private const val ERROR_LOG_TAG = "JARVIS_ERROR"

private enum class AppTab(val label: String) {
    TUTOR("Tutor"),
    CBT("CBT"),
    HISTORY("History"),
    PLANS("Plans"),
    SETTINGS("Settings"),
    ACCOUNT("Account")
}

@Composable
fun JarvisApp() {
    val context = LocalContext.current

    val activity = remember(context) {
        context.findActivity()
    }

    val scope = rememberCoroutineScope()

    val preferencesRepository = remember {
        DataStorePreferencesRepository(context)
    }

    val preferences by preferencesRepository.preferences.collectAsState(
        initial = AppPreferences()
    )

    val accountRepository = remember {
        SupabaseAccountRepository(JarvisApplication.supabase)
    }

    var account by remember {
        mutableStateOf<Account?>(null)
    }

    var accountLoading by remember {
        mutableStateOf(true)
    }

    var accountBusy by remember {
        mutableStateOf(false)
    }

    var accountMessage by remember {
        mutableStateOf<String?>(null)
    }

    var selectedTab by remember {
        mutableStateOf(AppTab.TUTOR)
    }

    var usedToday by remember {
        mutableIntStateOf(0)
    }

    fun reportError(error: Throwable) {
        val appError = JarvisErrorHandler.handle(error)

        accountMessage = appError.message

        Log.w(
            ERROR_LOG_TAG,
            "code=${appError.code}, " +
                "category=${appError.category.name}, " +
                "retryable=${appError.retryable}, " +
                "httpStatus=${appError.httpStatus ?: "unknown"}, " +
                "exception=${error::class.simpleName ?: "Unknown"}"
        )
    }

    /**
     * Reloads the authenticated account and its current
     * server-managed Masterclass entitlement.
     */
    suspend fun refreshAccount() {
        account = accountRepository.currentAccount()
    }

    LaunchedEffect(Unit) {
        JarvisApplication.supabase.auth.sessionStatus.collect { status ->
            when (status) {
                SessionStatus.Initializing -> {
                    accountLoading = true
                }

                is SessionStatus.Authenticated -> {
                    accountLoading = true

                    runCatching {
                        refreshAccount()
                    }.onSuccess {
                        if (account != null) {
                            selectedTab = AppTab.ACCOUNT
                            accountMessage = "Your account is ready."
                        }
                    }.onFailure { error ->
                        reportError(error)
                    }

                    accountLoading = false
                }

                is SessionStatus.NotAuthenticated -> {
                    account = null
                    accountLoading = false
                }

                else -> {
                    accountLoading = false
                }
            }
        }
    }

    fun runAccountAction(
        action: suspend () -> Result<Account>
    ) {
        scope.launch {
            accountBusy = true
            accountMessage = null

            action().onSuccess { result ->
                account = result

                accountMessage =
                    if (result.id == "pending-email-verification") {
                        "Registration request received. Check your email for verification before signing in."
                    } else {
                        "Account updated successfully."
                    }

                if (result.id != "pending-email-verification") {
                    selectedTab = AppTab.ACCOUNT
                }
            }.onFailure { error ->
                reportError(error)
            }

            accountBusy = false
        }
    }

    JarvisTheme(theme = preferences.theme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                            },
                            icon = {},
                            label = {
                                Text(tab.label)
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->

            Box(
                modifier = Modifier.padding(paddingValues)
            ) {
                when (selectedTab) {

                    AppTab.TUTOR -> {
                        TutorScreen(
                            tutorName = preferences.tutorName,
                            languageCode = preferences.languageCode,
                            webSearch = preferences.webSearchEnabled,
                            onUsage = {
                                usedToday++
                            }
                        )
                    }

                    AppTab.CBT -> CbtScreen()

                    AppTab.HISTORY -> HistoryScreen()

                    AppTab.PLANS -> {
                        PlansScreen(
                            accountType = account?.type
                                ?: AccountType.FREE,
                            usedToday = usedToday,
                            masterclassExpiresAt =
                                account?.masterclassExpiresAt,
                            onSelectPlan = {
                                accountMessage =
                                    "Secure plan payments will be enabled after backend integration."

                                selectedTab = AppTab.ACCOUNT
                            }
                        )
                    }

                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            preferences = preferences,
                            onSave = { updated ->
                                scope.launch {
                                    runCatching {
                                        preferencesRepository.save(updated)
                                    }.onFailure { error ->
                                        reportError(error)
                                    }
                                }
                            }
                        )
                    }

                    AppTab.ACCOUNT -> {
                        AccountScreen(
                            account = account,
                            busy = accountBusy || accountLoading,
                            message = accountMessage,

                            onEmailSignIn = { email, password ->
                                runAccountAction {
                                    accountRepository.signInWithEmail(
                                        email,
                                        password
                                    )
                                }
                            },

                            onEmailSignUp = { name, email, password ->
                                runAccountAction {
                                    accountRepository.signUpWithEmail(
                                        email,
                                        password,
                                        name
                                    )
                                }
                            },

                            // AccountScreen handles the recovery flow itself.
                            onPasswordRecovery = { },

                            onGoogleSignIn = {
                                if (activity == null) {
                                    accountMessage =
                                        "Unable to start Google sign-in from this screen."
                                } else {
                                    scope.launch {
                                        accountBusy = true
                                        accountMessage = null

                                        runCatching {
                                            val googleResult =
                                                GoogleCredentialManager(
                                                    activity
                                                ).getGoogleIdToken()

                                            accountRepository
                                                .signInWithGoogle(
                                                    idToken = googleResult.idToken,
                                                    nonce = googleResult.nonce
                                                )
                                                .getOrThrow()
                                        }.onSuccess { signedInAccount ->
                                            account = signedInAccount
                                            accountMessage =
                                                "Google sign-in successful!"
                                            selectedTab = AppTab.ACCOUNT
                                        }.onFailure { error ->
                                            reportError(error)
                                        }

                                        accountBusy = false
                                    }
                                }
                            },

                            onSignOut = {
                                scope.launch {
                                    accountBusy = true
                                    accountMessage = null

                                    runCatching {
                                        accountRepository.signOut()
                                    }.onSuccess {
                                        account = null
                                        accountMessage =
                                            "You have signed out."
                                    }.onFailure { error ->
                                        reportError(error)
                                    }

                                    accountBusy = false
                                }
                            },

                            onVerifyMasterclass = { whatsappNumber ->
                                scope.launch {
                                    accountBusy = true
                                    accountMessage = null

                                    accountRepository
                                        .verifyFlexiMasterclassAccount(
                                            whatsappNumber
                                        )
                                        .onSuccess { result ->

                                            if (result.eligible) {
                                                // Reload the entitlement from
                                                // Supabase instead of trusting
                                                // the API response alone.
                                                runCatching {
                                                    refreshAccount()
                                                }.onSuccess {
                                                    accountMessage =
                                                        if (account?.type ==
                                                            AccountType.FLEXI_MASTERCLASS
                                                        ) {
                                                            buildString {
                                                                append(
                                                                    "Flexi Masterclass verification successful!"
                                                                )

                                                                result.displayName?.let {
                                                                    append(
                                                                        "\nRegistered name: $it"
                                                                    )
                                                                }

                                                                account
                                                                    ?.masterclassExpiresAt
                                                                    ?.let {
                                                                        append(
                                                                            "\nSubscription expiry: $it"
                                                                        )
                                                                    }

                                                                append(
                                                                    "\nYour unlimited Masterclass access is now active."
                                                                )
                                                            }
                                                        } else {
                                                            "Your registration was verified, but an active entitlement could not be confirmed in Supabase. Please try again."
                                                        }
                                                }.onFailure { error ->
                                                    reportError(error)
                                                }
                                            } else {
                                                // A failed verification must
                                                // never leave an old premium
                                                // account state on screen.
                                                runCatching {
                                                    refreshAccount()
                                                }

                                                accountMessage = result.message
                                            }
                                        }
                                        .onFailure { error ->
                                            reportError(error)
                                        }

                                    accountBusy = false
                                }
                            },

                            onGoToSignIn = {
                                account = null
                                accountMessage = null
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): android.app.Activity? {
    return when (this) {
        is android.app.Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
