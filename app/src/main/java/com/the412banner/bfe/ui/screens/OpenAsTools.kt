package com.the412banner.bfe.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import com.the412banner.bfe.ApkInternalsActivity
import com.the412banner.bfe.DocumentViewer
import com.the412banner.bfe.FontViewerActivity
import com.the412banner.bfe.HexEditorActivity
import com.the412banner.bfe.HtmlViewerActivity
import com.the412banner.bfe.ScriptRunnerActivity
import com.the412banner.bfe.SvgViewerActivity
import com.the412banner.bfe.apk.internals.InternalsTool
import com.the412banner.bfe.media.MediaType
import com.the412banner.bfe.unpack.SevenZip
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The file the "Open in BFE as" tiles act on. [path] is a real, readable file when there is one (an
 * archive entry's cache copy counts), [uri] always works. [writable] = edits may go back to the
 * file itself (not to a cache copy). [rootPath] is the real path of a file only root can read.
 */
internal class OpenAsTarget(
    val name: String,
    val path: String?,
    val uri: Uri,
    val mime: String,
    val writable: Boolean,
    val rootPath: String?,
)

/**
 * The "Open in BFE as" tiles of the Open with sheet: every one of BFE's own tools, offered for any
 * file whatever its extension, so a file with a wrong or missing extension (or a file you simply want
 * to look at differently) still opens the way you choose. A tile only opens the tool — nothing is
 * remembered as a default. The file-manager actions come in as callbacks: [onArchive] browses the
 * file as a folder (null when it isn't a real file), [onMedia] opens the viewer / player as the given
 * type, [onInstall] the system installer, [onApkTools] BFE's APK editor.
 */
internal fun openAsTools(
    context: Context,
    t: OpenAsTarget,
    onArchive: (() -> Unit)?,
    onMedia: (MediaType) -> Unit,
    onInstall: () -> Unit,
    onApkTools: () -> Unit,
): List<SheetAction> = buildList {
    fun start(intent: android.content.Intent) {
        runCatching { context.startActivity(intent) }.onFailure { AppSnackbar.show(context, "Couldn't open ${t.name}") }
    }
    add(SheetAction("Text editor", Icons.Filled.EditNote) {
        DocumentViewer.open(context, t.path, t.uri, t.name, t.mime, forceText = true)
    })
    add(SheetAction("Hex editor", Icons.Filled.Tag) {
        start(HexEditorActivity.intent(context, t.path, t.uri, t.name, t.writable))
    })
    add(SheetAction("Archive", Icons.Filled.FolderOpen) {
        if (onArchive != null) onArchive()
        else AppSnackbar.show(context, "Only a file in a folder BFE reads directly can be opened as an archive")
    })
    add(SheetAction("Image", Icons.Filled.Photo) { onMedia(MediaType.IMAGE) })
    add(SheetAction("Music", Icons.Filled.MusicNote) { onMedia(MediaType.AUDIO) })
    add(SheetAction("Video", Icons.Filled.Movie) { onMedia(MediaType.VIDEO) })
    add(SheetAction("Web page", Icons.Filled.Language) {
        start(HtmlViewerActivity.intent(context, t.path, t.uri, t.name, forceHtml = true))
    })
    add(SheetAction("Document", Icons.Filled.Description) {
        DocumentViewer.open(context, t.path, t.uri, t.name, t.mime)
    })
    add(SheetAction("Install APK", Icons.Filled.Android, onClick = onInstall))
    add(SheetAction("APK tools", Icons.Filled.Build, onClick = onApkTools))
    add(SheetAction("Font", Icons.Filled.FontDownload) {
        start(FontViewerActivity.intent(context, t.path, t.uri, t.name))
    })
    add(SheetAction("SVG", Icons.Filled.Brush) {
        start(SvgViewerActivity.intent(context, t.path, t.uri, t.name))
    })
    add(SheetAction("Run script", Icons.Filled.Terminal) {
        start(ScriptRunnerActivity.intent(context, t.rootPath ?: t.path, t.uri, t.name, rootOnly = t.rootPath != null))
    })
    // Android app internals: an APK as a whole goes to the hub; each tool also reads a file on its own.
    fun internals(tool: InternalsTool) = start(ApkInternalsActivity.intent(context, tool, t.path, t.uri, t.name))
    add(SheetAction("APK internals", Icons.Filled.AccountTree) { internals(InternalsTool.HUB) })
    add(SheetAction("Binary XML", Icons.Filled.Code) { internals(InternalsTool.AXML) })
    add(SheetAction("Resources", Icons.Filled.Inventory2) { internals(InternalsTool.ARSC) })
    add(SheetAction("Dex", Icons.Filled.Memory) { internals(InternalsTool.DEX) })
    add(SheetAction("Java class / jar", Icons.Filled.Coffee) { internals(InternalsTool.JAVA) })
}

/**
 * "Open in BFE as → Archive": asks 7-Zip whether it can list [file] at all (whatever its extension),
 * then hands it to [browse]; a file it can't read gets a short message instead of an empty folder.
 */
internal fun openAsArchive(context: Context, scope: CoroutineScope, file: File, browse: (File) -> Unit) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { SevenZip.list(context, file)?.type != null }.getOrDefault(false) }
        if (ok) browse(file) else AppSnackbar.show(context, "${file.name} can't be read as an archive")
    }
}
