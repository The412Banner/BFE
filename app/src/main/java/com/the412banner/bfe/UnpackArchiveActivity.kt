package com.the412banner.bfe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.the412banner.bfe.core.FileUtils
import com.the412banner.bfe.ui.screens.UnpackArchiveScreen
import com.the412banner.bfe.ui.theme.BfeTheme

/**
 * Themed host for the "Unpack Archive" flow ([UnpackArchiveScreen]). Launched from the File Manager's
 * ⋮ menu with the source archive's path, reopened by the unpack notification, and — new for BFE —
 * reachable from the OS "Open with"/Share sheet for an archive or a GOG setup.exe (see the manifest
 * intent-filters), in which case the path is resolved from the incoming content:// or file:// Uri.
 */
class UnpackArchiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val archivePath = resolveArchivePath()
        setContent {
            BfeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UnpackArchiveScreen(archivePath = archivePath, onClose = { finish() })
                }
            }
        }
    }

    /** Path from the explicit extra, else from a VIEW/SEND intent's Uri (best-effort). */
    private fun resolveArchivePath(): String {
        intent.getStringExtra(EXTRA_ARCHIVE_PATH)?.takeIf { it.isNotBlank() }?.let { return it }
        val uri: Uri? = intent.data
            ?: (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        if (uri != null) {
            if (uri.scheme == "file") uri.path?.let { return it }
            runCatching { FileUtils.getFilePathFromUri(this, uri) }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    companion object {
        const val EXTRA_ARCHIVE_PATH = "archivePath"

        fun intent(context: Context, archivePath: String): Intent =
            Intent(context, UnpackArchiveActivity::class.java)
                .putExtra(EXTRA_ARCHIVE_PATH, archivePath)
    }
}
