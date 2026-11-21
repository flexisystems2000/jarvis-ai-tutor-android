package com.flexi.jarvis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.ChatMessage
import com.flexi.jarvis.data.repository.ConversationRepository
import com.flexi.jarvis.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TutorScreen(
    tutorName: String,
    languageCode: String,
    webSearch: Boolean,
    userId: String?,
    conversationRepository: ConversationRepository,
    selectedConversationId: String?,
    onConversationOpened: (String) -> Unit,
    onNewConversation: () -> Unit,
    onUsage: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId, selectedConversationId) {
        messages = emptyList()
        notice = null
        if (userId != null && selectedConversationId != null) {
            loading = true
            runCatching { conversationRepository.messages(userId, selectedConversationId) }
                .onSuccess { messages = it }
                .onFailure { notice = it.message ?: "Unable to load this conversation." }
            loading = false
        }
    }

    fun sendMessage() {
        val cleanPrompt = prompt.trim()
        val owner = userId
        if (cleanPrompt.isEmpty() || sending) return
        if (owner.isNullOrBlank()) {
            notice = "Sign in to save conversations to your account."
            return
        }
        scope.launch {
            sending = true
            notice = null
            runCatching {
                val conversation = selectedConversationId?.let { id ->
                    // Reuse an open conversation; create one for a new chat.
                    id
                } ?: conversationRepository.create(owner, cleanPrompt.take(70)).id.also(onConversationOpened)
                val saved = conversationRepository.addMessage(owner, conversation, "user", cleanPrompt)
                conversationRepository.messages(owner, conversation)
                saved
            }.onSuccess {
                prompt = ""
                messages = runCatching {
                    conversationRepository.messages(owner, selectedConversationId ?: it.conversationId)
                }.getOrDefault(messages + it)
                onUsage()
            }.onFailure { notice = it.message ?: "Your message could not be saved. Please try again." }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().background(JarvisBackground).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tutorName, color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Your intelligent learning companion", color = JarvisTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onNewConversation) {
                Icon(Icons.Default.Add, contentDescription = "New conversation", tint = JarvisGreen)
            }
            Surface(shape = CircleShape, color = JarvisSurfaceVariant) {
                Icon(Icons.Default.AutoAwesome, null, tint = JarvisGreen, modifier = Modifier.padding(11.dp))
            }
        }
        Spacer(Modifier.height(18.dp))

        if (messages.isEmpty() && !loading) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123B78), Color(0xFF075D55))), RoundedCornerShape(24.dp)).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR AI STUDY PARTNER", color = Color(0xFFB9F5E7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("What would you\nlike to learn today?", color = Color.White, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ask questions, understand difficult topics and practise at your pace.", color = Color(0xFFD7E7F5), fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("EXPLORE", color = JarvisTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SuggestionCard("Study a topic", "Step-by-step lessons", Icons.Default.MenuBook, Modifier.weight(1f)) { prompt = "Teach me a topic step by step" }
                SuggestionCard("Ask anything", "Get clear explanations", Icons.Default.Psychology, Modifier.weight(1f)) { prompt = "Explain a difficult concept simply" }
            }
            Spacer(Modifier.height(16.dp))
            listOf("Explain a topic in simple terms", "Give me practice questions", "Summarise my notes").forEach { item ->
                Surface(onClick = { prompt = item }, color = JarvisSurface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, JarvisOutline), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = JarvisGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp)); Text(item, color = JarvisTextPrimary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role.equals("user", true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (isUser) JarvisSurfaceVariant else JarvisSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(0.92f)) {
                            Column(Modifier.padding(15.dp)) {
                                Text(if (isUser) "You" else tutorName, color = JarvisGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(5.dp))
                                Text(message.content, color = JarvisTextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = JarvisGreen)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
        if (messages.isNotEmpty() && !loading) Text("Your message is saved. AI responses will be available when the tutor backend is connected.", color = JarvisTextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 5.dp))
        Surface(color = JarvisSurface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, JarvisOutline), modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Column(Modifier.padding(13.dp)) {
                BasicTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 120.dp), textStyle = LocalTextStyle.current.copy(color = JarvisTextPrimary, fontSize = 15.sp), decorationBox = { inner -> Box { if (prompt.isBlank()) Text("Ask JARVIS anything...", color = JarvisTextSecondary, fontSize = 15.sp); inner() } })
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = JarvisTextSecondary)
                    Spacer(Modifier.weight(1f)); Icon(Icons.Default.Mic, null, tint = JarvisTextSecondary); Spacer(Modifier.width(12.dp))
                    FilledIconButton(onClick = ::sendMessage, enabled = prompt.isNotBlank() && !sending, colors = IconButtonDefaults.filledIconButtonColors(containerColor = JarvisBlue, contentColor = Color.White)) {
                        if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.ArrowUpward, contentDescription = "Send")
                    }
                }
            }
        }
        Text("Language: $languageCode  •  Web search: ${if (webSearch) "On" else "Off"}", color = JarvisTextSecondary, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp))
    }
}

@Composable
private fun SuggestionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(112.dp), color = JarvisSurface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, JarvisOutline)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = JarvisGreen)
            Text(title, color = JarvisTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = JarvisTextSecondary, fontSize = 10.sp)
        }
    }
}
