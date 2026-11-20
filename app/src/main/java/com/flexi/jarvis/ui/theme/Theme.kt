package com.flexi.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.flexi.jarvis.data.model.AppTheme

private val LightColors = lightColorScheme(
    primary = FlexiBlue,
    secondary = FlexiGold,
    background = LightBackground
)
private val DarkColors = darkColorScheme(primary = FlexiGold, secondary = FlexiBlue)

@Composable
fun JarvisTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val useDarkTheme = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
