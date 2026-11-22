package com.flexi.jarvis.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log
import com.flexi.jarvis.network.GeminiLiveToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Bidirectional Gemini Live transport. Call only after RECORD_AUDIO permission is granted.
 * Camera and MediaProjection producers can pass JPEG frames through sendVideoFrame().
 */
class GeminiLiveSession(
    private val context: Context,
    private val token: GeminiLiveToken,
    private val listener: Listener
) {
    private val logTag = "JarvisGeminiLive"
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
        fun onDiagnostic(message: String) {}
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val executor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private val connectionTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var setupTimeout: ScheduledFuture<*>? = null
    private val setupComplete = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var socketOpenedAtMs: Long = 0L
    private val localCloseIntent = AtomicReference<String?>(null)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var track: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private fun closeSocket(webSocket: WebSocket?, code: Int, reason: String, source: String) {
        localCloseIntent.set("$source (code=$code, reason=$reason)")
        val elapsed = if (socketOpenedAtMs > 0L) SystemClock.elapsedRealtime() - socketOpenedAtMs else -1L
        listener.onDiagnostic("App requested WebSocket close: source=$source, code=$code, reason=$reason, elapsedMs=$elapsed.")
        webSocket?.close(code, reason)
    }

    fun connect() {
        if (closed.get()) return
        listener.onDiagnostic("Live transport: validating temporary token and model.")
        val wsVersion = token.apiVersion.ifBlank { "v1alpha" }
        if (wsVersion != "v1alpha") {
            listener.onError("Gemini Live temporary-token sessions require API version v1alpha (received $wsVersion).")
            return
        }
        if (token.token.isBlank() || token.model.isBlank()) {
            listener.onError("The Live service returned an incomplete session configuration.")
            return
        }
        listener.onDiagnostic("Live transport: opening Gemini WebSocket (API $wsVersion, model ${token.model}).")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.$wsVersion.GenerativeService.BidiGenerateContentConstrained?access_token=" +
            java.net.URLEncoder.encode(token.token, "UTF-8")
        val request = try { Request.Builder().url(url).build() } catch (error: Exception) {
            listener.onError("Invalid Gemini Live connection URL: ${error.message ?: "invalid URL"}")
            return
        }
        try {
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(logTag, "websocket_open status=${response.code} protocol=${response.protocol} model=${token.model} apiVersion=${token.apiVersion}")
                socketOpenedAtMs = SystemClock.elapsedRealtime()
                localCloseIntent.set(null)
                listener.onDiagnostic("WebSocket opened: HTTP ${response.code}, protocol ${response.protocol}.")
                // Minimal protocol-isolation handshake: keep only the required model and
                // AUDIO response modality. Optional setup fields are restored individually
                // after setupComplete is confirmed.
                val generationConfig = JSONObject()
                    .put("responseModalities", org.json.JSONArray().put("AUDIO"))

                val setupBody = JSONObject()
                    .put("model", "models/${token.model}")
                    .put("generationConfig", generationConfig)

                val setup = JSONObject().put("setup", setupBody)
                Log.i(logTag, "setup_send model=models/${token.model} modalities=AUDIO minimal=true bytes=${setup.toString().toByteArray(Charsets.UTF_8).size}")
                setupTimeout = connectionTimer.schedule({
                    if (!setupComplete.get() && !closed.get()) {
                        listener.onError("Gemini connected, but did not complete session setup within 20 seconds. Check the Live model and setup configuration.")
                        closeSocket(webSocket, 1001, "Gemini setup timeout", "setup-timeout")
                    }
                }, 20, TimeUnit.SECONDS)
                val setupSent = webSocket.send(setup.toString())
                Log.i(logTag, "setup_send_accepted=$setupSent")
                listener.onDiagnostic(if (setupSent) "Gemini setup frame queued successfully; waiting for setupComplete." else "Gemini setup frame could not be queued.")
                if (!setupSent) {
                    setupTimeout?.cancel(false)
                    listener.onError("Could not send the Gemini Live session setup message.")
                    closeSocket(webSocket, 1001, "Setup send failed", "setup-send-failed")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val messageKeys = root.keys().asSequence().toList().joinToString(",")
                    Log.i(logTag, "server_message keys=$messageKeys")
                    listener.onDiagnostic("Gemini message received: ${messageKeys.ifBlank { "empty message" }}.")
                    if (root.has("error")) {
                        val serverError = root.optJSONObject("error")
                        val message = serverError?.optString("message").orEmpty()
                        val status = serverError?.optString("status").orEmpty()
                        setupTimeout?.cancel(false)
                        Log.e(logTag, "server_setup_error status=$status message=${message.take(1200)}")
                        listener.onError("Gemini Live rejected the session${if (status.isNotBlank()) " ($status)" else ""}: ${message.ifBlank { "unspecified server error" }}")
                        closeSocket(webSocket, 1008, "Gemini setup rejected", "server-setup-error")
                        return
                    }
                    if (root.has("setupComplete")) {
                        setupTimeout?.cancel(false)
                        if (setupComplete.compareAndSet(false, true)) {
                            Log.i(logTag, "setup_complete")
                            listener.onDiagnostic("SUCCESS: Gemini sent setupComplete; Live session is ready.")
                            listener.onConnected()
                        }
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
                Log.e(logTag, "websocket_failure httpStatus=${response?.code ?: -1} message=${t.message ?: "unknown"}", t)
                setupTimeout?.cancel(false)
                val causes = generateSequence(t as Throwable?) { it.cause }
                    .take(5)
                    .joinToString(" <- ") { cause ->
                        val safeMessage = (cause.message ?: "no message")
                            .replace(Regex("Address@[0-9a-fA-F]+"), "Address@[redacted]")
                            .take(240)
                        "${cause.javaClass.simpleName}: $safeMessage"
                    }
                val detail = response?.let {
                    val body = runCatching { it.peekBody(4_096).string() }.getOrNull().orEmpty()
                    "HTTP ${it.code} ${it.message}${if (body.isNotBlank()) ": ${body.take(1200)}" else ""}"
                } ?: causes.ifBlank { "Unknown network failure (${t.javaClass.simpleName})." }
                listener.onDiagnostic("WebSocket failure: $detail")
                listener.onError("Gemini Live WebSocket failed: $detail")
                stopAudio()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(logTag, "websocket_closing code=$code reason=${reason.take(500)} setupComplete=${setupComplete.get()}")
                val elapsed = if (socketOpenedAtMs > 0L) SystemClock.elapsedRealtime() - socketOpenedAtMs else -1L
                val requestedByApp = localCloseIntent.get()
                listener.onDiagnostic("WebSocket closing: code $code; reason=${reason.take(240)}; setupComplete=${setupComplete.get()}; elapsedMs=$elapsed; appCloseIntent=${requestedByApp ?: "none-recorded"}.")
                super.onClosing(webSocket, code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(logTag, "websocket_closed code=$code reason=${reason.take(500)} setupComplete=${setupComplete.get()}")
                val elapsed = if (socketOpenedAtMs > 0L) SystemClock.elapsedRealtime() - socketOpenedAtMs else -1L
                val requestedByApp = localCloseIntent.get()
                listener.onDiagnostic("WebSocket closed: code $code; reason=${reason.take(240)}; setupComplete=${setupComplete.get()}; elapsedMs=$elapsed; appCloseIntent=${requestedByApp ?: "none-recorded"}.")
                setupTimeout?.cancel(false)
                stopAudio()
                if (!setupComplete.get() && !closed.get()) {
                    listener.onError("Gemini closed before setup completed (code $code; elapsed ${elapsed}ms; app close intent ${requestedByApp ?: "none recorded"}).")
                }
                listener.onClosed()
            }
        })
        } catch (error: Exception) {
            setupTimeout?.cancel(false)
            listener.onDiagnostic("WebSocket creation exception: ${error.javaClass.simpleName}: ${(error.message ?: "unknown").take(240)}")
            listener.onError("Could not open Gemini Live WebSocket: ${error.javaClass.simpleName}: ${error.message ?: "unknown error"}")
        }
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
        closeSocket(socket, 1000, "Call ended", "session-close-called")
        stopPlayback()
        recorder?.runCatching { stop() }
        track?.release()
        track = null
        setupTimeout?.cancel(false)
        connectionTimer.shutdownNow()
        executor.shutdownNow()
        playbackExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }
}
