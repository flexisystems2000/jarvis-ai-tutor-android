
package com.flexi.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flexi.jarvis.ui.JarvisApp
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Process Supabase email and authentication callbacks.
        JarvisApplication.supabase.handleDeeplinks(intent)

        setContent {
            JarvisApp()
        }
    }
}
