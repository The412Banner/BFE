package com.the412banner.bfe

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.the412banner.bfe.media.MediaLaunch
import com.the412banner.bfe.media.MediaRequest
import com.the412banner.bfe.media.MediaType
import com.the412banner.bfe.ui.media.MediaTheme
import com.the412banner.bfe.ui.media.PhotoViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BFE's photo viewer: full screen, edge to edge, over a translucent window so it fades and scales in
 * over the screen it was opened from. Swipes through the images of the same folder (or category) in
 * the order that screen showed them. Opened from BFE's Open with sheet (View) and offered to other
 * apps for image/\* files (then the playlist is the file's folder when BFE can read it).
 */
class PhotoViewerActivity : ComponentActivity() {

    private val request = mutableStateOf<MediaRequest?>(null)
    private var currentKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val restoreKey = savedInstanceState?.getString(STATE_CURRENT)
        val launchIntent = intent
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { MediaLaunch.resolve(applicationContext, launchIntent, setOf(MediaType.IMAGE)) }
            if (r == null) { finish(); return@launch }
            val restored = restoreKey?.let { k -> r.items.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
            request.value = if (restored != null) r.copy(index = restored) else r
        }
        setContent {
            MediaTheme {
                request.value?.let { r ->
                    PhotoViewerScreen(
                        request = r,
                        onCurrentChanged = { currentKey = it },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentKey?.let { outState.putString(STATE_CURRENT, it) }
    }

    private companion object {
        const val STATE_CURRENT = "current"
    }
}
