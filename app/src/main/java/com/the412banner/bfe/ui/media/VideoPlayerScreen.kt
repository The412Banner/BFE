@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.the412banner.bfe.ui.media

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.the412banner.bfe.media.AspectMode
import com.the412banner.bfe.media.CropMode
import com.the412banner.bfe.media.GestureMath
import com.the412banner.bfe.media.MediaPrefs
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.VideoGeometry
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.screens.OpenWithSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** The feedback pill in the middle of the screen while a gesture or a quick toggle is in progress. */
private sealed interface Hud {
    data class Brightness(val value: Float) : Hud
    data class Volume(val level: Float) : Hud
    data class Seek(val target: Long, val delta: Long, val length: Long) : Hud
    data class Zoom(val zoom: Float) : Hud
    data class Message(val text: String, val icon: ImageVector) : Hud
}

/** Double-tap seeking on one side: [totalMs] grows while the taps keep coming. */
private data class SeekRipple(val side: Int, val totalMs: Long, val tick: Int)

private const val DRAG_NONE = 0
private const val DRAG_SEEK = 1
private const val DRAG_BRIGHTNESS = 2
private const val DRAG_VOLUME = 3
private const val DRAG_PINCH = 4

private val QUICK_ASPECTS = listOf(AspectMode.BEST_FIT, AspectMode.FIT_SCREEN, AspectMode.FILL, AspectMode.R16_9, AspectMode.R4_3, AspectMode.ORIGINAL)
private val QUICK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * The video player: libVLC drawing into TextureViews, Compose controls on frosted glass that hide
 * after a few seconds, gestures (tap = controls, double tap left/right = ∓10 s and keeps adding while
 * you tap, double tap centre = pause, hold = 2× while held, swipe left half = brightness, right half =
 * volume up to VLC's 200 % boost, sideways = seek with live preview, pinch = zoom), lock, rotation,
 * aspect, speed, repeat, picture-in-picture, "Resume from", and the effects sheet. Audio files get a
 * music layout (cover art, title, artist) with the same controls.
 */
@Composable
internal fun VideoPlayerScreen(
    state: PlayerState,
    pipSupported: Boolean,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val engine = state.engine
    val s = state.settings
    val scope = rememberCoroutineScope()
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var sheetTab by remember { mutableStateOf<Int?>(null) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var ripple by remember { mutableStateOf<SeekRipple?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var speedBoost by remember { mutableStateOf(false) }
    var pinchZoom by remember { mutableStateOf<Float?>(null) }
    var lockHint by remember { mutableStateOf(false) }
    var openWith by remember { mutableStateOf<MediaTarget?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val audioOnly = state.audioOnly
    val showControls = (controlsVisible || audioOnly) && !state.locked && !state.inPip

    fun showHud(h: Hud) { hud = h; hudTick++ }
    fun seekStep(side: Int) {
        val r = ripple
        val total = if (r != null && r.side == side) r.totalMs + 10_000L else 10_000L
        ripple = SeekRipple(side, total, (r?.tick ?: 0) + 1)
        engine.seekBy(side * 10_000L)
    }

    LaunchedEffect(hudTick) { if (hud != null) { delay(900); hud = null } }
    LaunchedEffect(controlsVisible, poke, engine.isPlaying, sheetTab, scrubbing, audioOnly) {
        if (controlsVisible && engine.isPlaying && sheetTab == null && !scrubbing && !audioOnly) {
            delay(3_500)
            controlsVisible = false
        }
    }
    LaunchedEffect(ripple?.tick) { if (ripple != null) { delay(800); ripple = null } }
    LaunchedEffect(lockHint) { if (lockHint) { delay(2_500); lockHint = false } }
    LaunchedEffect(state.resumeOffer) { if (state.resumeOffer != null) { delay(9_000); state.dismissResume() } }
    LaunchedEffect(state.orientation) {
        activity?.requestedOrientation = when (state.orientation) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    SystemBarsVisible(audioOnly || showControls)
    KeepScreenOn(engine.isPlaying && !audioOnly)

    BackHandler {
        when {
            sheetTab != null -> sheetTab = null
            state.locked -> lockHint = true
            engine.isPlaying && engine.hasVideo && pipSupported && MediaPrefs.pipOnLeave(context) -> onEnterPip()
            else -> onClose()
        }
    }

    openWith?.let { t ->
        OpenWithSheet(
            file = mediaDisplayLoc(t.item),
            uri = t.uri,
            detectedMime = t.mime,
            actions = emptyList(),
            allowWrite = false,
            onDismiss = { openWith = null },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .onSizeChanged {
                containerSize = it
                engine.setWindowSize(it.width, it.height)
            },
    ) {
        // ── Picture ──
        val layout = engine.layout
        val placement = remember(layout, containerSize, s.aspect, s.crop, s.zoom, pinchZoom, s.rotation, state.inPip) {
            layout?.let {
                VideoGeometry.place(
                    containerSize.width, containerSize.height, it,
                    aspect = if (state.inPip) AspectMode.BEST_FIT else s.aspect,
                    crop = if (state.inPip) CropMode.NONE else s.crop,
                    zoom = if (state.inPip) 1f else pinchZoom ?: s.zoom,
                    rotation = s.rotation,
                )
            }
        }
        AndroidView(
            factory = { ctx -> VideoSurfaceView(ctx).also { v -> engine.attachHost(v) } },
            update = { v ->
                v.place(placement, s.rotation, s.flipH, s.flipV)
                v.setColorMatrix(if (s.hasColorChange) s.colorMatrix else null)
                v.setSharpen(s.sharpen)
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (audioOnly) 0f else 1f },
        )
        if (!audioOnly && engine.hasVideo) FilmGrain(s.grain, Modifier.fillMaxSize())
        if (audioOnly) AudioArtwork(state.current)

        // ── Gestures ──
        if (!state.inPip) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.locked, audioOnly, sheetTab) {
                        detectTapGestures(
                            onTap = { off ->
                                val r = ripple
                                when {
                                    sheetTab != null -> sheetTab = null
                                    state.locked -> lockHint = !lockHint
                                    r != null && GestureMath.tapSide(off.x, size.width.toFloat()) == r.side -> seekStep(r.side)
                                    else -> { controlsVisible = !controlsVisible; poke++ }
                                }
                            },
                            onDoubleTap = { off ->
                                if (!state.locked && !audioOnly && sheetTab == null) {
                                    val side = GestureMath.tapSide(off.x, size.width.toFloat())
                                    if (side == 0) {
                                        engine.toggle()
                                        showHud(Hud.Message(if (engine.isPlaying) "Paused" else "Playing", if (engine.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow))
                                    } else seekStep(side)
                                }
                            },
                            // A long press is "hold for 2×" (onPress below), never a tap.
                            onLongPress = { },
                            onPress = {
                                if (!state.locked && !audioOnly && sheetTab == null && engine.isPlaying) {
                                    val boost = scope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis + 80)
                                        speedBoost = true
                                        engine.overrideRate(2f)
                                    }
                                    tryAwaitRelease()
                                    boost.cancel()
                                    if (speedBoost) {
                                        speedBoost = false
                                        engine.overrideRate(null)
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(state.locked, audioOnly, sheetTab) {
                        if (state.locked || audioOnly || sheetTab != null) return@pointerInput
                        val edge = 44.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Leave the screen edges to the system's own swipe gestures.
                            if (down.position.y < edge || down.position.y > size.height - edge) return@awaitEachGesture
                            var mode = DRAG_NONE
                            var total = Offset.Zero
                            var startBrightness = 0.5f
                            var startLevel = 1f
                            var startTime = 0L
                            var seekTarget = 0L
                            var lastFastSeek = 0L
                            var boostNow = state.settings.boost
                            var zoomNow = state.settings.zoom
                            val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2 && (mode == DRAG_NONE || mode == DRAG_PINCH)) {
                                    mode = DRAG_PINCH
                                    zoomNow = (zoomNow * event.calculateZoom()).coerceIn(1f, 3f)
                                    pinchZoom = zoomNow
                                    showHud(Hud.Zoom(zoomNow))
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                if (mode == DRAG_PINCH) { event.changes.forEach { it.consume() }; continue }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                                total += change.positionChange()
                                if (mode == DRAG_NONE) {
                                    if (total.getDistance() < viewConfiguration.touchSlop) continue
                                    mode = when {
                                        abs(total.x) > abs(total.y) * 1.2f -> DRAG_SEEK
                                        down.position.x < size.width / 2f -> DRAG_BRIGHTNESS
                                        else -> DRAG_VOLUME
                                    }
                                    when (mode) {
                                        DRAG_SEEK -> { startTime = engine.timeMs; seekTarget = startTime }
                                        DRAG_BRIGHTNESS -> startBrightness = windowBrightness(activity)
                                        DRAG_VOLUME -> startLevel = GestureMath.levelOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC), maxIndex, state.settings.boost)
                                    }
                                }
                                change.consume()
                                when (mode) {
                                    DRAG_SEEK -> if (engine.seekable && engine.lengthMs > 0) {
                                        seekTarget = GestureMath.seekTarget(startTime, total.x, size.width.toFloat(), engine.lengthMs)
                                        showHud(Hud.Seek(seekTarget, seekTarget - startTime, engine.lengthMs))
                                        val now = SystemClock.uptimeMillis()
                                        if (now - lastFastSeek > 160) { engine.seekTo(seekTarget, fast = true); lastFastSeek = now }
                                    }
                                    DRAG_BRIGHTNESS -> {
                                        val b = GestureMath.brightnessAfterDrag(startBrightness, total.y, size.height.toFloat())
                                        setWindowBrightness(activity, b)
                                        showHud(Hud.Brightness(b))
                                    }
                                    DRAG_VOLUME -> {
                                        val level = GestureMath.volumeAfterDrag(startLevel, total.y, size.height.toFloat(), maxLevel = 2f)
                                        val (index, vlc) = GestureMath.splitVolume(level, maxIndex)
                                        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != index) {
                                            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
                                        }
                                        boostNow = vlc
                                        engine.previewVolume(vlc)
                                        showHud(Hud.Volume(level))
                                    }
                                }
                            }
                            when (mode) {
                                DRAG_SEEK -> if (engine.seekable && engine.lengthMs > 0) engine.seekTo(seekTarget)
                                DRAG_VOLUME -> if (boostNow != state.settings.boost) state.update { it.copy(boost = boostNow) }
                                DRAG_PINCH -> { state.update { it.copy(zoom = zoomNow) }; pinchZoom = null }
                            }
                        }
                    },
            )
        }

        // ── Double-tap seek ripples ──
        SeekRippleOverlay(ripple, Modifier.fillMaxSize())

        val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(insets).padding(12.dp)) {
            // Spinner while opening / buffering, after a short grace so quick opens don't flash it.
            var showSpinner by remember { mutableStateOf(false) }
            val busy = engine.error == null && (engine.opening || (engine.isPlaying && engine.buffering < 100f))
            LaunchedEffect(busy) { showSpinner = false; if (busy) { delay(400); showSpinner = true } }
            if (showSpinner && busy && !state.inPip) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp), strokeWidth = 3.dp)
            }

            // ── Controls ──
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopBar(
                    state = state,
                    pipSupported = pipSupported,
                    onClose = onClose,
                    onSheet = { sheetTab = it; poke++ },
                    onOpenWith = { state.current?.let { openWith = mediaTarget(context, it) } },
                    onShare = { state.current?.let { shareMedia(context, it) } },
                    onPip = onEnterPip,
                )
            }
            if (!audioOnly) {
                AnimatedVisibility(
                    visible = showControls && engine.error == null,
                    enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.9f),
                    exit = fadeOut(tween(200)) + scaleOut(tween(240), targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    CenterControls(state, onPoke = { poke++ })
                }
            } else {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 128.dp)) {
                    CenterControls(state, onPoke = { })
                }
            }
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(
                    state = state,
                    pipSupported = pipSupported,
                    onScrub = { active, target ->
                        scrubbing = active
                        poke++
                        if (active && target != null) showHud(Hud.Seek(target, target - engine.timeMs, engine.lengthMs))
                    },
                    onHud = { showHud(Hud.Message(it.first, it.second)) },
                    onPip = onEnterPip,
                    onPoke = { poke++ },
                )
            }

            // ── Lock: only the lock button, shown on a tap ──
            AnimatedVisibility(
                visible = state.locked && lockHint && !state.inPip,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.glass(Glass.Pill).padding(end = 14.dp)) {
                    GlassIconButton(Icons.Filled.Lock, "Unlock", onClick = { state.locked = false; lockHint = false; controlsVisible = true; poke++ }, size = 52.dp, active = true)
                    Spacer(Modifier.width(8.dp))
                    Text("Tap to unlock", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Hold for 2× ──
            AnimatedVisibility(
                visible = speedBoost,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.glass(Glass.Pill).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.FastForward, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2×", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── "Applying…" while libVLC restarts for an effect ──
            AnimatedVisibility(
                visible = state.reloading && !state.inPip,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.glass(Glass.Pill).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Applying…", style = MaterialTheme.typography.labelLarge)
                }
            }

            // ── Resume from ──
            val offer = state.resumeOffer
            AnimatedVisibility(
                visible = offer != null && !state.inPip && engine.timeMs < (offer ?: 0L) - 3_000,
                enter = fadeIn(tween(220)) + slideInHorizontally(tween(280)) { -it / 3 },
                exit = fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { -it / 3 },
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = if (showControls) 132.dp else 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.glass(Glass.Pill)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(Glass.Pill).clickable { state.acceptResume() }.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Icon(Icons.Filled.Replay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resume from ${GestureMath.formatTime(offer ?: 0L)}", style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = { state.dismissResume() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, "Start from the beginning", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Error ──
            engine.error?.let { msg ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 420.dp).glass(strong = true).padding(20.dp),
                ) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        TextButton(onClick = { state.current?.let { openWith = mediaTarget(context, it) } }) { Text("Open with…") }
                        if (state.hasNext) TextButton(onClick = { state.next() }) { Text("Next file") }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
            }
        }

        // ── Gesture / toggle feedback ──
        HudPill(hud, Modifier.align(Alignment.Center))

        // ── Effects sheet ──
        AnimatedVisibility(
            visible = sheetTab != null && !state.inPip,
            enter = if (landscape) fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it } else fadeIn(tween(200)) + slideInVertically(tween(300)) { it },
            exit = if (landscape) fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { it } else fadeOut(tween(200)) + slideOutVertically(tween(260)) { it },
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
        ) {
            var lastTab by remember { mutableIntStateOf(TAB_PICTURE) }
            sheetTab?.let { lastTab = it }
            PlayerSheet(
                state = state,
                tab = lastTab,
                onTab = { sheetTab = it },
                onClose = { sheetTab = null },
                landscape = landscape,
                audioOnly = audioOnly,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: PlayerState,
    pipSupported: Boolean,
    onClose: () -> Unit,
    onSheet: (Int) -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onPip: () -> Unit,
) {
    val engine = state.engine
    val ref = state.current
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().glass().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(ref?.name ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (state.items.size > 1) add("${state.index + 1} / ${state.items.size}")
                engine.layout?.let { add("${it.visibleWidth}×${it.visibleHeight}") }
                if (state.settings.speed != 1f) add(GestureMath.formatRate(state.settings.speed))
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (engine.hasVideo) IconButton(onClick = { onSheet(TAB_SUBTITLES) }) { Icon(Icons.Filled.ClosedCaption, "Subtitles") }
        IconButton(onClick = { onSheet(TAB_AUDIO) }) { Icon(Icons.Filled.Audiotrack, "Audio") }
        if (engine.hasVideo) IconButton(onClick = { onSheet(TAB_PICTURE) }) { Icon(Icons.Filled.Tune, "Picture and effects") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Open with…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    onClick = { menu = false; onOpenWith() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = { menu = false; onShare() },
                )
                if (pipSupported && engine.hasVideo) {
                    DropdownMenuItem(
                        text = { Text("Picture-in-picture") },
                        leadingIcon = { Icon(Icons.Filled.PictureInPictureAlt, null) },
                        onClick = { menu = false; onPip() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterControls(state: PlayerState, onPoke: () -> Unit) {
    val engine = state.engine
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.items.size > 1) {
            GlassIconButton(Icons.Filled.SkipPrevious, "Previous", onClick = { state.previous(); onPoke() }, size = 48.dp)
        }
        GlassIconButton(Icons.Filled.Replay10, "Back 10 seconds", onClick = { engine.seekBy(-10_000); onPoke() }, size = 52.dp, iconSize = 26.dp, enabled = engine.seekable)
        Box(
            modifier = Modifier.size(76.dp).glass(CircleShape).clickable { engine.toggle(); onPoke() },
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                engine.ended -> Icons.Filled.Replay
                engine.isPlaying -> Icons.Filled.Pause
                else -> Icons.Filled.PlayArrow
            }
            Crossfade(targetState = icon, animationSpec = tween(180), label = "playPause") { i ->
                Icon(i, if (engine.isPlaying) "Pause" else "Play", modifier = Modifier.size(40.dp))
            }
        }
        GlassIconButton(Icons.Filled.Forward10, "Forward 10 seconds", onClick = { engine.seekBy(10_000); onPoke() }, size = 52.dp, iconSize = 26.dp, enabled = engine.seekable)
        if (state.items.size > 1) {
            GlassIconButton(Icons.Filled.SkipNext, "Next", onClick = { state.next(); onPoke() }, size = 48.dp, enabled = state.hasNext)
        }
    }
}

@Composable
private fun BottomBar(
    state: PlayerState,
    pipSupported: Boolean,
    onScrub: (active: Boolean, target: Long?) -> Unit,
    onHud: (Pair<String, ImageVector>) -> Unit,
    onPip: () -> Unit,
    onPoke: () -> Unit,
) {
    val engine = state.engine
    val len = engine.lengthMs
    var scrub by remember { mutableStateOf<Float?>(null) }
    var lastFastSeek by remember { mutableLongStateOf(0L) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    val fraction = scrub ?: if (len > 0) (engine.timeMs.toFloat() / len).coerceIn(0f, 1f) else 0f
    val shownTime = scrub?.let { (it * len).toLong() } ?: engine.timeMs

    Column(
        modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth().glass().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                GestureMath.formatTime(shownTime),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            Slider(
                value = fraction,
                onValueChange = { f ->
                    scrub = f
                    val target = (f * len).toLong()
                    onScrub(true, target)
                    val now = SystemClock.uptimeMillis()
                    if (len > 0 && now - lastFastSeek > 150) { engine.seekTo(target, fast = true); lastFastSeek = now }
                },
                onValueChangeFinished = {
                    scrub?.let { if (len > 0) engine.seekTo((it * len).toLong()) }
                    scrub = null
                    onScrub(false, null)
                },
                enabled = len > 0 && engine.seekable,
                colors = SliderDefaults.colors(
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                if (len <= 0) "--:--" else if (showRemaining) "−" + GestureMath.formatTime(len - shownTime) else GestureMath.formatTime(len),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(6.dp)).clickable { showRemaining = !showRemaining },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { state.locked = true; onHud("Controls locked" to Icons.Filled.Lock) }) {
                Icon(Icons.Filled.LockOpen, "Lock controls")
            }
            if (state.engine.hasVideo) {
                IconButton(onClick = {
                    val next = OrientationMode.values()[(state.orientation.ordinal + 1) % OrientationMode.values().size]
                    state.orientation = next
                    onHud(next.label to Icons.Filled.ScreenRotation)
                    onPoke()
                }) {
                    Icon(Icons.Filled.ScreenRotation, "Rotation: ${state.orientation.label}", tint = if (state.orientation == OrientationMode.AUTO) LocalContentColor.current else MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    val i = QUICK_ASPECTS.indexOf(state.settings.aspect)
                    val next = QUICK_ASPECTS[(i + 1) % QUICK_ASPECTS.size]
                    state.update { it.copy(aspect = next) }
                    onHud(next.label to Icons.Filled.AspectRatio)
                    onPoke()
                }) { Icon(Icons.Filled.AspectRatio, "Aspect ratio: ${state.settings.aspect.label}") }
            }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { speedMenu = true; onPoke() }) {
                    Icon(Icons.Filled.Speed, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(GestureMath.formatRate(state.settings.speed))
                }
                BfeDropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    QUICK_SPEEDS.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(GestureMath.formatRate(r), fontWeight = if (r == state.settings.speed) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { speedMenu = false; state.update { it.copy(speed = r) } },
                        )
                    }
                }
            }
            IconButton(onClick = {
                val next = RepeatMode.values()[(state.repeat.ordinal + 1) % RepeatMode.values().size]
                state.repeat = next
                onHud(next.label to if (next == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat)
                onPoke()
            }) {
                Icon(
                    if (state.repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat: ${state.repeat.label}",
                    tint = if (state.repeat == RepeatMode.OFF) LocalContentColor.current.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                )
            }
            if (pipSupported && state.engine.hasVideo) {
                IconButton(onClick = onPip) { Icon(Icons.Filled.PictureInPictureAlt, "Picture-in-picture") }
            }
        }
    }
}

@Composable
private fun SeekRippleOverlay(ripple: SeekRipple?, modifier: Modifier) {
    var last by remember { mutableStateOf<SeekRipple?>(null) }
    if (ripple != null) last = ripple
    Box(modifier) {
        listOf(-1, 1).forEach { side ->
            AnimatedVisibility(
                visible = ripple?.side == side,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(320)),
                modifier = Modifier.align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.38f),
            ) {
                val shape = if (side < 0) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (side < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward, null, modifier = Modifier.size(32.dp))
                        Text(
                            "${(last?.totalMs ?: 10_000L) / 1000} s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudPill(hud: Hud?, modifier: Modifier) {
    var shown by remember { mutableStateOf<Hud?>(null) }
    if (hud != null) shown = hud
    AnimatedVisibility(
        visible = hud != null,
        enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.92f),
        exit = fadeOut(tween(260)),
        modifier = modifier,
    ) {
        val h = shown ?: return@AnimatedVisibility
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 180.dp).glass(strong = true).padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            when (h) {
                is Hud.Brightness -> HudLevel(Icons.Filled.BrightnessMedium, "Brightness", "${(h.value * 100).toInt()} %", h.value)
                is Hud.Volume -> {
                    val boosted = h.level > 1f
                    HudLevel(
                        if (h.level <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        if (boosted) "Volume boost" else "Volume",
                        "${(h.level * 100).toInt()} %",
                        if (boosted) h.level - 1f else h.level,
                        accent = boosted,
                    )
                }
                is Hud.Seek -> {
                    Text(
                        GestureMath.formatTime(h.target) + " / " + GestureMath.formatTime(h.length),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(GestureMath.formatDelta(h.delta), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                is Hud.Zoom -> HudLevel(Icons.Filled.ZoomIn, "Zoom", GestureMath.formatRate(h.zoom), (h.zoom - 1f) / 2f)
                is Hud.Message -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(h.icon, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(h.text, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun HudLevel(icon: ImageVector, label: String, value: String, fraction: Float, accent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (accent) MaterialTheme.colorScheme.primary else LocalContentColor.current, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.width(180.dp).height(4.dp).clip(Glass.Pill),
        color = if (accent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
    )
}

/** Title / artist / album and cover art (embedded, or a cover.jpg-style file next to the track). */
private class AudioMeta(val title: String?, val artist: String?, val album: String?, val art: ImageBitmap?)

@Composable
private fun AudioArtwork(ref: MediaRef?) {
    val context = LocalContext.current
    val meta by produceState<AudioMeta?>(null, ref?.key) {
        value = if (ref == null) null else withContext(Dispatchers.IO) { readAudioMeta(context, ref) }
    }
    val art = meta?.art
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (art != null) {
            // A soft, darkened wash of the cover behind everything (the blur needs Android 12+).
            Image(
                art,
                null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(56.dp).graphicsLayer { alpha = 0.38f },
            )
        }
        val side = minOf(maxWidth * 0.7f, maxHeight * 0.42f, 320.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 140.dp, start = 24.dp, end = 24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(side)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), RoundedCornerShape(24.dp)),
            ) {
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(side * 0.4f))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                meta?.title?.takeIf { it.isNotBlank() } ?: ref?.name?.substringBeforeLast('.') ?: "",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(meta?.artist?.takeIf { it.isNotBlank() }, meta?.album?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (line.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

private val COVER_NAMES = listOf("cover.jpg", "folder.jpg", "front.jpg", "albumart.jpg", "cover.png", "folder.png")

private fun readAudioMeta(context: Context, ref: MediaRef): AudioMeta? = runCatching {
    val mmr = MediaMetadataRetriever()
    try {
        if (ref.path != null) mmr.setDataSource(ref.path) else mmr.setDataSource(context, Uri.parse(ref.uri))
        val bytes = mmr.embeddedPicture
        val art = bytes?.let { decodeSampled(it) }
            ?: ref.path?.let { p ->
                File(p).parentFile?.listFiles()?.firstOrNull { f -> f.name.lowercase() in COVER_NAMES }?.readBytes()?.let { decodeSampled(it) }
            }
        AudioMeta(
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            art,
        )
    } finally {
        runCatching { mmr.release() }
    }
}.getOrNull()

/** Cover art decoded at most ~1024 px on the long side. */
private fun decodeSampled(bytes: ByteArray): ImageBitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / sample > 1024) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private fun windowBrightness(activity: Activity?): Float {
    val a = activity ?: return 0.5f
    val b = a.window.attributes.screenBrightness
    if (b >= 0f) return b
    // Following the system setting: start from it (0..255) so the first swipe doesn't jump.
    return runCatching { Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
        .getOrDefault(0.5f).coerceIn(0.01f, 1f)
}

private fun setWindowBrightness(activity: Activity?, value: Float) {
    val w = activity?.window ?: return
    w.attributes = w.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
}
