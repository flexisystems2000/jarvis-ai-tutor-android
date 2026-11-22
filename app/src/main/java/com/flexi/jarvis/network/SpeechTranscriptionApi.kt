package com.flexi.jarvis.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SpeechTranscriptionApi(
    private val baseUrl: String,
    private val accessTokenProvider: suspend () -> String?
) {
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        require(audioFile.exists() && audioFile.length() > 0L) {
            "The voice recording is empty."
        }

        val endpoint = baseUrl.trim().trimEnd('/')
        require(endpoint.startsWith("https://")) {
            "The speech service URL is not configured. Add SPEECH_API_URL after deploying the backend."
        }

        val accessToken = accessTokenProvider()
            ?: throw IOException("Please sign in again before using voice transcription.")

        val boundary = "JarvisBoundary-${UUID.randomUUID()}"
        val connection = (URL("$endpoint/transcribe").openConnection()
            as HttpURLConnection)

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 180_000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )
            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )
            connection.setRequestProperty("Accept", "application/json")
            connection.setChunkedStreamingMode(64 * 1024)

            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray())
                val isWav = audioFile.extension.equals("wav", ignoreCase = true)
                val audioName = if (isWav) "voice.wav" else "voice.m4a"
                val audioMime = if (isWav) "audio/wav" else "audio/mp4"
                output.write(
                    "Content-Disposition: form-data; name=\"audio\"; filename=\"$audioName\"\r\n"
                        .toByteArray()
                )
                output.write("Content-Type: $audioMime\r\n\r\n".toByteArray())

                audioFile.inputStream().use { input ->
                    input.copyTo(output)
                }

                output.write("\r\n--$boundary--\r\n".toByteArray())
                output.flush()
            }

            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = responseStream?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()

            if (status !in 200..299) {
                val serverMessage = runCatching {
                    JSONObject(responseBody).optString("detail")
                }.getOrNull().orEmpty()

                throw IOException(
                    serverMessage.ifBlank {
                        "Speech transcription failed (HTTP $status)."
                    }
                )
            }

            val transcript = JSONObject(responseBody)
                .optString("text")
                .trim()

            if (transcript.isBlank()) {
                throw IOException("No speech was detected. Please try again.")
            }

            transcript
        } finally {
            connection.disconnect()
        }
    }
}
