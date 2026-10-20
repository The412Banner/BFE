package com.the412banner.bfe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.the412banner.bfe.ui.components.BfePage
import com.the412banner.bfe.ui.hex.HexEditorScreen
import com.the412banner.bfe.ui.theme.BfeTheme

/**
 * BFE's hex viewer / editor (Open with sheet → Open in BFE as → Hex editor): any file as offset | hex |
 * text columns, read lazily so multi-GB files open at once. Overwrite-only editing with Undo and Save
 * in place when the file can be written; read-only otherwise. Only started from inside BFE.
 */
class HexEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val path = intent.getStringExtra(EXTRA_PATH)
        if (uri == null && path == null) { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: path?.substringAfterLast('/') ?: "File"
        val writable = intent.getBooleanExtra(EXTRA_WRITABLE, false)
        setContent {
            BfeTheme {
                BfePage {
                    HexEditorScreen(path = path, uri = uri, name = name, allowWrite = writable, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "com.the412banner.bfe.extra.HEX_PATH"
        private const val EXTRA_NAME = "com.the412banner.bfe.extra.HEX_NAME"
        private const val EXTRA_WRITABLE = "com.the412banner.bfe.extra.HEX_WRITABLE"

        /**
         * Opens [path] (a real file, preferred) or [uri] in the hex editor. [writable] = edits may be
         * saved back (false for cache copies of archive entries and root files).
         */
        fun intent(context: Context, path: String?, uri: Uri?, name: String, writable: Boolean): Intent =
            Intent(context, HexEditorActivity::class.java)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_WRITABLE, writable)
                .apply {
                    path?.let { putExtra(EXTRA_PATH, it) }
                    uri?.let {
                        data = it
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                    }
                }
    }
}
