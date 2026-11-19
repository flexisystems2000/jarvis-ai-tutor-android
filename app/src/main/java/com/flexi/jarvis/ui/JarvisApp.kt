package com.flexi.jarvis.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flexi.jarvis.data.model.AccountType
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.model.AppTheme
import com.flexi.jarvis.data.repository.PlanPolicy
import com.flexi.jarvis.ui.screens.*

private enum class Screen(val label: String) {
    HOME("Tutor"), CBT("CBT Practice"), HISTORY("History"), SETTINGS("Settings"), PLANS("Plans")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var drawerOpen by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf(AppPreferences()) }
    var accountType by remember { mutableStateOf(AccountType.FREE) }
    var usedToday by remember { mutableIntStateOf(0) }
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
                Divider()
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
                        TextButton(onClick = { screen = Screen.PLANS }) { Text("Plans") }
                    }
                )
            }
        ) { padding ->
            when (screen) {
                Screen.HOME -> TutorScreen(
                    tutorName = prefs.tutorName,
                    languageCode = prefs.languageCode,
                    webSearch = prefs.webSearchEnabled,
                    onUsage = { if (PlanPolicy.canUseTokens(accountType, usedToday)) usedToday++ }
                )
                Screen.CBT -> CbtScreen()
                Screen.HISTORY -> HistoryScreen()
                Screen.SETTINGS -> SettingsScreen(
                    preferences = prefs,
                    onSave = { prefs = it }
                )
                Screen.PLANS -> PlansScreen(
                    accountType = accountType,
                    usedToday = usedToday,
                    onSelectPlan = { accountType = it; usedToday = 0 }
                )
            }
        }
    }
}
