package com.the412banner.bfe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.the412banner.bfe.ui.components.BfePage
import com.the412banner.bfe.ui.script.ScriptRunnerScreen
import com.the412banner.bfe.ui.theme.BfeTheme

/**
 * BFE's script runner (Open with sheet → Open in BFE as → Run script): runs a text file with `sh` (or
 * the interpreter its #! line names, when the device has it) in the file's folder, optionally as root,
 * with live output, the exit code, Stop and Copy output. It asks before every run. Only started from
 * inside BFE.
 */
class ScriptRunnerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val path = intent.getStringExtra(EXTRA_PATH)
        if (uri == null && path == null) { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME) ?: path?.substringAfterLast('/') ?: "script"
        val rootOnly = intent.getBooleanExtra(EXTRA_ROOT_ONLY, false)
        setContent {
            BfeTheme {
                BfePage {
                    ScriptRunnerScreen(path = path, uri = uri, name = name, rootOnly = rootOnly, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "com.the412banner.bfe.extra.SCRIPT_PATH"
        private const val EXTRA_NAME = "com.the412banner.bfe.extra.SCRIPT_NAME"
        private const val EXTRA_ROOT_ONLY = "com.the412banner.bfe.extra.SCRIPT_ROOT_ONLY"

        /**
         * Runs [path] (the real file; with [rootOnly] a path only root can read) — or, when there is no
         * path, a cache copy of [uri] from BFE's cache folder.
         */
        fun intent(context: Context, path: String?, uri: Uri?, name: String, rootOnly: Boolean): Intent =
            Intent(context, ScriptRunnerActivity::class.java)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_ROOT_ONLY, rootOnly)
                .apply {
                    path?.let { putExtra(EXTRA_PATH, it) }
                    uri?.let { data = it; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
    }
}
