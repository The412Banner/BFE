package com.the412banner.bfe.ui.theme

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Everything the Aurora Glass look adds to the theme for one palette + light/dark: the Material colour
 * scheme, the glass recipe and Aurora's rounder shapes (16–24 dp cards, 24 dp dialogs; menus are 12 dp
 * via BfeDropdownMenu).
 */
@Immutable
internal class AuroraLook(
    val colors: PaletteColors,
    val scheme: ColorScheme,
    val glass: GlassTokens,
    val shapes: Shapes,
) {
    /**
     * Edge-to-edge main window: the backdrop runs under transparent status and navigation bars and the
     * root pads its content for them (and for the keyboard, hence adjustResize). Other windows keep
     * fitting the bars, coloured with the backdrop's ground.
     */
    fun applyToWindow(window: Window, edgeToEdge: Boolean, originalSoftInput: Int) {
        if (edgeToEdge) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            window.setSoftInputMode(
                (originalSoftInput and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        } else {
            window.statusBarColor = colors.base.toArgb()
            window.navigationBarColor = colors.base.toArgb()
        }
    }
}

private val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The Aurora look for the persisted palette, wallpaper tinting it on Android 12+ when Dynamic colour is on. */
@Composable
internal fun rememberAuroraLook(dark: Boolean): AuroraLook {
    val context = LocalContext.current
    val palette = ThemeSettings.palette
    val dynamic = ThemeSettings.dynamicColor && ThemeSettings.dynamicSupported
    val base = paletteColors(palette, dark)
    // Wallpaper colours are read fresh each time the theme recomposes (like Classic's dynamic scheme);
    // the look itself is only rebuilt when the resulting colours actually differ.
    val colors = if (dynamic) base.withDynamic(context) else base
    // Haze's RenderNode blur runs on API 32+ (API 31 has a platform RenderEffect bug it works around
    // with a scrim), so real in-window blur starts at Android 12L.
    val realBlur = Build.VERSION.SDK_INT >= 32
    return remember(colors, realBlur) {
        AuroraLook(colors, auroraColorScheme(colors), glassTokens(colors, realBlur), AuroraShapes)
    }
}
