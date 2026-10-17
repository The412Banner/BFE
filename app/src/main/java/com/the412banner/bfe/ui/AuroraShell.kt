package com.the412banner.bfe.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.the412banner.bfe.ui.screens.home.AppRoute
import com.the412banner.bfe.ui.theme.BfeMotion
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.isAurora
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color

/**
 * Aurora Glass's screen host: how the app's top-level screens (Home, the file manager, the category
 * browser, Apps, Cloud, Remote, Network, Settings) hand over to each other. Classic keeps its instant
 * swaps (MainActivity); everything here is Aurora-only.
 *
 * Aurora's screens are glass over the shared backdrop, so a screen underneath would show through the
 * one on top. Each layer therefore stops DRAWING once it is fully covered (it stays composed, so the
 * file manager keeps its panes, selections and scroll) and takes no touches while another is on top.
 */
object LaunchOrigin {
    /**
     * Where the next screen grows from: the centre of the Home tile that was tapped, as a fraction of
     * the screen. This emulates a shared-element "tile becomes the screen" transition on Compose 1.6,
     * which has no SharedTransitionLayout (that arrives in Compose 1.7).
     */
    var pivot: TransformOrigin = TransformOrigin.Center
        private set

    /** Records the tapped tile's centre ([center] in root coordinates, [rootWidth]×[rootHeight] the screen). */
    fun from(center: Offset, rootWidth: Float, rootHeight: Float) {
        if (rootWidth > 0f && rootHeight > 0f) {
            pivot = TransformOrigin((center.x / rootWidth).coerceIn(0f, 1f), (center.y / rootHeight).coerceIn(0f, 1f))
        }
    }

    fun reset() { pivot = TransformOrigin.Center }
}

/** Swallows touches so nothing underneath an opaque-acting glass screen can be tapped through it. */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}

/**
 * The file manager's layer: grows in from the tapped tile (scale 0.92 → 1 + fade, 380 ms emphasized
 * decelerate) when it becomes the visible screen, and recedes (220 ms) when covered. Fully hidden ⇒
 * not drawn at all.
 */
@Composable
fun Modifier.auroraScreenLayer(visible: Boolean): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible, reduced) {
        val target = if (visible) 1f else 0f
        if (reduced) progress.snapTo(target)
        else progress.animateTo(
            target,
            if (visible) tween(380, easing = BfeMotion.EmphasizedDecelerate) else tween(220, easing = BfeMotion.EmphasizedAccelerate),
        )
    }
    return this
        .graphicsLayer {
            val p = progress.value
            alpha = p
            val s = 0.92f + 0.08f * p
            scaleX = s
            scaleY = s
            transformOrigin = LaunchOrigin.pivot
        }
        .drawWithContent { if (progress.value > 0.002f) drawContent() }
}

/**
 * Home and the screens opened from it. Tile → screen grows from the tile; Back shrinks the screen back
 * into it while Home settles in behind; Home ↔ file manager is a soft fade + scale. [hidden] (Settings
 * on top) fades the whole host out.
 */
@Composable
fun AuroraRouteHost(route: AppRoute, hidden: Boolean, content: @Composable (AppRoute) -> Unit) {
    val reduced = LocalReducedMotion.current
    val shown: AppRoute? = route.takeIf { it != AppRoute.FILES }
    Box(Modifier.fillMaxSize().auroraCover(hidden)) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = { routeTransform(reduced) },
            label = "auroraRoute",
        ) { r ->
            if (r != null) {
                Box(Modifier.fillMaxSize().blockTouchesBelow()) { content(r) }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<AppRoute?>.routeTransform(reduced: Boolean): ContentTransform {
    val from = initialState
    val to = targetState
    if (reduced) return (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false))
    val origin = LaunchOrigin.pivot
    val enterTween = tween<Float>(380, easing = BfeMotion.EmphasizedDecelerate)
    val exitTween = tween<Float>(220, easing = BfeMotion.EmphasizedAccelerate)
    val t = when {
        // Into the file manager (it animates its own layer) / back out of it to Home.
        to == null -> EnterTransition.None togetherWith (fadeOut(exitTween) + scaleOut(exitTween, targetScale = 1.04f))
        from == null -> (fadeIn(enterTween) + scaleIn(enterTween, initialScale = 1.04f)) togetherWith ExitTransition.None
        // Home → a screen: grows out of its tile; Home eases back.
        from == AppRoute.HOME ->
            (fadeIn(enterTween) + scaleIn(enterTween, initialScale = 0.86f, transformOrigin = origin)) togetherWith
                (fadeOut(exitTween) + scaleOut(exitTween, targetScale = 1.04f))
        // A screen → Home: shrinks back into its tile above Home.
        to == AppRoute.HOME ->
            ((fadeIn(enterTween) + scaleIn(enterTween, initialScale = 1.04f)) togetherWith
                (fadeOut(exitTween) + scaleOut(exitTween, targetScale = 0.86f, transformOrigin = origin)))
                .apply { targetContentZIndex = -1f }
        else -> fadeIn(enterTween) togetherWith fadeOut(exitTween)
    }
    return t.using(SizeTransform(clip = false))
}

/**
 * Settings over everything. Classic: appears and disappears instantly on the page background, as it
 * always has. Aurora: rises with a short slide + fade over the backdrop and leaves the same way. The
 * same host (and so the same Settings composition) serves both looks.
 */
@Composable
fun SettingsHost(visible: Boolean, content: @Composable () -> Unit) {
    val aurora = isAurora
    val animate = aurora && !LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (!animate) EnterTransition.None else
            fadeIn(tween(300, easing = BfeMotion.EmphasizedDecelerate)) +
                slideInVertically(tween(380, easing = BfeMotion.EmphasizedDecelerate)) { it / 12 },
        exit = if (!animate) ExitTransition.None else
            fadeOut(tween(200, easing = BfeMotion.EmphasizedAccelerate)) +
                slideOutVertically(tween(220, easing = BfeMotion.EmphasizedAccelerate)) { it / 16 },
        label = "settings",
    ) {
        // A Surface blocks touches to the screen underneath in both looks; Aurora's is clear glass ground.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (aurora) Color.Transparent else MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}

/** Fades a layer out while something covers it, and stops drawing it once it is fully covered. */
@Composable
private fun Modifier.auroraCover(covered: Boolean): Modifier {
    val reduced = LocalReducedMotion.current
    val shown = remember { Animatable(if (covered) 0f else 1f) }
    LaunchedEffect(covered, reduced) {
        val target = if (covered) 0f else 1f
        if (reduced) shown.snapTo(target) else shown.animateTo(target, tween(if (covered) 260 else 320, easing = BfeMotion.Emphasized))
    }
    return graphicsLayer { alpha = shown.value }.drawWithContent { if (shown.value > 0.002f) drawContent() }
}
