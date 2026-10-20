package com.the412banner.bfe.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.cloud.CloudTransfers
import com.the412banner.bfe.cloud.RcloneApi
import com.the412banner.bfe.cloud.RemoteFiles
import com.the412banner.bfe.cloud.TransferItem
import com.the412banner.bfe.cloud.TransferKind
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.util.DateText
import java.io.File

/*
 * The file manager's remote folders (an SMB share, SFTP / FTP / WebDAV server or cloud account shown as
 * a [Loc.RemoteLoc], listed through [com.the412banner.bfe.storage.RemoteBackend]): the paste plan that
 * hands copies and moves to the background transfer service, fetching a remote file into the cache to
 * open it, and the Properties dialog. Kept out of FileManagerScreen.kt, which only calls into here.
 */

/**
 * Pastes that involve a remote. Instead of streaming bytes through the app (which stalls on big folders
 * and stops when the screen goes off), each pasted item becomes one rclone job in
 * [com.the412banner.bfe.cloud.CloudTransferService]: `operations/copyfile` / `movefile` for a file,
 * `sync/copy` / `sync/move` for a folder. They run in the background with the silent progress
 * notification, keep the phone and Wi-Fi awake, are retried after a failure, and can be cancelled from
 * the notification or the transfers sheet.
 */
internal object RemotePaste {

    /** Whether rclone can do this paste itself: both ends are this device's own storage or a remote, and one is a remote. */
    fun handles(sources: List<Loc>, dstDir: Loc): Boolean {
        val all = sources + dstDir
        return all.any { it is Loc.RemoteLoc } && all.all { it is Loc.RemoteLoc || it is Loc.FileLoc }
    }

    /** Why [src] can't go into [dstDir] as [targetName], or null when it can. */
    enum class Problem { INTO_ITSELF, SAME_PLACE }

    fun problem(src: Loc, dstDir: Loc, targetName: String, cut: Boolean): Problem? {
        val (sFs, sPath) = endpoint(src) ?: return null
        val (dFs, dPath) = endpoint(dstDir) ?: return null
        if (sFs != dFs) return null
        val target = RcloneApi.join(dPath, targetName)
        return when {
            // A folder into itself or anything below it would never end.
            src.isDir && (dPath == sPath || dPath.startsWith("$sPath/")) -> Problem.INTO_ITSELF
            // Onto itself: nothing to do (a move into its own folder, or Overwrite on the same file).
            target == sPath -> Problem.SAME_PLACE
            cut && RcloneApi.parentOf(sPath) == dPath && targetName == src.name -> Problem.SAME_PLACE
            else -> null
        }
    }

    /** The queue item for pasting [src] into [dstDir] under [targetName] (null for a location rclone can't reach). */
    fun item(src: Loc, dstDir: Loc, targetName: String, cut: Boolean, dstLabel: String): TransferItem? {
        val (sFs, sPath) = endpoint(src) ?: return null
        val (dFs, dPath) = endpoint(dstDir) ?: return null
        return TransferItem(
            id = CloudTransfers.newId(),
            kind = TransferKind.COPY,
            remote = (src as? Loc.RemoteLoc)?.remote ?: (dstDir as? Loc.RemoteLoc)?.remote.orEmpty(),
            name = targetName,
            isDir = src.isDir,
            size = if (src.isDir) -1L else src.size,
            srcFs = sFs, srcPath = sPath,
            dstFs = dFs, dstPath = RcloneApi.join(dPath, targetName),
            move = cut,
            fromLabel = placeOf(parentOf(src)),
            toLabel = dstLabel,
            srcDirId = parentOf(src)?.id,
            dstDirId = dstDir.id,
        )
    }

    /** A location as an rclone fs + path: this device's storage is rclone's local fs ("/"). */
    private fun endpoint(loc: Loc): Pair<String, String>? = when (loc) {
        is Loc.FileLoc -> RcloneApi.LOCAL_FS to RcloneApi.localPath(loc.file.absolutePath)
        is Loc.RemoteLoc -> RcloneApi.fsOf(loc.remote) to loc.path
        else -> null
    }

    private fun parentOf(loc: Loc): Loc? = when (loc) {
        is Loc.FileLoc -> loc.file.parentFile?.let { Loc.FileLoc(it) }
        is Loc.RemoteLoc -> RcloneApi.parentOf(loc.path).let { p ->
            Loc.RemoteLoc(loc.remote, p, if (p.isEmpty()) loc.remote else p.substringAfterLast('/'), true, 0L, 0L)
        }
        else -> null
    }

    /** "NAS › Movies", "Internal › Download": where something is, for the transfer list. */
    fun placeOf(dir: Loc?): String = when (dir) {
        is Loc.RemoteLoc -> if (dir.path.isEmpty()) dir.remote else "${dir.remote} › ${dir.path.substringAfterLast('/')}"
        is Loc.FileLoc -> {
            val drive = describeLocation(dir.file).driveLabel
            val volume = com.the412banner.bfe.ui.screens.home.volumeRootOf(dir.file)
            if (dir.file.absolutePath == volume.absolutePath) drive else "$drive › ${dir.file.name}"
        }
        null -> "?"
        else -> dir.name
    }
}

/**
 * Fetches the remote files among [locs] into BFE's cache so they can be opened, shared or installed
 * (a copy that is still the same size and date is reused). [onProgress] gets the fraction of all their
 * bytes done, or null when the sizes are unknown. Cancelling the coroutine stops the download.
 */
internal suspend fun fetchRemoteCopies(context: Context, locs: List<Loc.RemoteLoc>, onProgress: (Float?) -> Unit): List<File> {
    val total = locs.sumOf { it.size }
    var before = 0L
    val out = ArrayList<File>()
    for (l in locs) {
        val file = RemoteFiles.openCacheFile(context, l.remote, l.path, l.name, l.lastModified, l.size)
        if (!RemoteFiles.isCached(file, l.size)) {
            RemoteFiles.fetch(context, l.remote, l.path, file) { b -> onProgress(if (total > 0) ((before + b).toFloat() / total).coerceIn(0f, 1f) else null) }
        }
        before += l.size
        out += file
    }
    return out
}

/** Properties of a file or folder on a remote: name, where it is, size and date as the server reports them. */
@Composable
internal fun RemotePropertiesDialog(loc: Loc.RemoteLoc, onDismiss: () -> Unit) {
    val context = LocalContext.current
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(loc.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                RemotePropRow("On", loc.remote)
                RemotePropRow("Folder", "/" + RcloneApi.parentOf(loc.path))
                RemotePropRow(
                    "Type",
                    if (loc.isDir) "Folder" else loc.name.substringAfterLast('.', "").uppercase().let { if (it.isBlank() || !loc.name.contains('.')) "File" else "$it file" },
                )
                if (!loc.isDir) RemotePropRow("Size", StringUtils.formatBytes(loc.size))
                // Some servers (SMB share roots, FTP folders) don't report a date at all.
                RemotePropRow("Modified", if (loc.lastModified > 0) DateText.dateTime(context, loc.lastModified) else "—")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun RemotePropRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
