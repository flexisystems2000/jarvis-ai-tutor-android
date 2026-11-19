package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CbtScreen() {
    var selected by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CBT Practice", style = MaterialTheme.typography.headlineSmall)
        Text("Question 1 of 20")
        LinearProgressIndicator(progress = { 0.05f }, modifier = Modifier.fillMaxWidth())
        Text("Sample question: Which of these is a chemical element?")
        listOf("Water", "Oxygen", "Salt", "Sugar").forEachIndexed { index, option ->
            OutlinedButton(
                onClick = { selected = index },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) { Text("${'A' + index}. $option") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { selected = null }) { Text("Previous") }
            Button(onClick = { selected = null }) { Text("Next") }
        }
        Text("CBT engine preview. Timed exams, question banks, marking and explanations require backend/question-bank integration.")
    }
}
