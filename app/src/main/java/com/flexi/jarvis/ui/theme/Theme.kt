package com.flexi.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun JarvisTheme(
    theme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit
) {
    // JARVIS uses its dark brand interface consistently. The saved preference
    // is retained for compatibility; light-theme support can be added later.
    MaterialTheme(
        colorScheme = JarvisDarkColors,
        content = content
    )
}
