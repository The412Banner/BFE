package com.the412banner.bfe.ui.theme

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The living backdrop behind Aurora Glass: the palette's ground colour with three very large, very soft
 * light sources drifting on slow 22 / 26 / 30 s orbits (barely there in Neutral, vivid in Aurora & co.).
 *
 * Cheap by design: it draws in its own layer (nothing else is re-recorded when it moves), it advances
 * ~15 times a second rather than every frame (at these speeds a step is under half a pixel), it stops
 * whenever BFE is not in the foreground, and it stands still when the system says "remove animations".
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier) {
    val p = LocalGlass.current.palette
    val reduced = LocalReducedMotion.current
    val time = remember { mutableFloatStateOf(0f) }
    if (!reduced) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val start = SystemClock.uptimeMillis() - (time.floatValue * 1000f).toLong()
                while (isActive) {
                    time.floatValue = (SystemClock.uptimeMillis() - start) / 1000f
                    delay(66)
                }
            }
        }
    }
    Box(
        modifier
            .graphicsLayer { }
            .drawBehind {
                drawRect(p.base)
                val t = time.floatValue
                val w = size.width
                val h = size.height
                val unit = max(w, h)
                fun blob(color: Color, cx: Float, cy: Float, r: Float, ax: Float, ay: Float, period: Float, phase: Float) {
                    val k = 2f * PI.toFloat() * t / period + phase
                    val center = Offset(w * (cx + ax * sin(k)), h * (cy + ay * sin(k * 0.83f + 1.1f)))
                    val radius = unit * r * (1f + 0.08f * sin(k * 0.6f))
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to color.copy(alpha = p.blobAlpha),
                            0.55f to color.copy(alpha = p.blobAlpha * 0.35f),
                            1f to Color.Transparent,
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
                blob(p.blobs[0], 0.08f, 0.10f, 0.62f, 0.16f, 0.14f, 22f, 0f)
                blob(p.blobs[1], 0.95f, 0.52f, 0.55f, -0.14f, -0.10f, 26f, 2.1f)
                blob(p.blobs[2], 0.30f, 1.02f, 0.66f, 0.12f, -0.12f, 30f, 4.2f)
                // A faint top-to-bottom falloff keeps headers readable over the brightest blob.
                drawRect(
                    Brush.verticalGradient(
                        0f to p.base.copy(alpha = if (p.dark) 0.10f else 0.18f),
                        0.35f to Color.Transparent,
                        1f to p.base.copy(alpha = if (p.dark) 0.18f else 0.10f),
                    ),
                )
            },
    )
}

@Preview(name = "Backdrop — Neutral dark", widthDp = 360, heightDp = 640)
@Composable
private fun BackdropPreview() {
    BfeTheme(style = UiStyle.AURORA) { AuroraBackdrop(Modifier.size(360.dp, 640.dp)) }
}
