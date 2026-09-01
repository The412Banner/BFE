package com.the412banner.bfe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Minimal Material3 theme for BFE. The screens are theme-agnostic — they only read
 * MaterialTheme.colorScheme / typography tokens — so a plain light+dark scheme with a blue accent is
 * all that's needed. (Lifted-and-renamed from the source app's WinlatorTheme, stripped of its
 * preset/accent-picker machinery.)
 */
private val Accent = Color(0xFF3B82F6)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF1B2434),
    onSurfaceVariant = Color(0xFF9BA6B7),
    outline = Color(0xFF2C3647),
    error = Color(0xFFE05C4A),
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF16202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFEAEDF2),
    onSurfaceVariant = Color(0xFF55606E),
    outline = Color(0xFFCBD2DC),
    error = Color(0xFFC0392B),
)

@Composable
fun BfeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
