package com.the412banner.bfe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.the412banner.bfe.ui.theme.BfeMotion
import com.the412banner.bfe.ui.theme.BfeTheme
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.LocalHazeState
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.UiStyle
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.glassShadow
import com.the412banner.bfe.ui.theme.motionSpring
import com.the412banner.bfe.ui.theme.motionTween

/*
 * Aurora Glass controls shared by every Aurora screen: round glass icon buttons, the floating pill
 * toolbar and its landscape twin (the vertical pill rail), the accent-gradient "+" button, section
 * labels, glass chips, and the shimmering progress bar. Classic never uses these.
 */

/** Scale-on-press, the one tactile response Aurora uses everywhere (springs back on release). */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.92f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val s by animateFloatAsState(if (isPressed) pressed else 1f, motionSpring(reduced, dampingRatio = 0.6f), label = "press")
    return graphicsLayer { scaleX = s; scaleY = s }
}

/**
 * A round icon button on glass. [selected] gives it the accent tint and selection fill (e.g. Home on
 * the Home screen, dual pane while split). [onGlass] = it sits inside a pill/rail (no own surface).
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    onGlass: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val g = LocalGlass.current
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val baseTint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor by animateColorAsState(
        when { !enabled -> baseTint.copy(alpha = 0.38f); selected -> accent; else -> baseTint },
        motionTween(reduced, BfeMotion.SHORT), label = "iconTint",
    )
    val fill by animateColorAsState(if (selected) g.selection else Color.Transparent, motionTween(reduced, BfeMotion.SHORT), label = "iconFill")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .pressScale(interaction)
            .then(if (onGlass) Modifier else Modifier.glass(CircleShape, GlassLevel.Panel))
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription, tint = iconColor, modifier = Modifier.size(iconSize))
    }
}

/** The accent-gradient round "+" (New) button that sits in the middle of the pill / rail. */
@Composable
fun AccentFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 46.dp,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val brush = remember(cs.primary, cs.tertiary) {
        Brush.linearGradient(listOf(cs.primary, androidx.compose.ui.graphics.lerp(cs.primary, cs.tertiary, 0.55f)))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .pressScale(interaction, pressed = 0.9f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .glassShadow(CircleShape, Color.Transparent, glow = cs.primary.copy(alpha = 0.55f), spread = 10.dp)
            .clip(CircleShape)
            .background(brush)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription, tint = cs.onPrimary, modifier = Modifier.size(24.dp))
    }
}

/** Aurora's primary button: an accent-gradient capsule with a soft glow (for the one main action on a screen). */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val brush = remember(cs.primary, cs.tertiary) {
        Brush.linearGradient(listOf(cs.primary, androidx.compose.ui.graphics.lerp(cs.primary, cs.tertiary, 0.55f)))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(48.dp)
            .pressScale(interaction, 0.96f)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .glassShadow(CapsuleShape, Color.Transparent, glow = cs.primary.copy(alpha = 0.45f), spread = 12.dp)
            .clip(CapsuleShape)
            .background(brush)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
    ) {
        if (icon != null) { Icon(icon, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = cs.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

/**
 * The floating glass pill toolbar (portrait, bottom centre, in the thumb zone). Real blur of the list
 * behind it on API 32+ when the screen provides a [LocalHazeState]; a denser frosted fill otherwise.
 */
@Composable
fun GlassPillBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .glass(Glass.Pill, GlassLevel.Floating, haze = LocalHazeState.current)
            .padding(5.dp),
        content = content,
    )
}

/** The landscape twin of [GlassPillBar]: a vertical glass pill rail floating at the left edge. */
@Composable
fun GlassPillRail(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .glass(Glass.Pill, GlassLevel.Floating, haze = LocalHazeState.current)
            .padding(5.dp),
        content = content,
    )
}

/** A small uppercase section label (STORAGE · LIBRARY · CONNECT …). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp),
    )
}

/** A glass chip: icon + label, optionally selected (accent). */
@Composable
fun GlassChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
) {
    val g = LocalGlass.current
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) g.selection else g.panel, motionTween(reduced, BfeMotion.SHORT), label = "chipFill")
    val ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressScale(interaction, 0.96f)
            .glass(Glass.Chip, GlassLevel.Panel, fill = fill, border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else Color.Unspecified)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Text(label, fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Aurora's progress bar: a rounded accent→accent-2 gradient with a soft highlight sweeping across it
 * (still under reduced motion). [progress] null = indeterminate (a gliding segment).
 */
@Composable
fun ShimmerProgressBar(progress: Float?, modifier: Modifier = Modifier, height: Dp = 4.dp, paused: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val shown by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), motionTween(reduced, 600, BfeMotion.Emphasized), label = "progress")
    val moving = !reduced && !paused
    val sweep = if (moving) {
        val t = rememberInfiniteTransition(label = "shimmer")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (progress == null) 1400 else 1900, easing = LinearEasing), RepeatMode.Restart), label = "sweep").value
    } else 0.35f
    val track = cs.onSurface.copy(alpha = if (LocalGlass.current.palette.dark) 0.12f else 0.10f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val (start, end) = if (progress == null) {
            val w = size.width * 0.32f
            val x = -w + (size.width + w) * sweep
            x.coerceAtLeast(0f) to (x + w).coerceAtMost(size.width)
        } else 0f to size.width * shown
        if (end > start) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(cs.primary, cs.tertiary), startX = 0f, endX = size.width),
                topLeft = Offset(start, 0f), size = Size(end - start, size.height), cornerRadius = r,
            )
            if (progress != null && moving) {
                val w = (end - start) * 0.35f
                val x = start - w + (end - start + w) * sweep
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startX = x, endX = x + w,
                    ),
                    topLeft = Offset(start, 0f), size = Size(end - start, size.height), cornerRadius = r,
                )
            }
        }
    }
}

/** Fades content in from slightly below on first show, [index] steps later than the first item. */
@Composable
fun Modifier.staggeredEnter(index: Int, key: Any?, enabled: Boolean = true): Modifier {
    val reduced = LocalReducedMotion.current
    if (reduced || !enabled || index >= BfeMotion.STAGGER_MAX) return this
    val anim = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(key) {
        anim.animateTo(1f, tween(360, delayMillis = 40 + index * BfeMotion.STAGGER_STEP, easing = BfeMotion.EmphasizedDecelerate))
    }
    return graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 12.dp.toPx()
    }
}

/** The pill toolbar and rail as they look on Home (for checking in Android Studio). */
@Preview(name = "Aurora pill + rail", widthDp = 380, heightDp = 320)
@Composable
private fun PillPreview() {
    BfeTheme(style = UiStyle.AURORA) {
        Box(Modifier.size(380.dp, 320.dp)) {
            com.the412banner.bfe.ui.theme.AuroraBackdrop(Modifier.matchParentSize())
            GlassPillRail(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
                GlassIconButton(Icons.Filled.Home, "Home", {}, selected = true, size = 40.dp)
                GlassIconButton(Icons.Filled.Search, "Search", {}, size = 40.dp)
                AccentFab(Icons.Filled.Add, "New", {}, modifier = Modifier.padding(vertical = 4.dp))
                GlassIconButton(Icons.Filled.MoreVert, "More", {}, size = 40.dp)
            }
            GlassPillBar(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                GlassIconButton(Icons.Filled.ArrowBack, "Back", {})
                GlassIconButton(Icons.Filled.Home, "Home", {}, selected = true)
                GlassIconButton(Icons.Filled.Search, "Search", {})
                AccentFab(Icons.Filled.Add, "New", {}, modifier = Modifier.padding(horizontal = 4.dp))
                GlassIconButton(Icons.Filled.MoreVert, "More", {})
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 24.dp, start = 80.dp, end = 16.dp)) {
                SectionLabel("Progress")
                ShimmerProgressBar(0.62f)
                Spacer(Modifier.height(12.dp))
                ShimmerProgressBar(null)
            }
        }
    }
}

/** A drawWithContent that hides the content entirely (used to keep a composed-but-covered layer cheap). */
fun Modifier.drawIf(visible: () -> Boolean): Modifier = drawWithContent { if (visible()) drawContent() }

/** The shape helper for pill-style capsules of any height. */
val CapsuleShape = RoundedCornerShape(percent = 50)
