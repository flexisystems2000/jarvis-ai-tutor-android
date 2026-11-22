package com.flexi.jarvis.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.flexi.jarvis.live.LiveCameraPublisher
import com.flexi.jarvis.live.ScreenShareFrameBus
import com.flexi.jarvis.live.JarvisScreenShareService
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flexi.jarvis.live.LiveSessionClient
import com.flexi.jarvis.network.LiveSessionApi
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val JarvisPurple = Color(0xFF9B5CFF)
private val JarvisViolet = Color(0xFF6D28D9)

@Composable
fun GeminiLiveCallScreen(
    tutorName: String,
    languageCode: String,
    liveApi: LiveSessionApi,
    onCompletedTurn: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var session by remember { mutableStateOf<LiveSessionClient?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var assistantSpeaking by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Your private, real-time conversation with JARVIS.") }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf(listOf("Live diagnostics ready.")) }
    var userTurn by remember { mutableStateOf("") }
    var jarvisTurn by remember { mutableStateOf("") }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var outputDevice by remember { mutableStateOf("Phone speaker") }
    var volume by remember { mutableFloatStateOf(1f) }
    var showVolume by remember { mutableStateOf(false) }
    var callEnded by remember { mutableStateOf(false) }
    var aiEndCallHandled by remember { mutableStateOf(false) }
    var cameraEnabled by remember { mutableStateOf(false) }
    var screenSharing by remember { mutableStateOf(false) }
    var startVideoWhenConnected by remember { mutableStateOf(false) }
    var startScreenShareWhenConnected by remember { mutableStateOf(false) }
    var cameraPublisher by remember { mutableStateOf<LiveCameraPublisher?>(null) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Short native call tones: a WhatsApp-style outgoing ringback on connect,
    // followed by a brief disconnect tone whenever the call ends (manual or AI).
    fun playCallTone(toneType: Int, durationMs: Long) {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_RING, 78)
            tone.startTone(toneType, durationMs.toInt())
            mainHandler.postDelayed({ runCatching { tone.release() } }, durationMs + 120L)
        }
    }

    val previousAudioMode = remember { audioManager.mode }
    val previousSpeakerphoneOn = remember { audioManager.isSpeakerphoneOn }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1) }
    val minVolume = remember { if (android.os.Build.VERSION.SDK_INT >= 28) audioManager.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL) else 0 }
    val currentVolume = remember {
        audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL).toFloat() / maxVolume.toFloat()
    }

    fun refreshAudioRoute() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val selected = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        } ?: outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        outputDevice = when (selected?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth audio"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> "Wired earphones"
            else -> if (audioManager.isSpeakerphoneOn) "Phone speaker" else "Phone earpiece"
        }
    }

    val deviceCallback = remember {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshAudioRoute()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshAudioRoute()
        }
    }

    DisposableEffect(Unit) {
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        refreshAudioRoute()
        onDispose {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            audioManager.mode = previousAudioMode
            audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
            session?.close()
            session = null
        }
    }

    fun endCall() {
        if (callEnded) return
        callEnded = true
        playCallTone(ToneGenerator.TONE_PROP_PROMPT, 220L)
        cameraPublisher?.stop()
        cameraPublisher = null
        cameraEnabled = false
        if (screenSharing) context.startService(Intent(context, JarvisScreenShareService::class.java).setAction(JarvisScreenShareService.ACTION_STOP))
        screenSharing = false
        ScreenShareFrameBus.onFrame = null
        ScreenShareFrameBus.onStopped = null
        session?.close()
        session = null
        connected = false
        connecting = false
        audioManager.mode = previousAudioMode
        audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
        onDismiss()
    }

    fun beginCall() {
        if (connecting || connected || callEnded) return
        connecting = true
        playCallTone(ToneGenerator.TONE_SUP_RINGTONE, 900L)
        error = null
        diagnostics = listOf("Starting JARVIS Live connection.", "Requesting a secure live session from Cloudflare.")
        status = "Connecting securely to JARVIS..."
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        refreshAudioRoute()
        // Prefer connected headsets; otherwise use the phone loudspeaker.
        // The LiveSessionClient enables Android acoustic echo cancellation and
        // noise suppression for the VOICE_COMMUNICATION microphone source.
        val hasExternalHeadset = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        audioManager.isSpeakerphoneOn = !hasExternalHeadset
        if (!hasExternalHeadset) {
            val maximumCallVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maximumCallVolume, 0)
            volume = 1f
            outputDevice = "Phone speaker"
            diagnostics = diagnostics + "Loudspeaker enabled at maximum supported call volume."
        } else {
            refreshAudioRoute()
            diagnostics = diagnostics + "Using connected headset audio output."
        }
        scope.launch {
            runCatching { liveApi.create(tutorName, languageCode) }
                .onSuccess { liveConfig ->
                    diagnostics = diagnostics + "Cloudflare connection configuration received; provider credentials remain server-side."
                    val live = LiveSessionClient(context, liveConfig, object : LiveSessionClient.Listener {
                        override fun onDiagnostic(message: String) {
                            mainHandler.post { diagnostics = (diagnostics + message).takeLast(30) }
                        }
                        override fun onConnected() {
                            mainHandler.post {
                                diagnostics = (diagnostics + "Live session established; microphone starting.").takeLast(30)
                                connecting = false
                                connected = true
                                assistantSpeaking = false
                                status = "Live conversation • Speak naturally."
                                session?.startMicrophone()
                            }
                        }
                        override fun onInputTranscript(text: String) {
                            mainHandler.post {
                                userTurn = (userTurn + " " + text).trim()
                            }
                        }
                        override fun onEndCallRequested(reason: String) {
                            mainHandler.post {
                                if (aiEndCallHandled || callEnded) return@post
                                aiEndCallHandled = true
                                val finalUserText = userTurn.trim()
                                val finalAssistantText = jarvisTurn.trim()
                                if (finalUserText.isNotBlank() || finalAssistantText.isNotBlank()) {
                                    onCompletedTurn(finalUserText, finalAssistantText)
                                }
                                status = "JARVIS is ending the call."
                                endCall()
                            }
                        }
                        override fun onOutputTranscript(text: String) {
                            mainHandler.post {
                                assistantSpeaking = true
                                jarvisTurn = (jarvisTurn + " " + text).trim()
                            }
                        }
                        override fun onAudioLevel(level: Float) {
                            mainHandler.post { audioLevel = level.coerceIn(0f, 1f) }
                        }
                        override fun onTurnComplete() {
                            mainHandler.post {
                                val userText = userTurn.trim()
                                val assistantText = jarvisTurn.trim()
                                if (userText.isNotBlank() || assistantText.isNotBlank()) {
                                    onCompletedTurn(userText, assistantText)
                                }
                                userTurn = ""
                                jarvisTurn = ""
                                assistantSpeaking = false
                                status = "Live conversation • Speak naturally."
                            }
                        }
                        override fun onInterrupted() {
                            mainHandler.post {
                                assistantSpeaking = false
                                status = "Live conversation • Speak naturally."
                            }
                        }
                        override fun onError(message: String) {
                            mainHandler.post {
                                error = message
                                connecting = false
                                if (!connected) status = "Unable to connect."
                            }
                        }
                        override fun onClosed() {
                            mainHandler.post {
                                connected = false
                                connecting = false
                                status = "Call ended."
                            }
                        }
                    })
                    session = live
                    live.connect()
                }
                .onFailure {
                    connecting = false
                    val detail = "${it.javaClass.simpleName}: ${(it.message ?: "no error message").take(300)}"
                    diagnostics = (diagnostics + "Token request failed: $detail").takeLast(30)
                    error = detail
                    status = "Connection failed."
                }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraEnabled = true else error = "Camera permission is required for video calls."
    }

    val screenSharePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            ScreenShareFrameBus.onFrame = { frame -> session?.sendVideoFrame(frame) }
            ScreenShareFrameBus.onStopped = {
                mainHandler.post {
                    screenSharing = false
                    status = "Screen sharing stopped."
                }
            }
            val serviceIntent = Intent(context, JarvisScreenShareService::class.java)
                .putExtra(JarvisScreenShareService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(JarvisScreenShareService.EXTRA_RESULT_DATA, result.data)
            ContextCompat.startForegroundService(context, serviceIntent)
            screenSharing = true
            status = "Sharing your screen with JARVIS."
        } else {
            error = "Screen sharing permission was not granted."
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginCall() else error = "Microphone permission is required for a voice call."
    }

    // Video and screen sharing require an active Gemini Live session. If either
    // control is tapped before the call starts, establish the voice session first.
    LaunchedEffect(connected, startVideoWhenConnected) {
        if (connected && startVideoWhenConnected) {
            startVideoWhenConnected = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraEnabled = true
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    LaunchedEffect(connected, startScreenShareWhenConnected) {
        if (connected && startScreenShareWhenConnected) {
            startScreenShareWhenConnected = false
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenSharePermission.launch(manager.createScreenCaptureIntent())
        }
    }

    LaunchedEffect(cameraEnabled, connected) {
        if (cameraEnabled && connected && cameraPublisher == null) {
            val owner = context as? LifecycleOwner
            if (owner == null) {
                error = "Camera preview is unavailable in this activity."
                cameraEnabled = false
            } else {
                val publisher = LiveCameraPublisher(
                    context = context,
                    lifecycleOwner = owner,
                    previewView = previewView,
                    onJpegFrame = { frame -> session?.sendVideoFrame(frame) },
                    onError = { message -> mainHandler.post { error = message; cameraEnabled = false } }
                )
                cameraPublisher = publisher
                publisher.start()
            }
        } else if ((!cameraEnabled || !connected) && cameraPublisher != null) {
            cameraPublisher?.stop()
            cameraPublisher = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraPublisher?.stop()
            ScreenShareFrameBus.onFrame = null
            ScreenShareFrameBus.onStopped = null
            if (screenSharing) {
                context.startService(Intent(context, JarvisScreenShareService::class.java).setAction(JarvisScreenShareService.ACTION_STOP))
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "jarvis-purple-wave")
    val pulse by infinite.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF080611), Color(0xFF10091E), Color(0xFF080611))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = ::endCall) { Text("Close", color = Color.LightGray) }
            Spacer(Modifier.weight(1f))
            Text("JARVIS LIVE", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            when (outputDevice) {
                "Bluetooth audio" -> Icon(Icons.Default.Bluetooth, contentDescription = outputDevice, tint = JarvisPurple)
                "Wired earphones" -> Icon(Icons.Default.Headphones, contentDescription = outputDevice, tint = JarvisPurple)
                else -> Icon(Icons.Default.VolumeUp, contentDescription = outputDevice, tint = JarvisPurple)
            }
        }

        if (!cameraEnabled) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(JarvisPurple.copy(alpha = 0.35f), JarvisViolet.copy(alpha = 0.14f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size((112 * pulse).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFDCB8FF), JarvisPurple, JarvisViolet, Color(0xFF211039))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = CircleShape,
                        color = Color(0xFF160D27),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("J", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = CircleShape,
                    color = Color(0xCC160D27),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    IconButton(
                        onClick = { cameraPublisher?.switchCamera() },
                        enabled = connected,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Flip front or rear camera",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Text(tutorName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            when {
                connecting -> "Connecting..."
                connected && assistantSpeaking -> "JARVIS is speaking"
                connected && audioLevel > 0.08f -> "Listening to you"
                connected -> "Listening • Ready when you are"
                else -> "Voice conversation"
            },
            color = Color(0xFFC9B7E8),
            fontSize = 13.sp
        )
        PurpleAudioWaveform(
            level = audioLevel,
            active = connected || connecting,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )
        Text(status, color = Color(0xFFB9AFC8), fontSize = 11.sp)

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(112.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f))
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("LIVE TRANSCRIPT", color = JarvisPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(
                    userTurn.ifBlank { "Your words will appear here..." },
                    color = Color.White.copy(alpha = if (userTurn.isBlank()) 0.4f else 0.95f),
                    fontSize = 12.sp,
                    maxLines = 2
                )
                Text(
                    jarvisTurn.ifBlank { "JARVIS's response will appear here..." },
                    color = Color(0xFFD8C5FF).copy(alpha = if (jarvisTurn.isBlank()) 0.4f else 1f),
                    fontSize = 12.sp,
                    maxLines = 2
                )
            }
        }

        error?.let {
            Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f))
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CONNECTION DIAGNOSTICS", color = JarvisPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { clipboard.setText(AnnotatedString(diagnostics.joinToString("\n"))) }) {
                            Text("Copy diagnostics", color = Color(0xFFD8C5FF), fontSize = 11.sp)
                        }
                    }
                    diagnostics.takeLast(6).forEach { line ->
                        Text("• $line", color = Color(0xFFD6CCEA), fontSize = 10.sp)
                    }
                    Text("Diagnostic report excludes temporary tokens and authorization credentials.", color = Color.Gray, fontSize = 9.sp)
                }
            }
        }

        if (showVolume) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeDown, contentDescription = "Lower volume", tint = Color.White)
                Slider(
                    value = volume,
                    onValueChange = { next ->
                        volume = next
                        val target = (minVolume + (next * (maxVolume - minVolume))).toInt().coerceIn(minVolume, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, target, 0)
                    },
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.VolumeUp, contentDescription = "Higher volume", tint = Color.White)
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showVolume = !showVolume }) {
                Icon(
                    when (outputDevice) {
                        "Bluetooth audio" -> Icons.Default.Bluetooth
                        "Wired earphones" -> Icons.Default.Headphones
                        else -> Icons.Default.VolumeUp
                    },
                    contentDescription = "Output volume: $outputDevice",
                    tint = Color.White
                )
            }
            Surface(shape = CircleShape, color = if (cameraEnabled) JarvisPurple else Color(0xFF302044)) {
                IconButton(
                    onClick = {
                        if (cameraEnabled) {
                            cameraEnabled = false
                        } else if (!connected) {
                            startVideoWhenConnected = true
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                beginCall()
                            } else {
                                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraEnabled = true
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !cameraEnabled || connected
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = if (cameraEnabled) "Turn video off" else "Turn video on",
                        tint = Color.White
                    )
                }
            }
            Surface(shape = CircleShape, color = if (screenSharing) JarvisPurple else Color(0xFF302044)) {
                IconButton(onClick = {
                    if (screenSharing) {
                        context.startService(Intent(context, JarvisScreenShareService::class.java).setAction(JarvisScreenShareService.ACTION_STOP))
                        screenSharing = false
                        ScreenShareFrameBus.onFrame = null
                        status = "Screen sharing stopped."
                    } else if (!connected) {
                        startScreenShareWhenConnected = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            beginCall()
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenSharePermission.launch(manager.createScreenCaptureIntent())
                    }
                }) {
                    Icon(Icons.Default.ScreenShare, contentDescription = if (screenSharing) "Stop screen sharing" else "Share screen", tint = Color.White)
                }
            }
            Surface(shape = CircleShape, color = if (muted) Color(0xFF5A2630) else Color(0xFF302044)) {
                IconButton(onClick = {
                    if (muted) session?.startMicrophone() else session?.stopMicrophone()
                    muted = !muted
                    status = if (muted) "Microphone muted." else "Listening — speak naturally."
                }) {
                    Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = if (muted) "Unmute" else "Mute", tint = Color.White)
                }
            }
            Surface(shape = CircleShape, color = Color(0xFFD83B54)) {
                IconButton(onClick = ::endCall, modifier = Modifier.size(62.dp)) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (!connected && !connecting) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        beginCall()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start voice call") }
        } else if (connecting) {
            Text("Securing your connection...", color = Color(0xFFC9B7E8), fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PurpleAudioWaveform(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave-flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave-phase"
    )
    Canvas(modifier = modifier) {
        val count =  fiftyBars
        val centerY = size.height / 2f
        val gap = size.width / (count * 1.8f)
        val barWidth = gap * 0.62f
        for (i in 0 until count) {
            val position = i.toFloat() / (count - 1).toFloat()
            val envelope = 0.18f + 0.82f * abs(sin(position * PI)).toFloat()
            val flow = abs(sin(phase + i * 0.48f)).toFloat()
            val quiet = if (active) 0.12f else 0.045f
            val heightFactor = (quiet + (if (active) level * 0.9f else 0.08f) + flow * if (active) 0.32f else 0.08f) * envelope
            val barHeight = (size.height * heightFactor).coerceIn(4f, size.height * 0.92f)
            val x = i * gap + gap / 2f
            drawLine(
                brush = Brush.verticalGradient(listOf(Color(0xFFC9A1FF), JarvisPurple, JarvisViolet)),
                start = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2f),
                end = androidx.compose.ui.geometry.Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private const val fiftyBars = 48
