@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.the412banner.bfe.ui.screens.categories

import com.the412banner.bfe.ui.components.bfeBarBackground
import com.the412banner.bfe.ui.components.bfeTopBarColors
import com.the412banner.bfe.ui.components.bfeCardColors
import com.the412banner.bfe.ui.components.bfeCardBorder
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.icons.BfeIcon
import com.the412banner.bfe.ui.icons.fileIconRes
import com.the412banner.bfe.UnpackArchiveActivity
import com.the412banner.bfe.core.FileUtils
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.screens.MenuItemDivider
import com.the412banner.bfe.ui.screens.OpenWithSheet
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.SheetAction
import com.the412banner.bfe.ui.screens.SheetIcons
import com.the412banner.bfe.ui.screens.guessMime
import com.the412banner.bfe.ui.screens.mediaSheetAction
import com.the412banner.bfe.media.MediaLaunch
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.MediaSiblings
import com.the412banner.bfe.media.MediaTypes
import com.the412banner.bfe.ui.screens.home.FolderPickerOverlay
import com.the412banner.bfe.ui.screens.home.HomeTileStyles
import com.the412banner.bfe.ui.screens.outlinedMenuCard
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.DateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A file waiting in the Open with sheet. */
private class CategoryOpenWith(val item: MediaItem, val uri: Uri, val mime: String)

/** Copy to… / Move to… waiting for its destination folder. */
private class PendingTransfer(val items: List<MediaItem>, val move: Boolean)

private fun shareUri(context: Context, file: File): Uri? = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

/** A non-colliding target in [dir] for [name] ("a.jpg" → "a (1).jpg"); copies never overwrite. */
private fun uniqueTarget(dir: File, name: String): File {
    var f = File(dir, name)
    if (!f.exists()) return f
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var i = 1
    while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
    return f
}

/**
 * One category's files gathered from every volume through MediaStore (Images, Audio, Videos,
 * Documents, New files): list or grid (thumbnails for images and videos), sort by name / size / date,
 * search, and multi-select with Share / Copy to… / Move to… / Delete. Tapping a file opens BFE's own
 * Open with sheet (never an app directly); "Show in folder" jumps into the file manager there.
 */
@Composable
fun CategoryScreen(
    category: MediaCategory,
    onBack: () -> Unit,
    onShowInFolder: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val prefKey = "cat_${category.name.lowercase()}"
    val visualDefault = category == MediaCategory.IMAGES || category == MediaCategory.VIDEOS
    var grid by remember(category) { mutableStateOf(prefs.getBoolean("${prefKey}_grid", visualDefault)) }
    var sortBy by remember(category) { mutableStateOf(prefs.getString("${prefKey}_sort", "date") ?: "date") }
    var sortDesc by remember(category) { mutableStateOf(prefs.getBoolean("${prefKey}_desc", true)) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var allItems by remember(category) { mutableStateOf<List<MediaItem>?>(null) }
    var loadFailed by remember(category) { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var openWith by remember { mutableStateOf<CategoryOpenWith?>(null) }
    var pendingTransfer by remember { mutableStateOf<PendingTransfer?>(null) }
    var pendingDelete by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var opJob by remember { mutableStateOf<Job?>(null) }
    var opLabel by remember { mutableStateOf("") }
    var opProgress by remember { mutableFloatStateOf(0f) }
    var opDeterminate by remember { mutableStateOf(false) }

    // ── Load (and reload when BFE comes back, so files changed elsewhere show up) ──
    LaunchedEffect(category, reloadTick) {
        val result = withContext(Dispatchers.IO) { MediaQueries.list(context, category) }
        loadFailed = result == null
        val loaded = result.orEmpty()
        allItems = loaded
        if (selected.isNotEmpty()) {
            val ids = loaded.mapTo(HashSet()) { it.id }
            selected = selected.filterTo(HashSet()) { it in ids }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var first = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { if (first) first = false else reloadTick++ }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    BackHandler {
        when {
            selectionMode -> { selectionMode = false; selected = emptySet() }
            showSearch -> { showSearch = false; query = "" }
            else -> onBack()
        }
    }

    val dateOf: (MediaItem) -> Long = { if (category == MediaCategory.RECENT) it.added else it.modified }
    val shown = remember(allItems, sortBy, sortDesc, query) {
        val base = allItems.orEmpty().let { list -> if (query.isBlank()) list else list.filter { it.name.contains(query, ignoreCase = true) } }
        val cmp: Comparator<MediaItem> = when (sortBy) {
            "name" -> compareBy { it.name.lowercase() }
            "size" -> compareBy { it.size }
            else -> compareBy { dateOf(it) }
        }
        base.sortedWith(if (sortDesc) cmp.reversed() else cmp)
    }
    val totalBytes = remember(allItems) { allItems.orEmpty().sumOf { it.size } }

    fun toggle(item: MediaItem) {
        selected = if (item.id in selected) selected - item.id else selected + item.id
        if (selected.isEmpty()) selectionMode = false
    }

    fun openItem(item: MediaItem) {
        val f = item.file
        if (!f.exists()) {
            AppSnackbar.show(context, "${item.name} is no longer there")
            reloadTick++
            return
        }
        val uri = shareUri(context, f) ?: run { AppSnackbar.show(context, "Couldn't open ${item.name}"); return }
        openWith = CategoryOpenWith(item, uri, guessMime(item.name, item.mime))
    }

    fun share(list: List<MediaItem>) {
        val uris = ArrayList(list.mapNotNull { shareUri(context, it.file) })
        if (uris.isEmpty()) return
        val mimes = list.map { guessMime(it.name, it.mime) }.distinct()
        val type = when {
            mimes.size == 1 -> mimes.first()
            mimes.map { it.substringBefore('/') }.distinct().size == 1 -> mimes.first().substringBefore('/') + "/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        send.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, if (uris.size == 1) "Share ${list.first().name}" else "Share ${uris.size} files")) }
            .onFailure { AppSnackbar.show(context, "Couldn't share") }
    }

    fun endSelection() { selectionMode = false; selected = emptySet() }

    // Copy/move with FileUtils' byte-progress copy; names never collide (a clash gets " (1)").
    fun transfer(list: List<MediaItem>, dest: File, move: Boolean) {
        endSelection()
        opJob = scope.launch {
            var done = 0
            var failed = 0
            var skipped = 0
            val scan = ArrayList<String>()
            val moved = HashMap<Long, String>()
            list.forEachIndexed { i, item ->
                val src = item.file
                opLabel = "${if (move) "Moving" else "Copying"} ${i + 1}/${list.size} — ${item.name}"
                opDeterminate = true
                opProgress = 0f
                if (move && src.parentFile?.absolutePath == dest.absolutePath) { skipped++; return@forEachIndexed }
                val ok = withContext(Dispatchers.IO) {
                    if (!src.exists()) return@withContext null
                    val target = uniqueTarget(dest, src.name)
                    val cb = FileUtils.ProgressCallback { c, t -> opProgress = if (t > 0) (c.toFloat() / t).coerceIn(0f, 1f) else 1f }
                    val r = if (move) FileUtils.moveWithProgress(src, target, cb) else FileUtils.copyWithProgress(src, target, cb)
                    if (r) target else null
                }
                if (ok != null) {
                    done++
                    scan += ok.absolutePath
                    if (move) { scan += src.absolutePath; moved[item.id] = ok.absolutePath }
                } else failed++
            }
            if (scan.isNotEmpty()) MediaScannerConnection.scanFile(context.applicationContext, scan.toTypedArray(), null, null)
            if (moved.isNotEmpty()) {
                allItems = allItems?.map { m -> moved[m.id]?.let { p -> m.copy(path = p, name = File(p).name) } ?: m }
            }
            opJob = null
            val verb = if (move) "moved" else "copied"
            AppSnackbar.show(
                context,
                when {
                    failed > 0 -> "$done $verb, $failed failed"
                    skipped > 0 -> "$done $verb, $skipped already there"
                    else -> "$done item${if (done == 1) "" else "s"} $verb to ${dest.name.ifBlank { dest.path }}"
                },
            )
        }
    }

    fun delete(list: List<MediaItem>) {
        endSelection()
        opJob = scope.launch {
            opDeterminate = false
            var failed = 0
            val gone = HashSet<Long>()
            list.forEachIndexed { i, item ->
                opLabel = "Deleting ${i + 1}/${list.size} — ${item.name}"
                val ok = withContext(Dispatchers.IO) { val f = item.file; !f.exists() || FileUtils.delete(f) }
                if (ok) gone += item.id else failed++
            }
            MediaScannerConnection.scanFile(context.applicationContext, list.map { it.path }.toTypedArray(), null, null)
            allItems = allItems?.filterNot { it.id in gone }
            opJob = null
            if (failed > 0) AppSnackbar.show(context, "$failed couldn't be deleted")
        }
    }

    // ── Dialogs / overlays ──
    openWith?.let { req ->
        val item = req.item
        val actions = remember(req) {
            val f = item.file
            val lower = item.name.lowercase()
            buildList {
                // Photos, videos and music: BFE's viewer / player, paging through this category in the
                // order it's shown (sort + search).
                MediaTypes.typeOf(item.name, item.mime)?.let { type ->
                    add(mediaSheetAction(context, type) {
                        val list = shown
                        scope.launch {
                            val (items, index) = withContext(Dispatchers.IO) {
                                val (same, i) = MediaSiblings.pick(list, type, item.path, { it.name }, { it.path })
                                same.map { m -> MediaRef(uri = m.file.toURI().toString(), name = m.name, path = m.path, size = m.size, modified = m.modified) } to i
                            }
                            MediaLaunch.open(context, type, items, index)
                        }
                    })
                }
                add(SheetAction("Show in folder", Icons.Filled.FolderOpen) { f.parentFile?.let(onShowInFolder) })
                if (lower.endsWith(".apk")) {
                    add(SheetAction("Install", SheetIcons.Install) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(req.uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }.onFailure { AppSnackbar.show(context, "Couldn't start the installer") }
                    })
                }
                val isInno = com.the412banner.bfe.unpack.SevenZip.isInnoSetup(f)
                if (isInno || com.the412banner.bfe.unpack.SevenZip.looksLikeArchive(f)) {
                    add(SheetAction(if (isInno) "Unpack / Install…" else "Extract to…", SheetIcons.ExtractTo) {
                        context.startActivity(UnpackArchiveActivity.intent(context, f.absolutePath))
                    })
                }
                add(SheetAction("Share", Icons.Filled.Share) { share(listOf(item)) })
            }
        }
        OpenWithSheet(
            file = Loc.FileLoc(item.file),
            uri = req.uri,
            detectedMime = req.mime,
            actions = actions,
            allowWrite = true,
            onDismiss = { openWith = null },
        )
    }

    if (pendingDelete.isNotEmpty()) {
        val victims = pendingDelete
        OutlinedAlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text("Delete ${victims.size} file${if (victims.size == 1) "" else "s"}?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    Spacer(Modifier.height(6.dp))
                    victims.take(5).forEach { Text("• ${it.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    if (victims.size > 5) Text("…and ${victims.size - 5} more", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingDelete = emptyList(); delete(victims) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = emptyList() }) { Text("Cancel") } },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoryTopBar(
                title = if (selectionMode) "${selected.size} selected" else category.title,
                subtitle = if (selectionMode) null else allItems?.let { "${it.size} file${if (it.size == 1) "" else "s"} · ${StringUtils.formatBytes(totalBytes)}" },
                selectionMode = selectionMode,
                grid = grid,
                sortBy = sortBy,
                sortDesc = sortDesc,
                dateLabel = if (category == MediaCategory.RECENT) "Date added" else "Date modified",
                onBack = { if (selectionMode) endSelection() else onBack() },
                onSearch = { showSearch = !showSearch; if (!showSearch) query = "" },
                onToggleView = { grid = !grid; prefs.edit().putBoolean("${prefKey}_grid", grid).apply() },
                onSort = { key ->
                    if (sortBy == key) sortDesc = !sortDesc else { sortBy = key; sortDesc = key != "name" }
                    prefs.edit().putString("${prefKey}_sort", sortBy).putBoolean("${prefKey}_desc", sortDesc).apply()
                },
                onSelectAll = { selected = if (selected.size == shown.size) emptySet() else shown.map { it.id }.toSet() },
            )

            if (showSearch && !selectionMode) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search ${category.title.lowercase()}", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (selectionMode) {
                val sel = shown.filter { it.id in selected }
                val pad = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    OutlinedButton(enabled = sel.isNotEmpty(), onClick = { share(sel) }, contentPadding = pad) { Text("Share", fontSize = 12.sp) }
                    OutlinedButton(enabled = sel.isNotEmpty(), onClick = { pendingTransfer = PendingTransfer(sel, false) }, contentPadding = pad) { Text("Copy to…", fontSize = 12.sp) }
                    OutlinedButton(enabled = sel.isNotEmpty(), onClick = { pendingTransfer = PendingTransfer(sel, true) }, contentPadding = pad) { Text("Move to…", fontSize = 12.sp) }
                    OutlinedButton(
                        enabled = sel.isNotEmpty(),
                        onClick = { pendingDelete = sel },
                        contentPadding = pad,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    OutlinedButton(onClick = { endSelection() }, contentPadding = pad) { Text("Done", fontSize = 12.sp) }
                }
            }

            // Running copy / move / delete.
            if (opJob != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bfeBarBackground(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            opLabel + if (opDeterminate) "  ${(opProgress * 100).toInt()}%" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { opJob?.cancel(); opJob = null; reloadTick++ }) { Text("Cancel", fontSize = 12.sp) }
                    }
                    if (opDeterminate) {
                        LinearProgressIndicator(progress = { opProgress }, modifier = Modifier.fillMaxWidth().height(4.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val list = allItems
                when {
                    list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    loadFailed -> CategoryMessage(Icons.Filled.SearchOff, "Couldn't read the media library", "Android didn't let BFE list these files.")
                    list.isEmpty() -> CategoryMessage(
                        HomeTileStyles.of(category).icon,
                        "No ${category.title.lowercase()} yet",
                        if (category == MediaCategory.RECENT) "Nothing was added in the last ${MediaQueries.RECENT_DAYS} days." else null,
                    )
                    shown.isEmpty() -> CategoryMessage(Icons.Filled.SearchOff, "No matches", "Nothing here matches “$query”.")
                    grid -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (visualDefault) 96.dp else 104.dp),
                        contentPadding = PaddingValues(if (visualDefault) 2.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (visualDefault) 2.dp else 6.dp),
                        verticalArrangement = Arrangement.spacedBy(if (visualDefault) 2.dp else 6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.id }) { item ->
                            MediaGridCell(
                                item = item,
                                selectionMode = selectionMode,
                                selected = item.id in selected,
                                onTap = { if (selectionMode) toggle(item) else openItem(item) },
                                onLongPress = { if (!selectionMode) selectionMode = true; toggle(item) },
                            )
                        }
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(shown, key = { it.id }) { item ->
                            MediaListRow(
                                item = item,
                                date = dateOf(item),
                                selectionMode = selectionMode,
                                selected = item.id in selected,
                                menuExpanded = menuFor == item.id,
                                onTap = { if (selectionMode) toggle(item) else openItem(item) },
                                onLongPress = { if (!selectionMode) selectionMode = true; toggle(item) },
                                onMenu = { menuFor = item.id },
                                onDismissMenu = { menuFor = null },
                                onOpenWith = { openItem(item) },
                                onShowInFolder = { item.file.parentFile?.let(onShowInFolder) },
                                onShare = { share(listOf(item)) },
                                onCopy = { pendingTransfer = PendingTransfer(listOf(item), false) },
                                onMove = { pendingTransfer = PendingTransfer(listOf(item), true) },
                                onDelete = { pendingDelete = listOf(item) },
                            )
                        }
                    }
                }
            }
        }

        // Destination for Copy to… / Move to…: BFE's own folder picker (can choose Download/ and roots).
        pendingTransfer?.let { t ->
            FolderPickerOverlay(
                title = if (t.move) "Move ${t.items.size} to…" else "Copy ${t.items.size} to…",
                onPick = { dir -> pendingTransfer = null; transfer(t.items, dir, t.move) },
                onDismiss = { pendingTransfer = null },
            )
        }
    }
}

@Composable
private fun CategoryTopBar(
    title: String,
    subtitle: String?,
    selectionMode: Boolean,
    grid: Boolean,
    sortBy: String,
    sortDesc: Boolean,
    dateLabel: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onSort: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(if (selectionMode) Icons.Filled.Close else Icons.Filled.ArrowBack, if (selectionMode) "End selection" else "Back")
            }
        },
        actions = {
            if (selectionMode) {
                IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, "Select all") }
            } else {
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                IconButton(onClick = onToggleView) { Icon(if (grid) Icons.Filled.ViewList else Icons.Filled.GridView, if (grid) "List view" else "Grid view") }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, "Sort") }
                    BfeDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, modifier = Modifier.outlinedMenuCard()) {
                        listOf("name" to "Name", "date" to dateLabel, "size" to "Size").forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(if (sortBy == key) "$label  ${if (sortDesc) "↓" else "↑"}" else label) },
                                onClick = { sortMenu = false; onSort(key) },
                            )
                        }
                    }
                }
            }
        },
        colors = bfeTopBarColors(TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)),
    )
}

/**
 * The file's thumbnail in a [size] box: Coil for images (as in the file manager), MediaStore's
 * thumbnail for video / audio art, BFE's type icon otherwise or while loading.
 */
@Composable
private fun MediaThumb(item: MediaItem, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconRes = fileIconRes(item.name, isDir = false)
    // The artwork has its own keyline padding: full size in list/grid boxes, capped in gallery cells.
    val iconSize = size.coerceAtMost(64.dp)
    when (item.kind) {
        MediaKind.IMAGE -> AsyncImage(
            model = item.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(iconRes),
            error = painterResource(iconRes),
            modifier = modifier,
        )
        MediaKind.VIDEO, MediaKind.AUDIO -> {
            val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(96, 512)
            var bmp by remember(item.id) { mutableStateOf<ImageBitmap?>(MediaThumbs.cached(item)) }
            if (bmp == null) LaunchedEffect(item.id) { bmp = MediaThumbs.load(context, item, px) }
            val b = bmp
            if (b != null) Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
            else Box(modifier, contentAlignment = Alignment.Center) {
                BfeIcon(iconRes, size = iconSize)
            }
        }
        MediaKind.OTHER -> Box(modifier, contentAlignment = Alignment.Center) {
            BfeIcon(iconRes, size = iconSize)
        }
    }
}

@Composable
private fun MediaGridCell(
    item: MediaItem,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val visual = item.kind == MediaKind.IMAGE || item.kind == MediaKind.VIDEO
    if (visual) {
        // Gallery cell: a square thumbnail, a play badge on videos, a check when selected.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        ) {
            MediaThumb(item, 120.dp, Modifier.fillMaxSize())
            if (item.kind == MediaKind.VIDEO) {
                Icon(
                    Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                )
            }
            if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)))
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                )
            }
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onTap, onLongClick = onLongPress),
        shape = RoundedCornerShape(10.dp),
        colors = bfeCardColors(
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
            ),
            selected,
        ),
        border = bfeCardBorder(BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), selected),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Box {
                MediaThumb(item, 56.dp, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onTap() }, modifier = Modifier.align(Alignment.TopStart))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(item.name, fontSize = 11.sp, lineHeight = 14.sp, minLines = 2, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            Text(StringUtils.formatBytes(item.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaListRow(
    item: MediaItem,
    date: Long,
    selectionMode: Boolean,
    selected: Boolean,
    menuExpanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpenWith: () -> Unit,
    onShowInFolder: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val folder = remember(item.path) { File(item.path).parentFile?.name.orEmpty() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        shape = RoundedCornerShape(10.dp),
        colors = bfeCardColors(
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer,
            ),
            selected,
        ),
        border = bfeCardBorder(BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), selected),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onTap() })
                Spacer(Modifier.width(4.dp))
            }
            MediaThumb(item, 40.dp, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        StringUtils.formatBytes(item.size),
                        date.takeIf { it > 0 }?.let { DateText.dateTime(context, it) },
                        folder.takeIf { it.isNotEmpty() },
                    ).joinToString("  ·  "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selectionMode) Box {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, "Actions", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                BfeDropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu, modifier = Modifier.outlinedMenuCard()) {
                    @Composable
                    fun Item(label: String, icon: ImageVector, danger: Boolean = false, action: () -> Unit) {
                        val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        DropdownMenuItem(
                            text = { Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(icon, null, tint = tint) },
                            onClick = { onDismissMenu(); action() },
                        )
                    }
                    Item("Open with…", Icons.Filled.OpenInNew, action = onOpenWith)
                    Item("Show in folder", Icons.Filled.FolderOpen, action = onShowInFolder)
                    Item("Share", Icons.Filled.Share, action = onShare)
                    MenuItemDivider()
                    Item("Copy to…", Icons.Filled.FileCopy, action = onCopy)
                    Item("Move to…", Icons.Filled.ContentCut, action = onMove)
                    MenuItemDivider()
                    Item("Delete", Icons.Filled.Delete, danger = true, action = onDelete)
                }
            }
        }
    }
}

@Composable
private fun CategoryMessage(icon: ImageVector, title: String, detail: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
