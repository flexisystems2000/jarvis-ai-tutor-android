package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Conversation history", style = MaterialTheme.typography.headlineSmall)
        Text("Today")
        Text("No synced conversations yet.")
        Text("Yesterday")
        Text("Previous 7 days")
        Text("Older")
        Text("Conversation sync, search, rename and delete will be connected to the Supabase data layer.")
    }
}
