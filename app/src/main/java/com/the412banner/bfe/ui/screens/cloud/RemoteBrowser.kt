@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.the412banner.bfe.ui.screens.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.icons.BfeIcon
import com.the412banner.bfe.ui.icons.FileIcons
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.cloud.CloudServices
import com.the412banner.bfe.cloud.CloudSetup
import com.the412banner.bfe.cloud.CloudTransfers
import com.the412banner.bfe.cloud.RcEntry
import com.the412banner.bfe.cloud.RcRemote
import com.the412banner.bfe.cloud.RcloneApi
import com.the412banner.bfe.cloud.RcloneDaemon
import com.the412banner.bfe.cloud.TransferItem
import com.the412banner.bfe.cloud.TransferKind
import com.the412banner.bfe.cloud.TransferStatus
import com.the412banner.bfe.cloud.awaitJob
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.screens.FileManagerScreen
import com.the412banner.bfe.ui.screens.OpenWithSheet
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.SheetAction
import com.the412banner.bfe.ui.screens.guessMime
import com.the412banner.bfe.ui.screens.mediaSheetAction
import com.the412banner.bfe.media.MediaLaunch
import com.the412banner.bfe.media.MediaTypes
import com.the412banner.bfe.ui.screens.outlinedMenuCard
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.IncomingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Where a BFE-side folder/file pick is going. */
private sealed class LocalPick {
    object UploadFile : LocalPick()
    object UploadFolder : LocalPick()
    class DownloadDir(val targets: List<RcEntry>) : LocalPick()
}

private class OpenedFile(val entry: RcEntry, val file: File, val uri: Uri, val mime: String)

@Suppress("DEPRECATION")
private fun defaultDownloadDir(): String = File(Environment.getExternalStorageDirectory(), "Download").absolutePath

/** The cached copy an "open" downloads to; keyed on path + size + date so a changed file is re-fetched. */
private fun openCacheFile(ctx: Context, remote: String, e: RcEntry): File {
    val key = Integer.toHexString("$remote:${e.path}:${e.modTime}:${e.size}".hashCode())
    return File(ctx.cacheDir, "cloud-open/$key/${e.name}")
}

/** Opened-file copies older than a day are dropped. */
private fun cleanOpenCache(ctx: Context) {
    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    File(ctx.cacheDir, "cloud-open").listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
}

private fun copyUnique(src: File, dir: File): File {
    dir.mkdirs()
    val dot = src.name.lastIndexOf('.')
    val (base, ext) = if (dot <= 0) src.name to "" else src.name.substring(0, dot) to src.name.substring(dot)
    var dest = File(dir, src.name)
    var i = 1
    while (dest.exists()) dest = File(dir, "$base (${i++})$ext")
    src.copyTo(dest)
    return dest
}

private fun fileNameProblem(name: String, taken: Collection<String>): String? = when {
    name.isBlank() -> "Enter a name"
    name.contains('/') -> "A name can't contain /"
    name == "." || name == ".." -> "Not a valid name"
    taken.any { it == name } -> "Something here already has that name"
    else -> null
}

/**
 * Browses one remote: folders and files with size and date, breadcrumb, sort, in-folder search,
 * multi-select; open (download to cache → BFE's Open with sheet), download to a local folder, upload
 * (system picker, or a file/folder chosen in BFE's own browser), new folder, rename, delete. Copies
 * between the device and the remote go through [CloudTransfers] (background service with progress);
 * the quick operations (list, rename, delete) are direct rcd calls.
 */
@Composable
internal fun RemoteBrowser(remote: RcRemote, onBack: () -> Unit, onTransfers: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val service = remember(remote) { CloudServices.forRemote(remote) }
    val label = service?.label ?: remote.type
    val startPath = remember(remote.name) { CloudSetup.startPath(context, remote.name) }
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    var path by rememberSaveable(remote.name) { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf<List<RcEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val cache = remember(remote.name) { HashMap<String, List<RcEntry>>() }
    var sortBy by rememberSaveable { mutableStateOf(prefs.getString("cloud_sort", "name") ?: "name") }
    var sortDesc by rememberSaveable { mutableStateOf(prefs.getBoolean("cloud_sort_desc", false)) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RcEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<RcEntry>?>(null) }
    var downloadTargets by remember { mutableStateOf<List<RcEntry>?>(null) }
    var downloadDir by remember { mutableStateOf(prefs.getString("cloud_download_dir", null) ?: defaultDownloadDir()) }
    var pick by remember { mutableStateOf<LocalPick?>(null) }
    var openTarget by remember { mutableStateOf<RcEntry?>(null) }
    var openBytes by remember { mutableStateOf(0L) }
    var opened by remember { mutableStateOf<OpenedFile?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var showUpload by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(remote.name, path, reload) {
        selected = emptySet()
        entries = cache[path]
        error = null
        loading = true
        try {
            val api = RcloneDaemon.api(context)
            val list = withContext(Dispatchers.IO) { api.list(remote.name, path) }
            cache[path] = list
            entries = list
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = CloudSetup.friendly(e.message)
        } finally {
            loading = false
        }
    }

    // A finished upload refreshes the folder it landed in.
    LaunchedEffect(remote.name) {
        CloudTransfers.finished.collect { t ->
            if (t.kind == TransferKind.UPLOAD && t.remote == remote.name && t.status == TransferStatus.DONE) {
                val dir = t.remoteDir.trim('/')
                cache.remove(dir)
                if (dir == path) reload++
            }
        }
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { runCatching { cleanOpenCache(context) } } }

    val visible = remember(entries, query, sortBy, sortDesc) {
        val q = query.trim().lowercase()
        val base = entries.orEmpty().filter { q.isEmpty() || it.name.lowercase().contains(q) }
        val by: Comparator<RcEntry> = when (sortBy) {
            "size" -> compareBy { it.size }
            "date" -> compareBy { it.modTime }
            else -> compareBy { it.name.lowercase() }
        }
        base.sortedWith(compareBy<RcEntry> { if (it.isDir) 0 else 1 }.then(if (sortDesc) by.reversed() else by))
    }

    fun go(to: String) {
        path = to.trim('/')
        query = ""
        searching = false
    }

    fun handleBack() {
        if (selected.isNotEmpty()) selected = emptySet()
        else if (searching) { searching = false; query = "" }
        else if (path.isNotEmpty() && path != startPath) go(RcloneApi.parentOf(path))
        else onBack()
    }
    BackHandler(enabled = pick == null) { handleBack() }

    fun runBusy(what: String, block: suspend (RcloneApi) -> Unit) {
        busy = what
        scope.launch {
            try {
                val api = RcloneDaemon.api(context)
                withContext(Dispatchers.IO) { block(api) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppSnackbar.show(context, CloudSetup.friendly(e.message), long = true)
            } finally {
                busy = null
                cache.remove(path)
                reload++
            }
        }
    }

    fun enqueueDownloads(targets: List<RcEntry>, dir: String) {
        CloudTransfers.enqueue(context, targets.map { e ->
            TransferItem(
                id = CloudTransfers.newId(), kind = TransferKind.DOWNLOAD, remote = remote.name, name = e.name,
                isDir = e.isDir, remotePath = e.path, localDir = dir, size = e.size,
            )
        })
        AppSnackbar.show(context, if (targets.size == 1) "Downloading ${targets[0].name}" else "Downloading ${targets.size} items")
        selected = emptySet()
    }

    fun enqueueUploads(files: List<File>) {
        CloudTransfers.enqueue(context, files.map { f ->
            TransferItem(
                id = CloudTransfers.newId(), kind = TransferKind.UPLOAD, remote = remote.name, name = f.name,
                isDir = f.isDirectory, localPath = f.absolutePath, remoteDir = path, size = if (f.isFile) f.length() else -1L,
            )
        })
        AppSnackbar.show(context, if (files.size == 1) "Uploading ${files[0].name}" else "Uploading ${files.size} items")
    }

    val pickDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val dir = path
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.map { u ->
                    // Keep read access until the (possibly queued) upload has copied the file.
                    runCatching { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    val meta = IncomingFile.meta(context, u)
                    val real = IncomingFile.resolvePath(context, u)
                    TransferItem(
                        id = CloudTransfers.newId(), kind = TransferKind.UPLOAD, remote = remote.name,
                        name = real?.let { File(it).name } ?: meta.name, isDir = false,
                        localPath = real.orEmpty(), remoteDir = dir,
                        sourceUri = if (real == null) u.toString() else null, size = meta.size,
                    )
                }
            }
            CloudTransfers.enqueue(context, items)
            AppSnackbar.show(context, if (items.size == 1) "Uploading ${items[0].name}" else "Uploading ${items.size} files")
        }
    }

    // Open = fetch into the cache (reused while the file is unchanged), then BFE's Open with sheet.
    LaunchedEffect(openTarget) {
        val e = openTarget ?: return@LaunchedEffect
        openBytes = 0L
        try {
            val file = openCacheFile(context, remote.name, e)
            val cached = withContext(Dispatchers.IO) { file.isFile && e.size >= 0 && file.length() == e.size }
            if (!cached) {
                val api = RcloneDaemon.api(context)
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    file.delete()
                    val job = api.startCopyFile(RcloneApi.fsOf(remote.name), e.path, RcloneApi.LOCAL_FS, RcloneApi.localPath(file.absolutePath))
                    api.rc.awaitJob(job, pollMs = 300) {
                        runCatching { api.rc.stats("job/$job") }.getOrNull()?.let { s -> openBytes = s.optLong("bytes", 0L) }
                    }
                    api.rc.deleteStats("job/$job")
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            opened = OpenedFile(e, file, uri, guessMime(e.name, e.mimeType?.substringBefore(';')?.trim()))
        } catch (x: kotlinx.coroutines.CancellationException) {
            throw x
        } catch (x: Exception) {
            AppSnackbar.show(context, "Couldn't open ${e.name}: ${CloudSetup.friendly(x.message)}", long = true)
        } finally {
            if (openTarget == e) openTarget = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                when {
                    selected.isNotEmpty() -> TopAppBar(
                        title = { Text("${selected.size} selected") },
                        navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") } },
                        actions = {
                            val sel = visible.filter { it.path in selected }
                            IconButton(onClick = { downloadTargets = sel }) { Icon(Icons.Filled.Download, "Download") }
                            IconButton(onClick = { deleteTargets = sel }) { Icon(Icons.Filled.Delete, "Delete") }
                            IconButton(onClick = { selected = visible.map { it.path }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        },
                    )
                    searching -> {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                        TopAppBar(
                            title = {
                                TextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text("Search this folder") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                )
                            },
                            navigationIcon = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Filled.ArrowBack, "Close search") } },
                        )
                    }
                    else -> TopAppBar(
                        title = {
                            Column {
                                Text(remote.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        },
                        navigationIcon = { IconButton(onClick = { handleBack() }) { Icon(Icons.Filled.ArrowBack, "Back") } },
                        actions = {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
                            Box {
                                IconButton(onClick = { showUpload = true }) { Icon(Icons.Filled.CloudUpload, "Upload") }
                                BfeDropdownMenu(expanded = showUpload, onDismissRequest = { showUpload = false }, modifier = Modifier.outlinedMenuCard()) {
                                    DropdownMenuItem(
                                        text = { Text("Files…") },
                                        leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                                        onClick = { showUpload = false; runCatching { pickDocs.launch(arrayOf("*/*")) } },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("A file in BFE…") },
                                        leadingIcon = { Icon(Icons.Filled.InsertDriveFile, null) },
                                        onClick = { showUpload = false; pick = LocalPick.UploadFile },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("A folder in BFE…") },
                                        leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                        onClick = { showUpload = false; pick = LocalPick.UploadFolder },
                                    )
                                }
                            }
                            TransfersButton(onTransfers)
                            Box {
                                IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreVert, "More") }
                                BfeDropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }, modifier = Modifier.outlinedMenuCard()) {
                                    DropdownMenuItem(
                                        text = { Text("New folder") },
                                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                        onClick = { showMore = false; showNewFolder = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                        onClick = { showMore = false; cache.remove(path); reload++ },
                                    )
                                    listOf("name" to "Sort by name", "size" to "Sort by size", "date" to "Sort by date").forEach { (key, text) ->
                                        DropdownMenuItem(
                                            text = { Text(text, fontWeight = if (sortBy == key) FontWeight.SemiBold else FontWeight.Normal) },
                                            leadingIcon = { if (sortBy == key) Icon(Icons.Filled.Check, null) else Icon(Icons.Filled.Sort, null) },
                                            onClick = {
                                                showMore = false
                                                if (sortBy == key) sortDesc = !sortDesc else { sortBy = key; sortDesc = key != "name" }
                                                prefs.edit().putString("cloud_sort", sortBy).putBoolean("cloud_sort_desc", sortDesc).apply()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = { CloudTransferPill(onClick = onTransfers) },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Breadcrumb(remote.name, path, onGo = { go(it) })
                if (loading && entries != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))
                val list = entries
                Box(Modifier.fillMaxSize()) {
                    when {
                        list == null && error == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        list == null -> FolderError(
                            error.orEmpty(),
                            canSignIn = remote.type in CloudServices.OAUTH_TYPES,
                            onRetry = { reload++ },
                            onSignIn = { CloudSetup.reconnect(context, remote) },
                        )
                        visible.isEmpty() && query.isNotBlank() -> EmptyState(Icons.Filled.Search, "No matches", "Nothing in this folder matches “$query”.")
                        visible.isEmpty() -> EmptyState(
                            Icons.Filled.FolderOpen, "This folder is empty", "Upload files here from this device.", iconRes = FileIcons.FOLDER_OPEN,
                            action = "Upload files", onAction = { runCatching { pickDocs.launch(arrayOf("*/*")) } },
                        )
                        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(visible, key = { it.path }) { e ->
                                val isSel = e.path in selected
                                EntryRow(
                                    e = e,
                                    selected = isSel,
                                    selecting = selected.isNotEmpty(),
                                    subtitle = buildString {
                                        if (!e.isDir && e.size >= 0) append(StringUtils.formatBytes(e.size))
                                        if (e.modTime > 0) { if (isNotEmpty()) append("  •  "); append(dateFmt.format(Date(e.modTime))) }
                                    },
                                    onClick = {
                                        when {
                                            selected.isNotEmpty() -> selected = if (isSel) selected - e.path else selected + e.path
                                            e.isDir -> go(e.path)
                                            else -> openTarget = e
                                        }
                                    },
                                    onLongClick = { selected = if (isSel) selected - e.path else selected + e.path },
                                ) {
                                    Box {
                                        IconButton(onClick = { menuFor = e.path }) { Icon(Icons.Filled.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        BfeDropdownMenu(expanded = menuFor == e.path, onDismissRequest = { menuFor = null }, modifier = Modifier.outlinedMenuCard()) {
                                            if (!e.isDir) {
                                                DropdownMenuItem(
                                                    text = { Text("Open") },
                                                    leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                                                    onClick = { menuFor = null; openTarget = e },
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Download…") },
                                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                                onClick = { menuFor = null; downloadTargets = listOf(e) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                                                onClick = { menuFor = null; renameTarget = e },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = { menuFor = null; deleteTargets = listOf(e) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BFE's own browser as the picker for uploads and the download folder.
        pick?.let { pk ->
            // Registered before the file manager's own handler, so Back walks up its folders first
            // and only closes the picker at the top.
            BackHandler { pick = null }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                when (pk) {
                                    LocalPick.UploadFile -> "Choose a file to upload"
                                    LocalPick.UploadFolder -> "Choose a folder to upload"
                                    is LocalPick.DownloadDir -> "Choose where to download"
                                },
                            )
                        },
                        navigationIcon = { IconButton(onClick = { pick = null }) { Icon(Icons.Filled.Close, "Cancel") } },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        FileManagerScreen(
                            pickMode = true,
                            pickDirMode = pk !is LocalPick.UploadFile,
                            initialDir = if (pk is LocalPick.DownloadDir) File(downloadDir) else null,
                            onPick = { f ->
                                pick = null
                                when (pk) {
                                    LocalPick.UploadFile, LocalPick.UploadFolder -> enqueueUploads(listOf(f))
                                    is LocalPick.DownloadDir -> {
                                        downloadDir = f.absolutePath
                                        prefs.edit().putString("cloud_download_dir", downloadDir).apply()
                                        downloadTargets = pk.targets
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ──

    openTarget?.let { e ->
        OutlinedAlertDialog(
            onDismissRequest = {},
            title = { Text("Opening ${e.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    if (e.size > 0) {
                        LinearProgressIndicator(progress = { (openBytes.toFloat() / e.size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${StringUtils.formatBytes(openBytes)} of ${StringUtils.formatBytes(e.size)}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { openTarget = null }) { Text("Cancel") } },
        )
    }

    opened?.let { o ->
        val actions = remember(o, downloadDir) {
            buildList {
                // Photos, videos and music: the downloaded copy in BFE's own viewer / player.
                MediaTypes.typeOf(o.entry.name, o.mime)?.let { type ->
                    add(mediaSheetAction(context, type) { MediaLaunch.openFile(context, type, o.file) })
                }
                add(SheetAction("Save to ${File(downloadDir).name}", Icons.Filled.Download) {
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { runCatching { copyUnique(o.file, File(downloadDir)) } }
                        AppSnackbar.show(context, res.fold({ "Saved to ${it.absolutePath}" }, { "Couldn't save: ${it.message}" }))
                    }
                })
                add(SheetAction("Download to…", Icons.Filled.FolderOpen) { pick = LocalPick.DownloadDir(listOf(o.entry)) })
            }
        }
        OpenWithSheet(
            file = Loc.FileLoc(o.file),
            uri = o.uri,
            detectedMime = o.mime,
            actions = actions,
            // It's a downloaded copy: edits wouldn't reach the cloud file.
            allowWrite = false,
            onDismiss = { opened = null },
        )
    }

    downloadTargets?.let { targets ->
        val files = targets.filter { !it.isDir }
        val dirs = targets.size - files.size
        OutlinedAlertDialog(
            onDismissRequest = { downloadTargets = null },
            title = { Text(if (targets.size == 1) "Download ${targets[0].name}" else "Download ${targets.size} items", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    val bytes = files.sumOf { it.size.coerceAtLeast(0L) }
                    Text(
                        buildString {
                            if (files.isNotEmpty()) append("${files.size} file${if (files.size == 1) "" else "s"}, ${StringUtils.formatBytes(bytes)}")
                            if (dirs > 0) { if (isNotEmpty()) append(" + "); append("$dirs folder${if (dirs == 1) "" else "s"} with everything inside") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { pick = LocalPick.DownloadDir(targets); downloadTargets = null }
                            .padding(vertical = 8.dp),
                    ) {
                        BfeIcon(FileIcons.FOLDER, size = 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(downloadDir, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    downloadTargets = null
                    enqueueDownloads(targets, downloadDir)
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { downloadTargets = null }) { Text("Cancel") } },
        )
    }

    deleteTargets?.let { targets ->
        OutlinedAlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text(if (targets.size == 1) "Delete ${targets[0].name}?" else "Delete ${targets.size} items?", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Text(
                    "This deletes " + (if (targets.size == 1) "it" else "them") + " from ${remote.name}" +
                        (if (targets.any { it.isDir }) ", folders with everything inside" else "") +
                        ". Some services keep deleted files in their trash for a while.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTargets = null
                    selected = emptySet()
                    runBusy("Deleting…") { api ->
                        targets.forEach { if (it.isDir) api.purge(remote.name, it.path) else api.deleteFile(remote.name, it.path) }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTargets = null }) { Text("Cancel") } },
        )
    }

    renameTarget?.let { e ->
        NameDialog(
            title = "Rename",
            initial = e.name,
            confirm = "Rename",
            taken = entries.orEmpty().map { it.name }.filter { it != e.name },
            onDismiss = { renameTarget = null },
        ) { newName ->
            renameTarget = null
            if (newName != e.name) {
                runBusy("Renaming…") { api ->
                    val dst = RcloneApi.join(RcloneApi.parentOf(e.path), newName)
                    if (e.isDir) api.rc.awaitJob(api.startMoveDir(RcloneApi.fsOf(remote.name, e.path), RcloneApi.fsOf(remote.name, dst)))
                    else api.moveFile(RcloneApi.fsOf(remote.name), e.path, RcloneApi.fsOf(remote.name), dst)
                }
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            initial = "",
            confirm = "Create",
            taken = entries.orEmpty().map { it.name },
            onDismiss = { showNewFolder = false },
        ) { name ->
            showNewFolder = false
            runBusy("Creating folder…") { api -> api.mkdir(remote.name, RcloneApi.join(path, name)) }
            if (service?.bucketBased == true && path.isNotEmpty()) {
                AppSnackbar.show(context, "On object storage a folder only shows up once it holds a file.", long = true)
            }
        }
    }

    busy?.let { what ->
        OutlinedAlertDialog(
            onDismissRequest = {},
            title = { Text(what) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }
}

@Composable
private fun Breadcrumb(remoteName: String, path: String, onGo: (String) -> Unit) {
    val segs = if (path.isEmpty()) emptyList() else path.split('/')
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.scrollTo(Int.MAX_VALUE) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Crumb(remoteName, current = segs.isEmpty()) { onGo("") }
        segs.forEachIndexed { i, s ->
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Crumb(s, current = i == segs.lastIndex) { onGo(segs.take(i + 1).joinToString("/")) }
        }
    }
}

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !current, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
private fun EntryRow(
    e: RcEntry,
    selected: Boolean,
    selecting: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) cs.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        FileTypeIcon(e.name, e.isDir, Modifier.padding(horizontal = 4.dp), size = 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun FolderError(error: String, canSignIn: Boolean, onRetry: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Couldn't open this folder", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = onRetry) { Text("Retry") }
            if (canSignIn) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onSignIn) { Text("Sign in again") }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    taken: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val problem = if (name.trim() == initial && initial.isNotEmpty()) null else fileNameProblem(name.trim(), taken)
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                isError = problem != null && name.isNotEmpty(),
                supportingText = if (problem != null && name.isNotEmpty()) { { Text(problem) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(enabled = problem == null, onClick = { onConfirm(name.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
