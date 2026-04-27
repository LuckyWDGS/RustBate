package com.moviecat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6C85F),
    secondary = Color(0xFF89C2D9),
    tertiary = Color(0xFFF4978E),
    background = Color(0xFF111318),
    surface = Color(0xFF191D24),
    surfaceVariant = Color(0xFF232A33),
    onPrimary = Color(0xFF2C2000),
    onBackground = Color(0xFFF4F7FB),
    onSurface = Color(0xFFF4F7FB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C5A00),
    secondary = Color(0xFF006782),
    tertiary = Color(0xFF8E3D37),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF4E6CC)
)

@Composable
fun MovieCatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

