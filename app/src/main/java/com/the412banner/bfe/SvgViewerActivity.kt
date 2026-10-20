package com.the412banner.bfe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.the412banner.bfe.ui.svg.SvgViewerScreen
import com.the412banner.bfe.ui.components.BfePage
import com.the412banner.bfe.ui.theme.BfeTheme

/**
 * BFE's SVG viewer (Open with sheet → Open in BFE as → SVG): the drawing rendered in a WebView with
 * pinch zoom and a light / dark / checkerboard background, for any file holding SVG markup (also
 * gzipped .svgz). Only started from inside BFE.
 */
class SvgViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val path = intent.getStringExtra(EXTRA_PATH)
        if (uri == null && path == null) { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: path?.substringAfterLast('/') ?: "File"
        setContent {
            BfeTheme {
                BfePage {
                    SvgViewerScreen(path = path, uri = uri, name = name, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "com.the412banner.bfe.extra.SVG_PATH"
        private const val EXTRA_NAME = "com.the412banner.bfe.extra.SVG_NAME"

        /** Opens [path] (a real file, preferred) or [uri] in the viewer. */
        fun intent(context: Context, path: String?, uri: Uri?, name: String): Intent =
            Intent(context, SvgViewerActivity::class.java)
                .putExtra(EXTRA_NAME, name)
                .apply {
                    path?.let { putExtra(EXTRA_PATH, it) }
                    uri?.let { data = it; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
    }
}
