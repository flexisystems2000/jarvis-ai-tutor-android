package com.flexi.jarvis.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flexi.jarvis.R
import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.repository.DataStorePreferencesRepository
import com.flexi.jarvis.data.repository.FirebaseAccountRepository
import com.flexi.jarvis.data.repository.PlanPolicy
import com.flexi.jarvis.ui.screens.*
import com.flexi.jarvis.ui.theme.JarvisTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch

private enum class Screen(val label: String) {
    HOME("Tutor"), CBT("CBT Practice"), HISTORY("History"),
    SETTINGS("Settings"), PLANS("Plans"), ACCOUNT("Account")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp() {
    val context = LocalContext.current
    val preferencesRepository = remember(context) {
        DataStorePreferencesRepository(context.applicationContext)
    }
    val accountRepository = remember { FirebaseAccountRepository() }
    val prefs by preferencesRepository.preferences.collectAsState(initial = AppPreferences())
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf<Account?>(null) }
    var authBusy by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountRepository) { account = accountRepository.currentAccount() }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.getResult(com.google.android.gms.common.api.ApiException::class.java) }
            .onSuccess { googleAccount ->
                val token = googleAccount.idToken
                if (token.isNullOrBlank()) {
                    authMessage = "Google did not return an ID token. Check the Web OAuth client ID."
                } else {
                    scope.launch {
                        authBusy = true
                        accountRepository.signInWithGoogle(token)
                            .onSuccess { account = it; authMessage = "Signed in successfully." }
                            .onFailure { authMessage = it.message ?: "Google sign-in failed." }
                        authBusy = false
                    }
                }
            }
            .onFailure { authMessage = it.message ?: "Google sign-in was cancelled or failed." }
    }

    JarvisTheme(theme = prefs.theme) {
        JarvisAppContent(
            prefs = prefs,
            account = account,
            authBusy = authBusy,
            authMessage = authMessage,
            onEmailSignIn = { email, password ->
                scope.launch {
                    authBusy = true; authMessage = null
                    accountRepository.signInWithEmail(email, password)
                        .onSuccess { account = it; authMessage = "Signed in successfully." }
                        .onFailure { authMessage = it.message ?: "Sign-in failed." }
                    authBusy = false
                }
            },
            onEmailSignUp = { name, email, password ->
                scope.launch {
                    authBusy = true; authMessage = null
                    accountRepository.signUpWithEmail(email, password, name)
                        .onSuccess { account = it; authMessage = "Account created. Check your email if verification is requested." }
                        .onFailure { authMessage = it.message ?: "Registration failed." }
                    authBusy = false
                }
            },
            onPasswordRecovery = { email ->
                scope.launch {
                    authBusy = true; authMessage = null
                    accountRepository.sendPasswordReset(email)
                        .onSuccess { authMessage = "If the address is registered, a password recovery email has been sent." }
                        .onFailure { authMessage = it.message ?: "Could not send recovery email." }
                    authBusy = false
                }
            },
            onGoogleSignIn = {
                val webClientId = context.getString(R.string.default_web_client_id)
                if (webClientId.isBlank()) {
                    authMessage = "Google sign-in setup is incomplete: add the Firebase Web OAuth client ID first."
                } else {
                    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestIdToken(webClientId)
                        .build()
                    googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                }
            },
            onMasterclassLink = { token ->
                scope.launch {
                    authBusy = true
                    accountRepository.linkFlexiMasterclassAccount(token)
                        .onSuccess { account = it; authMessage = "Flexi Masterclass account verified." }
                        .onFailure { authMessage = it.message ?: "Masterclass verification unavailable." }
                    authBusy = false
                }
            },
            onSignOut = {
                scope.launch {
                    accountRepository.signOut()
                    account = null
                    authMessage = "You have signed out."
                }
            },
            onSavePreferences = { updated -> scope.launch { preferencesRepository.save(updated) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisAppContent(
    prefs: AppPreferences,
    account: Account?,
    authBusy: Boolean,
    authMessage: String?,
    onEmailSignIn: (String, String) -> Unit,
    onEmailSignUp: (String, String, String) -> Unit,
    onPasswordRecovery: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onMasterclassLink: (String) -> Unit,
    onSignOut: () -> Unit,
    onSavePreferences: (AppPreferences) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var drawerOpen by remember { mutableStateOf(false) }
    var accountType by remember { mutableStateOf(account?.type ?: AccountType.FREE) }
    var usedToday by remember { mutableIntStateOf(0) }
    LaunchedEffect(account?.type) { accountType = account?.type ?: AccountType.FREE }
    val usage = PlanPolicy.usage(accountType, usedToday)

    ModalNavigationDrawer(
        drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed),
        drawerContent = {
            ModalDrawerSheet {
                Text("JARVIS AI Tutor", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
                Screen.values().forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = screen == item,
                        onClick = { screen = item; drawerOpen = false },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                HorizontalDivider()
                Text("Account: ${usage.planLabel}", modifier = Modifier.padding(20.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(prefs.tutorName.ifBlank { "JARVIS" }) },
                    navigationIcon = {
                        TextButton(onClick = { drawerOpen = true }) { Text("☰") }
                    },
                    actions = {
                        TextButton(onClick = { screen = Screen.ACCOUNT }) { Text("Account") }
                        TextButton(onClick = { screen = Screen.PLANS }) { Text("Plans") }
                    }
                )
            }
        ) { _ ->
            when (screen) {
                Screen.HOME -> TutorScreen(
                    tutorName = prefs.tutorName,
                    languageCode = prefs.languageCode,
                    webSearch = prefs.webSearchEnabled,
                    onUsage = { if (PlanPolicy.canUseTokens(accountType, usedToday)) usedToday++ }
                )
                Screen.CBT -> CbtScreen()
                Screen.HISTORY -> HistoryScreen()
                Screen.SETTINGS -> SettingsScreen(preferences = prefs, onSave = onSavePreferences)
                Screen.PLANS -> PlansScreen(
                    accountType = accountType,
                    usedToday = usedToday,
                    onSelectPlan = { accountType = it; usedToday = 0 }
                )
                Screen.ACCOUNT -> AccountScreen(
                    account = account,
                    busy = authBusy,
                    message = authMessage,
                    onEmailSignIn = onEmailSignIn,
                    onEmailSignUp = onEmailSignUp,
                    onPasswordRecovery = onPasswordRecovery,
                    onGoogleSignIn = onGoogleSignIn,
                    onMasterclassLink = onMasterclassLink,
                    onSignOut = onSignOut
                )
            }
        }
    }
}
