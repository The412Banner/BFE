package com.the412banner.bfe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.the412banner.bfe.apk.internals.InternalsTool
import com.the412banner.bfe.ui.components.BfePage
import com.the412banner.bfe.ui.internals.ApkInternalsScreen
import com.the412banner.bfe.ui.theme.BfeTheme

/**
 * BFE's Android app internals tools: binary XML (manifest, res XML), resources (resources.arsc), dex
 * (classes, smali, editing), Java classes / jars, and the APK hub that links them. Opened from the
 * Open with sheet's "Open in BFE as" tiles, a tap on such an entry inside an APK, or an APK's ⋮ menu.
 * Only started from inside BFE.
 */
class ApkInternalsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tool = runCatching { InternalsTool.valueOf(intent.getStringExtra(EXTRA_TOOL) ?: "") }.getOrNull()
        val path = intent.getStringExtra(EXTRA_PATH)
        val uri = intent.data
        if (tool == null || (path == null && uri == null)) { finish(); return }
        val entry = intent.getStringExtra(EXTRA_ENTRY)
        val name = intent.getStringExtra(EXTRA_NAME) ?: entry?.substringAfterLast('/') ?: path?.substringAfterLast('/') ?: "File"
        setContent {
            BfeTheme {
                BfePage {
                    ApkInternalsScreen(tool = tool, path = path, uri = uri, entry = entry, name = name, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TOOL = "com.the412banner.bfe.extra.INTERNALS_TOOL"
        private const val EXTRA_PATH = "com.the412banner.bfe.extra.INTERNALS_PATH"
        private const val EXTRA_ENTRY = "com.the412banner.bfe.extra.INTERNALS_ENTRY"
        private const val EXTRA_NAME = "com.the412banner.bfe.extra.INTERNALS_NAME"

        /**
         * Opens [tool] on [path] (a real file, preferred) or [uri] (copied into the cache first). With
         * [entry], [path] is an APK / zip / jar and the tool opens that entry inside it.
         */
        fun intent(context: Context, tool: InternalsTool, path: String?, uri: Uri?, name: String, entry: String? = null): Intent =
            Intent(context, ApkInternalsActivity::class.java)
                .putExtra(EXTRA_TOOL, tool.name)
                .putExtra(EXTRA_NAME, name)
                .apply {
                    path?.let { putExtra(EXTRA_PATH, it) }
                    entry?.let { putExtra(EXTRA_ENTRY, it) }
                    if (path == null && uri != null) {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
    }
}
