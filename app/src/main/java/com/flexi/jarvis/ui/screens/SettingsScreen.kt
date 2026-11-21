package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.model.AppTheme
import kotlinx.coroutines.launch

private data class LanguageOption(val code: String, val label: String)

private val languageOptions = listOf(
    LanguageOption("en", "English"),
    LanguageOption("fr", "French"),
    LanguageOption("ha", "Hausa"),
    LanguageOption("yo", "Yoruba"),
    LanguageOption("ig", "Igbo"),
    LanguageOption("pcm", "Nigerian Pidgin")
)

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onSave: suspend (AppPreferences) -> Unit
) {
    var tutorName by remember(preferences) { mutableStateOf(preferences.tutorName) }
    var language by remember(preferences) { mutableStateOf(preferences.languageCode) }
    var theme by remember(preferences) { mutableStateOf(preferences.theme) }
    var webSearch by remember(preferences) { mutableStateOf(preferences.webSearchEnabled) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", color = colors.onBackground, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Make JARVIS work the way you prefer. Your choices are saved on this device and synced to your account when signed in.",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        SettingsSection(title = "PERSONALIZATION") {
            OutlinedTextField(
                value = tutorName,
                onValueChange = { tutorName = it; statusMessage = null },
                label = { Text("Tutor name") },
                supportingText = { Text("The name JARVIS uses in your tutor conversations.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("App language", color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
            Box {
                OutlinedButton(
                    onClick = { languageMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(languageOptions.firstOrNull { it.code == language }?.label ?: "English")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ExpandMore, contentDescription = "Choose language")
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false }
                ) {
                    languageOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (language == option.code) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                language = option.code
                                languageMenuExpanded = false
                                statusMessage = null
                            }
                        )
                    }
                }
            }
        }

        SettingsSection(title = "APPEARANCE") {
            Text("Theme", color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
            Text(
                "Choose how JARVIS looks across the app.",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTheme.values().forEach { option ->
                    FilterChip(
                        selected = theme == option,
                        onClick = { theme = option; statusMessage = null },
                        label = {
                            Text(
                                when (option) {
                                    AppTheme.SYSTEM -> "System"
                                    AppTheme.LIGHT -> "Light"
                                    AppTheme.DARK -> "Dark"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SettingsSection(title = "TUTOR CAPABILITIES") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Web search", color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Allow JARVIS to use current online information when answering.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = webSearch,
                    onCheckedChange = { webSearch = it; statusMessage = null }
                )
            }
            Text(
                if (webSearch) "Web search is enabled." else "Web search is disabled.",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }

        statusMessage?.let { message ->
            Text(
                message,
                color = if (saveFailed) colors.error else colors.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = {
                val updated = AppPreferences(
                    theme = theme,
                    tutorName = tutorName.trim().ifBlank { "JARVIS" },
                    languageCode = language,
                    webSearchEnabled = webSearch
                )
                scope.launch {
                    saving = true
                    statusMessage = null
                    runCatching { onSave(updated) }
                        .onSuccess {
                            tutorName = updated.tutorName
                            statusMessage = "Preferences saved successfully."
                            saveFailed = false
                        }
                        .onFailure { error ->
                            statusMessage = error.message?.takeIf { it.isNotBlank() }
                                ?: "Could not save preferences. Please try again."
                            saveFailed = true
                        }
                    saving = false
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text("Saving…")
            } else {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save preferences")
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.sp
                )
                content()
            }
        )
    }
}
