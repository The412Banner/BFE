@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.the412banner.bfe.ui.media

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.map.Mapper
import coil.request.ImageRequest
import coil.size.Precision
import com.the412banner.bfe.MainActivity
import com.the412banner.bfe.core.FileUtils
import com.the412banner.bfe.media.MediaChanges
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.MediaRequest
import com.the412banner.bfe.media.MediaTypes
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.screens.OpenWithSheet
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.guessMime
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Model for images that must not be tiled (GIF, HEIC, AVIF, BMP…): telephoto only sub-samples Uri /
 * String models, so wrapping the Uri makes it fall back to Coil's single decode (animated where the
 * format is); the viewer's loader maps it straight back to the Uri.
 */
private class WholeImage(val uri: Uri)

/**
 * Full-screen photo viewer. A pager across [request]'s images; each page is a telephoto zoomable
 * image (pinch, animated double-tap zoom, pan with fling; large JPEG/PNG/WebP are sub-sampled into
 * tiles so they stay sharp without decoding the whole bitmap). Tap toggles the glass bars; swipe down
 * when not zoomed closes. Actions: Share, Open with (BFE's sheet), Rotate (view only; JPEG can save it
 * losslessly as EXIF), Info, Delete (confirm, then the next photo), and in ⋮ Slideshow, Set as…, Show
 * in folder.
 */
@Composable
internal fun PhotoViewerScreen(request: MediaRequest, onCurrentChanged: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val items = remember(request) { request.items.toMutableStateList() }
    val pager = rememberPagerState(initialPage = request.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val loader = rememberViewerImageLoader()

    var barsVisible by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val rotations = remember { mutableStateMapOf<String, Int>() }
    val versions = remember { mutableStateMapOf<String, Int>() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<MediaRef?>(null) }
    var details by remember { mutableStateOf<MediaRef?>(null) }
    var openWith by remember { mutableStateOf<MediaTarget?>(null) }

    // Enter: the backdrop fades in and the photo scales up from 92 %; leaving runs it backwards.
    val enter = remember { Animatable(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    fun close() {
        scope.launch {
            enter.animateTo(0f, tween(180))
            onClose()
        }
    }

    val current = items.getOrNull(pager.currentPage)
    LaunchedEffect(current?.key) { current?.key?.let(onCurrentChanged) }
    LaunchedEffect(items.size) { if (items.isEmpty()) onClose() }

    BackHandler {
        when {
            slideshow -> { slideshow = false; barsVisible = true }
            else -> close()
        }
    }
    // Zooming in is for looking: the bars step aside (a tap brings them back).
    LaunchedEffect(zoomed) { if (zoomed) barsVisible = false }
    SystemBarsVisible(barsVisible && !slideshow)
    KeepScreenOn(slideshow)

    // Slideshow: a slow, eased page turn every few seconds; ends on the last photo.
    LaunchedEffect(slideshow) {
        if (!slideshow) return@LaunchedEffect
        barsVisible = false
        while (true) {
            delay(3_500)
            val next = pager.currentPage + 1
            if (next >= items.size) { slideshow = false; barsVisible = true; break }
            pager.animateScrollToPage(next, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
    }

    fun setAs(item: MediaRef) {
        val t = mediaTarget(context, item) ?: return
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_ATTACH_DATA).apply {
                setDataAndType(t.uri, t.mime)
                putExtra("mimeType", t.mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Set as"))
        }.onFailure { AppSnackbar.show(context, "No app can use this as a wallpaper or photo") }
    }

    fun showInFolder(item: MediaRef) {
        val dir = item.path?.let { File(it).parentFile } ?: return
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_PATH, dir.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        onClose()
    }

    fun rotate(item: MediaRef) {
        rotations[item.key] = (rotations[item.key] ?: 0) + 90
    }

    fun saveRotation(item: MediaRef) {
        val path = item.path ?: return
        val degrees = ((rotations[item.key] ?: 0) % 360 + 360) % 360
        if (degrees == 0) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val exif = ExifInterface(path)
                    exif.rotate(degrees)
                    exif.saveAttributes()
                    MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null)
                }.isSuccess
            }
            if (ok) {
                rotations.remove(item.key)
                versions[item.key] = (versions[item.key] ?: 0) + 1
                MediaChanges.bump()
                AppSnackbar.show(context, "Rotation saved")
            } else AppSnackbar.show(context, "Couldn't save the rotation")
        }
    }

    fun delete(item: MediaRef) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { deletePhoto(context, item) }
            if (!ok) { AppSnackbar.show(context, "Couldn't delete ${item.name}"); return@launch }
            val idx = items.indexOfFirst { it.key == item.key }
            if (idx >= 0) items.removeAt(idx)
            MediaChanges.bump()
            AppSnackbar.show(context, "Deleted ${item.name}")
        }
    }

    // ── Overlays ──
    confirmDelete?.let { item ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this photo?") },
            text = { Text("${item.name} will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; delete(item) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    details?.let { item -> PhotoDetailsSheet(item, onDismiss = { details = null }) }
    openWith?.let { t ->
        OpenWithSheet(
            file = mediaDisplayLoc(t.item),
            uri = t.uri,
            detectedMime = t.mime,
            actions = emptyList(),
            allowWrite = t.item.path != null,
            onDismiss = { openWith = null },
        )
    }

    // ── Layout ──
    // How far the swipe-to-close has gone, read in the draw phase so dragging never recomposes.
    val dragRange = with(density) { 600.dp.toPx() }
    fun dragFraction() = (abs(dragY) / dragRange).coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value * (1f - dragFraction() * 0.85f) }
                .background(MaterialTheme.colorScheme.scrim),
        )
        HorizontalPager(
            state = pager,
            key = { items.getOrNull(it)?.key ?: it },
            beyondBoundsPageCount = 1,
            pageSpacing = 16.dp,
            userScrollEnabled = !slideshow,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 0.92f + 0.08f * enter.value
                    val d = 1f - dragFraction() * 0.15f
                    scaleX = s * d
                    scaleY = s * d
                    alpha = enter.value
                    translationY = dragY
                }
                // Swipe down (or up) to close while not zoomed: the photo follows the finger, the backdrop fades.
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !zoomed && !slideshow,
                    state = rememberDraggableState { d -> dragY += d },
                    onDragStopped = { velocity ->
                        val far = abs(dragY) > with(density) { 120.dp.toPx() } || abs(velocity) > with(density) { 1600.dp.toPx() }
                        if (far) close()
                        else animate(dragY, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ -> dragY = v }
                    },
                ),
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            key(item.key, versions[item.key] ?: 0) {
                PhotoPage(
                    item = item,
                    loader = loader,
                    rotation = rotations[item.key] ?: 0,
                    settled = pager.settledPage == page,
                    onTap = { if (slideshow) { slideshow = false; barsVisible = true } else barsVisible = !barsVisible },
                    onZoomed = { z -> if (page == pager.currentPage) zoomed = z },
                )
            }
        }

        val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(insets).padding(12.dp)) {
            // Top: back · name · position · slideshow · ⋮
            AnimatedVisibility(
                visible = barsVisible && !slideshow && current != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { -it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val item = current ?: return@AnimatedVisibility
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().glass().absorbTaps().padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${pager.currentPage + 1} / ${items.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { slideshow = true },
                        enabled = items.size > 1,
                    ) { Icon(Icons.Filled.Slideshow, "Slideshow") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        BfeDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Set as…") },
                                leadingIcon = { Icon(Icons.Filled.Wallpaper, null) },
                                onClick = { menuOpen = false; setAs(item) },
                            )
                            // Archive / cloud photos are BFE's temporary copies: nothing to show or save there.
                            val real = remember(item.key) { item.path != null && !isCacheCopy(context, item) }
                            if (real) {
                                DropdownMenuItem(
                                    text = { Text("Show in folder") },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                    onClick = { menuOpen = false; showInFolder(item) },
                                )
                            }
                            val turned = ((rotations[item.key] ?: 0) % 360) != 0
                            if (turned && real && MediaTypes.canSaveRotation(item.name)) {
                                DropdownMenuItem(
                                    text = { Text("Save rotation") },
                                    leadingIcon = { Icon(Icons.Filled.Save, null) },
                                    onClick = { menuOpen = false; saveRotation(item) },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom: Share · Open with · Rotate · Info · Delete
            AnimatedVisibility(
                visible = barsVisible && !slideshow && current != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val item = current ?: return@AnimatedVisibility
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().glass().absorbTaps().padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    BarAction(Icons.Filled.Share, "Share", onClick = { shareMedia(context, item) })
                    BarAction(Icons.AutoMirrored.Filled.OpenInNew, "Open with", onClick = { openWith = mediaTarget(context, item) })
                    BarAction(Icons.AutoMirrored.Filled.RotateRight, "Rotate", onClick = { rotate(item) }, active = ((rotations[item.key] ?: 0) % 360) != 0)
                    BarAction(Icons.Filled.Info, "Info", onClick = { details = item })
                    BarAction(
                        Icons.Filled.Delete,
                        "Delete",
                        onClick = { confirmDelete = item },
                        enabled = remember(item.key) { canDeletePhoto(context, item) },
                    )
                }
            }
        }
    }
}

/** One page: the zoomable photo, turned by [rotation] and re-fitted so a turned photo still fills the screen. */
@Composable
private fun PhotoPage(
    item: MediaRef,
    loader: ImageLoader,
    rotation: Int,
    settled: Boolean,
    onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val zoomState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f))
    val imageState = rememberZoomableImageState(zoomState)
    var failed by remember(item.uri) { mutableStateOf(false) }
    var slowLoad by remember(item.uri) { mutableStateOf(false) }
    // The photo's fitted size at zoom 1, captured while not zoomed (the turn re-fit is based on it).
    var baseSize by remember(item.uri) { mutableStateOf(Size.Zero) }

    // Each page starts un-zoomed: leaving a page resets it.
    LaunchedEffect(settled) { if (!settled) zoomState.resetZoom(withAnimation = false) }
    LaunchedEffect(zoomState) {
        snapshotFlow { (zoomState.zoomFraction ?: 0f) > 0.01f }.distinctUntilChanged().collect { onZoomed(it) }
    }
    LaunchedEffect(zoomState) {
        snapshotFlow { zoomState.transformedContentBounds to (zoomState.zoomFraction ?: 0f) }.collect { (b, z) ->
            if (z <= 0.01f && b.width > 0f && b.height > 0f) baseSize = b.size
        }
    }
    LaunchedEffect(rotation) { if (rotation != 0) zoomState.resetZoom(withAnimation = true) }
    LaunchedEffect(item.uri) { delay(400); slowLoad = true }

    val model = remember(item.uri) { viewerRequest(context, item) { failed = true } }
    val turn by animateFloatAsState(rotation.toFloat(), tween(340, easing = FastOutSlowInEasing), label = "turn")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ZoomableAsyncImage(
            model = model,
            contentDescription = item.name,
            state = imageState,
            imageLoader = loader,
            onClick = { onTap() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = turn
                    if (turn != 0f && baseSize.width > 0f && baseSize.height > 0f) {
                        // Scale the turned photo's bounding box to fit the screen: 1 at 0° / 180°, and at
                        // 90° a landscape photo on a portrait screen grows to the full width.
                        val r = Math.toRadians(turn.toDouble())
                        val c = abs(cos(r)).toFloat()
                        val s = abs(sin(r)).toFloat()
                        val bw = baseSize.width * c + baseSize.height * s
                        val bh = baseSize.width * s + baseSize.height * c
                        val k = min(size.width / bw, size.height / bh).coerceIn(0.2f, 4f)
                        scaleX = k
                        scaleY = k
                    }
                },
        )
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                Spacer(Modifier.size(8.dp))
                Text("Can't show this image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            slowLoad && !imageState.isImageDisplayed && !imageState.isPlaceholderDisplayed ->
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        }
    }
}

/**
 * The request for one photo. Sub-sampling formats go in as their Uri (telephoto tiles them);
 * the rest as [WholeImage], decoded once at up to 2560 px on the long side — sharp enough to zoom,
 * bounded in memory, and animated when the format is (GIF, animated WebP / HEIF).
 */
private fun viewerRequest(context: Context, item: MediaRef, onError: () -> Unit): ImageRequest {
    val uri = Uri.parse(item.uri)
    val tiled = MediaTypes.canSubsample(item.name)
    return ImageRequest.Builder(context)
        .data(if (tiled) uri else WholeImage(uri))
        .apply { if (!tiled) size(2560).precision(Precision.INEXACT) }
        .listener(onError = { _, _ -> onError() })
        .build()
}

/** The app's Coil loader (shared caches) plus GIF / animated WebP decoding and the [WholeImage] mapping. */
@Composable
private fun rememberViewerImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        context.imageLoader.newBuilder()
            .components {
                add(Mapper<WholeImage, Uri> { data, _ -> data.uri })
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
    }
}

/** BFE's own temporary copy (a file opened from inside an archive or from the cloud). */
private fun isCacheCopy(context: Context, item: MediaRef): Boolean {
    val p = item.path ?: return false
    return runCatching { File(p).canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator) }.getOrDefault(false)
}

/** Real files BFE can write, and SAF documents; never cache copies or other apps' content Uris. */
private fun canDeletePhoto(context: Context, item: MediaRef): Boolean {
    if (isCacheCopy(context, item)) return false
    item.path?.let { p -> return File(p).parentFile?.canWrite() == true }
    val uri = Uri.parse(item.uri)
    return runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)
}

private fun deletePhoto(context: Context, item: MediaRef): Boolean {
    val p = item.path
    if (p != null) {
        val f = File(p)
        val ok = !f.exists() || FileUtils.delete(f)
        if (ok) MediaScannerConnection.scanFile(context.applicationContext, arrayOf(p), null, null)
        return ok
    }
    return runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(item.uri)) }.getOrDefault(false)
}
