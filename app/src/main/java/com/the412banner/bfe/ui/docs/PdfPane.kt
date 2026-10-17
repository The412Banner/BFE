package com.the412banner.bfe.ui.docs

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.docs.DocException
import com.the412banner.bfe.ui.theme.isAurora
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A PDF opened with Android's [PdfRenderer]. The renderer holds one page open at a time and isn't
 * thread-safe, so every call goes through one worker thread; [close] queues behind any render.
 */
internal class PdfDoc private constructor(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) : Closeable {
    val pageCount: Int = renderer.pageCount

    /** Each page's size in PDF points (width, height). */
    val sizes: List<Pair<Int, Int>> = (0 until pageCount).map { i -> renderer.openPage(i).use { it.width.coerceAtLeast(1) to it.height.coerceAtLeast(1) } }

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "bfe-pdf").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    @Volatile private var closed = false

    /** Page [index] drawn [widthPx] wide on white. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(dispatcher) {
        if (closed) return@withContext null
        renderer.openPage(index).use { page ->
            val w = widthPx.coerceAtLeast(16)
            val h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(16)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            val m = Matrix().apply { setScale(w / page.width.toFloat(), h / page.height.toFloat()) }
            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.execute { runCatching { renderer.close() }; runCatching { pfd.close() } }
        executor.shutdown()
    }

    companion object {
        /** Opens [file]; a password-protected or damaged PDF throws a [DocException] saying so. */
        fun open(file: File): PdfDoc {
            val pfd = try { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } catch (e: IOException) {
                throw DocException("The PDF couldn't be opened.", e)
            }
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: SecurityException) {
                pfd.close()
                throw DocException("This PDF is password-protected. BFE's viewer can't open protected PDFs; open it with another app.", e)
            } catch (e: Exception) {
                pfd.close()
                throw DocException("This PDF is damaged or isn't really a PDF.", e)
            }
            return try { PdfDoc(pfd, renderer) } catch (e: Exception) {
                runCatching { renderer.close() }; pfd.close()
                throw DocException("This PDF is damaged or isn't really a PDF.", e)
            }
        }
    }
}

/**
 * Where the PDF view is: zoom (1 = pages fit the width), the vertical scroll through the zoomed
 * pages and the horizontal pan, all in pixels, all read during layout — so a pinch moves zoom and
 * both offsets in one step and the point under the fingers stays put.
 */
@Stable
internal class PdfViewState(private val sizes: List<Pair<Int, Int>>) {
    var zoom by mutableFloatStateOf(1f)
    var scrollY by mutableFloatStateOf(0f)
    var panX by mutableFloatStateOf(0f)
    /** The zoom pages are rendered for (updated when a gesture ends, so pinching doesn't re-render). */
    var renderZoom by mutableFloatStateOf(1f)
    /** Set from the layout's constraints on every composition (plain fields: not observed). */
    var viewportW = 0
    var viewportH = 0
    var gapPx = 0

    private var prefix = DoubleArray(0)   // page tops at zoom 1, without gaps
    private var heights = DoubleArray(0)
    private var laidOutFor = -1

    /** Page heights at zoom 1 for a viewport [w] px wide. */
    fun layoutFor(w: Int) {
        if (w == laidOutFor || w <= 0) return
        laidOutFor = w
        heights = DoubleArray(sizes.size) { i -> w.toDouble() * sizes[i].second / sizes[i].first }
        prefix = DoubleArray(sizes.size + 1).also { p -> for (i in sizes.indices) p[i + 1] = p[i] + heights[i] }
    }

    fun top(i: Int, z: Float = zoom): Float = (prefix[i] * z).toFloat() + gapPx * (i + 1)
    fun height(i: Int, z: Float = zoom): Float = (heights[i] * z).toFloat()
    fun contentHeight(z: Float = zoom): Float = if (sizes.isEmpty()) 0f else top(sizes.size - 1, z) + height(sizes.size - 1, z) + gapPx
    fun maxScrollY(z: Float = zoom): Float = (contentHeight(z) - viewportH).coerceAtLeast(0f)
    fun maxPanX(z: Float = zoom): Float = (viewportW * (z - 1f)).coerceAtLeast(0f)

    /** The page at content offset [y]. */
    fun pageAt(y: Float, z: Float = zoom): Int {
        if (sizes.isEmpty()) return 0
        var lo = 0
        var hi = sizes.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (top(mid, z) <= y) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** The page in the middle of the screen (the "3 / 41" one). */
    val currentPage: Int get() = if (laidOutFor <= 0) 0 else pageAt(scrollY + viewportH / 2f)

    fun scrollBy(dy: Float): Float {
        val old = scrollY
        scrollY = (scrollY + dy).coerceIn(0f, maxScrollY())
        return scrollY - old
    }

    fun panBy(dx: Float): Float {
        val old = panX
        panX = (panX + dx).coerceIn(0f, maxPanX())
        return panX - old
    }

    fun goTo(page: Int) {
        if (laidOutFor <= 0) { pendingPage = page; return }
        scrollY = (top(page.coerceIn(0, sizes.size - 1)) - gapPx).coerceIn(0f, maxScrollY())
    }

    var pendingPage = -1

    /** Zooms to [target] keeping the content point under [focus] (screen px) where it is, then pans by [pan]. */
    fun zoomAround(target: Float, focus: Offset, pan: Offset = Offset.Zero) {
        val z0 = zoom
        val z1 = target.coerceIn(1f, MAX_ZOOM)
        // Content position under the focus at the old zoom, in zoom-1 units.
        val cx = (panX + focus.x) / z0
        val cy = scrollY + focus.y
        val page = pageAt(cy, z0)
        val frac = ((cy - top(page, z0)) / height(page, z0).coerceAtLeast(1f)).coerceIn(-0.5f, 1.5f)
        zoom = z1
        panX = (cx * z1 - focus.x - pan.x).coerceIn(0f, maxPanX(z1))
        scrollY = (top(page, z1) + frac * height(page, z1) - focus.y - pan.y).coerceIn(0f, maxScrollY(z1))
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}

/** Rendered pages kept for redraw (a few screens' worth; bitmaps live in native memory on API 26+). */
internal class PageCache(maxBytes: Int = 96 * 1024 * 1024) {
    private val lru = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }
    fun get(page: Int): Bitmap? = lru.get(page)
    fun put(page: Int, bmp: Bitmap) { lru.put(page, bmp) }
    fun clear() = lru.evictAll()
}

/** Inverts a page's colours (night mode) — black text on white becomes light on black. */
private val NightFilter = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

/**
 * The pages of [doc], top to bottom, fitted to the width. Pinch or double-tap to zoom (1–5×); drag to
 * scroll and, when zoomed, to pan. Pages render on demand at the settled zoom (capped per page so a
 * zoomed-in page stays within ~20 MB) and stay sharp after zooming.
 */
@Composable
internal fun PdfPane(doc: PdfDoc, state: PdfViewState, night: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    val density = LocalDensity.current
    val cache = remember(doc) { PageCache() }
    val scope = rememberCoroutineScope()
    val zoomAnim = remember { Animatable(1f) }
    val vc = LocalViewConfiguration.current
    state.gapPx = with(density) { 8.dp.roundToPx() }
    val vScroll = rememberScrollableState { delta -> -state.scrollBy(-delta) }
    val hScroll = rememberScrollableState { delta -> -state.panBy(-delta) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            // Pinch (two fingers) and double-tap, seen before the scrollers so a pinch never scrolls.
            .pointerInput(doc) {
                var lastTapAt = 0L
                var lastTapPos = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinched = false
                    var moved = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            pinched = true
                            val zc = event.calculateZoom()
                            val c = event.calculateCentroid(useCurrent = true)
                            if (c != Offset.Unspecified) state.zoomAround(state.zoom * zc, c, event.calculatePan())
                            event.changes.forEach { it.consume() }
                        } else if (event.changes.any { (it.position - down.position).getDistance() > vc.touchSlop }) {
                            moved = true
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinched) { state.renderZoom = state.zoom; return@awaitEachGesture }
                    if (moved) return@awaitEachGesture
                    val now = System.currentTimeMillis()
                    if (now - lastTapAt < vc.doubleTapTimeoutMillis && (down.position - lastTapPos).getDistance() < vc.touchSlop * 4) {
                        lastTapAt = 0L
                        val focus = down.position
                        val target = if (state.zoom > 1.2f) 1f else 2.5f
                        scope.launch {
                            zoomAnim.snapTo(state.zoom)
                            zoomAnim.animateTo(target, tween(220)) { state.zoomAround(value, focus) }
                            state.renderZoom = state.zoom
                        }
                    } else {
                        lastTapAt = now
                        lastTapPos = down.position
                        onTap()
                    }
                }
            }
            .scrollable(hScroll, Orientation.Horizontal)
            .scrollable(vScroll, Orientation.Vertical),
    ) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        state.viewportW = w
        state.viewportH = h
        state.layoutFor(w)
        SideEffect { if (state.pendingPage >= 0) { val p = state.pendingPage; state.pendingPage = -1; state.goTo(p) } }
        // Which pages are on screen (plus one each side, so a scroll finds them ready).
        val first = (state.pageAt(state.scrollY) - 1).coerceAtLeast(0)
        val last = (state.pageAt(state.scrollY + h) + 1).coerceAtMost(doc.pageCount - 1)
        val renderZoom = state.renderZoom
        val pageGround = if (night) Color(0xFF111111) else Color.White
        Layout(
            content = {
                for (i in first..last) key(i) {
                    PdfPageView(doc, i, renderWidth(w, renderZoom, doc.sizes[i]), cache, night, pageGround)
                }
            },
        ) { measurables, c ->
            val z = state.zoom
            val placeables = measurables.mapIndexed { k, m ->
                val i = first + k
                val pw = (w * z).roundToInt().coerceAtLeast(1)
                val ph = state.height(i, z).roundToInt().coerceAtLeast(1)
                m.measure(Constraints.fixed(pw, ph))
            }
            layout(c.maxWidth, c.maxHeight) {
                placeables.forEachIndexed { k, p ->
                    val i = first + k
                    p.place(-state.panX.roundToInt(), (state.top(i, z) - state.scrollY).roundToInt())
                }
            }
        }
        if (doc.pageCount > 3) PdfScrubber(state, Modifier.align(Alignment.CenterEnd))
    }
}

/** The width to render a page of [size] points at: the zoomed width, capped at about 5 M pixels a page. */
private fun renderWidth(viewportW: Int, zoom: Float, size: Pair<Int, Int>): Int {
    val (pw, ph) = size
    val want = (viewportW * zoom).roundToInt()
    val cap = sqrt(5_000_000.0 * pw / ph).toInt()
    // Bucketed, so a small zoom change doesn't re-render.
    return (minOf(want, cap) / 128 * 128).coerceAtLeast(256)
}

@Composable
private fun PdfPageView(doc: PdfDoc, index: Int, targetW: Int, cache: PageCache, night: Boolean, ground: Color) {
    var bmp by remember(doc, index) { mutableStateOf(cache.get(index)) }
    LaunchedEffect(doc, index, targetW) {
        val have = bmp
        if (have != null && have.width >= targetW) return@LaunchedEffect
        try {
            doc.render(index, targetW)?.let { cache.put(index, it); bmp = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // A page the renderer chokes on stays a blank sheet with its number.
        }
    }
    Box(Modifier.fillMaxSize().background(ground)) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = if (night) NightFilter else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("${index + 1}", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** A fast-scroll handle on the right edge for long PDFs. */
@Composable
private fun PdfScrubber(state: PdfViewState, modifier: Modifier) {
    val density = LocalDensity.current
    val thumbH = with(density) { 44.dp.toPx() }
    val track = (state.viewportH - thumbH).coerceAtLeast(1f)
    val max = state.maxScrollY()
    if (max <= 0f) return
    val y = state.scrollY / max * track
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .width(28.dp)
            .fillMaxHeight()
            .pointerInput(state) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val f = ((change.position.y - thumbH / 2) / track).coerceIn(0f, 1f)
                    state.scrollY = f * state.maxScrollY()
                }
            },
    ) {
        Box(
            Modifier
                .padding(end = 4.dp)
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y.roundToInt()) }
                .width(6.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isAurora) accent.copy(alpha = 0.7f) else accent.copy(alpha = 0.55f)),
        )
    }
}
