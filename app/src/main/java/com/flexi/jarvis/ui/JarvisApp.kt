
package com.flexi.jarvis.ui

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.flexi.jarvis.BuildConfig
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
import com.flexi.jarvis.network.SpeechTranscriptionApi
import com.flexi.jarvis.network.CloudflareAiTutorApi
import com.flexi.jarvis.network.LiveSessionApi
import com.flexi.jarvis.network.GeminiTtsApi
import com.flexi.jarvis.network.SupabaseBackendApi
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

    val preferencesRepository = remember {
        DataStorePreferencesRepository(context)
    }

    val preferences by preferencesRepository.preferences.collectAsState(
        initial = AppPreferences()
    )

    val accountRepository = remember {
        SupabaseAccountRepository(JarvisApplication.supabase)
    }

    val backendApi = remember {
        SupabaseBackendApi(JarvisApplication.supabase)
    }

    val conversationRepository = remember(backendApi) {
        ConversationRepository(backendApi)
    }

    // Online speech transcription client.
    // The signed-in user's Supabase access token is sent with each recording.
    val speechTranscriptionApi = remember {
        SpeechTranscriptionApi(
            baseUrl = BuildConfig.SPEECH_API_URL,
            accessTokenProvider = {
                JarvisApplication.supabase.auth.currentAccessTokenOrNull()
            }
        )
    }

    // Gemini text chat is proxied through the same authenticated Cloudflare Worker.
    val aiTutorApi = remember {
        CloudflareAiTutorApi(
            baseUrl = BuildConfig.SPEECH_API_URL,
            accessTokenProvider = {
                JarvisApplication.supabase.auth.currentAccessTokenOrNull()
            }
        )
    }

    val geminiTtsApi = remember {
        GeminiTtsApi(
            baseUrl = BuildConfig.SPEECH_API_URL,
            accessTokenProvider = {
                JarvisApplication.supabase.auth.currentAccessTokenOrNull()
            }
        )
    }

    val liveSessionApi = remember {
        LiveSessionApi(
            baseUrl = BuildConfig.SPEECH_API_URL,
            accessToken = {
                JarvisApplication.supabase.auth.currentAccessTokenOrNull()
            }
        )
    }

    val cloudPreferencesRepository = remember(
        backendApi,
        preferencesRepository
    ) {
        CloudPreferencesRepository(
            preferencesRepository,
            backendApi
        )
    }

    var account by remember { mutableStateOf<Account?>(null) }
    var accountLoading by remember { mutableStateOf(true) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.TUTOR) }
    var historyExpanded by remember { mutableStateOf(false) }
    var selectedConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var usedToday by remember { mutableIntStateOf(0) }

    fun reportError(error: Throwable) {
        val appError = JarvisErrorHandler.handle(error)

        accountMessage = appError.message

        Log.w(
            ERROR_LOG_TAG,
            "code=${appError.code}, category=${appError.category.name}, " +
                "retryable=${appError.retryable}, " +
                "httpStatus=${appError.httpStatus ?: "unknown"}, " +
                "exception=${error::class.simpleName ?: "Unknown"}"
        )
    }

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
                            accountMessage = "Your account is ready."
                        }
                    }.onFailure(::reportError)

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

    // Clear the selected conversation whenever the account changes.
    // This prevents a previous user's conversation ID from being reused.
    var previousAccountId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(account?.id) {
        val currentAccountId = account?.id
        if (previousAccountId != null && previousAccountId != currentAccountId) {
            selectedConversationId = null
        }
        previousAccountId = currentAccountId

        val signedInUserId = currentAccountId
            ?: return@LaunchedEffect

        runCatching {
            cloudPreferencesRepository.syncFromCloud(signedInUserId)
        }.onFailure(::reportError)
    }

    fun runAccountAction(action: suspend () -> Result<Account>) {
        scope.launch {
            accountBusy = true
            accountMessage = null

            action()
                .onSuccess { result ->
                    account = result

                    accountMessage =
                        if (result.id == "pending-email-verification") {
                            "Registration request received. Check your email for verification before signing in."
                        } else {
                            "Account updated successfully."
                        }

                }
                .onFailure(::reportError)

            accountBusy = false
        }
    }

    suspend fun closeDrawerAndNavigate(tab: AppTab) {
        selectedTab = tab
        drawerState.close()
    }

    JarvisTheme(theme = preferences.theme) {
        val colors = MaterialTheme.colorScheme

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = colors.surface,
                    drawerContentColor = colors.onSurface,
                    drawerShape = MaterialTheme.shapes.extraLarge
                ) {
                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
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
                            Text(
                                "JARVIS AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )

                            Text(
                                "Your learning companion",
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(
                        color = colors.outline.copy(alpha = 0.35f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    closeDrawerAndNavigate(AppTab.ACCOUNT)
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = colors.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = colors.primary
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                account?.displayName
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Your JARVIS account",
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                account?.email
                                    ?: "Sign in or manage your account",
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        color = colors.outline.copy(alpha = 0.35f)
                    )

                    DrawerEntry(
                        label = "Ask",
                        icon = Icons.Default.AutoAwesome,
                        selected = selectedTab == AppTab.TUTOR
                    ) {
                        scope.launch {
                            closeDrawerAndNavigate(AppTab.TUTOR)
                        }
                    }

                    DrawerEntry(
                        label = "CBT Practice",
                        icon = Icons.Default.Quiz,
                        selected = selectedTab == AppTab.CBT
                    ) {
                        scope.launch {
                            closeDrawerAndNavigate(AppTab.CBT)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                historyExpanded = !historyExpanded
                            }
                            .padding(
                                start = 18.dp,
                                end = 16.dp,
                                top = 13.dp,
                                bottom = 13.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = if (selectedTab == AppTab.HISTORY) {
                                colors.primary
                            } else {
                                colors.onSurfaceVariant
                            }
                        )

                        Spacer(Modifier.width(18.dp))

                        Text(
                            "Conversation history",
                            modifier = Modifier.weight(1f),
                            color = colors.onSurface
                        )

                        Icon(
                            if (historyExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }

                    if (historyExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        closeDrawerAndNavigate(AppTab.HISTORY)
                                    }
                                }
                                .padding(
                                    start = 58.dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    bottom = 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Forum,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(12.dp))

                            Text(
                                "All conversations",
                                color = colors.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            "Open your saved conversations or start a new chat.",
                            color = colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(
                                start = 58.dp,
                                end = 20.dp,
                                bottom = 12.dp
                            )
                        )
                    }

                    DrawerEntry(
                        label = "Plans",
                        icon = Icons.Default.WorkspacePremium,
                        selected = selectedTab == AppTab.PLANS
                    ) {
                        scope.launch {
                            closeDrawerAndNavigate(AppTab.PLANS)
                        }
                    }

                    DrawerEntry(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        selected = selectedTab == AppTab.SETTINGS
                    ) {
                        scope.launch {
                            closeDrawerAndNavigate(AppTab.SETTINGS)
                        }
                    }

                    DrawerEntry(
                        label = "Account",
                        icon = Icons.Default.AccountCircle,
                        selected = selectedTab == AppTab.ACCOUNT
                    ) {
                        scope.launch {
                            closeDrawerAndNavigate(AppTab.ACCOUNT)
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        "JARVIS AI • Flexi Digital Academy",
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = colors.background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_jarvis_mark),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(
                                    "JARVIS AI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                        },

                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open menu",
                                    tint = colors.onSurface
                                )
                            }
                        },

                        actions = {
                            IconButton(
                                onClick = {
                                    selectedTab = AppTab.ACCOUNT
                                }
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Account",
                                    tint = colors.onSurface
                                )
                            }
                        },

                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = colors.background,
                            titleContentColor = colors.onBackground
                        )
                    )
                }
            ) { paddingValues ->

                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (selectedTab) {

                        AppTab.TUTOR -> TutorScreen(
                            tutorName = preferences.tutorName,
                            languageCode = preferences.languageCode,
                            webSearch = preferences.webSearchEnabled,
                            userId = account?.id,
                            conversationRepository = conversationRepository,
                            speechTranscriptionApi = speechTranscriptionApi,
                            aiTutorApi = aiTutorApi,
                            ttsApi = geminiTtsApi,
                            liveSessionApi = liveSessionApi,
                            selectedConversationId = selectedConversationId,
                            onConversationOpened = {
                                selectedConversationId = it
                            },
                            onNewConversation = {
                                selectedConversationId = null
                            },
                            onUsage = {
                                usedToday++
                            }
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
                                accountMessage =
                                    "Secure plan payments will be enabled after backend integration."
                                selectedTab = AppTab.ACCOUNT
                            }
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            preferences = preferences,
                            onSave = { updated ->
                                cloudPreferencesRepository.save(
                                    account?.id,
                                    updated
                                )
                            }
                        )

                        AppTab.ACCOUNT -> AccountScreen(
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
                                                GoogleCredentialManager(activity)
                                                    .getGoogleIdToken()

                                            accountRepository.signInWithGoogle(
                                                idToken = googleResult.idToken,
                                                nonce = googleResult.nonce
                                            ).getOrThrow()
                                        }.onSuccess {
                                            account = it
                                            accountMessage =
                                                "Google sign-in successful!"
                                        }.onFailure(::reportError)

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
                                        accountMessage = "You have signed out."
                                    }.onFailure(::reportError)

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
                                                runCatching {
                                                    refreshAccount()
                                                }.onSuccess {

                                                    accountMessage =
                                                        if (
                                                            account?.type ==
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

                                                                account?.masterclassExpiresAt?.let {
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
                                                }.onFailure(::reportError)

                                            } else {
                                                runCatching {
                                                    refreshAccount()
                                                }

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
    val colors = MaterialTheme.colorScheme

    NavigationDrawerItem(
        label = {
            Text(label)
        },

        icon = {
            Icon(icon, contentDescription = null)
        },

        selected = selected,
        onClick = onClick,

        modifier = Modifier.padding(
            horizontal = 10.dp,
            vertical = 2.dp
        ),

        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colors.secondaryContainer,
            selectedIconColor = colors.primary,
            selectedTextColor = colors.onSurface,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurface
        )
    )
}

private fun Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
