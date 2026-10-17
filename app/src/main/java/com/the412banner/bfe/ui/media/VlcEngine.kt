package com.the412banner.bfe.ui.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.the412banner.bfe.media.AspectMode
import com.the412banner.bfe.media.FramePolicy
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.PlayerSettings
import com.the412banner.bfe.media.RenderMode
import com.the412banner.bfe.media.VideoLayout
import com.the412banner.bfe.media.VlcArgs
import com.the412banner.bfe.util.IncomingFile
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import java.io.File
import java.util.concurrent.Executors

/**
 * libVLC behind the player screen: one LibVLC instance + MediaPlayer rendering into the screen's
 * video TextureView, with its state mirrored into Compose state. Events arrive on the main thread
 * (libVLC posts them to the main looper).
 *
 * Only the video view is handed to libVLC — never a second TextureView for subtitles: libVLC's
 * window helper (AWindow) treats a TextureView as ready before its surface exists, so a second one
 * makes the first surface flip it to READY and the second throw "invalid state" (libVLC's own
 * VideoHelper does the same in TextureView mode). Subtitles are drawn into the picture instead: the
 * GL output blends them over hardware frames, the Android display over copied-out frames (see
 * [FramePolicy] for when a file is re-opened in copy-out mode).
 *
 * libVLC reads its video-filter / deinterlace / audio-stretch / subtitle-style options only when the
 * instance is created, so when [VlcArgs] for the settings change, [open] / [reload] build a new
 * instance and resume at the same position. The old one is stopped and released off the main thread.
 */
class VlcEngine(context: Context) : IVLCVout.OnNewVideoLayoutListener {

    /**
     * Where the player draws. [renewSurfaces] swaps in a fresh TextureView: a SurfaceTexture takes
     * one producer at a time, and a libVLC instance being torn down may still hold the old one.
     */
    interface SurfaceHost {
        val video: TextureView
        fun renewSurfaces()
    }
    private val app = context.applicationContext

    data class Track(val id: Int, val name: String)

    var isPlaying by mutableStateOf(false); private set
    var timeMs by mutableLongStateOf(0L); private set
    var lengthMs by mutableLongStateOf(-1L); private set
    var buffering by mutableFloatStateOf(100f); private set
    var ended by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** The picture size from the Android display; null on the GL output (it fills the view). */
    var layout by mutableStateOf<VideoLayout?>(null); private set
    var hasVideo by mutableStateOf(false); private set
    var seekable by mutableStateOf(true); private set
    var audioTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var spuTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var audioTrack by mutableIntStateOf(-1); private set
    var spuTrack by mutableIntStateOf(-1); private set
    /** True between asking for a file and its first frame / sound. */
    var opening by mutableStateOf(false); private set
    /** Which video output is drawing (decided shortly after the picture appears). */
    var renderMode by mutableStateOf(RenderMode.UNKNOWN); private set

    /** Called once per file when it plays to the end. */
    var onEnded: (() -> Unit)? = null

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var engineArgs: List<String>? = null
    private var fd: ParcelFileDescriptor? = null
    private var current: MediaRef? = null
    private var settings: PlayerSettings = PlayerSettings.DEFAULT
    private var equalizer: MediaPlayer.Equalizer? = null
    private var eqApplied = false
    private var host: SurfaceHost? = null
    /** The view the current vout is bound to, and the one whose window attachment is watched. */
    private var boundView: TextureView? = null
    private var watchedView: TextureView? = null
    /** The input is running (libVLC ignores seeks before that, so they wait in [pendingSeek]). */
    private var started = false
    private var pendingSeek: Long? = null
    /** Re-opened while paused (effect change, copy-out switch): pause again once the first frame is up. */
    private var pauseWhenReady = false
    /** An open asked for before the screen's surfaces exist; runs as soon as they're attached. */
    private var pendingOpen: (() -> Unit)? = null
    /** The current media was opened with frames copied out of MediaCodec. */
    private var mediaCopies = false
    /** Copy-out was needed once for this file (subtitles or geometry): stays on until the next file. */
    private var copyForFile = false
    private var copyReopenPosted = false
    /** External subtitle / audio files added to this file; added again whenever it's re-opened. */
    private val slaves = ArrayList<Pair<Boolean, File>>()
    /** A subtitle track to select again once a re-opened file lists it. */
    private var wantSpu: Int? = null
    private var winW = 0
    private var winH = 0
    private val releaser = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val currentRef: MediaRef? get() = current
    val isReady: Boolean get() = player != null

    private val listener = object : MediaPlayer.EventListener {
        override fun onEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Opening -> opening = true
                MediaPlayer.Event.Buffering -> buffering = event.buffering
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    ended = false
                    opening = false
                    started = true
                    pendingSeek?.let { t -> pendingSeek = null; player?.setTime(t, false) }
                    applyLive(settings)
                    refreshTracks()
                }
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    ended = true
                    opening = false
                    timeMs = lengthMs.coerceAtLeast(timeMs)
                    onEnded?.invoke()
                }
                MediaPlayer.Event.EncounteredError -> {
                    isPlaying = false
                    opening = false
                    error = "VLC couldn't play ${current?.name ?: "this file"}."
                }
                MediaPlayer.Event.TimeChanged -> {
                    timeMs = event.timeChanged
                    if (pauseWhenReady) { pauseWhenReady = false; player?.pause() }
                }
                MediaPlayer.Event.LengthChanged -> lengthMs = event.lengthChanged
                MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
                MediaPlayer.Event.Vout -> {
                    hasVideo = event.voutCount > 0
                    opening = false
                    if (hasVideo && renderMode == RenderMode.UNKNOWN) {
                        // The Android display reports its size right away; no report means the GL output.
                        val p = player
                        main.postDelayed({
                            if (player === p && hasVideo && layout == null && renderMode == RenderMode.UNKNOWN) {
                                renderMode = RenderMode.GL
                                checkCopyOut()
                            }
                        }, 700)
                    }
                }
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
            }
        }
    }

    /**
     * AWindow detaches itself when a view's SurfaceTexture is destroyed (the view left the window)
     * and drops the view's listener, so nothing would bind again when the view comes back.
     */
    private val voutCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout) = Unit
        override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
            val p = player ?: return
            if (vlcVout !== p.getVLCVout()) return
            boundView = null
            main.post { if (host?.video?.isAttachedToWindow == true) rebind() }
        }
    }

    private val attachWatcher = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { main.post { rebind() } }
        override fun onViewDetachedFromWindow(v: View) = Unit
    }

    /** The screen's surfaces. Re-binding is safe; the current player attaches to them at once. */
    fun attachHost(h: SurfaceHost) {
        host = h
        player?.let { bindVout(it) }
        pendingOpen?.let { todo -> pendingOpen = null; todo() }
    }

    fun setWindowSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        winW = w
        winH = h
        player?.getVLCVout()?.setWindowSize(w, h)
    }

    override fun onNewVideoLayout(vlcVout: IVLCVout, width: Int, height: Int, visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int) {
        val l = VideoLayout(width, height, visibleWidth, visibleHeight, sarNum, sarDen)
        // An OpenGL output may report zeros: it places the picture itself.
        if (!l.isValid) return
        layout = l
        if (renderMode != RenderMode.NATIVE) {
            renderMode = RenderMode.NATIVE
            // The view is sized to the picture now: libVLC's own aspect / scale must stay neutral.
            player?.let { p -> p.setAspectRatio(null); p.setScale(0f) }
            checkCopyOut()
        }
    }

    /**
     * Opens [ref] at [startMs] with [s] (rebuilding libVLC first when its instance options changed).
     * [startPaused] shows the first frame and pauses there.
     */
    fun open(ref: MediaRef, startMs: Long, s: PlayerSettings, startPaused: Boolean = false) {
        if (host == null) {
            // libVLC needs its window before play(): wait for the screen's TextureView.
            settings = s
            opening = true
            pendingOpen = { open(ref, startMs, s, startPaused) }
            return
        }
        val sameFile = current?.key == ref.key
        if (!sameFile) {
            lengthMs = -1L
            copyForFile = false
            slaves.clear()
            wantSpu = null
        }
        settings = s
        error = null
        ended = false
        opening = true
        started = false
        pendingSeek = null
        pauseWhenReady = startPaused
        layout = null
        hasVideo = false
        renderMode = RenderMode.UNKNOWN
        audioTracks = emptyList()
        spuTracks = emptyList()
        timeMs = startMs.coerceAtLeast(0L)
        current = ref
        val args = VlcArgs.build(s)
        val p = try {
            ensureEngine(args)
        } catch (t: Throwable) {
            Log.e(TAG, "libVLC failed to start", t)
            error = "The video engine couldn't start on this device."
            opening = false
            return
        }
        val lib = libVlc ?: return
        val media = try {
            createMedia(lib, ref)
        } catch (t: Throwable) {
            Log.w(TAG, "open ${ref.name}", t)
            null
        }
        if (media == null) {
            error = "Couldn't open ${ref.name}."
            opening = false
            return
        }
        media.setHWDecoderEnabled(true, false)
        // Copy-out: MediaCodec still decodes, but frames come back to memory so VLC's CPU filters,
        // the size-reporting Android display and software subtitle blending can work on them.
        mediaCopies = copyForFile || FramePolicy.copyAtOpen(s)
        if (mediaCopies) media.addOption(":no-mediacodec-dr")
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        slaves.forEach { (subtitle, f) ->
            val type = if (subtitle) IMedia.Slave.Type.Subtitle else IMedia.Slave.Type.Audio
            runCatching { media.addSlave(IMedia.Slave(type, 4, Uri.fromFile(f).toString())) }
        }
        p.setMedia(media)
        media.release()
        p.setAspectRatio(null)
        p.setScale(0f)
        p.setRate(s.speed)
        applyEqualizer(s)
        playWhenSurfaceReady(p, SystemClock.uptimeMillis())
    }

    /**
     * Starts [p] once the video TextureView has its SurfaceTexture (it appears with the view's first
     * draw; libVLC's window helper owns the view's listener, so this checks once a frame). Gives up
     * waiting after 800 ms — music, PiP or a stopped activity may never draw it.
     */
    private fun playWhenSurfaceReady(p: MediaPlayer, since: Long) {
        if (player !== p) return
        val ready = host?.video?.isAvailable != false
        if (ready || SystemClock.uptimeMillis() - since > 800) { p.play(); return }
        main.postDelayed({ playWhenSurfaceReady(p, since) }, 16)
    }

    /** Re-opens the current file at the current position (after an instance-option change). */
    fun reload(s: PlayerSettings) {
        val ref = current ?: return
        val resumeAt = if (ended) 0L else timeMs
        open(ref, resumeAt, s, startPaused = !(isPlaying || opening))
    }

    /** Whether [s] needs a new libVLC instance compared with the running one. */
    fun needsReload(s: PlayerSettings): Boolean = engineArgs != null && engineArgs != VlcArgs.build(s)

    /**
     * Re-opens the file with copy-out at the same spot when the output in use can't do what's
     * asked (see [FramePolicy.mustCopyNow]). Once per file at most.
     */
    private fun checkCopyOut() {
        if (player == null || mediaCopies || copyReopenPosted || !hasVideo) return
        if (!FramePolicy.mustCopyNow(renderMode, settings, subtitleOn = spuTrack >= 0)) return
        copyForFile = true
        if (spuTrack >= 0) wantSpu = spuTrack
        copyReopenPosted = true
        main.post {
            copyReopenPosted = false
            val ref = current ?: return@post
            if (player == null) return@post
            Log.i(TAG, "re-opening ${ref.name} with copied-out frames ($renderMode)")
            open(ref, if (ended) 0L else timeMs, settings, startPaused = !(isPlaying || opening))
        }
    }

    fun play() {
        val p = player ?: return
        if (ended) { current?.let { open(it, 0L, settings) }; return }
        p.play()
    }

    fun pause() { player?.pause() }

    fun toggle() { if (isPlaying) pause() else play() }

    /** [fast] = keyframe seek (scrubbing); precise otherwise. */
    fun seekTo(ms: Long, fast: Boolean = false) {
        val p = player ?: return
        val len = lengthMs
        val t = if (len > 0) ms.coerceIn(0L, len) else ms.coerceAtLeast(0L)
        if (ended) {
            current?.let { open(it, t, settings) }
            return
        }
        timeMs = t
        if (!started) { pendingSeek = t; return }
        p.setTime(t, fast)
    }

    fun seekBy(deltaMs: Long) = seekTo(timeMs + deltaMs)

    /** Settings that apply to the running player without a reload. */
    fun applyLive(s: PlayerSettings) {
        settings = s
        val p = player ?: return
        p.setRate(s.speed)
        p.setVolume(s.boost.coerceIn(0, 200))
        p.setAudioDelay(s.audioDelayMs * 1000L)
        p.setSpuDelay(s.subDelayMs * 1000L)
        applyEqualizer(s)
        checkCopyOut()
    }

    /**
     * Picture placement on the GL output, which fills the view: libVLC's own aspect / scale, the way
     * its VideoHelper does it for OpenGL outputs. (On the Android display the view itself is sized.)
     */
    fun applyGlPlacement(aspect: AspectMode, viewW: Int, viewH: Int) {
        val p = player ?: return
        if (renderMode != RenderMode.GL || viewW <= 0 || viewH <= 0) return
        when (aspect) {
            AspectMode.BEST_FIT -> { p.setAspectRatio(null); p.setScale(0f) }
            AspectMode.ORIGINAL -> { p.setAspectRatio(null); p.setScale(1f) }
            AspectMode.FIT_SCREEN, AspectMode.FILL -> {
                val t = p.getCurrentVideoTrack() ?: return
                val swapped = t.orientation == IMedia.VideoTrack.Orientation.LeftBottom ||
                    t.orientation == IMedia.VideoTrack.Orientation.RightTop
                if (aspect == AspectMode.FIT_SCREEN) {
                    p.setScale(FramePolicy.coverScale(t.width, t.height, t.sarNum, t.sarDen, swapped, viewW, viewH))
                    p.setAspectRatio(null)
                } else {
                    p.setScale(0f)
                    p.setAspectRatio(if (!swapped) "$viewW:$viewH" else "$viewH:$viewW")
                }
            }
            else -> { p.setAspectRatio(FramePolicy.vlcAspect(aspect)); p.setScale(0f) }
        }
    }

    /**
     * Width / height of the picture as shown (sample aspect ratio applied): from the Android
     * display's layout, or from the playing video track on the GL output. Null before it's known.
     */
    fun pictureAspect(): Double? {
        layout?.let { return it.aspect }
        val t = player?.getCurrentVideoTrack() ?: return null
        if (t.width <= 0 || t.height <= 0) return null
        val sar = if (t.sarNum > 0 && t.sarDen > 0) t.sarNum.toDouble() / t.sarDen else 1.0
        val a = t.width * sar / t.height
        val swapped = t.orientation == IMedia.VideoTrack.Orientation.LeftBottom ||
            t.orientation == IMedia.VideoTrack.Orientation.RightTop
        return if (swapped) 1 / a else a
    }

    /** Hold-to-speed-up: a temporary rate over the chosen speed (null restores it). */
    fun overrideRate(rate: Float?) { player?.setRate(rate ?: settings.speed) }

    /** VLC's own gain while a volume swipe is in progress (committed to the settings afterwards). */
    fun previewVolume(percent: Int) { player?.setVolume(percent.coerceIn(0, 200)) }

    private fun applyEqualizer(s: PlayerSettings) {
        val p = player ?: return
        if (!s.eqOn) {
            if (eqApplied) { p.setEqualizer(null); eqApplied = false }
            return
        }
        val eq = equalizer ?: MediaPlayer.Equalizer.create().also { equalizer = it }
        eq.setPreAmp(s.eqPreamp)
        val n = MediaPlayer.Equalizer.getBandCount()
        s.eqBands.forEachIndexed { i, v -> if (i < n) eq.setAmp(i, v) }
        p.setEqualizer(eq)
        eqApplied = true
    }

    fun selectAudio(id: Int) { player?.setAudioTrack(id); refreshTracks() }

    fun selectSubtitle(id: Int) {
        player?.setSpuTrack(id)
        refreshTracks()
    }

    /**
     * Adds an external subtitle or audio file and selects it. It's remembered for this file, so a
     * re-open (an effect change, the switch to copied-out frames) brings it back.
     */
    fun addSlave(subtitle: Boolean, file: File): Boolean {
        val p = player ?: return false
        if (slaves.none { it.first == subtitle && it.second == file }) slaves += subtitle to file
        val type = if (subtitle) IMedia.Slave.Type.Subtitle else IMedia.Slave.Type.Audio
        return runCatching { p.addSlave(type, Uri.fromFile(file), true) }.getOrDefault(false)
    }

    // ── Equalizer presets (libVLC's own list) ──

    fun eqPresetNames(): List<String> = runCatching {
        ensureLoaded()
        (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
    }.getOrDefault(emptyList())

    /** Preamp and band gains of preset [index]. */
    fun eqPreset(index: Int): Pair<Float, List<Float>>? = runCatching {
        ensureLoaded()
        val e = MediaPlayer.Equalizer.createFromPreset(index)
        e.preAmp to (0 until MediaPlayer.Equalizer.getBandCount()).map { e.getAmp(it) }
    }.getOrNull()

    fun eqBandLabels(): List<String> = runCatching {
        ensureLoaded()
        (0 until MediaPlayer.Equalizer.getBandCount()).map { i ->
            val f = MediaPlayer.Equalizer.getBandFrequency(i)
            if (f >= 1000f) "${(f / 1000f).let { if (it >= 10f) it.toInt().toString() else "%.1f".format(it).removeSuffix(".0") }}k" else f.toInt().toString()
        }
    }.getOrDefault(emptyList())

    private fun ensureLoaded() { if (libVlc == null) LibVLC.loadLibraries() }

    fun release() {
        pendingOpen = null
        main.removeCallbacksAndMessages(null)
        releasePlayer()
        watchedView?.removeOnAttachStateChangeListener(attachWatcher)
        watchedView = null
        current = null
        releaser.shutdown()
    }

    // ── Internals ──

    private fun ensureEngine(args: List<String>): MediaPlayer {
        player?.let { if (args == engineArgs) return it }
        val rebuilding = player != null
        releasePlayer()
        if (rebuilding) host?.renewSurfaces()
        val lib = LibVLC(app, ArrayList(VlcArgs.BASE + args))
        val p = MediaPlayer(lib)
        p.setEventListener(listener)
        libVlc = lib
        player = p
        engineArgs = args
        eqApplied = false
        bindVout(p)
        return p
    }

    /** Re-binds the current player after its surface was lost (view back in the window, or replaced). */
    private fun rebind() {
        val p = player ?: return
        val view = host?.video ?: return
        if (p.getVLCVout().areViewsAttached() && boundView === view) return
        bindVout(p)
        // A new surface under a running player: re-create the video output on it, as libVLC's
        // VideoHelper does when it attaches views.
        if (hasVideo) {
            p.setVideoTrackEnabled(false)
            p.setVideoTrackEnabled(true)
        }
    }

    /**
     * Binds [p]'s vout to the host's video TextureView — the only view libVLC gets. A vout still
     * bound to another (replaced) view is detached first; AWindow only accepts views while detached.
     */
    private fun bindVout(p: MediaPlayer) {
        val h = host ?: return
        val view = h.video
        val vout = p.getVLCVout()
        if (vout.areViewsAttached()) {
            if (boundView === view) return
            vout.detachViews()
        }
        vout.addCallback(voutCallback)
        vout.setVideoView(view)
        vout.attachViews(this)
        boundView = view
        if (watchedView !== view) {
            watchedView?.removeOnAttachStateChangeListener(attachWatcher)
            view.addOnAttachStateChangeListener(attachWatcher)
            watchedView = view
        }
        if (winW > 0 && winH > 0) vout.setWindowSize(winW, winH)
    }

    /** A real path whenever there is one (VLC then also finds same-named subtitle files next to it). */
    private fun createMedia(lib: LibVLC, ref: MediaRef): Media? {
        ref.path?.let { p -> if (File(p).canRead()) { closeFd(); return Media(lib, p) } }
        val uri = Uri.parse(ref.uri)
        return when (uri.scheme) {
            "file" -> uri.path?.let { closeFd(); Media(lib, it) }
            "content" -> {
                IncomingFile.resolvePath(app, uri)?.takeIf { File(it).canRead() }?.let { closeFd(); return Media(lib, it) }
                val pfd = app.contentResolver.openFileDescriptor(uri, "r") ?: return null
                closeFd()
                fd = pfd
                Media(lib, pfd.fileDescriptor)
            }
            else -> { closeFd(); Media(lib, uri) }
        }
    }

    private fun closeFd() {
        val old = fd ?: return
        fd = null
        releaser.execute { runCatching { old.close() } }
    }

    private fun refreshTracks() {
        val p = player ?: return
        audioTracks = p.audioTracks?.map { Track(it.id, it.name ?: "Track ${it.id}") }.orEmpty()
        spuTracks = p.spuTracks?.map { Track(it.id, it.name ?: "Track ${it.id}") }.orEmpty()
        audioTrack = p.getAudioTrack()
        spuTrack = p.getSpuTrack()
        // After a re-open, put back the subtitle track that was showing.
        wantSpu?.let { id ->
            if (spuTracks.any { it.id == id }) {
                wantSpu = null
                if (spuTrack != id) { p.setSpuTrack(id); spuTrack = p.getSpuTrack() }
            }
        }
        checkCopyOut()
    }

    private fun releasePlayer() {
        val p = player
        val lib = libVlc
        player = null
        libVlc = null
        engineArgs = null
        eqApplied = false
        isPlaying = false
        boundView = null
        if (p != null) {
            p.setEventListener(null)
            runCatching {
                val vout = p.getVLCVout()
                vout.removeCallback(voutCallback)
                vout.detachViews()
            }
        }
        val oldFd = fd
        fd = null
        releaser.execute {
            runCatching { p?.stop() }
            runCatching { p?.release() }
            runCatching { lib?.release() }
            runCatching { oldFd?.close() }
        }
    }

    private companion object {
        const val TAG = "BfePlayer"
    }
}
