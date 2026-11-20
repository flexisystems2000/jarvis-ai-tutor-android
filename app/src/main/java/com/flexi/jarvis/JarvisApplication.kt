
package com.flexi.jarvis

import android.app.Application
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth

class JarvisApplication : Application() {

    companion object {
        lateinit var supabase: SupabaseClient
            private set
    }

    override fun onCreate() {
        super.onCreate()

        supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                host = "auth"
                scheme = "com.flexi.jarvis"
            }
        }
    }
}
