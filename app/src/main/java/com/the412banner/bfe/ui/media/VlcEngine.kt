package com.the412banner.bfe.ui.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.PlayerSettings
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
 * libVLC behind the player screen: one LibVLC instance + MediaPlayer rendering into the screen's two
 * TextureViews (video, and subtitles on a transparent one above it), with its state mirrored into
 * Compose state. Events arrive on the main thread (libVLC posts them to the main looper).
 *
 * libVLC reads its video-filter / deinterlace / audio-stretch / subtitle-style options only when the
 * instance is created, so when [VlcArgs] for the settings change, [open] / [reload] build a new
 * instance and resume at the same position. The old one is stopped and released off the main thread.
 */
class VlcEngine(context: Context) : IVLCVout.OnNewVideoLayoutListener {

    /**
     * Where the player draws. [renewSurfaces] swaps in fresh TextureViews: a SurfaceTexture takes one
     * producer at a time, and a libVLC instance being torn down may still hold the old one.
     */
    interface SurfaceHost {
        val video: TextureView
        val subs: TextureView
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
    var layout by mutableStateOf<VideoLayout?>(null); private set
    var hasVideo by mutableStateOf(false); private set
    var seekable by mutableStateOf(true); private set
    var audioTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var spuTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var audioTrack by mutableIntStateOf(-1); private set
    var spuTrack by mutableIntStateOf(-1); private set
    /** True between asking for a file and its first frame / sound. */
    var opening by mutableStateOf(false); private set

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
    /** The input is running (libVLC ignores seeks before that, so they wait in [pendingSeek]). */
    private var started = false
    private var pendingSeek: Long? = null
    /** Re-opened for an effect change while paused: pause again once the first frame is up. */
    private var pauseWhenReady = false
    private var winW = 0
    private var winH = 0
    private val releaser = Executors.newSingleThreadExecutor()

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
                }
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
            }
        }
    }

    /** The screen's surfaces. Re-binding is safe; the current player attaches to them at once. */
    fun attachHost(h: SurfaceHost) {
        host = h
        player?.let { bindVout(it) }
    }

    fun setWindowSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        winW = w
        winH = h
        player?.getVLCVout()?.setWindowSize(w, h)
    }

    override fun onNewVideoLayout(vlcVout: IVLCVout, width: Int, height: Int, visibleWidth: Int, visibleHeight: Int, sarNum: Int, sarDen: Int) {
        layout = VideoLayout(width, height, visibleWidth, visibleHeight, sarNum, sarDen)
    }

    /**
     * Opens [ref] at [startMs] with [s] (rebuilding libVLC first when its instance options changed).
     * [startPaused] shows the first frame and pauses there.
     */
    fun open(ref: MediaRef, startMs: Long, s: PlayerSettings, startPaused: Boolean = false) {
        settings = s
        error = null
        ended = false
        opening = true
        started = false
        pendingSeek = null
        pauseWhenReady = startPaused
        layout = null
        hasVideo = false
        audioTracks = emptyList()
        spuTracks = emptyList()
        timeMs = startMs.coerceAtLeast(0L)
        if (current?.key != ref.key) lengthMs = -1L
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
        // CPU video filters need frames in memory: MediaCodec still decodes, but copies frames out.
        if (VlcArgs.needsCpuFrames(s)) media.addOption(":no-mediacodec-dr")
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        p.setMedia(media)
        media.release()
        p.setRate(s.speed)
        applyEqualizer(s)
        p.play()
    }

    /** Re-opens the current file at the current position (after an instance-option change). */
    fun reload(s: PlayerSettings) {
        val ref = current ?: return
        val resumeAt = if (ended) 0L else timeMs
        open(ref, resumeAt, s, startPaused = !(isPlaying || opening))
    }

    /** Whether [s] needs a new libVLC instance compared with the running one. */
    fun needsReload(s: PlayerSettings): Boolean = engineArgs != null && engineArgs != VlcArgs.build(s)

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

    fun selectSubtitle(id: Int) { player?.setSpuTrack(id); refreshTracks() }

    /** Adds an external subtitle or audio file to the running media and selects it. */
    fun addSlave(subtitle: Boolean, file: File): Boolean {
        val p = player ?: return false
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
        releasePlayer()
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

    private fun bindVout(p: MediaPlayer) {
        val h = host ?: return
        val vout = p.getVLCVout()
        if (vout.areViewsAttached()) return
        vout.setVideoView(h.video)
        vout.setSubtitlesView(h.subs)
        vout.attachViews(this)
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
    }

    private fun releasePlayer() {
        val p = player
        val lib = libVlc
        player = null
        libVlc = null
        engineArgs = null
        eqApplied = false
        isPlaying = false
        if (p != null) {
            p.setEventListener(null)
            runCatching { p.getVLCVout().detachViews() }
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
