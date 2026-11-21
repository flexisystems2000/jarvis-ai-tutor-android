package com.flexi.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.flexi.jarvis.data.model.AppTheme

private val JarvisDarkColors = darkColorScheme(
    primary = JarvisBlue,
    onPrimary = Color.White,
    secondary = JarvisGreen,
    onSecondary = Color.White,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisOutline,
    error = Color(0xFFFF6B78)
)

private val JarvisLightColors = lightColorScheme(
    primary = Color(0xFF245FC7),
    onPrimary = Color.White,
    secondary = Color(0xFF087F6D),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF151923),
    surface = Color.White,
    onSurface = Color(0xFF151923),
    surfaceVariant = Color(0xFFE9EDF4),
    onSurfaceVariant = Color(0xFF596579),
    outline = Color(0xFFCBD2DE),
    error = Color(0xFFB3261E)
)

@Composable
fun JarvisTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) JarvisDarkColors else JarvisLightColors,
        content = content
    )
}
