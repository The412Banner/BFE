package com.the412banner.bfe.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView

/**
 * The crossfade when the look, palette or light/dark theme changes: the window as it is right now is
 * copied (PixelCopy, API 26+), the change is applied underneath, and the copy fades out over the new
 * look. Nothing is composed twice, so panes, selections and scroll positions carry straight over.
 * Under "remove animations" (or if the copy fails) the change just applies.
 */
object LookTransition {
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal var token by mutableIntStateOf(0)

    /** Runs [apply] (which changes theme state) behind a crossfade from the current screen. */
    fun run(view: View, apply: () -> Unit) {
        val window = view.context.findActivity()?.window
        val decor = window?.decorView
        if (window == null || decor == null || decor.width <= 0 || decor.height <= 0 || animationsOff(view.context)) {
            apply(); return
        }
        val bitmap = runCatching { Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: run { apply(); return }
        try {
            PixelCopy.request(window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    snapshot = bitmap.asImageBitmap()
                    token++
                }
                apply()
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            apply()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Draws the pending [LookTransition] snapshot over everything and fades it out. Place last in the
 * root so it covers the whole screen; the image is aligned to the window, so it lines up even when the
 * new look lays the screen out differently (Aurora draws under the system bars, Classic does not).
 */
@Composable
fun LookTransitionOverlay() {
    val snap = LookTransition.snapshot ?: return
    val token = LookTransition.token
    val alpha = remember(token) { Animatable(1f) }
    // Where this overlay sits in the window: known from the host view at once, refined after layout.
    val view = LocalView.current
    var origin by remember {
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        mutableStateOf(Offset(loc[0].toFloat(), loc[1].toFloat()))
    }
    LaunchedEffect(token) {
        alpha.animateTo(0f, tween(durationMillis = 420, easing = BfeMotion.Emphasized))
        if (LookTransition.token == token) LookTransition.snapshot = null
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        drawImage(snap, topLeft = Offset(-origin.x, -origin.y), alpha = alpha.value)
    }
}
