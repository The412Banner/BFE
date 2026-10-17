package com.the412banner.bfe

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.the412banner.bfe.media.AspectMode
import com.the412banner.bfe.media.MediaLaunch
import com.the412banner.bfe.media.MediaPrefs
import com.the412banner.bfe.media.MediaType
import com.the412banner.bfe.media.VideoGeometry
import com.the412banner.bfe.ui.media.MediaTheme
import com.the412banner.bfe.ui.media.PlayerState
import com.the412banner.bfe.ui.media.VideoPlayerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BFE's video (and audio) player, on libVLC. Survives rotation (configChanges), plays in
 * picture-in-picture (from the button, or on Home / Back while playing when Settings allows it) with
 * previous / play-pause / next actions, holds audio focus while playing (pauses for calls and other
 * players, resumes after a short interruption) and pauses when headphones are unplugged. Opened from
 * BFE's Open with sheet (Play) and offered to other apps for video/\* and audio/\* files.
 */
class VideoPlayerActivity : ComponentActivity() {

    private lateinit var state: PlayerState
    private val loaded = mutableStateOf(false)
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var focusRequest: AudioFocusRequest? = null
    private var pausedForFocus = false

    private val pipSupported: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { pausedForFocus = false; state.engine.pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                if (state.engine.isPlaying) { pausedForFocus = true; state.engine.pause() }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedForFocus) { pausedForFocus = false; state.engine.play() }
        }
    }

    /** Headphones / Bluetooth audio gone: pause instead of blasting the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) state.engine.pause()
        }
    }

    /** The picture-in-picture window's buttons. */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_TOGGLE -> state.engine.toggle()
                CONTROL_NEXT -> state.next()
                CONTROL_PREVIOUS -> state.previous()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        volumeControlStream = AudioManager.STREAM_MUSIC
        state = PlayerState(this, lifecycleScope)
        openFrom(intent)

        ContextCompat.registerReceiver(this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)

        // Audio focus and the PiP buttons follow the play state.
        lifecycleScope.launch {
            snapshotFlow { state.engine.isPlaying }.distinctUntilChanged().collect { playing ->
                if (playing) requestFocus()
                updatePipParams()
            }
        }
        lifecycleScope.launch {
            snapshotFlow { listOf(state.engine.layout, state.settings.rotation, state.engine.hasVideo, state.engine.renderMode) }.collect { updatePipParams() }
        }

        setContent {
            MediaTheme {
                if (loaded.value) {
                    VideoPlayerScreen(
                        state = state,
                        pipSupported = pipSupported,
                        onEnterPip = { enterPip() },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: Intent) {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                MediaLaunch.resolve(applicationContext, intent, setOf(MediaType.VIDEO, MediaType.AUDIO))
            }
            if (r == null) {
                if (!loaded.value) finish()
                return@launch
            }
            state.load(r)
            loaded.value = true
        }
    }

    // ── Picture-in-picture ──

    private fun pipParams(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
        val layout = state.engine.layout
        val aspect = layout?.let { l ->
            VideoGeometry.place(1000, 1000, l, AspectMode.BEST_FIT, rotation = state.settings.rotation)?.displayedAspect(state.settings.rotation)
        } ?: state.engine.pictureAspect() ?: (16.0 / 9.0)
        val (w, h) = VideoGeometry.pipAspect(aspect)
        b.setAspectRatio(Rational(w, h))
        val actions = ArrayList<RemoteAction>()
        if (state.items.size > 1) actions += action(CONTROL_PREVIOUS, R.drawable.ic_pip_previous, "Previous")
        actions += if (state.engine.isPlaying) action(CONTROL_TOGGLE, R.drawable.ic_pip_pause, "Pause")
        else action(CONTROL_TOGGLE, R.drawable.ic_pip_play, "Play")
        if (state.items.size > 1) actions += action(CONTROL_NEXT, R.drawable.ic_pip_next, "Next")
        b.setActions(actions)
        if (Build.VERSION.SDK_INT >= 31) {
            // Gesture navigation: slide into PiP smoothly on Home instead of waiting for onUserLeaveHint.
            b.setAutoEnterEnabled(pipWanted())
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    private fun action(control: Int, icon: Int, title: String): RemoteAction {
        val pi = PendingIntent.getBroadcast(
            this,
            control,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL, control),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
    }

    private fun pipWanted(): Boolean =
        pipSupported && ::state.isInitialized && state.engine.isPlaying && state.engine.hasVideo && MediaPrefs.pipOnLeave(this)

    private fun updatePipParams() {
        if (!pipSupported || !::state.isInitialized) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun enterPip() {
        if (!pipSupported) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && pipWanted()) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        state.inPip = isInPictureInPictureMode
    }

    // ── Lifecycle ──

    override fun onStop() {
        super.onStop()
        if (!::state.isInitialized) return
        state.savePosition()
        if (isInPictureInPictureMode) {
            // The PiP window was closed: that's the end of playback.
            state.engine.pause()
            finish()
            return
        }
        // Music keeps playing with the screen off; a video pauses when it can't be seen.
        if (!state.audioOnly) state.engine.pause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { unregisterReceiver(pipReceiver) }
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        if (::state.isInitialized) state.release()
        super.onDestroy()
    }

    private fun requestFocus() {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(if (state.audioOnly) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(true)
            .build()
            .also { focusRequest = it }
        runCatching { audioManager.requestAudioFocus(req) }
    }

    private companion object {
        const val ACTION_PIP_CONTROL = "com.the412banner.bfe.action.PIP_CONTROL"
        const val EXTRA_CONTROL = "control"
        const val CONTROL_TOGGLE = 1
        const val CONTROL_NEXT = 2
        const val CONTROL_PREVIOUS = 3
    }
}
