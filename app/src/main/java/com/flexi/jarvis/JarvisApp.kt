package com.flexi.jarvis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexi.jarvis.JarvisApplication
import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.repository.DataStorePreferencesRepository
import com.flexi.jarvis.data.repository.SupabaseAccountRepository
import com.flexi.jarvis.ui.screens.*
import com.flexi.jarvis.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    val preferencesRepository = remember {
        DataStorePreferencesRepository(context)
    }

    val preferences by preferencesRepository.preferences
        .collectAsStateWithLifecycle(
            initialValue = com.flexi.jarvis.data.model.AppPreferences()
        )

    val accountRepository = remember {
        SupabaseAccountRepository(JarvisApplication.supabase)
    }

    var account by remember { mutableStateOf<Account?>(null) }
    var accountLoading by remember { mutableStateOf(true) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember {
        mutableStateOf(AppTab.TUTOR)
    }

    var usedToday by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(Unit) {
        runCatching {
            accountRepository.currentAccount()
        }.onSuccess {
            account = it
        }.onFailure {
            accountMessage = it.message ?: "Unable to load your account."
        }

        accountLoading = false
    }

    fun runAccountAction(
        action: suspend () -> Result<Account>
    ) {
        scope.launch {
            accountBusy = true
            accountMessage = null

            action().onSuccess {
                account = it
                accountMessage = if (
                    it.id == "pending-email-verification"
                ) {
                    "Registration submitted. Verify your email, then sign in."
                } else {
                    "Account updated successfully."
                }
            }.onFailure {
                accountMessage = it.message ?: "Account operation failed."
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
                            onClick = { selectedTab = tab },
                            icon = {},
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { paddingValues ->

            androidx.compose.foundation.layout.Box(
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

                    AppTab.CBT -> {
                        CbtScreen()
                    }

                    AppTab.HISTORY -> {
                        HistoryScreen()
                    }

                    AppTab.PLANS -> {
                        PlansScreen(
                            accountType = account?.type
                                ?: AccountType.FREE,
                            usedToday = usedToday,
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
                                    preferencesRepository.save(updated)
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

                            onPasswordRecovery = { email ->
                                scope.launch {
                                    accountBusy = true
                                    accountMessage = null

                                    accountRepository
                                        .sendPasswordReset(email)
                                        .onSuccess {
                                            accountMessage =
                                                "Password recovery email sent. Check your inbox."
                                        }
                                        .onFailure {
                                            accountMessage =
                                                it.message ?: "Could not send recovery email."
                                        }

                                    accountBusy = false
                                }
                            },

                            onGoogleSignIn = {
                                accountMessage =
                                    "Google sign-in needs its Android credential flow and configured OAuth client ID before it can be activated."
                            },

                            onSignOut = {
                                scope.launch {
                                    accountBusy = true
                                    accountMessage = null

                                    runCatching {
                                        accountRepository.signOut()
                                    }.onSuccess {
                                        account = null
                                        accountMessage = "You have signed out."
                                    }.onFailure {
                                        accountMessage =
                                            it.message ?: "Sign-out failed."
                                    }

                                    accountBusy = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
