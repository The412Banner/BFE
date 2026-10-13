package com.the412banner.bfe.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager

/**
 * BFE's Material 3 theme. Screens only read MaterialTheme.colorScheme / typography tokens, so the
 * whole look lives here: a navy + blue brand scheme with EVERY role filled in (the surfaceContainer
 * family included, so toolbars and cards never fall back to the baseline purple tints), plus
 * optional Material You dynamic color on Android 12+ and a System / Light / Dark choice from Settings.
 */
private val Accent = Color(0xFF3B82F6)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1F4A8F),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFFA9C2E8),
    onSecondary = Color(0xFF14304F),
    secondaryContainer = Color(0xFF223A5A),
    onSecondaryContainer = Color(0xFFD4E3FF),
    tertiary = Color(0xFF7FD3C4),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF0E4F47),
    onTertiaryContainer = Color(0xFFA0F0E0),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1B2434),
    onSurfaceVariant = Color(0xFFA3AEC0),
    surfaceTint = Accent,
    outline = Color(0xFF2E3A4E),
    outlineVariant = Color(0xFF243044),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF5C0F06),
    errorContainer = Color(0xFF7D1D12),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFE6EAF2),
    inverseOnSurface = Color(0xFF16202B),
    inversePrimary = Color(0xFF2563EB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A3446),
    surfaceDim = Color(0xFF0B1220),
    surfaceContainerLowest = Color(0xFF070C16),
    surfaceContainerLow = Color(0xFF101828),
    surfaceContainer = Color(0xFF141D2E),
    surfaceContainerHigh = Color(0xFF1B2537),
    surfaceContainerHighest = Color(0xFF232E42),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2A66),
    secondary = Color(0xFF4F617D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E4F6),
    onSecondaryContainer = Color(0xFF0C1D33),
    tertiary = Color(0xFF00897B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8F0E6),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF16202B),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFF4F5B6A),
    surfaceTint = Color(0xFF2563EB),
    outline = Color(0xFFC3CBD6),
    outlineVariant = Color(0xFFDDE3EA),
    error = Color(0xFFC0392B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    inverseSurface = Color(0xFF2A3340),
    inverseOnSurface = Color(0xFFEFF2F6),
    inversePrimary = Color(0xFF7AA7FF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9DEE5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F6),
    surfaceContainerHighest = Color(0xFFE7EBF0),
)

/** Theme choices persisted in the default prefs and observable by Compose. */
object ThemeSettings {
    enum class Mode { SYSTEM, LIGHT, DARK }

    private const val PREF_MODE = "theme_mode"
    private const val PREF_DYNAMIC = "theme_dynamic_color"

    var mode by mutableStateOf(Mode.SYSTEM)
        private set
    var dynamicColor by mutableStateOf(false)
        private set
    private var loaded = false

    val dynamicSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun load(ctx: Context) {
        if (loaded) return
        val p = PreferenceManager.getDefaultSharedPreferences(ctx)
        mode = runCatching { Mode.valueOf(p.getString(PREF_MODE, Mode.SYSTEM.name)!!) }.getOrDefault(Mode.SYSTEM)
        dynamicColor = p.getBoolean(PREF_DYNAMIC, false)
        loaded = true
    }

    fun setMode(ctx: Context, m: Mode) {
        mode = m
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(PREF_MODE, m.name).apply()
    }

    fun setDynamic(ctx: Context, on: Boolean) {
        dynamicColor = on
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(PREF_DYNAMIC, on).apply()
    }
}

@Composable
fun BfeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    ThemeSettings.load(context)
    val dark = when (ThemeSettings.mode) {
        ThemeSettings.Mode.SYSTEM -> isSystemInDarkTheme()
        ThemeSettings.Mode.LIGHT -> false
        ThemeSettings.Mode.DARK -> true
    }
    val scheme = when {
        ThemeSettings.dynamicColor && ThemeSettings.dynamicSupported ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    // System bars follow the app: the status bar takes the toolbar colour, the navigation bar the
    // page background, and their icons flip light/dark with the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = scheme.surfaceContainer.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
