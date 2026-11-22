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
import android.util.Log
import com.flexi.jarvis.network.LiveSessionConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/** Captures and plays media. Cloudflare owns the AI session and provider protocol. */
class LiveSessionClient(
    private val context: Context,
    private val config: LiveSessionConfig,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onAudioLevel(level: Float)
        fun onTurnComplete()
        fun onInterrupted()
        fun onEndCallRequested(reason: String)
        fun onError(message: String)
        fun onClosed()
        fun onDiagnostic(message: String) {}
    }

    private val logTag = "JARVIS_LIVE"
    private val closed = AtomicBoolean(false)
    private val recording = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var track: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    fun connect() {
        if (closed.get()) return
        listener.onDiagnostic("Opening authenticated live connection to Cloudflare.")
        val request = try {
            Request.Builder()
                .url(config.endpoint)
                .header("Authorization", "Bearer ${config.accessToken}")
                .header("X-Jarvis-Tutor", config.tutorName.take(64))
                .header("X-Jarvis-Language", config.language.take(48))
                .build()
        } catch (error: Exception) {
            listener.onError("Invalid Cloudflare Live endpoint: ${error.message ?: "invalid URL"}")
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(logTag, "cloudflare_socket_open status=${response.code}")
                listener.onDiagnostic("Cloudflare connection opened (HTTP ${response.code}); waiting for session readiness.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "ready" -> {
                            listener.onDiagnostic("Cloudflare reports that the live session is ready.")
                            listener.onConnected()
                        }
                        "transcript" -> {
                            val value = message.optString("text")
                            if (value.isNotBlank()) {
                                if (message.optString("role") == "user") listener.onInputTranscript(value)
                                else listener.onOutputTranscript(value)
                            }
                        }
                        "audio" -> {
                            val encoded = message.optString("data")
                            if (encoded.isNotBlank()) playPcm(Base64.decode(encoded, Base64.DEFAULT))
                        }
                        "interrupted" -> {
                            stopPlayback()
                            listener.onInterrupted()
                        }
                        "turn_complete" -> listener.onTurnComplete()
                        "end_call" -> listener.onEndCallRequested(
                            message.optString("reason").ifBlank { "The call was ended by JARVIS." }
                        )
                        "error" -> {
                            val detail = message.optString("message").ifBlank { "The Cloudflare Live service reported an error." }
                            listener.onDiagnostic("Cloudflare Live error: ${detail.take(500)}")
                            listener.onError(detail)
                        }
                        "status" -> listener.onDiagnostic(message.optString("message"))
                    }
                } catch (error: Exception) {
                    listener.onError("Could not read the live response: ${error.message ?: "unknown error"}")
                }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                val body = runCatching { response?.peekBody(4096)?.string().orEmpty() }.getOrDefault("")
                val detail = buildString {
                    append("Cloudflare connection failed")
                    if (response != null) append(" (HTTP ${response.code})")
                    append(": ")
                    append(body.ifBlank { error.message ?: error.javaClass.simpleName })
                }
                Log.e(logTag, detail, error)
                listener.onDiagnostic(detail.take(1000))
                listener.onError(detail.take(1000))
                stopAudio()
                listener.onClosed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onDiagnostic("Cloudflare connection closed (code $code)${if (reason.isNotBlank()) ": $reason" else ""}.")
                stopAudio()
                if (!closed.get() && code != 1000) listener.onError("The live connection closed unexpectedly (code $code).")
                listener.onClosed()
            }
        })
    }

    fun startMicrophone() {
        if (closed.get() || !recording.compareAndSet(false, true)) return
        captureExecutor.execute {
            try {
                val sampleRate = 16_000
                val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                require(minBuffer > 0) { "This device does not support microphone capture." }
                val audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, 6_400)).build()
                recorder = audioRecord
                if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply { enabled = true }
                if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)?.apply { enabled = true }
                audioRecord.startRecording()
                val buffer = ByteArray(3_200)
                while (recording.get() && !closed.get()) {
                    val count = audioRecord.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        listener.onAudioLevel(pcmLevel(buffer, count))
                        val payload = JSONObject().put("type", "audio").put("mimeType", "audio/pcm;rate=16000")
                            .put("data", Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP))
                        if (socket?.send(payload.toString()) != true) listener.onDiagnostic("Audio frame was not accepted by Cloudflare.")
                    }
                }
            } catch (error: Exception) {
                if (!closed.get()) listener.onError("Microphone error: ${error.message ?: "unknown error"}")
            } finally {
                recorder?.runCatching { stop() }; recorder?.release(); recorder = null
                recording.set(false)
                echoCanceler?.release(); echoCanceler = null
                noiseSuppressor?.release(); noiseSuppressor = null
            }
        }
    }

    fun sendVideoFrame(jpeg: ByteArray) {
        if (closed.get() || jpeg.isEmpty()) return
        socket?.send(JSONObject().put("type", "video").put("mimeType", "image/jpeg")
            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)).toString())
    }

    fun stopMicrophone() {
        recording.set(false)
        socket?.send(JSONObject().put("type", "audio_end").toString())
    }

    fun sendText(text: String) {
        if (text.isNotBlank() && !closed.get()) socket?.send(JSONObject().put("type", "text").put("text", text).toString())
    }

    private fun playPcm(bytes: ByteArray) {
        if (bytes.isEmpty() || closed.get()) return
        playbackExecutor.execute {
            try {
                val minimum = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minimum <= 0) return@execute
                var output = track
                if (output == null) {
                    output = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(24_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(minimum * 2, bytes.size * 2))
                        .setTransferMode(AudioTrack.MODE_STREAM).build()
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
        var sum = 0.0; var samples = 0; var index = 0
        while (index + 1 < length) {
            val sample = ((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xFF)).toShort().toInt()
            val normalized = sample / 32768.0
            sum += normalized * normalized; samples++; index += 2
        }
        if (samples == 0) return 0f
        return (sqrt(sum / samples).toFloat() * 4.5f).coerceIn(0f, 1f)
    }

    private fun stopPlayback() {
        playing.set(false)
        track?.runCatching { pause(); flush() }
    }

    private fun stopAudio() {
        stopMicrophone()
        stopPlayback()
        track?.release(); track = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        recording.set(false)
        socket?.send(JSONObject().put("type", "end").toString())
        socket?.close(1000, "Call ended")
        stopPlayback()
        recorder?.runCatching { stop() }
        track?.release(); track = null
        captureExecutor.shutdownNow()
        playbackExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }
}
