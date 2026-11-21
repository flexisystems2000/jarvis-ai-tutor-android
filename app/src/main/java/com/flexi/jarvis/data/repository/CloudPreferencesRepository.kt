package com.flexi.jarvis.data.repository

import com.flexi.jarvis.data.model.AppPreferences
import com.flexi.jarvis.data.model.AppTheme
import com.flexi.jarvis.network.SupabaseBackendApi
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** Keeps DataStore as the immediate local source and syncs signed-in preferences to Supabase. */
class CloudPreferencesRepository(
    private val local: PreferencesRepository,
    private val api: SupabaseBackendApi
) {
    suspend fun save(userId: String?, preferences: AppPreferences) {
        local.save(preferences)
        if (!userId.isNullOrBlank()) api.savePreferences(userId, preferences.toJson())
    }

    suspend fun syncFromCloud(userId: String) {
        val remote = api.getPreferences(userId) ?: return
        val localValues = local.preferences.first()
        val theme = runCatching { AppTheme.valueOf(remote.optString("theme", localValues.theme.name)) }
            .getOrDefault(localValues.theme)
        local.save(
            AppPreferences(
                theme = theme,
                tutorName = remote.optString("tutor_name", localValues.tutorName).ifBlank { "JARVIS" },
                languageCode = remote.optString("language_code", localValues.languageCode).ifBlank { "en" },
                webSearchEnabled = if (remote.has("web_search_enabled")) remote.optBoolean("web_search_enabled") else localValues.webSearchEnabled
            )
        )
    }

    private fun AppPreferences.toJson() = JSONObject()
        .put("theme", theme.name)
        .put("tutor_name", tutorName)
        .put("language_code", languageCode)
        .put("web_search_enabled", webSearchEnabled)
}
