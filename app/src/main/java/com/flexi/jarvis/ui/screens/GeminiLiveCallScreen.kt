package com.flexi.jarvis.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.flexi.jarvis.live.GeminiLiveSession
import com.flexi.jarvis.network.GeminiLiveTokenApi
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
    tokenApi: GeminiLiveTokenApi,
    onCompletedTurn: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var session by remember { mutableStateOf<GeminiLiveSession?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Your private, real-time conversation with JARVIS.") }
    var error by remember { mutableStateOf<String?>(null) }
    var userTurn by remember { mutableStateOf("") }
    var jarvisTurn by remember { mutableStateOf("") }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var outputDevice by remember { mutableStateOf("Phone speaker") }
    var volume by remember { mutableFloatStateOf(currentVolume) }
    var showVolume by remember { mutableStateOf(false) }

    val previousAudioMode = remember { audioManager.mode }
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
            else -> "Phone speaker"
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
            session?.close()
            session = null
        }
    }

    fun beginCall() {
        if (connecting || connected) return
        connecting = true
        error = null
        status = "Connecting securely to JARVIS..."
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        refreshAudioRoute()
        scope.launch {
            runCatching { tokenApi.create(tutorName, languageCode) }
                .onSuccess { liveToken ->
                    val live = GeminiLiveSession(context, liveToken, object : GeminiLiveSession.Listener {
                        override fun onConnected() {
                            mainHandler.post {
                                connecting = false
                                connected = true
                                status = "Listening — speak naturally. You can interrupt JARVIS."
                                session?.startMicrophone()
                            }
                        }
                        override fun onInputTranscript(text: String) {
                            mainHandler.post { userTurn = (userTurn + " " + text).trim() }
                        }
                        override fun onOutputTranscript(text: String) {
                            mainHandler.post { jarvisTurn = (jarvisTurn + " " + text).trim() }
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
                            }
                        }
                        override fun onInterrupted() {
                            mainHandler.post { status = "JARVIS was interrupted. Go ahead." }
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
                    error = it.message ?: "Could not start the Live call."
                    status = "Connection failed."
                }
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginCall() else error = "Microphone permission is required for a voice call."
    }

    fun endCall() {
        session?.close()
        session = null
        connected = false
        connecting = false
        audioManager.mode = previousAudioMode
        onDismiss()
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

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .size(196.dp)
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
                    .size((142 * pulse).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFDCB8FF), JarvisPurple, JarvisViolet, Color(0xFF211039))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    color = Color(0xFF160D27),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("J", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(tutorName, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            when {
                connecting -> "Connecting..."
                connected && audioLevel > 0.08f -> "Listening to you"
                connected -> "Connected"
                else -> "Voice conversation"
            },
            color = Color(0xFFC9B7E8),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(18.dp))
        PurpleAudioWaveform(level = audioLevel, active = connected || connecting, modifier = Modifier.fillMaxWidth().height(76.dp))
        Text(status, color = Color(0xFFB9AFC8), fontSize = 12.sp)

        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("LIVE TRANSCRIPT", color = JarvisPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(
                    userTurn.ifBlank { "Your words will appear here..." },
                    color = Color.White.copy(alpha = if (userTurn.isBlank()) 0.4f else 0.95f),
                    fontSize = 14.sp
                )
                Text(
                    jarvisTurn.ifBlank { "JARVIS's response will appear here..." },
                    color = Color(0xFFD8C5FF).copy(alpha = if (jarvisTurn.isBlank()) 0.4f else 1f),
                    fontSize = 14.sp
                )
            }
        }

        error?.let {
            Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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

        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(18.dp))
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

private const val fiftyBars =  fiftyBarsValue
private const val fiftyBarsValue =  fiftyBarsCount
private const val fiftyBarsCount =   fiftyBarsFinal
private const val fiftyBarsFinal =  fiftyBarsNumber
private const val fiftyBarsNumber =  48
