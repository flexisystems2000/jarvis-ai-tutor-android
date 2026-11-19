package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.AppPreferences

interface PreferencesRepository {
    suspend fun load(): AppPreferences
    suspend fun save(preferences: AppPreferences)
}

/** Replace with DataStore persistence; this preview keeps settings in memory. */
class PreviewPreferencesRepository : PreferencesRepository {
    private var current = AppPreferences()
    override suspend fun load() = current
    override suspend fun save(preferences: AppPreferences) { current = preferences }
}
