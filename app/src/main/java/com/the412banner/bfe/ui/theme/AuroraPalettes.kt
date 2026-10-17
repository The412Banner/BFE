package com.the412banner.bfe.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * Aurora Glass palettes. NEUTRAL is the default: graphite / slate glass with a faint cool tint over a
 * barely tinted backdrop, with colour coming from one blue accent plus BFE's own icons. The others are
 * vivid options picked in Settings → Appearance. Every palette has a dark and a light variant.
 */
enum class BfePalette(val label: String) {
    NEUTRAL("Neutral"),
    AURORA("Aurora"),
    OCEAN("Ocean"),
    EMBER("Ember"),
    FOREST("Forest"),
    ROSE("Rose"),
}

/**
 * The raw colours of one palette variant. Everything else (the Material colour scheme, the glass
 * tokens, the backdrop) is derived from these, so a palette is just this table.
 *
 * @param base the backdrop's ground colour (also the opaque page colour for anything not on glass)
 * @param blobs the three soft light sources drifting across the backdrop
 * @param blobAlpha how strongly the blobs show: low for Neutral, high for the vivid palettes
 * @param tint the colour glass is made of (white-ish in dark, white in light)
 */
@Immutable
data class PaletteColors(
    val dark: Boolean,
    val base: Color,
    val blobs: List<Color>,
    val blobAlpha: Float,
    val accent: Color,
    val onAccent: Color,
    val accent2: Color,
    val ink: Color,
    val inkMuted: Color,
    val tint: Color,
    val danger: Color,
)

private val White = Color(0xFFFFFFFF)

internal fun paletteColors(palette: BfePalette, dark: Boolean): PaletteColors = if (dark) when (palette) {
    BfePalette.NEUTRAL -> PaletteColors(
        dark = true, base = Color(0xFF0E1117),
        blobs = listOf(Color(0xFF2B3A55), Color(0xFF1F2B3B), Color(0xFF26324A)), blobAlpha = 0.55f,
        accent = Color(0xFF8AB4FF), onAccent = Color(0xFF0B1220), accent2 = Color(0xFF9CC3FF),
        ink = Color(0xFFE9EDF3), inkMuted = Color(0xFFA2ABB9), tint = Color(0xFFE6ECF7), danger = Color(0xFFFF9B9B),
    )
    BfePalette.AURORA -> PaletteColors(
        dark = true, base = Color(0xFF0B1020),
        blobs = listOf(Color(0xFF5B4BFF), Color(0xFF0FB5C6), Color(0xFFD9468F)), blobAlpha = 0.62f,
        accent = Color(0xFF8AB4FF), onAccent = Color(0xFF0B1020), accent2 = Color(0xFF7EF0D8),
        ink = Color(0xFFEEF2FF), inkMuted = Color(0xFFAAB4D8), tint = White, danger = Color(0xFFFF9B9B),
    )
    BfePalette.OCEAN -> PaletteColors(
        dark = true, base = Color(0xFF06141C),
        blobs = listOf(Color(0xFF0E6BA8), Color(0xFF0FB5A6), Color(0xFF1D4ED8)), blobAlpha = 0.58f,
        accent = Color(0xFF5CC8FF), onAccent = Color(0xFF02131D), accent2 = Color(0xFF5EEAD4),
        ink = Color(0xFFE8F6FB), inkMuted = Color(0xFF9DBCC9), tint = White, danger = Color(0xFFFF9B9B),
    )
    BfePalette.EMBER -> PaletteColors(
        dark = true, base = Color(0xFF140D0A),
        blobs = listOf(Color(0xFFC2410C), Color(0xFFB91C1C), Color(0xFFD97706)), blobAlpha = 0.55f,
        accent = Color(0xFFFFB070), onAccent = Color(0xFF2A1305), accent2 = Color(0xFFFFD58A),
        ink = Color(0xFFFBEFE7), inkMuted = Color(0xFFCDB3A5), tint = Color(0xFFFFF4EC), danger = Color(0xFFFF9B9B),
    )
    BfePalette.FOREST -> PaletteColors(
        dark = true, base = Color(0xFF08130E),
        blobs = listOf(Color(0xFF15803D), Color(0xFF0F766E), Color(0xFF4D7C0F)), blobAlpha = 0.58f,
        accent = Color(0xFF7EE0A1), onAccent = Color(0xFF04170C), accent2 = Color(0xFFC5E66B),
        ink = Color(0xFFE8F5EC), inkMuted = Color(0xFFA3C2AE), tint = Color(0xFFF1FFF5), danger = Color(0xFFFF9B9B),
    )
    BfePalette.ROSE -> PaletteColors(
        dark = true, base = Color(0xFF160B12),
        blobs = listOf(Color(0xFFBE185D), Color(0xFF9333EA), Color(0xFFE11D48)), blobAlpha = 0.55f,
        accent = Color(0xFFFF8FB8), onAccent = Color(0xFF2A0716), accent2 = Color(0xFFD8B4FE),
        ink = Color(0xFFFBEAF1), inkMuted = Color(0xFFCFA9BA), tint = Color(0xFFFFF2F7), danger = Color(0xFFFFA3A3),
    )
} else when (palette) {
    BfePalette.NEUTRAL -> PaletteColors(
        dark = false, base = Color(0xFFECEFF4),
        blobs = listOf(Color(0xFFCBD7EC), Color(0xFFDDE3EC), Color(0xFFD3DCEA)), blobAlpha = 0.85f,
        accent = Color(0xFF2F5BEA), onAccent = White, accent2 = Color(0xFF3B6FD8),
        ink = Color(0xFF121821), inkMuted = Color(0xFF55606F), tint = White, danger = Color(0xFFC0392B),
    )
    BfePalette.AURORA -> PaletteColors(
        dark = false, base = Color(0xFFE8EDFA),
        blobs = listOf(Color(0xFFA5B4FC), Color(0xFF7EE8DF), Color(0xFFF9B4D6)), blobAlpha = 0.9f,
        accent = Color(0xFF3451D1), onAccent = White, accent2 = Color(0xFF0B8F86),
        ink = Color(0xFF101830), inkMuted = Color(0xFF4D5878), tint = White, danger = Color(0xFFC0392B),
    )
    BfePalette.OCEAN -> PaletteColors(
        dark = false, base = Color(0xFFE6F3F7),
        blobs = listOf(Color(0xFF9ED8F0), Color(0xFF99F0E0), Color(0xFFB5C8FA)), blobAlpha = 0.9f,
        accent = Color(0xFF0369A1), onAccent = White, accent2 = Color(0xFF0F766E),
        ink = Color(0xFF0B1E26), inkMuted = Color(0xFF4A6470), tint = White, danger = Color(0xFFC0392B),
    )
    BfePalette.EMBER -> PaletteColors(
        dark = false, base = Color(0xFFFBF0E8),
        blobs = listOf(Color(0xFFFDBA8C), Color(0xFFFCA5A5), Color(0xFFFCD34D)), blobAlpha = 0.85f,
        accent = Color(0xFFC2410C), onAccent = White, accent2 = Color(0xFFB45309),
        ink = Color(0xFF24150D), inkMuted = Color(0xFF6E5547), tint = White, danger = Color(0xFFB91C1C),
    )
    BfePalette.FOREST -> PaletteColors(
        dark = false, base = Color(0xFFEAF4EC),
        blobs = listOf(Color(0xFFA7E3B8), Color(0xFF9EDFD3), Color(0xFFCDE6A0)), blobAlpha = 0.85f,
        accent = Color(0xFF15803D), onAccent = White, accent2 = Color(0xFF4D7C0F),
        ink = Color(0xFF0E1E14), inkMuted = Color(0xFF4C6455), tint = White, danger = Color(0xFFC0392B),
    )
    BfePalette.ROSE -> PaletteColors(
        dark = false, base = Color(0xFFFBEFF3),
        blobs = listOf(Color(0xFFF9A8C9), Color(0xFFD8B4FE), Color(0xFFFDA4AF)), blobAlpha = 0.85f,
        accent = Color(0xFFBE185D), onAccent = White, accent2 = Color(0xFF7E22CE),
        ink = Color(0xFF26101A), inkMuted = Color(0xFF6E4E5D), tint = White, danger = Color(0xFFC0392B),
    )
}

/**
 * Material You on Android 12+: the wallpaper colours replace the palette's accent and tint its glass
 * and backdrop, while the palette keeps deciding how vivid the backdrop is.
 */
internal fun PaletteColors.withDynamic(context: Context): PaletteColors {
    val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return copy(
        base = lerp(base, d.surface, 0.45f),
        blobs = listOf(d.primary, d.tertiary, d.secondary).map { lerp(it, base, if (dark) 0.35f else 0.15f) },
        accent = d.primary,
        onAccent = d.onPrimary,
        accent2 = d.tertiary,
        tint = lerp(tint, d.primary, if (dark) 0.10f else 0.04f),
    )
}

/**
 * The glass recipe for a palette: translucent fills for the three heights of glass, the hairline and
 * its top highlight, the outside shadow, the accent glow, and the near-opaque fills used where a
 * surface floats over arbitrary content (menus, sheets, dialogs, the bottom pill when there is no
 * real blur behind it).
 */
@Immutable
data class GlassTokens(
    val palette: PaletteColors,
    /** Cards and panes resting on the backdrop. */
    val panel: Color,
    /** The active pane, pressed / selected glass, chips. */
    val raised: Color,
    /** A floating pill drawn over real blur (API 32+): lighter, the blur does the frosting. */
    val floatingOverBlur: Color,
    /** A floating pill with no blur behind it: denser so content under it never reads through. */
    val floatingSolid: Color,
    /** Drop-down menus (their own window; nothing to blur). */
    val menu: Color,
    /** Bottom sheets. */
    val sheet: Color,
    /** Dialogs when the system can blur the screen behind them. */
    val dialogOverBlur: Color,
    /** Dialogs without blur behind. */
    val dialogSolid: Color,
    val border: Color,
    val highlight: Color,
    val shadow: Color,
    val glow: Color,
    /** Selected rows / tiles. */
    val selection: Color,
    /** Strength of the grain drawn over glass (0..1 of the noise texture). */
    val noiseAlpha: Float,
    /** True when floating glass can blur what is behind it in-window (Haze RenderNode path, API 32+). */
    val realBlur: Boolean,
)

internal fun glassTokens(p: PaletteColors, realBlur: Boolean): GlassTokens {
    fun over(alpha: Float) = p.tint.copy(alpha = alpha).compositeOver(p.base)
    return if (p.dark) GlassTokens(
        palette = p,
        panel = p.tint.copy(alpha = 0.065f),
        raised = p.tint.copy(alpha = 0.115f),
        floatingOverBlur = p.base.copy(alpha = 0.42f).let { p.tint.copy(alpha = 0.10f).compositeOver(it) },
        floatingSolid = over(0.10f).copy(alpha = 0.93f),
        menu = over(0.11f).copy(alpha = 0.97f),
        sheet = over(0.08f).copy(alpha = 0.95f),
        dialogOverBlur = over(0.10f).copy(alpha = 0.82f),
        dialogSolid = over(0.10f).copy(alpha = 0.98f),
        border = p.tint.copy(alpha = 0.13f),
        highlight = p.tint.copy(alpha = 0.22f),
        shadow = Color.Black.copy(alpha = 0.34f),
        glow = p.accent.copy(alpha = 0.40f),
        selection = p.accent.copy(alpha = 0.20f),
        noiseAlpha = 0.045f,
        realBlur = realBlur,
    ) else GlassTokens(
        palette = p,
        panel = p.tint.copy(alpha = 0.52f),
        raised = p.tint.copy(alpha = 0.78f),
        floatingOverBlur = p.tint.copy(alpha = 0.58f),
        floatingSolid = over(0.72f).copy(alpha = 0.95f),
        menu = over(0.80f).copy(alpha = 0.98f),
        sheet = over(0.70f).copy(alpha = 0.97f),
        dialogOverBlur = over(0.70f).copy(alpha = 0.86f),
        dialogSolid = over(0.75f).copy(alpha = 0.99f),
        border = p.ink.copy(alpha = 0.10f),
        highlight = White.copy(alpha = 0.85f),
        shadow = p.ink.copy(alpha = 0.16f),
        glow = p.accent.copy(alpha = 0.32f),
        selection = p.accent.copy(alpha = 0.12f),
        noiseAlpha = 0.035f,
        realBlur = realBlur,
    )
}

/**
 * The Material 3 colour scheme for a palette. Screens that read MaterialTheme.colorScheme get a look
 * that matches the glass without knowing about it: primary = the accent, tertiary = the second
 * accent, and the surfaceContainer family = the solid colour of glass over the backdrop (what menus,
 * dialogs, sheets and any screen not yet on glass use). background/surface stay the opaque backdrop
 * ground so nothing that relies on them to cover what is underneath ever turns see-through.
 */
internal fun auroraColorScheme(p: PaletteColors): ColorScheme {
    fun over(c: Color, alpha: Float) = c.copy(alpha = alpha).compositeOver(p.base)
    val t = p.tint
    return if (p.dark) darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = over(p.accent, 0.26f),
        onPrimaryContainer = lerp(p.accent, p.ink, 0.55f),
        inversePrimary = lerp(p.accent, Color.Black, 0.35f),
        secondary = lerp(p.accent, p.inkMuted, 0.45f),
        onSecondary = p.onAccent,
        secondaryContainer = over(p.accent, 0.16f),
        onSecondaryContainer = p.ink,
        tertiary = p.accent2,
        onTertiary = p.onAccent,
        tertiaryContainer = over(p.accent2, 0.22f),
        onTertiaryContainer = lerp(p.accent2, p.ink, 0.5f),
        background = p.base,
        onBackground = p.ink,
        surface = p.base,
        onSurface = p.ink,
        surfaceVariant = over(t, 0.10f),
        onSurfaceVariant = p.inkMuted,
        surfaceTint = p.accent,
        inverseSurface = p.ink,
        inverseOnSurface = p.base,
        error = p.danger,
        onError = Color(0xFF3A0A0A),
        errorContainer = over(p.danger, 0.24f),
        onErrorContainer = lerp(p.danger, p.ink, 0.5f),
        outline = over(p.ink, 0.26f),
        outlineVariant = over(p.ink, 0.13f),
        scrim = Color.Black,
        surfaceBright = over(t, 0.16f),
        surfaceDim = p.base,
        surfaceContainerLowest = over(t, 0.025f),
        surfaceContainerLow = over(t, 0.05f),
        surfaceContainer = over(t, 0.075f),
        surfaceContainerHigh = over(t, 0.10f),
        surfaceContainerHighest = over(t, 0.13f),
    ) else lightColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = over(p.accent, 0.16f),
        onPrimaryContainer = lerp(p.accent, p.ink, 0.45f),
        inversePrimary = lerp(p.accent, White, 0.45f),
        secondary = lerp(p.accent, p.inkMuted, 0.45f),
        onSecondary = White,
        secondaryContainer = over(p.accent, 0.12f),
        onSecondaryContainer = p.ink,
        tertiary = p.accent2,
        onTertiary = White,
        tertiaryContainer = over(p.accent2, 0.16f),
        onTertiaryContainer = lerp(p.accent2, p.ink, 0.45f),
        background = p.base,
        onBackground = p.ink,
        surface = p.base,
        onSurface = p.ink,
        surfaceVariant = over(t, 0.55f),
        onSurfaceVariant = p.inkMuted,
        surfaceTint = p.accent,
        inverseSurface = lerp(p.ink, p.base, 0.12f),
        inverseOnSurface = p.base,
        error = p.danger,
        onError = White,
        errorContainer = over(p.danger, 0.14f),
        onErrorContainer = lerp(p.danger, p.ink, 0.4f),
        outline = over(p.ink, 0.22f),
        outlineVariant = over(p.ink, 0.11f),
        scrim = Color.Black,
        surfaceBright = over(t, 0.9f),
        surfaceDim = lerp(p.base, p.ink, 0.06f),
        surfaceContainerLowest = over(t, 0.85f),
        surfaceContainerLow = over(t, 0.62f),
        surfaceContainer = over(t, 0.72f),
        surfaceContainerHigh = over(t, 0.55f),
        surfaceContainerHighest = over(t, 0.42f),
    )
}
