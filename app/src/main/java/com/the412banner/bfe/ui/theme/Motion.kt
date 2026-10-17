package com.the412banner.bfe.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Aurora Glass motion tokens. Every animation in the Aurora look picks its easing and duration from
 * here, so the app moves with one rhythm: emphasized easing for things that travel, gentle springs for
 * things that settle (pills, the pane divider, sheets), 250–400 ms throughout.
 *
 * [reduced] follows Android's "Remove animations" / animator duration scale = 0. When it is on, the
 * specs below collapse to a snap and the living backdrop and progress shimmer stand still.
 */
object BfeMotion {
    /** Material's emphasized curve: quick start, long gentle settle. For things that move across the screen. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Emphasized decelerate: for things arriving. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Emphasized accelerate: for things leaving. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SHORT = 200
    const val MEDIUM = 300
    const val LONG = 400

    /** Per-item delay of a staggered list entrance, and how many items take part. */
    const val STAGGER_STEP = 24
    const val STAGGER_MAX = 12
}

/** True when the system asks for no animations (animator duration scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the system animator scale; re-read whenever the activity resumes (the user may have changed it). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(animationsOff(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reduced = animationsOff(context) }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    return reduced
}

internal fun animationsOff(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

/** A tween on the emphasized curve, or a snap under reduced motion. */
fun <T> motionTween(reduced: Boolean, durationMillis: Int = BfeMotion.MEDIUM, easing: Easing = BfeMotion.Emphasized, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    if (reduced) snap() else tween(durationMillis, delayMillis, easing)

/** The settle spring used by pills, the divider and selection marks, or a snap under reduced motion. */
fun <T> motionSpring(reduced: Boolean, dampingRatio: Float = 0.78f, stiffness: Float = Spring.StiffnessMediumLow, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
    if (reduced) snap() else spring(dampingRatio, stiffness, visibilityThreshold)
