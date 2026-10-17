package com.the412banner.bfe.ui.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.screens.guessMime
import com.the412banner.bfe.util.AppSnackbar
import java.io.File

/** A media file as other apps get it: a content:// Uri (FileProvider for real files) and its MIME type. */
internal class MediaTarget(val item: MediaRef, val uri: Uri, val mime: String)

internal fun mediaTarget(context: Context, item: MediaRef): MediaTarget? {
    val uri = item.path?.let { p ->
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(p)) }.getOrNull()
    } ?: Uri.parse(item.uri).takeIf { it.scheme == "content" } ?: return null
    return MediaTarget(item, uri, guessMime(item.name, runCatching { context.contentResolver.getType(uri) }.getOrNull()))
}

internal fun shareMedia(context: Context, item: MediaRef) {
    val t = mediaTarget(context, item) ?: return
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = t.mime
            putExtra(Intent.EXTRA_STREAM, t.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share ${item.name}"))
    }.onFailure { AppSnackbar.show(context, "Couldn't share ${item.name}") }
}

/** A [Loc] for the Open with sheet's header (name + size); content-only files get a stand-in. */
internal fun mediaDisplayLoc(item: MediaRef): Loc =
    item.path?.let { Loc.FileLoc(File(it)) }
        ?: Loc.RootLoc(path = item.uri, name = item.name, isDir = false, size = item.size.coerceAtLeast(0L), lastModified = item.modified)
