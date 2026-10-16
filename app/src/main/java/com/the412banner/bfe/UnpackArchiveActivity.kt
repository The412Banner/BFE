package com.the412banner.bfe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.ui.screens.UnpackArchiveScreen
import com.the412banner.bfe.ui.theme.BfeTheme
import com.the412banner.bfe.unpack.UnpackManager
import com.the412banner.bfe.util.IncomingFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Themed host for the "Unpack Archive" flow ([UnpackArchiveScreen]). Launched from the File Manager's
 * ⋮ menu with the source archive's path, reopened by the unpack notification, and — new for BFE —
 * reachable from the OS "Open with"/Share sheet for an archive or a GOG setup.exe (see the manifest
 * intent-filters). An incoming content:// or file:// Uri is resolved to the real file where possible
 * ([IncomingFile.resolvePath]); only when the sending app hides the location is the file copied in.
 */
private sealed interface Stage {
    data object Resolving : Stage
    data class Ready(val path: String, val initialDest: String?) : Stage
    data class Copying(val name: String, val size: Long) : Stage
    data class Failed(val message: String) : Stage
}

class UnpackArchiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val explicitPath = intent.getStringExtra(EXTRA_ARCHIVE_PATH)?.takeIf { it.isNotBlank() }
        val initialDest = intent.getStringExtra(EXTRA_INITIAL_DEST)?.takeIf { it.isNotBlank() }
        val uri: Uri? = intent.data ?: (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        setContent {
            BfeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var stage by remember {
                        mutableStateOf<Stage>(if (explicitPath != null) Stage.Ready(explicitPath, initialDest) else Stage.Resolving)
                    }
                    var copied by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(Unit) {
                        if (stage == Stage.Resolving) stage = resolve(uri, onStage = { stage = it }, onProgress = { copied = it })
                    }
                    when (val s = stage) {
                        Stage.Resolving -> Centered { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        is Stage.Ready -> UnpackArchiveScreen(
                            archivePath = s.path,
                            initialDestPath = s.initialDest,
                            onClose = { finish() },
                        )
                        is Stage.Copying -> CopyingScreen(s, copied, onCancel = { finish() })
                        is Stage.Failed -> Centered {
                            Text("Can't open this file", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(s.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { finish() }) { Text("Close") }
                        }
                    }
                }
            }
        }
    }

    /** Finds the real file behind [uri], else copies it into app storage, showing [Stage.Copying] meanwhile. */
    private suspend fun resolve(uri: Uri?, onStage: (Stage) -> Unit, onProgress: (Long) -> Unit): Stage {
        if (uri == null) return Stage.Failed("No file was handed to BFE.")
        withContext(Dispatchers.IO) { IncomingFile.resolvePath(this@UnpackArchiveActivity, uri) }
            ?.let { return Stage.Ready(it, null) }

        // The sending app didn't reveal where the file lives (a mail attachment, a browser download, a
        // chat app): stream a copy in and extract that, defaulting the output to Download/.
        val meta = withContext(Dispatchers.IO) { IncomingFile.meta(this@UnpackArchiveActivity, uri) }
        val dir = IncomingFile.incomingDir(this)
        withContext(Dispatchers.IO) {
            IncomingFile.clean(this@UnpackArchiveActivity, UnpackManager.current.takeIf { it.isRunning }?.archivePath)
        }
        val free = IncomingFile.freeBytes(dir)
        if (meta.size > 0 && meta.size > free) {
            return Stage.Failed(
                "${meta.name} is ${StringUtils.formatBytes(meta.size)} but only ${StringUtils.formatBytes(free)} is free. " +
                    "Free up space, or open it from BFE's file browser instead.",
            )
        }
        onStage(Stage.Copying(meta.name, meta.size))
        return try {
            val file = IncomingFile.copyIn(this, uri, meta, onProgress)
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            Stage.Ready(file.absolutePath, downloads)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Stage.Failed("Couldn't read the file from the app that sent it (${t.message ?: t.javaClass.simpleName}).")
        }
    }

    companion object {
        const val EXTRA_ARCHIVE_PATH = "archivePath"
        const val EXTRA_INITIAL_DEST = "initialDest"

        /**
         * @param initialDest optional starting extract destination (BFE dual-pane: the other pane's
         *   directory). When set, the screen seeds "Extract to" with a subfolder inside it; the user
         *   can still change it. Null keeps the screen's own sibling-folder default.
         */
        fun intent(context: Context, archivePath: String, initialDest: String? = null): Intent =
            Intent(context, UnpackArchiveActivity::class.java)
                .putExtra(EXTRA_ARCHIVE_PATH, archivePath)
                .apply { initialDest?.let { putExtra(EXTRA_INITIAL_DEST, it) } }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun CopyingScreen(s: Stage.Copying, copied: Long, onCancel: () -> Unit) {
    Centered {
        Text("Opening ${s.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "The app that sent this file doesn't share where it's stored, so BFE is copying it in first.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        if (s.size > 0) {
            LinearProgressIndicator(progress = { (copied.toFloat() / s.size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text("${StringUtils.formatBytes(copied)} of ${StringUtils.formatBytes(s.size)}", style = MaterialTheme.typography.bodySmall)
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(StringUtils.formatBytes(copied), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}
