package com.flexi.jarvis

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyCoGXb2XlvuwcJY8oyW6_J42fgxfH5vZao")
                .setApplicationId("1:868499596875:web:4bf592934f6086be8a4fce")
                .setProjectId("jarvisai-1a594")
                .setGcmSenderId("868499596875")
                .setStorageBucket("jarvisai-1a594.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
