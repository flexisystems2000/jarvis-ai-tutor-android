package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.ui.theme.*

@Composable
fun HistoryScreen() {
    Column(
        Modifier.fillMaxSize().background(JarvisBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("History", color = JarvisTextPrimary, fontSize = 26.sp)
        Surface(color = JarvisSurface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.History, null, tint = JarvisGreen, modifier = Modifier.size(42.dp))
                Text("Your conversations will appear here", color = JarvisTextPrimary)
                Text("Conversation syncing, search, rename and delete are not connected yet.", color = JarvisTextSecondary, fontSize = 12.sp)
            }
        }
    }
}
