package com.flexi.jarvis.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.flexi.jarvis.live.JarvisScreenShareService
import com.flexi.jarvis.live.LiveCameraPublisher
import com.flexi.jarvis.live.ScreenShareFrameBus
import com.flexi.jarvis.network.AiTutorApi
import com.flexi.jarvis.network.GeminiLiveTokenApi
import com.flexi.jarvis.network.GeminiTtsApi
import com.flexi.jarvis.network.SpeechTranscriptionApi
import com.flexi.jarvis.network.TutorRequest
import com.flexi.jarvis.network.TutorTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

private val CallPurple = Color(0xFF9B5CFF)
private const val CALL_SAMPLE_RATE = 16_000
private const val TTS_VOICE_NAME = "Charon"

/**
 * Cascaded voice call: microphone -> existing Cloudflare Whisper STT -> Gemini text
 * -> Gemini TTS -> AudioTrack. Camera and screen frames use the separate /vision route.
 */
@Composable
fun VoiceCallScreen(
    tutorName: String,
    languageCode: String,
    webSearch: Boolean,
    initialHistory: List<TutorTurn>,
    speechApi: SpeechTranscriptionApi,
    aiTutorApi: AiTutorApi,
    ttsApi: GeminiTtsApi,
    visionApi: GeminiLiveTokenApi,
    onCompletedTurn: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val active = remember { AtomicBoolean(false) }
    val mutedFlag = remember { AtomicBoolean(false) }
    val latestVision = remember { AtomicReference("") }
    val lastVisionAt = remember { AtomicLong(0L) }
    val audioTrackRef = remember { AtomicReference<AudioTrack?>(null) }
    val utterances = remember { Channel<File>(Channel.UNLIMITED) }
    val history = remember { initialHistory.toMutableList() }

    var callActive by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var cameraEnabled by remember { mutableStateOf(false) }
    var screenSharing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("A natural voice conversation with JARVIS.") }
    var userTurn by remember { mutableStateOf("") }
    var jarvisTurn by remember { mutableStateOf("") }
    var visualStatus by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var showVolume by remember { mutableStateOf(false) }
    var cameraPublisher by remember { mutableStateOf<LiveCameraPublisher?>(null) }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    var processingJob by remember { mutableStateOf<Job?>(null) }
    var previousAudioMode by remember { mutableStateOf<Int?>(null) }
    var previousSpeakerphoneOn by remember { mutableStateOf<Boolean?>(null) }

    fun stopPlayback() {
        val track = audioTrackRef.getAndSet(null) ?: return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    fun endCall() {
        active.set(false)
        callActive = false
        connecting = false
        speaking = false
        captureJob?.cancel()
        captureJob = null
        processingJob?.cancel()
        processingJob = null
        stopPlayback()
        cameraPublisher?.stop()
        cameraPublisher = null
        cameraEnabled = false
        if (screenSharing) {
            context.startService(Intent(context, JarvisScreenShareService::class.java).setAction(JarvisScreenShareService.ACTION_STOP))
        }
        screenSharing = false
        ScreenShareFrameBus.onFrame = null
        ScreenShareFrameBus.onStopped = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
        previousSpeakerphoneOn = null
        previousAudioMode?.let { audioManager.mode = it }
        previousAudioMode = null
        utterances.close()
        onDismiss()
    }

    suspend fun playGeneratedSpeech(text: String) {
        val generated = ttsApi.synthesize(text, TTS_VOICE_NAME)
        withContext(Dispatchers.IO) {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(generated.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val minBuffer = AudioTrack.getMinBufferSize(
                generated.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuffer, minOf(generated.pcm16.size, 64 * 1024)))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrackRef.set(track)
            track.setVolume(volume)
            track.play()
            var offset = 0
            while (offset < generated.pcm16.size && active.get() && audioTrackRef.get() === track) {
                val written = track.write(
                    generated.pcm16,
                    offset,
                    minOf(8192, generated.pcm16.size - offset),
                    AudioTrack.WRITE_BLOCKING
                )
                if (written < 0) throw IllegalStateException("Audio playback failed ($written).")
                offset += written
            }
            val expectedFrames = generated.pcm16.size / 2
            while (active.get() && audioTrackRef.get() === track && track.playbackHeadPosition.toLong() < expectedFrames) {
                delay(35)
            }
            if (audioTrackRef.compareAndSet(track, null)) {
                runCatching { track.stop() }
                track.release()
            } else {
                runCatching { track.release() }
            }
        }
    }

    fun analyseFrame(frame: ByteArray, source: String) {
        if (!active.get() || frame.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val previous = lastVisionAt.get()
        if (now - previous < 3_000L || !lastVisionAt.compareAndSet(previous, now)) return
        scope.launch {
            runCatching { visionApi.analyzeVideoFrame(frame) }
                .onSuccess { description ->
                    latestVision.set("$source: $description")
                    visualStatus = "JARVIS can see your $source."
                }
                .onFailure {
                    error = it.message ?: "Could not analyse the $source frame."
                }
        }
    }

    suspend fun processUtterance(file: File) {
        try {
            val transcript = speechApi.transcribe(file).trim()
            if (transcript.isBlank() || !active.get()) return
            userTurn = transcript
            status = "Thinking..."
            val visual = latestVision.get()
            val enriched = if (visual.isBlank()) transcript else {
                "$transcript\n\n[Current visual context from the $visual]"
            }
            val prior = history.toList()
            val reply = aiTutorApi.ask(
                TutorRequest(
                    conversationId = null,
                    message = enriched,
                    tutorName = tutorName,
                    languageCode = languageCode,
                    webSearch = webSearch,
                    history = prior + TutorTurn("user", enriched)
                )
            ).getOrThrow()
            if (!active.get()) return
            jarvisTurn = reply.text
            history += TutorTurn("user", transcript)
            history += TutorTurn("assistant", reply.text)
            onCompletedTurn(transcript, reply.text)
            status = "JARVIS is speaking..."
            speaking = true
            try {
                playGeneratedSpeech(reply.text)
            } finally {
                speaking = false
            }
            if (active.get()) status = "Listening — speak naturally."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "JARVIS could not complete that voice turn."
            if (active.get()) status = "Listening — please try again."
        } finally {
            file.delete()
        }
    }

    fun beginCall() {
        if (active.get() || connecting) return
        connecting = true
        error = null
        status = "Preparing your voice conversation..."
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        previousAudioMode = audioManager.mode
        previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        val connectedHeadset = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (!connectedHeadset) audioManager.isSpeakerphoneOn = true
        active.set(true)
        callActive = true
        connecting = false
        status = "Listening — speak naturally."
        processingJob = scope.launch {
            for (file in utterances) {
                if (!active.get()) {
                    file.delete()
                    break
                }
                processUtterance(file)
            }
        }
        captureJob = scope.launch(Dispatchers.IO) {
            val chunkSamples = 320 // 20 ms at 16 kHz
            val minBytes = AudioRecord.getMinBufferSize(
                CALL_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBytes <= 0) {
                error = "This device does not support microphone capture."
                active.set(false)
                return@launch
            }
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(CALL_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBytes, chunkSamples * 2 * 8))
                .build()
            val echoCanceler = runCatching { AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
            val noiseSuppressor = runCatching { NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true } }.getOrNull()
            val samples = ShortArray(chunkSamples)
            val preRoll = ArrayDeque<ShortArray>()
            val segment = ByteArrayOutputStream()
            var inSpeech = false
            var silenceMs = 0
            var speechSamples = 0
            var levelTicks = 0
            try {
                recorder.startRecording()
                while (isActive && active.get()) {
                    val count = recorder.read(samples, 0, samples.size)
                    if (count <= 0) continue
                    var sum = 0.0
                    for (i in 0 until count) {
                        val sample = samples[i].toDouble()
                        sum += sample * sample
                    }
                    val rms = sqrt(sum / count).toFloat()
                    if (levelTicks++ % 5 == 0) {
                        withContext(Dispatchers.Main) {
                            audioLevel = (rms / 12_000f).coerceIn(0f, 1f)
                        }
                    }

                    if (mutedFlag.get()) {
                        if (inSpeech) {
                            inSpeech = false
                            segment.reset()
                            speechSamples = 0
                        }
                        preRoll.clear()
                        continue
                    }

                    val voiced = rms > 650f
                    if (!inSpeech && voiced) {
                        inSpeech = true
                        silenceMs = 0
                        speechSamples = 0
                        while (preRoll.isNotEmpty()) {
                            val old = preRoll.removeFirst()
                            writePcm16(segment, old, old.size)
                            speechSamples += old.size
                        }
                        stopPlayback()
                        withContext(Dispatchers.Main) {
                            speaking = false
                            status = "Listening to you..."
                        }
                    }

                    if (inSpeech) {
                        writePcm16(segment, samples, count)
                        speechSamples += count
                        if (voiced) silenceMs = 0 else silenceMs += 20
                        if (silenceMs >= 850) {
                            val pcm = segment.toByteArray()
                            if (speechSamples >= CALL_SAMPLE_RATE / 3) {
                                val file = File(context.cacheDir, "jarvis-call-${System.currentTimeMillis()}.wav")
                                writeWav(file, pcm, CALL_SAMPLE_RATE)
                                utterances.send(file)
                            }
                            segment.reset()
                            inSpeech = false
                            silenceMs = 0
                            speechSamples = 0
                            preRoll.clear()
                        }
                    } else {
                        preRoll.addLast(samples.copyOf(count))
                        while (preRoll.size > 12) preRoll.removeFirst()
                    }
                }
            } catch (failure: Exception) {
                if (active.get()) withContext(Dispatchers.Main) {
                    error = failure.message ?: "Microphone capture stopped unexpectedly."
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
                runCatching { echoCanceler?.release() }
                runCatching { noiseSuppressor?.release() }
            }
        }
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginCall() else error = "Microphone permission is required for a voice call."
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraEnabled = true else error = "Camera permission is required for video calls."
    }
    val screenSharePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            ScreenShareFrameBus.onFrame = { frame -> analyseFrame(frame, "shared screen") }
            ScreenShareFrameBus.onStopped = {
                scope.launch {
                    screenSharing = false
                    status = "Screen sharing stopped."
                }
            }
            val serviceIntent = Intent(context, JarvisScreenShareService::class.java)
                .putExtra(JarvisScreenShareService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(JarvisScreenShareService.EXTRA_RESULT_DATA, result.data)
            ContextCompat.startForegroundService(context, serviceIntent)
            screenSharing = true
            status = "JARVIS is viewing your screen."
        } else error = "Screen sharing permission was not granted."
    }

    LaunchedEffect(cameraEnabled, callActive) {
        if (cameraEnabled && callActive && cameraPublisher == null) {
            val owner = context as? LifecycleOwner
            if (owner == null) {
                error = "Camera preview is unavailable in this activity."
                cameraEnabled = false
            } else {
                val publisher = LiveCameraPublisher(
                    context = context,
                    lifecycleOwner = owner,
                    previewView = previewView,
                    onJpegFrame = { frame -> analyseFrame(frame, "camera") },
                    onError = { message -> error = message; cameraEnabled = false }
                )
                cameraPublisher = publisher
                publisher.start()
            }
        } else if ((!cameraEnabled || !callActive) && cameraPublisher != null) {
            cameraPublisher?.stop()
            cameraPublisher = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            captureJob?.cancel()
            processingJob?.cancel()
            stopPlayback()
            cameraPublisher?.stop()
            ScreenShareFrameBus.onFrame = null
            ScreenShareFrameBus.onStopped = null
            if (screenSharing) {
                context.startService(Intent(context, JarvisScreenShareService::class.java).setAction(JarvisScreenShareService.ACTION_STOP))
            }
            utterances.close()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
            previousAudioMode?.let { audioManager.mode = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080611), Color(0xFF10091E), Color(0xFF080611))))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::endCall) { Text("Close", color = Color.LightGray) }
            Spacer(Modifier.weight(1f))
            Text("JARVIS VOICE", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.VolumeUp, contentDescription = "Voice output", tint = CallPurple)
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(176.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(CallPurple.copy(alpha = 0.55f), Color(0xFF211039), Color.Transparent))
            ),
            contentAlignment = Alignment.Center
        ) {
            Surface(Modifier.size(104.dp), shape = CircleShape, color = Color(0xFF160D27)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("J", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(tutorName, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            when {
                connecting -> "Preparing..."
                speaking -> "JARVIS is speaking"
                callActive && audioLevel > 0.035f -> "Listening to you"
                callActive -> "Listening"
                else -> "Voice conversation"
            },
            color = Color(0xFFC9B7E8),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))
        Text(status, color = Color(0xFFB9AFC8), fontSize = 12.sp)
        if (cameraEnabled) {
            Spacer(Modifier.height(12.dp))
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.large))
            TextButton(onClick = { cameraPublisher?.switchCamera() }) {
                Text("Switch front / rear camera", color = Color(0xFFD8C5FF))
            }
        }
        if (visualStatus.isNotBlank()) {
            Text(visualStatus, color = Color(0xFFB8A0E8), fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LIVE TRANSCRIPT", color = CallPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(userTurn.ifBlank { "Your words will appear here..." }, color = Color.White.copy(alpha = if (userTurn.isBlank()) 0.4f else 0.95f), fontSize = 14.sp)
                Text(jarvisTurn.ifBlank { "JARVIS's response will appear here..." }, color = Color(0xFFD8C5FF).copy(alpha = if (jarvisTurn.isBlank()) 0.4f else 1f), fontSize = 14.sp)
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (showVolume) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, null, tint = Color.White)
                Slider(value = volume, onValueChange = { volume = it; audioTrackRef.get()?.setVolume(it) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showVolume = !showVolume }) {
                Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color.White)
            }
            Surface(shape = CircleShape, color = if (muted) Color(0xFF5A2630) else Color(0xFF302044)) {
                IconButton(onClick = { muted = !muted; mutedFlag.set(muted) }) {
                    Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute microphone", tint = Color.White)
                }
            }
            Surface(shape = CircleShape, color = if (cameraEnabled) CallPurple else Color(0xFF302044)) {
                IconButton(onClick = {
                    if (cameraEnabled) {
                        cameraEnabled = false
                        cameraPublisher?.stop()
                        cameraPublisher = null
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        if (!callActive) error = "Start the voice call before enabling the camera." else cameraEnabled = true
                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Toggle camera", tint = Color.White)
                }
            }
            Surface(shape = CircleShape, color = if (screenSharing) CallPurple else Color(0xFF302044)) {
                IconButton(onClick = {
                    if (screenSharing) {
                        context.startService(Intent(context, JarvisScreenShareService::class.java).setAction(JarvisScreenShareService.ACTION_STOP))
                        screenSharing = false
                    } else if (!callActive) {
                        error = "Start the voice call before sharing your screen."
                    } else {
                        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenSharePermission.launch(manager.createScreenCaptureIntent())
                    }
                }) {
                    Icon(Icons.Default.ScreenShare, contentDescription = "Share screen", tint = Color.White)
                }
            }
            IconButton(onClick = { endCall() }) {
                Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color(0xFFFF7777))
            }
        }
        if (!callActive) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        beginCall()
                    } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start voice call") }
        }
    }
}

private fun writePcm16(output: ByteArrayOutputStream, samples: ShortArray, count: Int) {
    for (index in 0 until count) {
        val value = samples[index].toInt()
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }
}

private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
    FileOutputStream(file).use { stream ->
        val out = DataOutputStream(stream)
        val byteRate = sampleRate * 2
        out.writeBytes("RIFF")
        writeLeInt(out, 36 + pcm.size)
        out.writeBytes("WAVE")
        out.writeBytes("fmt ")
        writeLeInt(out, 16)
        writeLeShort(out, 1)
        writeLeShort(out, 1)
        writeLeInt(out, sampleRate)
        writeLeInt(out, byteRate)
        writeLeShort(out, 2)
        writeLeShort(out, 16)
        out.writeBytes("data")
        writeLeInt(out, pcm.size)
        out.write(pcm)
    }
}

private fun writeLeInt(out: DataOutputStream, value: Int) {
    out.writeByte(value and 0xff)
    out.writeByte((value ushr 8) and 0xff)
    out.writeByte((value ushr 16) and 0xff)
    out.writeByte((value ushr 24) and 0xff)
}

private fun writeLeShort(out: DataOutputStream, value: Int) {
    out.writeByte(value and 0xff)
    out.writeByte((value ushr 8) and 0xff)
}
