package com.flexi.jarvis.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisPreferencesDataStore by preferencesDataStore(name = "jarvis_app_preferences")

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun save(preferences: AppPreferences)
}

class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val tutorName = stringPreferencesKey("tutor_name")
        val languageCode = stringPreferencesKey("language_code")
        val webSearchEnabled = booleanPreferencesKey("web_search_enabled")
    }

    override val preferences: Flow<AppPreferences> = context.jarvisPreferencesDataStore.data.map { values ->
        AppPreferences(
            theme = values[Keys.theme]?.let { stored -> AppTheme.entries.firstOrNull { it.name == stored } }
                ?: AppTheme.SYSTEM,
            tutorName = values[Keys.tutorName] ?: "JARVIS",
            languageCode = values[Keys.languageCode] ?: "en",
            webSearchEnabled = values[Keys.webSearchEnabled] ?: true
        )
    }

    override suspend fun save(preferences: AppPreferences) {
        context.jarvisPreferencesDataStore.edit { values ->
            values[Keys.theme] = preferences.theme.name
            values[Keys.tutorName] = preferences.tutorName.trim().ifBlank { "JARVIS" }
            values[Keys.languageCode] = preferences.languageCode
            values[Keys.webSearchEnabled] = preferences.webSearchEnabled
        }
    }
}
