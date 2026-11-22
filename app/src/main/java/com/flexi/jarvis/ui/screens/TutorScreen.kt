
package com.flexi.jarvis.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import android.widget.TextView
import android.text.method.LinkMovementMethod
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flexi.jarvis.data.model.ChatMessage
import com.flexi.jarvis.data.repository.ConversationRepository
import com.flexi.jarvis.network.SpeechTranscriptionApi
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TutorScreen(
    tutorName: String,
    languageCode: String,
    webSearch: Boolean,
    userId: String?,
    conversationRepository: ConversationRepository,
    speechTranscriptionApi: SpeechTranscriptionApi,
    aiTutorApi: com.flexi.jarvis.network.AiTutorApi,
    geminiLiveTokenApi: com.flexi.jarvis.network.GeminiLiveTokenApi,
    selectedConversationId: String?,
    onConversationOpened: (String) -> Unit,
    onNewConversation: () -> Unit,
    onUsage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    var liveCallOpen by remember { mutableStateOf(false) }

    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    // PHOTO AND VIDEO PICKER
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedMedia = uri

        if (uri != null) {
            notice = "Photo or video selected. Media upload is not connected yet."
        }
    }

    // DOCUMENT AND FILE PICKER
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedMedia = uri

        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            notice = "File selected. File upload is not connected yet."
        }
    }

    // CAMERA
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null

        if (saved && uri != null) {
            selectedMedia = uri
            notice = "Photo captured. Media upload is not connected yet."
        } else if (uri != null) {
            runCatching {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    fun launchCamera() {
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "jarvis-photo-${System.currentTimeMillis()}.jpg"
            )

            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/JARVIS AI"
                )
            }
        }

        val uri = runCatching {
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
        }.getOrNull()

        if (uri == null) {
            notice = "Unable to open the camera. Please try again."
            return
        }

        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // VOICE TRANSCRIPTION
    fun appendTranscript(text: String) {
        val cleanText = text.trim()

        if (cleanText.isNotEmpty()) {
            prompt = listOf(
                prompt.trim(),
                cleanText
            )
                .filter { it.isNotBlank() }
                .joinToString(" ")

            notice = "Transcription added. Review or edit it before sending."
        }
    }

    fun startRecording() {
        if (!languageCode.equals("en", ignoreCase = true)) {
            notice = "Voice transcription currently supports English only."
            return
        }

        if (recording || transcribing) return

        val outputFile = File(
            context.cacheDir,
            "jarvis-voice-${System.currentTimeMillis()}.m4a"
        )

        try {
            val newRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            recorder = newRecorder
            recordingFile = outputFile
            recording = true

            notice = "Recording. Tap the microphone again when you finish speaking."

        } catch (error: Exception) {
            runCatching { outputFile.delete() }
            runCatching { recorder?.release() }

            recorder = null
            recordingFile = null
            recording = false

            notice = error.message
                ?: "Unable to start microphone recording."
        }
    }

    fun stopRecordingAndTranscribe() {
        val activeRecorder = recorder
        val audioFile = recordingFile

        if (activeRecorder == null || audioFile == null) {
            recording = false
            notice = "No active recording was found."
            return
        }

        recording = false
        recorder = null
        recordingFile = null

        val recordingStopped = runCatching {
            activeRecorder.stop()
        }.isSuccess

        runCatching {
            activeRecorder.reset()
            activeRecorder.release()
        }

        if (
            !recordingStopped ||
            !audioFile.exists() ||
            audioFile.length() == 0L
        ) {
            audioFile.delete()
            notice = "The recording was too short or could not be saved. Please try again."
            return
        }

        scope.launch {
            transcribing = true
            notice = "Uploading audio and transcribing..."

            try {
                val transcript = speechTranscriptionApi.transcribe(audioFile)
                appendTranscript(transcript)

            } catch (error: Exception) {
                notice = error.message
                    ?: "Voice transcription failed. Please try again."

            } finally {
                audioFile.delete()
                transcribing = false
            }
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecording()
            } else {
                notice = "Microphone permission is required for voice input."
            }
        }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                recorder?.reset()
                recorder?.release()
            }

            recorder = null
            recordingFile?.delete()
            recordingFile = null
        }
    }

    // LOAD CONVERSATION HISTORY
    LaunchedEffect(userId, selectedConversationId) {
        messages = emptyList()
        notice = null

        if (userId != null && selectedConversationId != null) {
            loading = true

            runCatching {
                conversationRepository.messages(
                    userId,
                    selectedConversationId
                )
            }.onSuccess {
                messages = it
            }.onFailure {
                notice = it.message
                    ?: "Unable to load this conversation."
            }

            loading = false
        }
    }

    fun saveLiveTurn(userText: String, assistantText: String) {
        val owner = userId ?: return
        scope.launch {
            runCatching {
                val activeId = selectedConversationId
                    ?: conversationRepository.create(owner, "JARVIS Live call").id.also(onConversationOpened)
                if (userText.isNotBlank()) {
                    conversationRepository.addMessage(owner, activeId, "user", userText)
                }
                if (assistantText.isNotBlank()) {
                    conversationRepository.addMessage(owner, activeId, "assistant", assistantText)
                }
                messages = conversationRepository.messages(owner, activeId)
                if (assistantText.isNotBlank()) onUsage()
            }.onFailure {
                notice = it.message ?: "Could not save the Live call transcript."
            }
        }
    }

    // SEND MESSAGE: persist the user's turn, call the tutor, then persist JARVIS's reply.
    fun sendMessage() {
        val cleanPrompt = prompt.trim()
        val owner = userId

        if (cleanPrompt.isEmpty() || sending || recording || transcribing) return
        if (owner.isNullOrBlank()) {
            notice = "Sign in to use JARVIS and save conversations to your account."
            return
        }

        scope.launch {
            sending = true
            notice = null

            var activeConversationId: String? = null
            try {
                val ownedConversation = selectedConversationId?.let { id ->
                    conversationRepository.list(owner).firstOrNull { it.id == id }
                }

                val conversationId = ownedConversation?.id
                    ?: conversationRepository.create(owner, cleanPrompt.take(70)).id.also(onConversationOpened)
                activeConversationId = conversationId

                // Save the user message first so it is not lost if the AI service is temporarily unavailable.
                conversationRepository.addMessage(owner, conversationId, "user", cleanPrompt)
                prompt = ""

                val priorTurns = messages.map {
                    com.flexi.jarvis.network.TutorTurn(it.role, it.content)
                } + com.flexi.jarvis.network.TutorTurn("user", cleanPrompt)

                val reply = aiTutorApi.ask(
                    com.flexi.jarvis.network.TutorRequest(
                        conversationId = conversationId,
                        message = cleanPrompt,
                        tutorName = tutorName,
                        languageCode = languageCode,
                        webSearch = webSearch,
                        history = priorTurns
                    )
                ).getOrThrow()

                conversationRepository.addMessage(
                    owner,
                    conversationId,
                    "assistant",
                    reply.text,
                    reply.citations
                )

                messages = conversationRepository.messages(owner, conversationId)
                selectedMedia?.let {
                    notice = "Text response received. The selected attachment still needs upload support."
                }
                selectedMedia = null
                onUsage()
            } catch (error: Exception) {
                if (activeConversationId != null) {
                    runCatching {
                        messages = conversationRepository.messages(owner, activeConversationId)
                    }
                }
                notice = error.message ?: "JARVIS could not respond. Your message may already be saved; please try again."
            } finally {
                sending = false
            }
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

            IconButton(onClick = {
                if (recording) stopRecordingAndTranscribe()
                onNewConversation()
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New conversation",
                    tint = colors.primary
                )
            }

            Surface(
                shape = CircleShape,
                color = colors.primaryContainer
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = {
                    if (userId.isNullOrBlank()) {
                        notice = "Sign in before starting a JARVIS Live call."
                    } else {
                        liveCallOpen = true
                    }
                }
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Live voice call")
            }
        }

        if (liveCallOpen) {
            GeminiLiveCallDialog(
                tutorName = tutorName,
                languageCode = languageCode,
                tokenApi = geminiLiveTokenApi,
                onCompletedTurn = ::saveLiveTurn,
                onDismiss = { liveCallOpen = false }
            )
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                        border = BorderStroke(
                            1.dp,
                            colors.outlineVariant
                        ),
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

                                RichMarkdownText(
                                    content = message.content,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (notice != null) {
            Text(
                notice!!,
                color = if (
                    notice!!.startsWith("Recording") ||
                    notice!!.startsWith("Uploading") ||
                    notice!!.startsWith("Transcription added") ||
                    notice!!.startsWith("Photo or video selected") ||
                    notice!!.startsWith("Photo captured") ||
                    notice!!.startsWith("File selected")
                ) colors.primary else colors.error,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            )
        }

        if (messages.isNotEmpty() && !loading) {
            Text(
                "Your conversations are saved to your account.",
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 5.dp)
            )
        }

        // SELECTED ATTACHMENT PREVIEW
        selectedMedia?.let { uri ->
            Surface(
                color = colors.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp)
            ) {
                Row(
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.onSecondaryContainer
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        uri.lastPathSegment?.substringAfterLast('/')
                            ?: "Selected attachment",
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

        // MESSAGE COMPOSER
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

                // ATTACHMENT MENU
                Box {
                    IconButton(
                        onClick = {
                            attachmentMenuExpanded = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add attachment",
                            tint = colors.primary
                        )
                    }

                    DropdownMenu(
                        expanded = attachmentMenuExpanded,
                        onDismissRequest = {
                            attachmentMenuExpanded = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Camera") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                launchCamera()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Photos") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false

                                mediaPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Files") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                documentPicker.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }

                BasicTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp, max = 110.dp)
                        .padding(
                            horizontal = 7.dp,
                            vertical = 10.dp
                        ),
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

                // MICROPHONE
                IconButton(
                    enabled = !transcribing,
                    onClick = {
                        if (recording) {
                            stopRecordingAndTranscribe()

                        } else if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startRecording()

                        } else {
                            microphonePermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }
                ) {
                    if (transcribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(21.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                    } else {
                        VectorMicIcon(
                            tint = if (recording) {
                                colors.error
                            } else {
                                colors.primary
                            },
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                // SEND
                FilledIconButton(
                    onClick = ::sendMessage,
                    enabled = prompt.isNotBlank() &&
                        !sending &&
                        !recording &&
                        !transcribing,
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
private fun RichMarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val bodyColor = colors.onSurfaceVariant.toArgb()
    val linkColor = colors.primary.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                textSize = 14f
                setTextColor(bodyColor)
                setLinkTextColor(linkColor)
                movementMethod = LinkMovementMethod.getInstance()
                includeFontPadding = true
            }
        },
        update = { textView ->
            textView.setTextColor(bodyColor)
            textView.setLinkTextColor(linkColor)
            markwon.setMarkdown(textView, content)
        }
    )
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
            topLeft = Offset(
                size.width * 0.37f,
                size.height * 0.08f
            ),
            size = Size(
                size.width * 0.26f,
                size.height * 0.53f
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                size.width * 0.14f
            )
        )

        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(
                size.width * 0.20f,
                size.height * 0.28f
            ),
            size = Size(
                size.width * 0.60f,
                size.height * 0.53f
            ),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )

        drawLine(
            color = tint,
            start = Offset(
                size.width * 0.50f,
                size.height * 0.80f
            ),
            end = Offset(
                size.width * 0.50f,
                size.height * 0.94f
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = tint,
            start = Offset(
                size.width * 0.34f,
                size.height * 0.94f
            ),
            end = Offset(
                size.width * 0.66f,
                size.height * 0.94f
            ),
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
            Icon(
                icon,
                null,
                tint = colors.primary
            )

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
