
package com.flexi.jarvis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flexi.jarvis.ui.JarvisApp
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleAuthIntent(intent)

        setContent {
            JarvisApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            runCatching {
                JarvisApplication.supabase.handleDeeplinks(intent)
            }
        }
    }
}
