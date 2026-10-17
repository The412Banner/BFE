package com.the412banner.bfe.ui.theme

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer

/**
 * Frosted windows for Aurora's dialogs and sheets. They are separate windows, so in-app blur can't
 * reach them; instead Android 12+ (API 31) blurs the whole screen behind the window in the compositor
 * ("blur behind"), which costs the app nothing. The system turns it off by itself under battery saver,
 * in some accessibility modes and on devices that can't afford it; [rememberWindowBlurAvailable]
 * follows that, so the surface can be made denser whenever there is no blur behind it.
 */
@Composable
fun rememberWindowBlurAvailable(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    val wm = remember(context) { context.getSystemService(WindowManager::class.java) }
        ?: return false
    var enabled by remember(wm) { mutableStateOf(wm.isCrossWindowBlurEnabled) }
    DisposableEffect(wm) {
        val listener = Consumer<Boolean> { enabled = it }
        runCatching { wm.addCrossWindowBlurEnabledListener(listener) }
        onDispose { runCatching { wm.removeCrossWindowBlurEnabledListener(listener) } }
    }
    return enabled
}

/**
 * Call from inside a Dialog's or bottom sheet's content: blurs the screen behind that window by
 * [radius] (and lightens the dim so the frost shows), on Android 12+. No-op elsewhere.
 */
@Composable
fun WindowBlurBehind(radius: Dp = 28.dp, dimAmount: Float = 0.32f) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, px) {
        runCatching { applyBlurBehind(view, px, dimAmount) }
        onDispose { }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applyBlurBehind(view: View, px: Int, dimAmount: Float) {
    // A Compose Dialog exposes its Window; a ModalBottomSheet is a plain view added to the window
    // manager, so its root view carries the window's LayoutParams.
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    if (dialogWindow != null) {
        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        dialogWindow.attributes = dialogWindow.attributes.apply { blurBehindRadius = px }
        dialogWindow.setDimAmount(dimAmount)
        return
    }
    val root = view.rootView
    val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    lp.blurBehindRadius = px
    root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, lp)
}
