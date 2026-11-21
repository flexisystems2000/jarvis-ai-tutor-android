package com.flexi.jarvis.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexi.jarvis.data.model.ChatMessage
import com.flexi.jarvis.data.repository.ConversationRepository
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
    val colors = MaterialTheme.colorScheme

    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedMedia by remember { mutableStateOf<android.net.Uri?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedMedia = uri
        if (uri != null) {
            notice = "File selected. Media upload will be available when the upload service is connected."
        }
    }

    val speechRecognizer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spokenText.isNullOrBlank()) {
                prompt = listOf(prompt, spokenText)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                notice = null
            }
        }
    }

    LaunchedEffect(userId, selectedConversationId) {
        messages = emptyList()
        notice = null

        if (userId != null && selectedConversationId != null) {
            loading = true
            runCatching {
                conversationRepository.messages(userId, selectedConversationId)
            }.onSuccess {
                messages = it
            }.onFailure {
                notice = it.message ?: "Unable to load this conversation."
            }
            loading = false
        }
    }

    fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }

        runCatching {
            speechRecognizer.launch(intent)
        }.onFailure {
            notice = "Speech recognition is not available on this device."
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
                val conversationId = selectedConversationId
                    ?: conversationRepository.create(
                        owner,
                        cleanPrompt.take(70)
                    ).id.also(onConversationOpened)

                conversationRepository.addMessage(
                    owner,
                    conversationId,
                    "user",
                    cleanPrompt
                )

                conversationRepository.messages(owner, conversationId)
            }.onSuccess { updatedMessages ->
                messages = updatedMessages
                prompt = ""
                onUsage()

                if (selectedMedia != null) {
                    notice = "Your text was saved. The selected file still needs media-upload support."
                }
            }.onFailure {
                notice = it.message
                    ?: "Your message could not be saved. Please try again."
            }

            sending = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tutorName,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Text(
                    "Your intelligent learning companion",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onNewConversation) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New conversation",
                    tint = colors.primary
                )
            }

            Surface(shape = CircleShape, color = colors.primaryContainer) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (messages.isEmpty() && !loading) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.primaryContainer,
                                    colors.secondaryContainer
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "YOUR AI STUDY PARTNER",
                            color = colors.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            "What would you\nlike to learn today?",
                            color = colors.onPrimaryContainer,
                            fontSize = 27.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Ask questions, understand difficult topics and practise at your pace.",
                            color = colors.onPrimaryContainer,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "EXPLORE",
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SuggestionCard(
                        "Study a topic",
                        "Step-by-step lessons",
                        Icons.Default.MenuBook,
                        Modifier.weight(1f)
                    ) {
                        prompt = "Teach me a topic step by step"
                    }

                    SuggestionCard(
                        "Ask anything",
                        "Get clear explanations",
                        Icons.Default.Psychology,
                        Modifier.weight(1f)
                    ) {
                        prompt = "Explain a difficult concept simply"
                    }
                }

                Spacer(Modifier.height(14.dp))

                listOf(
                    "Explain a topic in simple terms",
                    "Give me practice questions",
                    "Summarise my notes"
                ).forEach { suggestion ->
                    Surface(
                        onClick = { prompt = suggestion },
                        color = colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                suggestion,
                                color = colors.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isUser = message.role.equals("user", true)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        Surface(
                            color = if (isUser) {
                                colors.primaryContainer
                            } else {
                                colors.surfaceVariant
                            },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Column(Modifier.padding(15.dp)) {
                                Text(
                                    if (isUser) "You" else tutorName,
                                    color = colors.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    message.content,
                                    color = colors.onSurface,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary
            )
        }

        notice?.let {
            Text(
                it,
                color = colors.error,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            )
        }

        if (messages.isNotEmpty() && !loading) {
            Text(
                "Your message is saved. AI responses will appear when the tutor service is connected.",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 5.dp)
            )
        }

        // Selected media preview. Upload is not yet wired to the repository.
        selectedMedia?.let { uri ->
            Surface(
                color = colors.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file",
                        color = colors.onSecondaryContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    IconButton(
                        onClick = { selectedMedia = null },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            tint = colors.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Chat composer
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 9.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { mediaPicker.launch("*/*") }
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Attach media",
                        tint = colors.primary
                    )
                }

                BasicTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp, max = 110.dp)
                        .padding(horizontal = 7.dp, vertical = 10.dp),
                    textStyle = LocalTextStyle.current.copy(
                        color = colors.onSurface,
                        fontSize = 15.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (prompt.isBlank()) {
                                Text(
                                    "Ask JARVIS anything...",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(onClick = ::startSpeechRecognition) {
                    VectorMicIcon(
                        tint = colors.primary,
                        modifier = Modifier.size(25.dp)
                    )
                }

                FilledIconButton(
                    onClick = ::sendMessage,
                    enabled = prompt.isNotBlank() && !sending,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = colors.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }

        Text(
            "Language: $languageCode  •  Web search: ${if (webSearch) "On" else "Off"}",
            color = colors.onSurfaceVariant,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun VectorMicIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.085f

        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.37f, size.height * 0.08f),
            size = Size(size.width * 0.26f, size.height * 0.53f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                size.width * 0.14f
            )
        )

        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.20f, size.height * 0.28f),
            size = Size(size.width * 0.60f, size.height * 0.53f),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )

        drawLine(
            color = tint,
            start = Offset(size.width * 0.50f, size.height * 0.80f),
            end = Offset(size.width * 0.50f, size.height * 0.94f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = tint,
            start = Offset(size.width * 0.34f, size.height * 0.94f),
            end = Offset(size.width * 0.66f, size.height * 0.94f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SuggestionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        color = colors.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, null, tint = colors.primary)
            Text(
                title,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                subtitle,
                color = colors.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
