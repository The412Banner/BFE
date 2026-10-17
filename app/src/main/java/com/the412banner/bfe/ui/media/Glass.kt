package com.the412banner.bfe.ui.media

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.the412banner.bfe.ui.theme.BfeTheme

/** Shapes of the media screens' frosted-glass panels. */
internal object Glass {
    val Panel = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * Aurora-glass panel over a photo or video: a translucent dark surface with a 1 dp light hairline
 * and rounded corners, all from the theme's colour roles. [strong] is the denser fill for panels that
 * carry a lot of text (the effects sheet, details).
 */
@Composable
internal fun Modifier.glass(shape: Shape = Glass.Panel, strong: Boolean = false): Modifier {
    val cs = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(cs.surfaceContainerLowest.copy(alpha = if (strong) 0.88f else 0.58f))
        .border(1.dp, cs.onSurface.copy(alpha = 0.16f), shape)
}

/**
 * Keeps taps on a panel's empty parts (titles, padding) from falling through to the gesture layer
 * underneath, where they would toggle the bars. The panel's own buttons still get theirs first.
 */
internal fun Modifier.absorbTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * The viewer / player theme: BFE's dark scheme whatever the app theme is (controls sit on black),
 * transparent system bars drawn over by the content, light bar icons.
 */
@Composable
internal fun MediaTheme(content: @Composable () -> Unit) {
    BfeTheme(forceDark = true) {
        val view = LocalView.current
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

/** Shows or hides the status + navigation bars (a swipe from the edge brings them back briefly). */
@Composable
internal fun SystemBarsVisible(visible: Boolean) {
    val view = LocalView.current
    LaunchedEffect(visible) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/** Keeps the screen awake while [on] (slideshow, playback). */
@Composable
internal fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** A round glass button for the media bars. [active] tints the icon with the accent. */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .glass(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = when {
                !enabled -> LocalContentColor.current.copy(alpha = 0.38f)
                active -> MaterialTheme.colorScheme.primary
                else -> LocalContentColor.current
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Icon over a short label, for a glass action bar (Share · Open with · Rotate · Info · Delete). */
@Composable
internal fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val tint = when {
        !enabled -> LocalContentColor.current.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
