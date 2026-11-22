package com.flexi.jarvis.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.flexi.jarvis.network.GeminiLiveToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bidirectional Gemini Live transport. Call only after RECORD_AUDIO permission is granted.
 * Camera and MediaProjection producers can pass JPEG frames through sendVideoFrame().
 */
class GeminiLiveSession(
    private val context: Context,
    private val token: GeminiLiveToken,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected() {}
        fun onInputTranscript(text: String) {}
        fun onOutputTranscript(text: String) {}
        fun onAudioLevel(level: Float) {}
        fun onEndCallRequested(reason: String) {}
        fun onTurnComplete() {}
        fun onInterrupted() {}
        fun onError(message: String) {}
        fun onClosed() {}
        fun onResumptionHandle(handle: String) {}
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var track: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    fun connect() {
        val wsVersion = token.apiVersion.ifBlank { "v1alpha" }
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$wsVersion.GenerativeService.BidiGenerateContent?key=" +
            java.net.URLEncoder.encode(token.token, "UTF-8")
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val endCallParameters = JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", JSONObject().put(
                        "reason",
                        JSONObject()
                            .put("type", "STRING")
                            .put("description", "Brief explanation of why the user wants to end the call.")
                    ))
                    .put("required", org.json.JSONArray().put("reason"))

                val endCallDeclaration = JSONObject()
                    .put("name", "end_call")
                    .put(
                        "description",
                        "End the current JARVIS voice call only when the user clearly intends to finish or disconnect the conversation. Do not call this merely because the user mentions ending a call hypothetically or as part of another topic."
                    )
                    .put("parameters", endCallParameters)

                val functionDeclarations = org.json.JSONArray().put(endCallDeclaration)
                val tools = org.json.JSONArray().put(
                    JSONObject().put("functionDeclarations", functionDeclarations)
                )

                val generationConfig = JSONObject()
                    .put("responseModalities", org.json.JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put(
                                "prebuiltVoiceConfig",
                                JSONObject().put("voiceName", "Kore")
                            )
                        )
                    )

                val setupBody = JSONObject()
                    .put("model", "models/${token.model}")
                    .put("generationConfig", generationConfig)
                    .put("inputAudioTranscription", JSONObject())
                    .put("outputAudioTranscription", JSONObject())
                    .put("tools", tools)
                    .put("sessionResumption", JSONObject())

                val setup = JSONObject().put("setup", setupBody)
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.has("setupComplete")) {
                        listener.onConnected()
                        return
                    }
                    root.optJSONObject("sessionResumptionUpdate")
                        ?.optString("newHandle")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(listener::onResumptionHandle)

                    val content = root.optJSONObject("serverContent")
                    if (content != null) {
                    content.optJSONObject("inputTranscription")?.optString("text")
                        ?.takeIf { it.isNotBlank() }?.let(listener::onInputTranscript)
                    content.optJSONObject("outputTranscription")?.optString("text")
                        ?.takeIf { it.isNotBlank() }?.let(listener::onOutputTranscript)

                    if (content.optBoolean("interrupted")) {
                        stopPlayback()
                        listener.onInterrupted()
                    }

                    val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) {
                        for (index in 0 until parts.length()) {
                            val part = parts.optJSONObject(index) ?: continue
                            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                            val encoded = inline?.optString("data").orEmpty()
                            if (encoded.isNotBlank()) playPcm(Base64.decode(encoded, Base64.DEFAULT))
                        }
                    }
                    if (content.optBoolean("turnComplete")) listener.onTurnComplete()
                    }

                    val toolCalls = root.optJSONObject("toolCall")?.optJSONArray("functionCalls")
                    if (toolCalls != null) {
                        for (index in 0 until toolCalls.length()) {
                            val call = toolCalls.optJSONObject(index) ?: continue
                            val name = call.optString("name")
                            if (name == "end_call") {
                                val callId = call.optString("id")
                                val args = call.optJSONObject("args")
                                val reason = args?.optString("reason").orEmpty().ifBlank { "The user asked to finish the conversation." }
                                if (callId.isNotBlank()) {
                                    webSocket.send(JSONObject().put("toolResponse", JSONObject().put(
                                        "functionResponses", org.json.JSONArray().put(JSONObject()
                                            .put("id", callId)
                                            .put("name", "end_call")
                                            .put("response", JSONObject().put("status", "ending").put("reason", reason)))
                                    )).toString())
                                }
                                listener.onEndCallRequested(reason)
                            }
                        }
                    }

                } catch (error: Exception) {
                    listener.onError("Could not read the Live response: ${error.message ?: "unknown error"}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(response?.let { "Live connection failed (HTTP ${it.code})." }
                    ?: "Live connection failed: ${t.message ?: "network error"}")
                stopAudio()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopAudio()
                listener.onClosed()
            }
        })
    }

    fun startMicrophone() {
        if (closed.get() || !recording.compareAndSet(false, true)) return
        executor.execute {
            try {
                val sampleRate = 16_000
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                require(minBuffer > 0) { "This device does not support microphone capture." }
                val audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, 6_400))
                    .build()
                recorder = audioRecord
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)?.apply { enabled = true }
                }
                audioRecord.startRecording()
                val buffer = ByteArray(3_200)
                while (recording.get() && !closed.get()) {
                    val count = audioRecord.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        listener.onAudioLevel(pcmLevel(buffer, count))
                        val frame = Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP)
                        val message = JSONObject().put("realtimeInput", JSONObject().put(
                            "audio", JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", frame)
                        ))
                        socket?.send(message.toString())
                    }
                }
            } catch (error: Exception) {
                if (!closed.get()) listener.onError("Microphone error: ${error.message ?: "unknown error"}")
            } finally {
                recorder?.runCatching { stop() }
                recorder?.release()
                recorder = null
                recording.set(false)
                echoCanceler?.release()
                echoCanceler = null
                noiseSuppressor?.release()
                noiseSuppressor = null
            }
        }
    }

    /** Send one JPEG frame from CameraX or a MediaProjection ImageReader. */
    fun sendVideoFrame(jpeg: ByteArray) {
        if (closed.get() || jpeg.isEmpty()) return
        val encoded = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        socket?.send(JSONObject().put("realtimeInput", JSONObject().put(
            "video", JSONObject().put("mimeType", "image/jpeg").put("data", encoded)
        )).toString())
    }

    fun stopMicrophone() {
        recording.set(false)
        socket?.send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)).toString())
    }

    fun sendText(text: String) {
        if (text.isBlank() || closed.get()) return
        socket?.send(JSONObject().put("clientContent", JSONObject()
            .put("turns", org.json.JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", org.json.JSONArray().put(JSONObject().put("text", text)))))
            .put("turnComplete", true)).toString())
    }

    private fun playPcm(bytes: ByteArray) {
        if (bytes.isEmpty() || closed.get()) return
        playbackExecutor.execute {
            try {
                val sampleRate = 24_000
                val minimum = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minimum <= 0) return@execute
                var output = track
                if (output == null) {
                    output = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(minimum * 2, bytes.size * 2))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    track = output
                }
                if (playing.compareAndSet(false, true)) output.play()
                listener.onAudioLevel(pcmLevel(bytes, bytes.size))
                output.write(bytes, 0, bytes.size)
            } catch (error: Exception) {
                listener.onError("Audio playback error: ${error.message ?: "unknown error"}")
            }
        }
    }

    private fun pcmLevel(bytes: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sum = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < length) {
            val sample = ((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xFF)).toShort().toInt()
            val normalized = sample / 32768.0
            sum += normalized * normalized
            samples++
            index += 2
        }
        if (samples == 0) return 0f
        val rms = kotlin.math.sqrt(sum / samples).toFloat()
        return (rms * 4.5f).coerceIn(0f, 1f)
    }

    private fun stopPlayback() {
        playing.set(false)
        track?.runCatching { pause(); flush() }
    }

    private fun stopAudio() {
        stopMicrophone()
        stopPlayback()
        track?.release()
        track = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        recording.set(false)
        socket?.close(1000, "Call ended")
        stopPlayback()
        recorder?.runCatching { stop() }
        track?.release()
        track = null
        executor.shutdownNow()
        playbackExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }
}
