package com.flexi.jarvis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flexi.jarvis.ui.JarvisAnimatedSplash
import com.flexi.jarvis.ui.JarvisApp
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)

        setContent {
            val showBrandedSplash = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(true)
            }

            if (showBrandedSplash.value) {
                JarvisAnimatedSplash(onFinished = { showBrandedSplash.value = false })
            } else {
                JarvisApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            runCatching { JarvisApplication.supabase.handleDeeplinks(intent) }
        }
    }
}
