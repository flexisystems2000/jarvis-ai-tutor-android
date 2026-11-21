package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.model.AppTheme
import com.flexi.jarvis.ui.theme.*

@Composable
fun SettingsScreen(preferences: AppPreferences, onSave: (AppPreferences) -> Unit) {
    var tutorName by remember(preferences) { mutableStateOf(preferences.tutorName) }
    var language by remember(preferences) { mutableStateOf(preferences.languageCode) }
    var theme by remember(preferences) { mutableStateOf(preferences.theme) }
    var webSearch by remember(preferences) { mutableStateOf(preferences.webSearchEnabled) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(JarvisBackground).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", color = JarvisTextPrimary, fontSize = 26.sp)
        Text("Personalize your JARVIS experience.", color = JarvisTextSecondary, fontSize = 13.sp)
        Surface(color = JarvisSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PERSONALIZATION", color = JarvisGreen, fontSize = 11.sp)
                OutlinedTextField(
                    value = tutorName,
                    onValueChange = { tutorName = it },
                    label = { Text("Tutor name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("App language", color = JarvisTextPrimary)
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(languageName(language)) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("en", "fr", "ha", "yo", "ig", "pcm").forEach { code ->
                            DropdownMenuItem(
                                text = { Text(languageName(code)) },
                                onClick = { language = code; expanded = false }
                            )
                        }
                    }
                }
                Text("Theme", color = JarvisTextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.values().forEach { option ->
                        FilterChip(
                            selected = theme == option,
                            onClick = { theme = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Web search", color = JarvisTextPrimary)
                        Text("Allow the tutor to retrieve current information.", color = JarvisTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = webSearch, onCheckedChange = { webSearch = it })
                }
            }
        }
        Button(
            onClick = { onSave(AppPreferences(theme, tutorName.trim().ifBlank { "JARVIS" }, language, webSearch)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save preferences") }
    }
}

private fun languageName(code: String) = when (code) {
    "en" -> "English"
    "fr" -> "French"
    "ha" -> "Hausa"
    "yo" -> "Yoruba"
    "ig" -> "Igbo"
    "pcm" -> "Nigerian Pidgin"
    else -> code
}
