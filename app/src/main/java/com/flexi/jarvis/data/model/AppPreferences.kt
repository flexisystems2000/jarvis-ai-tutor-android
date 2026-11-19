package com.flexi.jarvis.data.model

enum class AppTheme { SYSTEM, LIGHT, DARK }

data class AppPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val tutorName: String = "JARVIS",
    val languageCode: String = "en",
    val webSearchEnabled: Boolean = true
)
