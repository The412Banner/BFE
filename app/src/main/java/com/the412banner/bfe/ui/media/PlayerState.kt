package com.the412banner.bfe.ui.media

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.the412banner.bfe.media.MediaPrefs
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.MediaRequest
import com.the412banner.bfe.media.MediaType
import com.the412banner.bfe.media.MediaTypes
import com.the412banner.bfe.media.PlayerSettings
import com.the412banner.bfe.media.ResumePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What happens at the end of a file. */
enum class RepeatMode(val label: String) { OFF("Play once"), ONE("Repeat this file"), ALL("Play the whole folder") }

/** The orientation toggle: follow the sensor, or hold landscape / portrait. */
enum class OrientationMode(val label: String) { AUTO("Auto-rotate"), LANDSCAPE("Landscape"), PORTRAIT("Portrait") }

/**
 * The player's session: the playlist and where it is, the effects settings, repeat / orientation /
 * lock, the "Resume from" offer, and remembering positions. Owns the [VlcEngine]; the activity drives
 * its lifecycle, the screen reads and changes it.
 */
@Stable
class PlayerState(private val context: Context, private val scope: CoroutineScope) {
    val engine = VlcEngine(context)

    var items by mutableStateOf<List<MediaRef>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var settings by mutableStateOf(PlayerSettings.DEFAULT); private set
    var repeat by mutableStateOf(RepeatMode.OFF)
    var orientation by mutableStateOf(OrientationMode.AUTO)
    var locked by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    /** A remembered position offered as "Resume from 12:34" for the current file (null = none). */
    var resumeOffer by mutableStateOf<Long?>(null); private set
    /** "Applying…" while libVLC restarts for an effect change. */
    var reloading by mutableStateOf(false); private set

    private var reloadJob: Job? = null
    private var saverJob: Job? = null
    private var persistJob: Job? = null

    val current: MediaRef? get() = items.getOrNull(index)
    val currentType: MediaType? get() = current?.let { MediaTypes.typeOf(it.name) }
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < items.size - 1

    /** Audio file without a picture: the player shows its music layout. */
    val audioOnly: Boolean get() = currentType == MediaType.AUDIO && !engine.hasVideo

    init {
        engine.onEnded = { onEnded() }
    }

    fun load(request: MediaRequest) {
        savePosition()
        items = request.items
        index = request.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        settings = MediaPrefs.startSettings(context)
        repeat = if (currentType == MediaType.AUDIO) RepeatMode.ALL else RepeatMode.OFF
        openCurrent()
        startSaver()
    }

    private fun openCurrent() {
        val ref = current ?: return
        val stored = MediaPrefs.position(context, ref.key)
        resumeOffer = stored?.takeIf { ResumePolicy.shouldOffer(it.positionMs, it.lengthMs) }?.positionMs
        engine.open(ref, 0L, settings)
    }

    fun goTo(i: Int) {
        if (i !in items.indices || i == index) return
        savePosition()
        index = i
        openCurrent()
    }

    fun next() { if (hasNext) goTo(index + 1) }

    /** Back to the start when more than 5 s in; otherwise the previous file. */
    fun previous() {
        if (engine.timeMs > 5_000 || !hasPrevious) engine.seekTo(0L) else goTo(index - 1)
    }

    fun acceptResume() {
        val at = resumeOffer ?: return
        resumeOffer = null
        engine.seekTo(at)
    }

    fun dismissResume() { resumeOffer = null }

    /** Changes the settings: live ones apply at once, instance options restart libVLC shortly after. */
    fun update(transform: (PlayerSettings) -> PlayerSettings) {
        val s = transform(settings)
        if (s == settings) return
        settings = s
        engine.applyLive(s)
        // Sliders change the settings every frame; write them out once they settle.
        persistJob?.cancel()
        persistJob = scope.launch { delay(500); MediaPrefs.saveSettings(context, settings) }
        if (engine.needsReload(s)) scheduleReload()
    }

    fun resetSettings() = update { PlayerSettings.DEFAULT }

    fun setKeepSettings(on: Boolean) {
        MediaPrefs.setKeepSettings(context, on)
        if (on) MediaPrefs.saveSettings(context, settings)
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            reloading = true
            // Coalesce quick successive changes (tapping through chips) into one restart.
            delay(450)
            engine.reload(settings)
            delay(600)
            reloading = false
        }
    }

    private fun onEnded() {
        val ref = current
        if (ref != null) MediaPrefs.forgetPosition(context, ref.key)
        when (repeat) {
            RepeatMode.ONE -> ref?.let { engine.open(it, 0L, settings) }
            RepeatMode.ALL -> if (hasNext) goTo(index + 1)
            RepeatMode.OFF -> Unit
        }
    }

    fun savePosition() {
        val ref = current ?: return
        if (engine.ended) return
        MediaPrefs.storePosition(context, ref.key, engine.timeMs, engine.lengthMs)
    }

    private fun startSaver() {
        saverJob?.cancel()
        saverJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (engine.isPlaying) savePosition()
            }
        }
    }

    fun release() {
        savePosition()
        saverJob?.cancel()
        reloadJob?.cancel()
        if (persistJob?.isActive == true) { persistJob?.cancel(); MediaPrefs.saveSettings(context, settings) }
        engine.release()
    }
}
