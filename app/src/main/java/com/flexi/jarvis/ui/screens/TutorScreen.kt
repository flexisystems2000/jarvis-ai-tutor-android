package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TutorScreen(
    tutorName: String,
    languageCode: String,
    webSearch: Boolean,
    onUsage: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf("Welcome! Ask me to explain any topic step by step.")) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your AI learning companion", style = MaterialTheme.typography.titleLarge)
        Text("Language: $languageCode • Web search: ${if (webSearch) "On" else "Off"}")
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { Text(it, modifier = Modifier.fillMaxWidth().padding(8.dp)) }
        }
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Ask $tutorName") }
        )
        Button(
            onClick = {
                if (prompt.isNotBlank()) {
                    messages = messages + "You: $prompt" + "AI backend is not connected yet. Your message is ready for integration."
                    prompt = ""
                    onUsage()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send") }
    }
}
