package com.flexi.jarvis.ui

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.flexi.jarvis.JarvisApplication
import com.flexi.jarvis.R
import com.flexi.jarvis.auth.GoogleCredentialManager
import com.flexi.jarvis.core.errors.JarvisErrorHandler
import com.flexi.jarvis.data.model.Account
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.repository.DataStorePreferencesRepository
import com.flexi.jarvis.data.repository.ConversationRepository
import com.flexi.jarvis.data.repository.CloudPreferencesRepository
import com.flexi.jarvis.network.SupabaseBackendApi
import com.flexi.jarvis.data.repository.SupabaseAccountRepository
import com.flexi.jarvis.ui.screens.AccountScreen
import com.flexi.jarvis.ui.screens.CbtScreen
import com.flexi.jarvis.ui.screens.HistoryScreen
import com.flexi.jarvis.ui.screens.PlansScreen
import com.flexi.jarvis.ui.screens.SettingsScreen
import com.flexi.jarvis.ui.screens.TutorScreen
import com.flexi.jarvis.ui.theme.JarvisBackground
import com.flexi.jarvis.ui.theme.JarvisGreen
import com.flexi.jarvis.ui.theme.JarvisSurface
import com.flexi.jarvis.ui.theme.JarvisSurfaceElevated
import com.flexi.jarvis.ui.theme.JarvisTextPrimary
import com.flexi.jarvis.ui.theme.JarvisTextSecondary
import com.flexi.jarvis.ui.theme.JarvisTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

private const val ERROR_LOG_TAG = "JARVIS_ERROR"

private enum class AppTab(val label: String) {
    TUTOR("Ask JARVIS"),
    CBT("CBT Practice"),
    HISTORY("Conversation history"),
    PLANS("Plans"),
    SETTINGS("Settings"),
    ACCOUNT("Account")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val preferencesRepository = remember { DataStorePreferencesRepository(context) }
    val preferences by preferencesRepository.preferences.collectAsState(initial = AppPreferences())
    val accountRepository = remember { SupabaseAccountRepository(JarvisApplication.supabase) }
    val backendApi = remember { SupabaseBackendApi(JarvisApplication.supabase) }
    val conversationRepository = remember(backendApi) { ConversationRepository(backendApi) }
    val cloudPreferencesRepository = remember(backendApi, preferencesRepository) {
        CloudPreferencesRepository(preferencesRepository, backendApi)
    }

    var account by remember { mutableStateOf<Account?>(null) }
    var accountLoading by remember { mutableStateOf(true) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.TUTOR) }
    var historyExpanded by remember { mutableStateOf(false) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var usedToday by remember { mutableIntStateOf(0) }

    fun reportError(error: Throwable) {
        val appError = JarvisErrorHandler.handle(error)
        accountMessage = appError.message
        Log.w(
            ERROR_LOG_TAG,
            "code=${appError.code}, category=${appError.category.name}, " +
                "retryable=${appError.retryable}, httpStatus=${appError.httpStatus ?: "unknown"}, " +
                "exception=${error::class.simpleName ?: "Unknown"}"
        )
    }

    suspend fun refreshAccount() {
        account = accountRepository.currentAccount()
    }

    LaunchedEffect(Unit) {
        JarvisApplication.supabase.auth.sessionStatus.collect { status ->
            when (status) {
                SessionStatus.Initializing -> accountLoading = true
                is SessionStatus.Authenticated -> {
                    accountLoading = true
                    runCatching { refreshAccount() }
                        .onSuccess {
                            if (account != null) {
                                selectedTab = AppTab.ACCOUNT
                                accountMessage = "Your account is ready."
                            }
                        }
                        .onFailure(::reportError)
                    accountLoading = false
                }
                is SessionStatus.NotAuthenticated -> {
                    account = null
                    accountLoading = false
                }
                else -> accountLoading = false
            }
        }
    }

    LaunchedEffect(account?.id) {
        val signedInUserId = account?.id ?: return@LaunchedEffect
        runCatching { cloudPreferencesRepository.syncFromCloud(signedInUserId) }
            .onFailure(::reportError)
    }

    fun runAccountAction(action: suspend () -> Result<Account>) {
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
                if (result.id != "pending-email-verification") selectedTab = AppTab.ACCOUNT
            }.onFailure(::reportError)
            accountBusy = false
        }
    }

    suspend fun closeDrawerAndNavigate(tab: AppTab) {
        selectedTab = tab
        drawerState.close()
    }

    JarvisTheme(theme = preferences.theme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = JarvisSurface,
                    drawerContentColor = JarvisTextPrimary,
                    drawerShape = MaterialTheme.shapes.extraLarge
                ) {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_jarvis_mark),
                            contentDescription = "JARVIS AI logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("JARVIS AI", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("Your learning companion", color = JarvisTextSecondary, fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { closeDrawerAndNavigate(AppTab.ACCOUNT) } }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(color = JarvisSurfaceElevated, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = JarvisGreen)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account?.displayName?.takeIf { it.isNotBlank() } ?: "Your JARVIS account", fontWeight = FontWeight.SemiBold)
                            Text(account?.email ?: "Sign in or manage your account", color = JarvisTextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = JarvisTextSecondary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    DrawerEntry("Ask", Icons.Default.AutoAwesome, selectedTab == AppTab.TUTOR) {
                        scope.launch { closeDrawerAndNavigate(AppTab.TUTOR) }
                    }
                    DrawerEntry("CBT Practice", Icons.Default.Quiz, selectedTab == AppTab.CBT) {
                        scope.launch { closeDrawerAndNavigate(AppTab.CBT) }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { historyExpanded = !historyExpanded }
                            .padding(start = 18.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, null, tint = if (selectedTab == AppTab.HISTORY) JarvisGreen else JarvisTextSecondary)
                        Spacer(Modifier.width(18.dp))
                        Text("Conversation history", modifier = Modifier.weight(1f), color = JarvisTextPrimary)
                        Icon(if (historyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = JarvisTextSecondary)
                    }
                    if (historyExpanded) {
                        Row(
                            Modifier.fillMaxWidth().clickable { scope.launch { closeDrawerAndNavigate(AppTab.HISTORY) } }
                                .padding(start = 58.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Forum, null, tint = JarvisGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("All conversations", color = JarvisTextSecondary, fontSize = 13.sp)
                        }
                        Text(
                            "Open your saved conversations or start a new chat.",
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(start = 58.dp, end = 20.dp, bottom = 12.dp)
                        )
                    }
                    DrawerEntry("Plans", Icons.Default.WorkspacePremium, selectedTab == AppTab.PLANS) {
                        scope.launch { closeDrawerAndNavigate(AppTab.PLANS) }
                    }
                    DrawerEntry("Settings", Icons.Default.Settings, selectedTab == AppTab.SETTINGS) {
                        scope.launch { closeDrawerAndNavigate(AppTab.SETTINGS) }
                    }
                    DrawerEntry("Account", Icons.Default.AccountCircle, selectedTab == AppTab.ACCOUNT) {
                        scope.launch { closeDrawerAndNavigate(AppTab.ACCOUNT) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "JARVIS AI • Flexi Digital Academy",
                        color = JarvisTextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = JarvisBackground,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_jarvis_mark),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("JARVIS AI", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = JarvisTextPrimary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { selectedTab = AppTab.ACCOUNT }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Account", tint = JarvisTextPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = JarvisBackground,
                            titleContentColor = JarvisTextPrimary
                        )
                    )
                }
            ) { paddingValues ->
                Box(Modifier.fillMaxSize().padding(paddingValues)) {
                    when (selectedTab) {
                        AppTab.TUTOR -> TutorScreen(
                            tutorName = preferences.tutorName,
                            languageCode = preferences.languageCode,
                            webSearch = preferences.webSearchEnabled,
                            userId = account?.id,
                            conversationRepository = conversationRepository,
                            selectedConversationId = selectedConversationId,
                            onConversationOpened = { selectedConversationId = it },
                            onNewConversation = { selectedConversationId = null },
                            onUsage = { usedToday++ }
                        )
                        AppTab.CBT -> CbtScreen()
                        AppTab.HISTORY -> HistoryScreen(
                            userId = account?.id,
                            repository = conversationRepository,
                            onOpenConversation = { conversationId ->
                                selectedConversationId = conversationId
                                selectedTab = AppTab.TUTOR
                            }
                        )
                        AppTab.PLANS -> PlansScreen(
                            accountType = account?.type ?: AccountType.FREE,
                            usedToday = usedToday,
                            masterclassExpiresAt = account?.masterclassExpiresAt,
                            onSelectPlan = {
                                accountMessage = "Secure plan payments will be enabled after backend integration."
                                selectedTab = AppTab.ACCOUNT
                            }
                        )
                        AppTab.SETTINGS -> SettingsScreen(
                            preferences = preferences,
                            onSave = { updated ->
                                scope.launch {
                                    runCatching { cloudPreferencesRepository.save(account?.id, updated) }
                                        .onFailure(::reportError)
                                }
                            }
                        )
                        AppTab.ACCOUNT -> AccountScreen(
                            account = account,
                            busy = accountBusy || accountLoading,
                            message = accountMessage,
                            onEmailSignIn = { email, password ->
                                runAccountAction { accountRepository.signInWithEmail(email, password) }
                            },
                            onEmailSignUp = { name, email, password ->
                                runAccountAction { accountRepository.signUpWithEmail(email, password, name) }
                            },
                            onPasswordRecovery = { },
                            onGoogleSignIn = {
                                if (activity == null) {
                                    accountMessage = "Unable to start Google sign-in from this screen."
                                } else {
                                    scope.launch {
                                        accountBusy = true
                                        accountMessage = null
                                        runCatching {
                                            val googleResult = GoogleCredentialManager(activity).getGoogleIdToken()
                                            accountRepository.signInWithGoogle(
                                                idToken = googleResult.idToken,
                                                nonce = googleResult.nonce
                                            ).getOrThrow()
                                        }.onSuccess {
                                            account = it
                                            accountMessage = "Google sign-in successful!"
                                            selectedTab = AppTab.ACCOUNT
                                        }.onFailure(::reportError)
                                        accountBusy = false
                                    }
                                }
                            },
                            onSignOut = {
                                scope.launch {
                                    accountBusy = true
                                    accountMessage = null
                                    runCatching { accountRepository.signOut() }
                                        .onSuccess {
                                            account = null
                                            accountMessage = "You have signed out."
                                        }
                                        .onFailure(::reportError)
                                    accountBusy = false
                                }
                            },
                            onVerifyMasterclass = { whatsappNumber ->
                                scope.launch {
                                    accountBusy = true
                                    accountMessage = null
                                    accountRepository.verifyFlexiMasterclassAccount(whatsappNumber)
                                        .onSuccess { result ->
                                            if (result.eligible) {
                                                runCatching { refreshAccount() }
                                                    .onSuccess {
                                                        accountMessage =
                                                            if (account?.type == AccountType.FLEXI_MASTERCLASS) {
                                                                buildString {
                                                                    append("Flexi Masterclass verification successful!")
                                                                    result.displayName?.let { append("\nRegistered name: $it") }
                                                                    account?.masterclassExpiresAt?.let { append("\nSubscription expiry: $it") }
                                                                    append("\nYour unlimited Masterclass access is now active.")
                                                                }
                                                            } else {
                                                                "Your registration was verified, but an active entitlement could not be confirmed in Supabase. Please try again."
                                                            }
                                                    }
                                                    .onFailure(::reportError)
                                            } else {
                                                runCatching { refreshAccount() }
                                                accountMessage = result.message
                                            }
                                        }
                                        .onFailure(::reportError)
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

@Composable
private fun DrawerEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = JarvisSurfaceElevated,
            selectedIconColor = JarvisGreen,
            selectedTextColor = JarvisTextPrimary,
            unselectedIconColor = JarvisTextSecondary,
            unselectedTextColor = JarvisTextPrimary
        )
    )
}

private fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
