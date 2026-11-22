package com.flexi.jarvis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flexi.jarvis.live.LiveSessionClient
import com.flexi.jarvis.network.LiveSessionApi
import kotlinx.coroutines.launch

@Composable
fun GeminiLiveCallDialog(
    tutorName: String,
    languageCode: String,
    liveApi: LiveSessionApi,
    onCompletedTurn: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var session by remember { mutableStateOf<LiveSessionClient?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Start a natural voice conversation with JARVIS.") }
    var error by remember { mutableStateOf<String?>(null) }
    var userTurn by remember { mutableStateOf("") }
    var jarvisTurn by remember { mutableStateOf("") }

    fun beginCall() {
        if (connecting || connected) return
        connecting = true
        error = null
        status = "Connecting securely to JARVIS..."
        scope.launch {
            runCatching { liveApi.create(tutorName, languageCode) }
                .onSuccess { liveConfig ->
                    val live = LiveSessionClient(context, liveConfig, object : LiveSessionClient.Listener {
                        override fun onConnected() = mainHandler.post {
                            connecting = false
                            connected = true
                            status = "Connected. Speak naturally; you can interrupt JARVIS."
                            session?.startMicrophone()
                        }.let { }

                        override fun onInputTranscript(text: String) = mainHandler.post {
                            userTurn = (userTurn + " " + text).trim()
                        }.let { }

                        override fun onOutputTranscript(text: String) = mainHandler.post {
                            jarvisTurn = (jarvisTurn + " " + text).trim()
                        }.let { }

                        override fun onTurnComplete() = mainHandler.post {
                            val userText = userTurn.trim()
                            val assistantText = jarvisTurn.trim()
                            if (userText.isNotBlank() || assistantText.isNotBlank()) {
                                onCompletedTurn(userText, assistantText)
                            }
                            userTurn = ""
                            jarvisTurn = ""
                        }.let { }

                        override fun onInterrupted() = mainHandler.post {
                            status = "JARVIS was interrupted. Go ahead."
                        }.let { }

                        override fun onError(message: String) = mainHandler.post {
                            error = message
                            connecting = false
                            if (!connected) status = "Unable to connect."
                        }.let { }

                        override fun onClosed() = mainHandler.post {
                            connected = false
                            connecting = false
                            status = "Call ended."
                        }.let { }
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
    }

    DisposableEffect(Unit) {
        onDispose { endCall() }
    }

    AlertDialog(
        onDismissRequest = {
            endCall()
            onDismiss()
        },
        title = { Text("JARVIS Live Voice Call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status)
                if (connecting) CircularProgressIndicator()
                error?.let { Text(it) }
                if (connected) {
                    Text(if (muted) "Microphone muted" else "Listening")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (muted) session?.startMicrophone() else session?.stopMicrophone()
                            muted = !muted
                        }) { Text(if (muted) "Unmute" else "Mute") }
                        Button(onClick = ::endCall) { Text("End call") }
                    }
                }
            }
        },
        confirmButton = {
            if (!connected && !connecting) {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) beginCall() else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Start voice call") }
            } else if (connecting) {
                OutlinedButton(onClick = ::endCall) { Text("Cancel") }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                endCall()
                onDismiss()
            }) { Text("Close") }
        }
    )
}
