package com.the412banner.bfe.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlin.random.Random

/**
 * Aurora Glass surfaces.
 *
 * How the frosting works, by Android version:
 *  - Panels resting on the backdrop (panes, Home tiles, headers, cards) are translucent fills with a
 *    vertical sheen, a fine grain and a hairline whose top edge catches the light. What is behind them
 *    is BFE's own soft, already-blurred gradient, so a real blur would add nothing there and costs GPU
 *    time; this looks the same on every Android version.
 *  - Floating glass over content (the bottom pill toolbar, the selection pill, the landscape rail) takes
 *    a [HazeState]. On Android 12L / 13+ (API 32+) Haze re-draws the content behind the pill through a
 *    RenderEffect blur, and the pill's own fill is light. Below that, or when blur is off, the pill gets
 *    a denser fill + grain instead, so the list under it never reads through.
 *  - Menus, sheets and dialogs live in their own windows; see GlassWindow.kt for the system blur-behind
 *    used there on Android 12+.
 */

/** The active palette's glass recipe (Aurora only; Classic never reads it). */
val LocalGlass = staticCompositionLocalOf { glassTokens(paletteColors(BfePalette.NEUTRAL, dark = true), realBlur = false) }

/** The blur source for floating glass in the current screen (null = nothing to blur, use the dense fill). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** How high a piece of glass sits, which decides how dense it is. */
enum class GlassLevel { Panel, Raised, Floating, Overlay }

/** Aurora's corner radii and spacing, so every screen uses the same rhythm (4/8 dp grid). */
object Glass {
    val Card = RoundedCornerShape(20.dp)
    val Pane = RoundedCornerShape(22.dp)
    val Tile = RoundedCornerShape(18.dp)
    val Row = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(14.dp)
    val Menu = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(30.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog = RoundedCornerShape(24.dp)

    val HairlineWidth = 1.dp

    const val SPACE_XS = 4
    const val SPACE_S = 8
    const val SPACE_M = 12
    const val SPACE_L = 16
    const val SPACE_XL = 24
}

/** A small tiled grain texture, made once per process (128×128, ~64 KB). */
private object Grain {
    val bitmap: ImageBitmap by lazy {
        val n = 128
        val rnd = Random(0x412B)
        val px = IntArray(n * n) {
            val v = rnd.nextInt(256)
            val a = 40 + rnd.nextInt(160)
            (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val brush: ShaderBrush by lazy { ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)) }
}

/** The glass grain as a tiling brush (for surfaces that draw their own glass, e.g. animated pane cards). */
val grainBrush: Brush get() = Grain.brush

/** The fill of a piece of glass at [level], given whether real blur sits behind it. */
fun GlassTokens.fillFor(level: GlassLevel, blurred: Boolean): Color = when (level) {
    GlassLevel.Panel -> panel
    GlassLevel.Raised -> raised
    GlassLevel.Floating -> if (blurred) floatingOverBlur else floatingSolid
    GlassLevel.Overlay -> menu
}

/** The hairline: brighter along the top edge (light catching the rim), fading to the plain border. */
fun GlassTokens.rimBrush(border: Color = this.border): Brush =
    Brush.verticalGradient(0f to highlight, 0.18f to border, 1f to border.copy(alpha = border.alpha * 0.8f))

/**
 * Turns this element into a piece of glass with [shape]: optional soft outside shadow and accent
 * [glow], the frosted fill (real blur when [haze] is given and the device supports it), a top sheen,
 * grain, and the rim hairline drawn over the content.
 *
 * [fill] replaces the level's fill (e.g. an animated active-pane fill); [border] the rim colour.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    level: GlassLevel = GlassLevel.Panel,
    fill: Color = Color.Unspecified,
    border: Color = Color.Unspecified,
    glow: Color = Color.Unspecified,
    shadow: Boolean = level == GlassLevel.Floating || level == GlassLevel.Overlay,
    haze: HazeState? = null,
): Modifier {
    val g = LocalGlass.current
    val blurred = haze != null && g.realBlur
    val bg = if (fill.isSpecified) fill else g.fillFor(level, blurred)
    val rim = if (border.isSpecified) border else g.border
    val noiseAlpha = g.noiseAlpha
    val dark = g.palette.dark
    return this
        .then(if (shadow || glow.isSpecified) Modifier.glassShadow(shape, if (shadow) g.shadow else Color.Transparent, glow) else Modifier)
        .then(
            if (blurred) Modifier.hazeChild(haze!!, shape, HazeStyle(tint = Color.Transparent, blurRadius = 26.dp, noiseFactor = 0f))
            else Modifier,
        )
        .clip(shape)
        .drawBehind {
            drawRect(bg)
            // A soft sheen from the top: glass is a touch brighter where the light hits it.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.045f else 0.30f),
                    0.55f to Color.Transparent,
                ),
            )
            drawRect(Grain.brush, alpha = noiseAlpha)
        }
        .border(Glass.HairlineWidth, g.rimBrush(rim), shape)
}

/**
 * A soft shadow (and/or accent halo) drawn only OUTSIDE [shape], like a CSS box-shadow: the glass
 * itself stays clean because nothing dark is painted under its translucent fill. Built from a few
 * expanding outlines of falling alpha; cached per size.
 */
fun Modifier.glassShadow(shape: Shape, shadow: Color, glow: Color = Color.Unspecified, spread: Dp = 14.dp, drop: Dp = 6.dp): Modifier =
    drawWithCache {
        val steps = 6
        val spreadPx = spread.toPx()
        val dropPx = drop.toPx()
        val inner = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val rings = (1..steps).map { i ->
            val s = spreadPx * i / steps
            Path().apply { addOutline(shape.createOutline(Size(size.width + 2 * s, size.height + 2 * s), layoutDirection, this@drawWithCache)) } to s
        }
        onDrawBehind {
            clipPath(inner, ClipOp.Difference) {
                if (shadow.alpha > 0f) {
                    val a = shadow.alpha / steps
                    rings.forEach { (path, s) -> translate(-s, -s + dropPx * (s / spreadPx)) { drawPath(path, shadow.copy(alpha = a * 0.9f)) } }
                }
                if (glow.isSpecified && glow.alpha > 0f) {
                    val a = glow.alpha / steps
                    rings.take(steps - 1).forEach { (path, s) -> translate(-s * 0.8f, -s * 0.8f) { drawPath(path, glow.copy(alpha = a)) } }
                }
            }
        }
    }

/** A Box made of glass — the building block for cards, pills and capsules. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Glass.Card,
    level: GlassLevel = GlassLevel.Panel,
    fill: Color = Color.Unspecified,
    border: Color = Color.Unspecified,
    glow: Color = Color.Unspecified,
    haze: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.glass(shape, level, fill, border, glow, haze = haze), content = content)
}

/** A thin accent glow line, used under the active pane header and the drive chip. */
fun accentGradient(accent: Color, accent2: Color): Brush =
    Brush.linearGradient(listOf(accent, accent2), start = Offset.Zero, end = Offset.Infinite)

@Preview(name = "Glass levels (dark)", widthDp = 360, heightDp = 240)
@Composable
private fun GlassLevelsPreview() {
    BfeTheme(style = UiStyle.AURORA) {
        Box(Modifier.size(360.dp, 240.dp)) {
            AuroraBackdrop(Modifier.matchParentSize())
            GlassSurface(Modifier.padding(16.dp).size(150.dp, 90.dp), shape = Glass.Card) {}
            GlassSurface(Modifier.padding(start = 190.dp, top = 16.dp).size(150.dp, 90.dp), shape = Glass.Card, level = GlassLevel.Raised, glow = LocalGlass.current.glow) {}
            GlassSurface(Modifier.padding(start = 40.dp, top = 150.dp).size(280.dp, 56.dp), shape = Glass.Pill, level = GlassLevel.Floating) {}
        }
    }
}
