package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.model.AppTheme

@Composable
fun SettingsScreen(preferences: AppPreferences, onSave: (AppPreferences) -> Unit) {
    var tutorName by remember(preferences) { mutableStateOf(preferences.tutorName) }
    var language by remember(preferences) { mutableStateOf(preferences.languageCode) }
    var theme by remember(preferences) { mutableStateOf(preferences.theme) }
    var webSearch by remember(preferences) { mutableStateOf(preferences.webSearchEnabled) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personalization", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(tutorName, { tutorName = it }, label = { Text("Tutor name") }, modifier = Modifier.fillMaxWidth())
        Text("App language")
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(languageName(language)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("en", "fr", "ha", "yo", "ig", "pcm").forEach { code ->
                    DropdownMenuItem(text = { Text(languageName(code)) }, onClick = { language = code; expanded = false })
                }
            }
        }
        Text("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTheme.values().forEach { option ->
                FilterChip(selected = theme == option, onClick = { theme = option }, label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Web search")
                Text("Allow the tutor to retrieve current information.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = webSearch, onCheckedChange = { webSearch = it })
        }
        Button(onClick = { onSave(AppPreferences(theme, tutorName.trim().ifBlank { "JARVIS" }, language, webSearch)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save preferences")
        }
        Text("More personalization options will be added as the app develops.", style = MaterialTheme.typography.bodySmall)
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
