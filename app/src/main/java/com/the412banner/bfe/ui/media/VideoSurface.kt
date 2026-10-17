package com.the412banner.bfe.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.the412banner.bfe.media.Placement
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The player's surface: a full-screen holder that clips, a frame sized and turned by
 * [com.the412banner.bfe.media.VideoGeometry], and inside it the video TextureView with the subtitle
 * TextureView (transparent) on top, both laid out exactly alike as libVLC expects. A TextureView —
 * not a SurfaceView — so the picture can take a colour matrix (layer paint), a sharpen shader and
 * view transforms in real time.
 */
internal class VideoSurfaceView(context: Context) : FrameLayout(context), VlcEngine.SurfaceHost {
    private var videoView = TextureView(context)
    private var subsView = newSubs()
    override val video: TextureView get() = videoView
    override val subs: TextureView get() = subsView
    private val frame = FrameLayout(context).apply { clipChildren = true }
    private var lastMatrix: FloatArray? = null
    private var lastSharpen = -1f

    init {
        clipChildren = true
        frame.addView(videoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        frame.addView(subsView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    private fun newSubs() = TextureView(context).apply { isOpaque = false }

    /** Fresh surfaces for a new libVLC instance, laid out and styled like the old ones. */
    override fun renewSurfaces() {
        val vlp = video.layoutParams
        val slp = subs.layoutParams
        frame.removeView(video)
        frame.removeView(subs)
        videoView = TextureView(context)
        subsView = newSubs()
        frame.addView(videoView, vlp)
        frame.addView(subsView, slp)
        lastMatrix?.let { m -> video.setLayerPaint(Paint().apply { colorFilter = ColorMatrixColorFilter(m) }) }
        if (lastSharpen > 0f && Build.VERSION.SDK_INT >= 33) Sharpen.apply(video, lastSharpen)
    }

    /** Applies a placement (null = fill, before the first video size arrives) and the turn / mirror. */
    fun place(p: Placement?, rotation: Int, flipH: Boolean, flipV: Boolean) {
        val flp = if (p == null) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        else LayoutParams(p.frameW, p.frameH, Gravity.CENTER)
        if (!sameLp(frame.layoutParams as? LayoutParams, flp)) frame.layoutParams = flp
        val slp = if (p == null) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        else LayoutParams(p.surfaceW, p.surfaceH).apply { leftMargin = p.surfaceX; topMargin = p.surfaceY }
        if (!sameLp(video.layoutParams as? LayoutParams, slp)) video.layoutParams = slp
        if (!sameLp(subs.layoutParams as? LayoutParams, slp)) subs.layoutParams = LayoutParams(slp)
        frame.rotation = (rotation % 360).toFloat()
        frame.scaleX = if (flipH) -1f else 1f
        frame.scaleY = if (flipV) -1f else 1f
    }

    private fun sameLp(a: LayoutParams?, b: LayoutParams): Boolean =
        a != null && a.width == b.width && a.height == b.height && a.leftMargin == b.leftMargin &&
            a.topMargin == b.topMargin && a.gravity == b.gravity

    /** The picture-tab colour matrix on the video layer only (subtitles keep their colours). */
    fun setColorMatrix(m: FloatArray?) {
        if (m == null && lastMatrix == null) return
        if (m != null && lastMatrix?.contentEquals(m) == true) return
        lastMatrix = m?.copyOf()
        video.setLayerPaint(m?.let { Paint().apply { colorFilter = ColorMatrixColorFilter(it) } })
    }

    /** Unsharp mask on Android 13+ (AGSL); a no-op below. */
    fun setSharpen(amount: Float) {
        if (amount == lastSharpen) return
        lastSharpen = amount
        if (Build.VERSION.SDK_INT >= 33) Sharpen.apply(video, amount)
    }
}

/** A small unsharp mask: the pixel minus the average of its four neighbours, scaled by `amount`. */
@RequiresApi(33)
private object Sharpen {
    private const val AGSL = """
        uniform shader content;
        uniform float amount;
        half4 main(float2 p) {
            half4 c = content.eval(p);
            half3 n = content.eval(p + float2(0.0, -1.5)).rgb;
            half3 s = content.eval(p + float2(0.0, 1.5)).rgb;
            half3 e = content.eval(p + float2(1.5, 0.0)).rgb;
            half3 w = content.eval(p + float2(-1.5, 0.0)).rgb;
            half3 blur = (n + s + e + w) * 0.25;
            half3 sharp = c.rgb + (c.rgb - blur) * half(amount);
            return half4(clamp(sharp, 0.0, 1.0), c.a);
        }
    """

    fun apply(view: View, amount: Float) {
        if (amount <= 0.001f) { view.setRenderEffect(null); return }
        runCatching {
            val shader = RuntimeShader(AGSL)
            shader.setFloatUniform("amount", amount * 2.5f)
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
        }
    }
}

/** Sharpen needs AGSL (Android 13+). */
internal val sharpenSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

/**
 * Film grain drawn over the picture: a tile of soft light/dark speckles re-positioned ~24 times a
 * second. Pure overlay, so it costs nothing in the decoder and works with hardware decoding.
 */
@Composable
internal fun FilmGrain(strength: Float, modifier: Modifier = Modifier) {
    if (strength <= 0f) return
    val tile = remember { grainTile(192) }
    val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(42)
            frame++
        }
    }
    Canvas(modifier) {
        val r = Random(frame)
        val dx = r.nextInt(192).toFloat()
        val dy = r.nextInt(192).toFloat()
        scale(2f, pivot = Offset.Zero) {
            translate(-dx, -dy) {
                drawRect(brush, topLeft = Offset.Zero, size = size.copy(width = size.width / 2f + 192f, height = size.height / 2f + 192f), alpha = (strength * 0.55f).coerceIn(0f, 0.6f))
            }
        }
    }
}

private fun grainTile(n: Int): ImageBitmap {
    val r = Random(0x412)
    val px = IntArray(n * n) {
        // Roughly normal noise around zero; light speckles white, dark ones black, alpha = magnitude.
        val v = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) / 1.5f
        val a = (kotlin.math.abs(v) * 255).toInt().coerceIn(0, 255)
        if (v >= 0) (a shl 24) or 0xFFFFFF else (a shl 24)
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}
