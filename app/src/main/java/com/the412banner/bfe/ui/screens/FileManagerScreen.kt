package com.the412banner.bfe.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewColumn
import com.the412banner.bfe.ui.components.CollapsibleRail
import com.the412banner.bfe.ui.components.RailItem
import com.the412banner.bfe.ui.components.RailSection
import com.the412banner.bfe.ui.components.rememberRailState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.core.FileUtils
import com.the412banner.bfe.core.PeIconExtractor
import com.the412banner.bfe.core.StorageRoots
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.util.FavoritesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What to do when a pasted item already exists at the destination.
 *
 * OVERWRITE and MERGE resolve to the same call — `copyWithProgress` recurses into an existing
 * directory and truncates existing files — but they mean different things to the user, so both are
 * offered and the wording is chosen per item type (files overwrite, folders merge).
 */
enum class ConflictChoice { OVERWRITE, MERGE, KEEP_BOTH, SKIP }

/**
 * The three file-list view modes, cycled by the toolbar's view button and persisted as `fmViewMode`.
 * GRID = thumbnail tiles; LIST = the tall cards; COMPACT = dense single-line rows (~4× more per screen).
 */
enum class FmViewMode { GRID, LIST, COMPACT }

/**
 * Ordering for the file list. Folders always lead regardless of direction — a descending sort that
 * buries every folder under the files is never what someone means by "Z to A".
 */
/**
 * Shortens a path from the LEFT, keeping whole segments.
 *
 * Compose's TextOverflow can only ellipsise the tail, which for a path throws away the part that
 * matters — `/storage/emulated/0/Winlator/Game…` tells you nothing about where you are.
 */
private fun elidePathStart(path: String, max: Int): String {
    if (path.length <= max) return path
    val parts = path.split('/').filter { it.isNotEmpty() }
    val out = StringBuilder()
    for (part in parts.asReversed()) {
        if (out.length + part.length + 1 > max - 2) break
        out.insert(0, "/$part")
    }
    return if (out.isEmpty()) "…" + path.takeLast(max - 1) else "…$out"
}

private fun comparatorFor(sortBy: String, desc: Boolean): Comparator<File> {
    val inner: Comparator<File> = when (sortBy) {
        "date" -> compareBy { it.lastModified() }
        // Directory length() is meaningless, so folders sort by name within the size ordering
        // instead of pretending to have one.
        "size" -> compareBy { if (it.isDirectory) -1L else it.length() }
        // Group by file type (extension), then by name within each type.
        "type" -> compareBy<File> { it.extension.lowercase() }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    val directed = if (desc) inner.reversed() else inner
    return compareBy<File> { if (it.isDirectory) 0 else 1 }.then(directed)
}

private val FileTypeIcon: Map<String, ImageVector> = mapOf(
    "folder" to Icons.Filled.Folder,
)

// Image extensions that get a real thumbnail (via Coil) instead of the generic file icon.
private val IMAGE_THUMB_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

// Color-only sweep: the former card-fill / card-stroke / divider / icon-blue / icon-white
// constants were rerouted onto MaterialTheme.colorScheme tokens (surface / outline / primary /
// onSurface) at their use sites so a theme preset/accent recolors them.

// True when [child] is [ancestor] itself or lives anywhere inside it.
private fun isWithin(child: File, ancestor: File): Boolean {
    val c = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
    val a = runCatching { ancestor.canonicalPath }.getOrDefault(ancestor.absolutePath)
    return c == a || c.startsWith(a + File.separator)
}

// ── File attribute: Read-only ──
// Mirrors the Read-only toggle in the Properties dialog. No root — we only touch the write
// permission bits the app already owns.

// Snapshot of a file's toggleable attributes.
private data class FileAttrState(
    val readOnly: Boolean,
)

// Read the current read-only state. Never throws.
private fun readFileAttrs(file: File): FileAttrState {
    val path = file.absolutePath
    val readOnly = runCatching {
        (android.system.Os.stat(path).st_mode and android.system.OsConstants.S_IWUSR) == 0
    }.getOrDefault(!file.canWrite())
    return FileAttrState(readOnly)
}

// Toggle read-only by flipping the write bits, preserving every other permission bit. Setting
// read-only clears owner/group/other write; clearing it restores owner write. Falls back to
// File.setWritable if chmod is somehow refused. Returns true on success.
private fun setReadOnly(file: File, readOnly: Boolean): Boolean = runCatching {
    val path = file.absolutePath
    val mode = android.system.Os.stat(path).st_mode
    val writeBits = android.system.OsConstants.S_IWUSR or
        android.system.OsConstants.S_IWGRP or android.system.OsConstants.S_IWOTH
    val newMode = if (readOnly) mode and writeBits.inv()
        else mode or android.system.OsConstants.S_IWUSR
    android.system.Os.chmod(path, newMode)
    true
}.getOrElse { file.setWritable(!readOnly, true) }

// ── Favorites: origin resolution ──

enum class FavStorage { INTERNAL, SD, OTHER }

data class FavLocation(
    val storage: FavStorage,
    val driveLabel: String,       // "Internal", "SD card", or "Storage"
    val displayPath: String       // the unix absolute path
)

// Resolve where [file] lives (storage source + a friendly path) by prefix-matching its absolute path.
fun describeLocation(file: File): FavLocation {
    val abs = file.absolutePath

    val internal = "/storage/emulated/0"
    if (abs == internal || abs.startsWith("$internal/")) {
        return FavLocation(FavStorage.INTERNAL, "Internal", abs)
    }

    if (abs.startsWith("/storage/")) {
        val name = abs.removePrefix("/storage/").substringBefore('/')
        if (name.isNotEmpty() && name != "emulated" && name != "self") {
            return FavLocation(FavStorage.SD, "SD card", abs)
        }
    }

    return FavLocation(FavStorage.OTHER, "Storage", abs)
}

// Semantic identity colours for the favourite-card drive badge. Intentionally NOT theme
// accent colours — they identify the storage source at a glance. Returns (background, foreground).
private fun badgeColors(loc: FavLocation): Pair<Color, Color> {
    val white = Color(0xFFFFFFFF)
    return when (loc.storage) {
        FavStorage.INTERNAL -> Color(0xFF2E5FB0) to white   // blue
        FavStorage.SD -> Color(0xFF2E7D32) to white         // green
        FavStorage.OTHER -> Color(0xFF555555) to white      // grey
    }
}

/**
 * Cross-pane-visible state for one [BrowserPane]: its current directory (saved across process death)
 * plus a reload signal the parent bumps to refresh the pane after a cross-pane operation. Everything
 * else a pane needs stays as the pane composable's own `remember` state.
 */
class PaneState(initialPath: String) {
    var path by mutableStateOf(initialPath)
    var reloadTick by mutableStateOf(0)
    fun requestReload() { reloadTick++ }
}

private val PaneStateSaver: Saver<PaneState, String> = Saver(
    save = { it.path },
    restore = { PaneState(it) },
)

@Composable
fun rememberPaneState(key: String, initialPath: String): PaneState =
    rememberSaveable(key = key, saver = PaneStateSaver) { PaneState(initialPath) }

// Fire [onActivate] the instant a touch lands anywhere in this pane (Initial pass, before children
// consume it), so tapping a pane focuses it without stealing the tap from whatever was tapped.
private fun Modifier.activateOnTouch(active: Boolean, onActivate: () -> Unit): Modifier =
    this.pointerInput(active) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!active && event.type == PointerEventType.Press) onActivate()
            }
        }
    }

/**
 * The File Manager entry point. Hosts one or two [BrowserPane]s: a persisted split toggle switches
 * between the single full-pane view (default) and a two-pane commander view — side by side on wide
 * screens (≥600dp), stacked top/bottom on narrow ones. Exactly one pane is active (accent-bordered);
 * its controls + multi-select operate on it, and its "Copy →"/"Move →" and extraction target the
 * OTHER pane. Pick mode always uses a single pane.
 */
@Composable
fun FileManagerScreen(
    pickMode: Boolean = false,
    pickDirMode: Boolean = false,
    pickExtensions: List<String> = emptyList(),
    initialDir: File? = null,
    pickerTitle: String? = null,
    onPick: ((File) -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val startPath = remember {
        (initialDir?.takeIf { it.isDirectory } ?: File("/storage/emulated/0")).absolutePath
    }
    val leftPane = rememberPaneState("fm_pane_left", startPath)
    val rightPane = rememberPaneState("fm_pane_right", startPath)

    var dualPane by rememberSaveable { mutableStateOf(prefs.getBoolean("fmDualPane", false)) }
    var activeIndex by rememberSaveable { mutableStateOf(0) }
    // Dual-pane never applies while picking a file/folder — a picker stays single-pane.
    val effectiveDual = dualPane && !pickMode
    val toggleDual: () -> Unit = {
        dualPane = !dualPane
        prefs.edit().putBoolean("fmDualPane", dualPane).apply()
        activeIndex = 0
    }

    if (!effectiveDual) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Width-based (not orientation-based): a very narrow device also gets the adaptive header.
            val narrow = maxWidth < 360.dp
            BrowserPane(
                paneState = leftPane,
                dualPane = false,
                onToggleDualPane = toggleDual,
                otherPaneDir = { null },
                onRequestOtherReload = {},
                showRail = !pickMode,
                narrowHeader = narrow,
                pickMode = pickMode,
                pickDirMode = pickDirMode,
                pickExtensions = pickExtensions,
                initialDir = initialDir,
                pickerTitle = pickerTitle,
                onPick = onPick,
            )
        }
        return
    }

    // Two-pane commander view: ALWAYS side by side (a horizontal Row), in portrait and landscape.
    // Each pane is a fully independent BrowserPane instance.
    val accent = MaterialTheme.colorScheme.primary
    val idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    @Composable
    fun Pane(index: Int, state: PaneState, other: PaneState, narrow: Boolean, modifier: Modifier) {
        val isActive = activeIndex == index
        Box(
            modifier = modifier
                .activateOnTouch(isActive) { activeIndex = index }
                .border(2.dp, if (isActive) accent else idleBorder),
        ) {
            BrowserPane(
                paneState = state,
                dualPane = true,
                onToggleDualPane = toggleDual,
                otherPaneDir = { File(other.path).takeIf { it.isDirectory } },
                onRequestOtherReload = { other.requestReload() },
                showRail = false,
                narrowHeader = narrow,
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Each pane is ~half the width; below ~360dp per pane the full toolbar can't fit, so the
        // per-pane header collapses to icons + a horizontally-scrollable action strip (never clips).
        val narrow = (maxWidth / 2) < 360.dp
        Row(modifier = Modifier.fillMaxSize()) {
            Pane(0, leftPane, rightPane, narrow, Modifier.weight(1f).fillMaxHeight())
            Pane(1, rightPane, leftPane, narrow, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * One independent file-browser instance. Two of these coexist in dual-pane mode; the single-pane
 * File Manager is just one of them. Its cross-pane-visible state (the current directory + a reload
 * signal) lives in [paneState] so the parent can read this pane's dir and refresh it after a
 * cross-pane copy/move/extract; everything else (selection, sort, view mode, scroll, search, hidden)
 * stays as this composable's own `remember` state and is therefore independent per pane automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPane(
    paneState: PaneState,
    // Dual-pane wiring. In single-pane mode: dualPane=false, otherPaneDir returns null, the reload
    // callback is a no-op, and the rail is shown.
    dualPane: Boolean = false,
    onToggleDualPane: () -> Unit = {},
    otherPaneDir: () -> File? = { null },
    onRequestOtherReload: () -> Unit = {},
    showRail: Boolean = true,
    // When true (a narrow pane — e.g. a dual-pane column in portrait, ~half the phone width) the
    // per-pane header collapses: the drive chip and "New Folder" become icons and the action cluster
    // becomes a horizontally-scrollable strip, so nothing clips. Computed from ACTUAL pane width.
    narrowHeader: Boolean = false,
    // Pick mode (issue #73): reuse this File Manager as a themed file picker. When on, editing/run
    // features are gated off and tapping a matching file returns it via [onPick]. Defaults keep the
    // full-featured File Manager nav destination unchanged.
    pickMode: Boolean = false,
    // Directory-pick mode (issue #70): only folders are listed, files are hidden, and a
    // "Select this folder" action returns the current directory via [onPick]. Implies pickMode.
    pickDirMode: Boolean = false,
    pickExtensions: List<String> = emptyList(),
    initialDir: File? = null,
    pickerTitle: String? = null,
    onPick: ((File) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Only matching files are shown in pick mode (directories are always shown). Empty = all files.
    val lowerExts = remember(pickExtensions) { pickExtensions.map { it.lowercase() } }
    fun matchesPickExt(file: File): Boolean {
        if (lowerExts.isEmpty()) return true
        val name = file.name.lowercase()
        return lowerExts.any { name.endsWith(".$it") }
    }

    val pickPrefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val browsePrefs = pickPrefs
    val rootDir = remember {
        // The pane's start dir comes from its saved [paneState] path (which the parent seeds from
        // initialDir or the internal storage root), so a pane survives process death at its own path.
        val saved = File(paneState.path).takeIf { it.isDirectory }
        saved ?: initialDir?.takeIf { it.isDirectory } ?: File("/storage/emulated/0")
    }

    var currentDir by remember { mutableStateOf(rootDir) }
    // The up/back FLOOR — back + the up-arrow are disabled while currentDir == currentRoot. It MUST be
    // the VOLUME ROOT of the start dir (internal /storage/emulated/0, or an SD card /storage/XXXX-XXXX),
    // NOT the start dir itself: otherwise opening at any subfolder disables up/back and traps the user
    // there. (Mirrors the volume-root logic in favLocationOf above.)
    var currentRoot by remember {
        val abs = rootDir.absolutePath
        val internal = "/storage/emulated/0"
        val vol = when {
            abs == internal || abs.startsWith("$internal/") -> File(internal)
            abs.startsWith("/storage/") -> {
                val name = abs.removePrefix("/storage/").substringBefore('/')
                if (name.isNotEmpty() && name != "emulated" && name != "self") File("/storage/$name") else rootDir
            }
            else -> rootDir
        }
        mutableStateOf(vol)
    }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var selectedEntry by remember { mutableStateOf<File?>(null) }
    var showMenuFor by remember { mutableStateOf<File?>(null) }
    // Clipboard holds a LIST so one paste can carry a whole selection. Cut/copy semantics are a
    // flag on the batch rather than per item — mixing the two in one clipboard has no sane meaning.
    var clipboardFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }
    // Multi-select. Keyed by absolute path rather than File so a directory reload (which builds
    // fresh File objects) doesn't silently drop the selection.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Paste conflict resolution, surfaced from the IO coroutine and answered by the dialog.
    var pendingConflict by remember { mutableStateOf<File?>(null) }
    var conflictChoice by remember { mutableStateOf<ConflictChoice?>(null) }
    var conflictApplyToAll by remember { mutableStateOf(false) }
    // Set while a copy/move runs so the progress UI can offer a cancel.
    var operationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<List<File>>(emptyList()) }
    // Browse controls. Persisted so the list doesn't reset its order every time you open a folder.
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(browsePrefs.getString("fmSortBy", "name") ?: "name") }
    var sortDesc by remember { mutableStateOf(browsePrefs.getBoolean("fmSortDesc", false)) }
    var showHidden by remember { mutableStateOf(browsePrefs.getBoolean("fmShowHidden", true)) }
    var showSortMenu by remember { mutableStateOf(false) }
    // View mode: list of cards (default) or a thumbnail grid. Density applies to the list only —
    // a grid tile has no second line to compact.
    // The grid/list toggle is the SOURCE OF TRUTH in BOTH orientations (it drives the view and its
    // choice persists across rotation). Grid is the default — most useful in landscape, and in
    // portrait GridCells.Adaptive naturally renders fewer columns (~2). Do NOT force portrait to list:
    // that broke the toggle on-device (tapping it did nothing in portrait).
    // 3-value view mode, migrated from the old boolean `fmGridView` when `fmViewMode` isn't set yet.
    var viewMode by remember {
        mutableStateOf(
            when (browsePrefs.getString("fmViewMode", null)) {
                "list" -> FmViewMode.LIST
                "compact" -> FmViewMode.COMPACT
                "grid" -> FmViewMode.GRID
                else -> if (browsePrefs.getBoolean("fmGridView", true)) FmViewMode.GRID else FmViewMode.LIST
            }
        )
    }
    var compactRows by remember { mutableStateOf(browsePrefs.getBoolean("fmCompactRows", false)) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    // Properties sheet (basic info + Read-only toggle) target; null when closed.
    var propertiesTarget by remember { mutableStateOf<File?>(null) }
    var isOperationRunning by remember { mutableStateOf(false) }
    var operationLabel by remember { mutableStateOf("") }
    var operationDeterminate by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    // Favorites view: when on, a dedicated bookmarks list replaces the file list.
    // favTick is bumped on any add/remove/toggle so the favorites view + per-row star recompute.
    var showFavorites by remember { mutableStateOf(false) }
    var favTick by remember { mutableIntStateOf(0) }
    // Listing state: a brief spinner covers a slow directory read; a non-null [loadError] surfaces a
    // read failure (e.g. an unreadable path) as an in-list message rather than a silent empty list.
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // resetScroll: jump to the top of the list (true for navigation; false for in-place reloads
    // after delete/paste/rename/refresh so the user keeps their scroll position).
    fun loadDirectory(dir: File, resetScroll: Boolean = true) {
        currentDir = dir
        // Publish the current dir so the parent (dual-pane) can read it as the cross-pane target.
        paneState.path = dir.absolutePath
        // Remember the browsed directory so the next pick resumes here.
        if (pickMode) pickPrefs.edit().putString("lastFilePickerDir", dir.absolutePath).apply()
        loading = true
        loadError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    dir.listFiles()?.toList()
                        // Dir-pick mode: folders only. File-pick: folders + matching files. Else: all.
                        ?.filter { if (pickDirMode) it.isDirectory else !pickMode || it.isDirectory || matchesPickExt(it) }
                        // Dotfiles are noise in a storage root (.aya, .$recycle_bin$) but occasionally
                        // the thing you came for, so it's a toggle rather than a permanent filter.
                        ?.filter { showHidden || !it.name.startsWith(".") }
                        ?.sortedWith(comparatorFor(sortBy, sortDesc))
                }
            }
            val list = result.getOrNull()
            loadError = when {
                result.isFailure -> "Couldn't read this folder."
                list == null && !dir.canRead() -> "This folder isn't readable."
                else -> null
            }
            entries = list ?: emptyList()
            loading = false
            if (resetScroll) listState.scrollToItem(0)
        }
    }

    // Pull-to-refresh: re-list the current directory, keeping scroll position.
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            loadDirectory(currentDir, resetScroll = false)
            pullState.endRefresh()
        }
    }

    // Cross-pane refresh: the parent bumps [paneState.reloadTick] after a copy/move/extract into this
    // pane's directory, so the newly-arrived files appear without a manual pull-to-refresh.
    LaunchedEffect(paneState.reloadTick) {
        if (paneState.reloadTick > 0) loadDirectory(currentDir, resetScroll = false)
    }

    // Jump to a drive's root; pins the Back boundary so we don't climb above it.
    fun openDrive(dir: File) {
        currentRoot = dir
        loadDirectory(dir)
    }

    LaunchedEffect(Unit) { openDrive(rootDir) }

    // System/gesture Back: while the Favorites view is open it closes that first; otherwise
    // it goes up one directory. Only at the current drive's root with Favorites closed is it
    // disabled, letting Back propagate to close the File Manager.
    BackHandler(enabled = showFavorites || currentDir != currentRoot) {
        if (showFavorites) {
            showFavorites = false
            return@BackHandler
        }
        val parent = currentDir.parentFile
        if (parent != null && parent.exists()) loadDirectory(parent)
    }

    // A content:// Uri for [file] via our FileProvider, for VIEW / SEND / INSTALL hand-offs.
    fun contentUri(file: File) = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file,
    )

    // "Open with": hand the file to another app through a plain ACTION_VIEW chooser (best-effort).
    // Replaces the emulator's "run this .exe in a Wine container" action — BFE has no container.
    fun openWith(file: File) {
        runCatching {
            val uri = contentUri(file)
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(view, "Open with"))
        }.onFailure {
            Toast.makeText(context, "No app can open ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // Share a file to another app via ACTION_SEND.
    fun shareFile(file: File) {
        runCatching {
            val uri = contentUri(file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${file.name}"))
        }.onFailure {
            Toast.makeText(context, "Couldn't share ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // Share several files at once via ACTION_SEND_MULTIPLE (used by the selection action bar).
    fun shareFiles(files: List<File>) {
        val onlyFiles = files.filter { it.isFile }
        if (onlyFiles.isEmpty()) return
        if (onlyFiles.size == 1) { shareFile(onlyFiles.first()); return }
        runCatching {
            val uris = ArrayList(onlyFiles.map { contentUri(it) })
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${onlyFiles.size} files"))
        }.onFailure {
            Toast.makeText(context, "Couldn't share the selection", Toast.LENGTH_SHORT).show()
        }
    }

    // Install an .apk via the system package installer (needs REQUEST_INSTALL_PACKAGES).
    fun installApk(file: File) {
        runCatching {
            val uri = contentUri(file)
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(install)
        }.onFailure {
            Toast.makeText(context, "Couldn't start the installer for ${file.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // Shared by both the list rows and the grid tiles so the big Fast-Extract when-branch and the
    // Unpack-screen launch aren't duplicated per call site. Both close the context menu first.
    // In dual-pane mode, extraction defaults into the OTHER pane's directory (so "extract left →
    // into right" is one tap); single-pane keeps the engine's own sibling-folder default.
    fun extractDest(): File? = if (dualPane) otherPaneDir() else null

    fun launchFastExtract(file: File) {
        showMenuFor = null
        val dest = extractDest()
        scope.launch {
            when (val o = com.the412banner.bfe.unpack.FastExtract.start(context, file, dest)) {
                is com.the412banner.bfe.unpack.FastExtract.Outcome.Started -> {
                    Toast.makeText(context, "Unpacking ${o.name}…", Toast.LENGTH_SHORT).show()
                    // Show the extracted files in the target pane as soon as the job finishes.
                    if (dualPane) onRequestOtherReload()
                }
                com.the412banner.bfe.unpack.FastExtract.Outcome.Busy ->
                    Toast.makeText(context, "Another unpack is already in progress", Toast.LENGTH_SHORT).show()
                is com.the412banner.bfe.unpack.FastExtract.Outcome.NotArchive ->
                    Toast.makeText(context, "Not a recognized archive — nothing to unpack", Toast.LENGTH_SHORT).show()
                is com.the412banner.bfe.unpack.FastExtract.Outcome.OpenScreen -> {
                    o.toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    context.startActivity(
                        com.the412banner.bfe.UnpackArchiveActivity.intent(context, o.archivePath, dest?.absolutePath)
                    )
                }
            }
        }
    }

    fun launchUnpack(file: File) {
        showMenuFor = null
        context.startActivity(
            com.the412banner.bfe.UnpackArchiveActivity.intent(context, file.absolutePath, extractDest()?.absolutePath)
        )
    }

    // Resolve a non-colliding destination in [dir] for [name] (foo.txt -> "foo (1).txt").
    fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        do {
            candidate = File(dir, "$base ($i)$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    fun performDelete(file: File) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Deleting..."
            val ok = withContext(Dispatchers.IO) { FileUtils.delete(file) }
            isOperationRunning = false
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Waits for the user to answer the conflict dialog for [file]; null if they cancelled it. */
    suspend fun askConflict(file: File): ConflictChoice? {
        pendingConflict = file
        conflictChoice = null
        // Poll rather than plumb a CompletableDeferred through Compose state — the dialog answers
        // by setting conflictChoice, and this coroutine is already off the critical path.
        while (pendingConflict != null && conflictChoice == null) kotlinx.coroutines.delay(50)
        return conflictChoice
    }

    // Copy or move [sources] into [dstDir]. Used by within-pane paste (dst = currentDir) and by the
    // cross-pane "Copy →"/"Move →" actions (dst = the other pane's dir). [onDone] refreshes the OTHER
    // pane after a cross-pane op; the source pane is always refreshed (a move removes items from it).
    fun performCopyMove(sources: List<File>, dstDir: File, cut: Boolean, onDone: () -> Unit = {}) {
        if (sources.isEmpty()) return
        operationJob = scope.launch {
            operationProgress = 0f
            operationDeterminate = true
            operationLabel = if (cut) "Moving..." else "Copying..."
            isOperationRunning = true

            var applyToAll: ConflictChoice? = null
            var failed = 0
            var skipped = 0
            var done = 0

            for (src in sources) {
                // Pasting a folder into itself or its own subtree would recurse forever.
                if (src.isDirectory && isWithin(dstDir, src)) {
                    failed++
                    continue
                }
                // Moving into the folder it already sits in is a no-op.
                if (cut && src.parentFile?.absolutePath == dstDir.absolutePath) {
                    skipped++
                    continue
                }

                var dst = File(dstDir, src.name)
                if (dst.exists()) {
                    val choice = applyToAll ?: askConflict(src)?.also {
                        if (conflictApplyToAll) applyToAll = it
                    } ?: run { skipped++; null } ?: continue
                    when (choice) {
                        // Overwrite and Merge both paste onto the real destination: copyWithProgress
                        // recurses into an existing directory and truncates existing files, so the
                        // two differ only in what the user expects, not in what we call.
                        ConflictChoice.OVERWRITE, ConflictChoice.MERGE -> Unit
                        ConflictChoice.KEEP_BOTH -> dst = uniqueDestination(dstDir, src.name)
                        ConflictChoice.SKIP -> { skipped++; continue }
                    }
                }

                // Progress is per item; with a batch the label carries the overall position.
                operationLabel = buildString {
                    append(if (cut) "Moving" else "Copying")
                    if (sources.size > 1) append(" ${done + 1}/${sources.size}")
                    append(" — ").append(src.name)
                }
                var lastPct = -1
                val onProgress = FileUtils.ProgressCallback { copied, total ->
                    val pct = if (total > 0) ((copied * 100) / total).toInt() else 100
                    if (pct != lastPct) {
                        lastPct = pct
                        operationProgress = pct / 100f
                    }
                }
                val target = dst
                val ok = withContext(Dispatchers.IO) {
                    if (cut) FileUtils.moveWithProgress(src, target, onProgress)
                    else FileUtils.copyWithProgress(src, target, onProgress)
                }
                if (ok) done++ else failed++
            }

            isOperationRunning = false
            operationDeterminate = false
            operationJob = null
            selectionMode = false
            selectedPaths = emptySet()
            loadDirectory(currentDir, resetScroll = false)
            onDone()

            val message = when {
                failed > 0 -> "$done done, $failed failed"
                skipped > 0 -> "$done done, $skipped skipped"
                sources.size > 1 -> "$done items ${if (cut) "moved" else "copied"}"
                else -> null
            }
            if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Within-pane clipboard paste: copy/move the clipboard into the current directory.
    fun performPaste() {
        val sources = clipboardFiles
        val cut = isCutOperation
        clipboardFiles = emptyList()
        isCutOperation = false
        performCopyMove(sources, currentDir, cut)
    }

    // Cross-pane: copy or move the current selection into the OTHER pane's directory, then refresh it.
    fun crossPaneTransfer(cut: Boolean) {
        val target = otherPaneDir() ?: return
        val sources = entries.filter { it.absolutePath in selectedPaths }
        performCopyMove(sources, target, cut) { onRequestOtherReload() }
    }

    fun performRename(file: File, newName: String) {
        val target = File(file.parentFile, newName)
        if (target.exists()) {
            Toast.makeText(context, "\"$newName\" already exists", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isOperationRunning = true
            operationLabel = "Renaming..."
            val ok = withContext(Dispatchers.IO) { file.renameTo(target) }
            isOperationRunning = false
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun createFolder(parent: File, name: String) {
        val target = File(parent, name)
        if (target.exists()) {
            Toast.makeText(context, "\"$name\" already exists", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isOperationRunning = true
            operationLabel = "Creating folder..."
            val ok = withContext(Dispatchers.IO) { target.mkdirs() }
            isOperationRunning = false
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, "Could not create folder", Toast.LENGTH_SHORT).show()
        }
    }

    var showDriveMenu by remember { mutableStateOf(false) }
    // Re-enumerated whenever we come back to the screen so the volume set is re-read, not cached.
    var storageTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val drives = remember(storageTick) { StorageRoots.list(context) }

    // ── Dialogs ──

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        OutlinedAlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    if (folderName.isNotBlank()) createFolder(currentDir, folderName)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") } },
        )
    }

    if (renameTarget != null) {
        var newName by remember(renameTarget) { mutableStateOf(renameTarget?.name ?: "") }
        OutlinedAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = renameTarget
                    renameTarget = null
                    if (file != null && newName.isNotBlank()) performRename(file, newName)
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    propertiesTarget?.let { file ->
        FilePropertiesDialog(
            file = file,
            onDismiss = { propertiesTarget = null },
            // Attribute changes affect Hidden/read-only which the listing filters/sorts on, so refresh
            // in place (keeping scroll) after any toggle applies.
            onChanged = { loadDirectory(currentDir, resetScroll = false) },
        )
    }

    if (selectedEntry != null && selectedEntry != showMenuFor) {
        val file = selectedEntry ?: return
        OutlinedAlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("Delete?") },
            text = { Text("Delete \"${file.name}\" permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedEntry = null
                    performDelete(file)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { selectedEntry = null }) { Text("Cancel") } },
        )
    }

    if (pendingBulkDelete.isNotEmpty()) {
        val victims = pendingBulkDelete
        OutlinedAlertDialog(
            onDismissRequest = { pendingBulkDelete = emptyList() },
            title = { Text("Delete ${victims.size} item${if (victims.size == 1) "" else "s"}?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    Spacer(Modifier.height(6.dp))
                    // Name a few so an accidental Select-All is obvious before it's too late.
                    victims.take(5).forEach {
                        Text("• ${it.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (victims.size > 5) {
                        Text(
                            "…and ${victims.size - 5} more",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkDelete = emptyList()
                    selectionMode = false
                    selectedPaths = emptySet()
                    operationJob = scope.launch {
                        isOperationRunning = true
                        var failed = 0
                        victims.forEachIndexed { i, f ->
                            operationLabel = "Deleting ${i + 1}/${victims.size} — ${f.name}"
                            if (!withContext(Dispatchers.IO) { FileUtils.delete(f) }) failed++
                        }
                        isOperationRunning = false
                        operationJob = null
                        loadDirectory(currentDir, resetScroll = false)
                        if (failed > 0) {
                            Toast.makeText(context, "$failed couldn't be deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = emptyList() }) { Text("Cancel") } },
        )
    }

    // Paste conflict — one per colliding item, with "apply to all" for a long batch.
    pendingConflict?.let { conflict ->
        val isDir = conflict.isDirectory
        OutlinedAlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text("\"${conflict.name}\" already exists") },
            text = {
                Column {
                    Text(
                        if (isDir) "Merge adds and replaces files inside the existing folder."
                        else "Overwrite replaces the existing file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    if (clipboardFiles.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = conflictApplyToAll,
                                onCheckedChange = { conflictApplyToAll = it },
                            )
                            Text("Apply to all conflicts", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        (if (isDir) ConflictChoice.MERGE else ConflictChoice.OVERWRITE) to
                            (if (isDir) "Merge" else "Overwrite"),
                        ConflictChoice.KEEP_BOTH to "Keep both",
                        ConflictChoice.SKIP to "Skip",
                    ).forEach { (choice, label) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { conflictChoice = choice; pendingConflict = null },
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { conflictChoice = ConflictChoice.SKIP; pendingConflict = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Pick-mode title ──
        if (pickMode && !pickerTitle.isNullOrEmpty()) {
            Text(
                text = pickerTitle,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        // ── Dir-pick action bar: confirm the currently-browsed folder ──
        if (pickDirMode) {
            Button(
                onClick = { onPick?.invoke(currentDir) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select this folder")
            }
        }
        // ── Path bar (ADAPTIVE) ──
        // Wide pane: the full inline toolbar. Narrow pane (e.g. a dual-pane column in portrait, ~half
        // the phone width): the drive chip + "New Folder" collapse to icons and the action cluster
        // becomes a horizontally-scrollable strip, so nothing ever clips. Width-based, not orientation.
        val currentDriveLabel = describeLocation(currentDir).driveLabel
        val driveChipAlpha = if (showFavorites) 0.45f else 1f

        // Drive selector: a labelled "Internal ▾" chip on a wide pane, an icon-only button when narrow.
        @Composable
        fun DriveControl() {
            Box {
                if (narrowHeader) {
                    IconButton(onClick = { showDriveMenu = true }) {
                        Icon(
                            Icons.Filled.Storage,
                            "Storage: $currentDriveLabel",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = driveChipAlpha),
                        )
                    }
                } else {
                    val driveChipShape = RoundedCornerShape(8.dp)
                    Text(
                        text = "  $currentDriveLabel  ▾",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = driveChipAlpha),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(driveChipShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), driveChipShape)
                            .clickable { showDriveMenu = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                DropdownMenu(
                    expanded = showDriveMenu,
                    onDismissRequest = { showDriveMenu = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    drives.forEachIndexed { index, drive ->
                        if (index > 0) MenuItemDivider()
                        DropdownMenuItem(
                            text = { Text(drive.label) },
                            leadingIcon = {
                                Icon(
                                    if (drive.removable) Icons.Filled.SdStorage else Icons.Filled.Storage,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showDriveMenu = false
                                if (drive.readable) {
                                    openDrive(drive.dir)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "${drive.label} is mounted but not readable right now",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }
        }

        // Trailing actions. "New Folder" collapses to an icon when narrow; the view button cycles
        // Grid → List → Compact. Emitted inline (wide) or inside the scrollable strip (narrow).
        @Composable
        fun HeaderActions() {
            if (!showFavorites) {
                if (!pickMode) {
                    if (narrowHeader) {
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Filled.CreateNewFolder, "New Folder", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showNewFolderDialog = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("New Folder", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                        }
                    }
                }
                // View-mode cycle: Grid → List → Compact → Grid (persisted). Icon shows current mode.
                IconButton(onClick = {
                    viewMode = when (viewMode) {
                        FmViewMode.GRID -> FmViewMode.LIST
                        FmViewMode.LIST -> FmViewMode.COMPACT
                        FmViewMode.COMPACT -> FmViewMode.GRID
                    }
                    browsePrefs.edit().putString("fmViewMode", viewMode.name.lowercase()).apply()
                }) {
                    Icon(
                        when (viewMode) {
                            FmViewMode.GRID -> Icons.Filled.GridView
                            FmViewMode.LIST -> Icons.Filled.ViewList
                            FmViewMode.COMPACT -> Icons.Filled.DensitySmall
                        },
                        "View mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf("name" to "Name", "date" to "Date modified", "size" to "Size", "type" to "Type")
                            .forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (sortBy == key) "$label  ${if (sortDesc) "↓" else "↑"}" else label)
                                    },
                                    onClick = {
                                        // Tapping the active field flips direction; a different
                                        // field switches to it ascending.
                                        if (sortBy == key) sortDesc = !sortDesc else { sortBy = key; sortDesc = false }
                                        browsePrefs.edit().putString("fmSortBy", sortBy)
                                            .putBoolean("fmSortDesc", sortDesc).apply()
                                        showSortMenu = false
                                        loadDirectory(currentDir, resetScroll = false)
                                    },
                                )
                            }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DropdownMenuItem(
                            text = { Text(if (compactRows) "Comfortable rows" else "Compact rows") },
                            onClick = {
                                compactRows = !compactRows
                                browsePrefs.edit().putBoolean("fmCompactRows", compactRows).apply()
                                showSortMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                            onClick = {
                                showHidden = !showHidden
                                browsePrefs.edit().putBoolean("fmShowHidden", showHidden).apply()
                                showSortMenu = false
                                loadDirectory(currentDir, resetScroll = false)
                            },
                        )
                    }
                }
            }
            // Favorites toggle (always visible).
            IconButton(onClick = { showFavorites = !showFavorites }) {
                if (showFavorites) {
                    Icon(Icons.Filled.Star, "Hide favorites", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.StarBorder, "Show favorites", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Dual-pane (split) toggle — opens/collapses the two-pane commander view (persisted).
            if (!pickMode) {
                IconButton(onClick = onToggleDualPane) {
                    Icon(
                        Icons.Filled.ViewColumn,
                        if (dualPane) "Single pane" else "Dual pane",
                        tint = if (dualPane) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = {
                val parent = currentDir.parentFile
                // Don't climb above the current drive's root.
                if (currentDir != currentRoot && parent != null && parent.exists()) loadDirectory(parent)
            }, enabled = currentDir != currentRoot) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
            }

            DriveControl()
            Spacer(Modifier.width(4.dp))

            if (narrowHeader) {
                // No folder-name; the action strip takes the remaining width and scrolls so nothing
                // clips at ~half the phone width. (The full path shows on the line below.)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    HeaderActions()
                }
            } else {
                if (showFavorites) {
                    Text(
                        text = "★ Favorites",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    // PORTRAIT (wide, i.e. single pane): hide the folder name — the path line below
                    // shows the full path; the spacer keeps the action icons right-aligned.
                    Spacer(Modifier.weight(1f))
                } else {
                    // LANDSCAPE: the CURRENT FOLDER name (the full path is on the line below).
                    Text(
                        text = currentDir.name.ifBlank { currentDir.absolutePath },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                HeaderActions()
            }
        }

        // ── Search field ── filters the current folder only; it is not a recursive search.
        if (showSearch && !showFavorites) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Filter this folder", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Free space on the volume being browsed — worth knowing before starting a 60 GB copy.
        val freeSpace = remember(currentDir.absolutePath, entries) {
            runCatching { currentDir.usableSpace }.getOrDefault(0L)
        }
        if (!showFavorites) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(
                    // Elided from the LEFT: the deepest part of a path is the informative part, so
                    // when it doesn't fit we drop the /storage/emulated/0 prefix, not the tail.
                    text = elidePathStart(currentDir.absolutePath, 52),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    // fill = true: the path takes all remaining width, so the free-space figure
                    // is pinned to the right edge instead of sliding around with the path length.
                    modifier = Modifier.weight(1f),
                )
                if (freeSpace > 0) {
                    Text(
                        "${StringUtils.formatBytes(freeSpace)} free",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── Selection bar ── replaces the paste banner while picking items.
        if (selectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "${selectedPaths.size} selected",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                // Compact outlined buttons; the strip scrolls horizontally so every action stays
                // reachable even in narrow portrait (six buttons + the count won't all fit at once).
                val selBarPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedPaths = if (selectedPaths.size == entries.size) emptySet()
                            else entries.map { it.absolutePath }.toSet()
                        },
                        contentPadding = selBarPadding,
                    ) { Text(if (selectedPaths.size == entries.size) "None" else "All", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedPaths.isNotEmpty(),
                        onClick = {
                            clipboardFiles = entries.filter { it.absolutePath in selectedPaths }
                            isCutOperation = false
                            selectionMode = false
                            selectedPaths = emptySet()
                        },
                        contentPadding = selBarPadding,
                    ) { Text("Copy", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedPaths.isNotEmpty(),
                        onClick = {
                            clipboardFiles = entries.filter { it.absolutePath in selectedPaths }
                            isCutOperation = true
                            selectionMode = false
                            selectedPaths = emptySet()
                        },
                        contentPadding = selBarPadding,
                    ) { Text("Cut", fontSize = 12.sp) }
                    // Cross-pane: copy / move the selection straight into the OTHER pane's directory.
                    if (dualPane && otherPaneDir() != null) {
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            enabled = selectedPaths.isNotEmpty(),
                            onClick = { crossPaneTransfer(cut = false) },
                            contentPadding = selBarPadding,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        ) { Text("Copy →", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            enabled = selectedPaths.isNotEmpty(),
                            onClick = { crossPaneTransfer(cut = true) },
                            contentPadding = selBarPadding,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        ) { Text("Move →", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedPaths.any { p -> entries.any { it.absolutePath == p && it.isFile } },
                        onClick = {
                            shareFiles(entries.filter { it.absolutePath in selectedPaths })
                        },
                        contentPadding = selBarPadding,
                    ) { Text("Share", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedPaths.isNotEmpty(),
                        onClick = { pendingBulkDelete = entries.filter { it.absolutePath in selectedPaths } },
                        contentPadding = selBarPadding,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { selectionMode = false; selectedPaths = emptySet() },
                        contentPadding = selBarPadding,
                    ) { Text("Done", fontSize = 12.sp) }
                }
            }
        }

        // ── Paste banner ──
        if (clipboardFiles.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    .clickable { performPaste() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.ContentPaste, "Paste", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val what = if (clipboardFiles.size == 1) clipboardFiles.first().name
                else "${clipboardFiles.size} items"
                Text(
                    "Paste $what${if (isCutOperation) " (move)" else ""} here",
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { clipboardFiles = emptyList(); isCutOperation = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        // ── Progress overlay ──
        if (isOperationRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val pctText = if (operationDeterminate) "  ${(operationProgress * 100).toInt()}%" else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$operationLabel$pctText",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // A multi-gigabyte copy onto a slow card is exactly when you discover you
                    // picked the wrong folder; without this the only way out was killing the app.
                    if (operationJob != null) {
                        TextButton(onClick = {
                            operationJob?.cancel()
                            operationJob = null
                            isOperationRunning = false
                            operationDeterminate = false
                            loadDirectory(currentDir, resetScroll = false)
                        }) { Text("Cancel", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (operationDeterminate) {
                    LinearProgressIndicator(
                        progress = { operationProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // ── Left locations rail (mockup Option 2) + content ──
        // Shared collapsible rail: landscape expanded by default, portrait collapsed icon-only. Not
        // shown in pick mode (the themed picker keeps its slim layout). Built each recompose (cheap)
        // so it tracks the current drive/favourites without stale click lambdas.
        val fmRailState = rememberRailState("filemanager")
        fun locItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, dir: File) =
            RailItem(label, icon, !showFavorites && currentRoot.absolutePath == dir.absolutePath) {
                showFavorites = false; openDrive(dir)
            }
        val storageItems = buildList {
            add(locItem("Internal", Icons.Filled.Smartphone, File("/storage/emulated/0")))
            drives.filter { it.removable }.forEach { d ->
                add(RailItem(d.label, Icons.Filled.SdStorage, !showFavorites && currentRoot.absolutePath == d.dir.absolutePath) {
                    showFavorites = false; if (d.readable) openDrive(d.dir)
                })
            }
        }
        val quickItems = buildList {
            File("/storage/emulated/0/Download").takeIf { it.isDirectory }?.let { add(locItem("Downloads", Icons.Filled.Download, it)) }
            File("/storage/emulated/0/Winlator/Games").takeIf { it.isDirectory }?.let { add(locItem("Games", Icons.Filled.SportsEsports, it)) }
            File("/storage/emulated/0/Pictures").takeIf { it.isDirectory }?.let { add(locItem("Pictures", Icons.Filled.Image, it)) }
        }
        val favItems = remember(favTick) { FavoritesStore.list(context).map(::File).filter { it.exists() } }
            .map { d -> RailItem(d.name, Icons.Filled.Star, false) { showFavorites = false; openDrive(d) } }
        val locationSections = buildList {
            add(RailSection("STORAGE", storageItems))
            if (quickItems.isNotEmpty()) add(RailSection("QUICK", quickItems))
            if (favItems.isNotEmpty()) add(RailSection("★ FAVORITES", favItems))
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The nav rail is shown only in the single full-pane File Manager. The slim picker and
            // each dual-pane column hide it (they navigate via the drive chip in the path bar).
            if (showRail) {
                CollapsibleRail(state = fmRailState, title = "Files", sections = locationSections, outlinedItems = true)
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
        // ── Favorites list OR file list ──
        if (showFavorites) {
            FavoritesList(
                currentDir = currentDir,
                favTick = favTick,
                onPinCurrent = {
                    FavoritesStore.add(context, currentDir.absolutePath)
                    favTick++
                    Toast.makeText(context, "Added \"${currentDir.name}\" to Favorites", Toast.LENGTH_SHORT).show()
                },
                onJump = { dir ->
                    showFavorites = false
                    openDrive(dir)
                },
                onUnpin = { dir ->
                    FavoritesStore.remove(context, dir.absolutePath)
                    favTick++
                    Toast.makeText(context, "Removed \"${dir.name}\" from Favorites", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
        // ── File list (pull down to refresh) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection),
        ) {
            val shownEntries = if (searchQuery.isBlank()) entries
            else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }

            if (loading && entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (loadError != null && entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        loadError ?: "Couldn't read this folder.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            } else if (viewMode == FmViewMode.GRID && entries.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(shownEntries, key = { it.absolutePath }) { file ->
                        val isFav = remember(file.absolutePath, favTick) {
                            FavoritesStore.isFavorite(context, file.absolutePath)
                        }
                        FileGridTile(
                            file = file,
                            selectionMode = selectionMode,
                            selected = file.absolutePath in selectedPaths,
                            onLongPress = {
                                if (!pickMode) {
                                    // In selection mode a long-press toggles; otherwise it opens the
                                    // same context menu the list rows show (the tile has no ⋮ button).
                                    if (selectionMode) {
                                        selectedPaths = if (file.absolutePath in selectedPaths)
                                            selectedPaths - file.absolutePath
                                        else selectedPaths + file.absolutePath
                                    } else {
                                        showMenuFor = file
                                    }
                                }
                            },
                            onToggleSelect = {
                                selectedPaths = if (file.absolutePath in selectedPaths)
                                    selectedPaths - file.absolutePath
                                else selectedPaths + file.absolutePath
                            },
                            onTap = {
                                if (file.isDirectory) loadDirectory(file)
                                else if (pickMode) {
                                    if (matchesPickExt(file)) {
                                        pickPrefs.edit().putString("lastFilePickerDir", currentDir.absolutePath).apply()
                                        onPick?.invoke(file)
                                    }
                                } else openWith(file)
                            },
                            onMenu = { showMenuFor = file },
                            menuExpanded = showMenuFor == file,
                            onDismissMenu = { showMenuFor = null },
                            isFavorite = isFav,
                            onSelect = {
                                selectionMode = true
                                selectedPaths = selectedPaths + file.absolutePath
                                showMenuFor = null
                            },
                            onOpenWith = { openWith(file) },
                            onInstallApk = { installApk(file) },
                            onShare = { shareFile(file) },
                            onUnpack = { launchUnpack(file) },
                            onFastExtract = { launchFastExtract(file) },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onCopy = { clipboardFiles = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboardFiles = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onToggleFavorite = {
                                val nowFav = FavoritesStore.toggle(context, file.absolutePath)
                                favTick++
                                showMenuFor = null
                                Toast.makeText(
                                    context,
                                    if (nowFav) "Added \"${file.name}\" to Favorites"
                                    else "Removed \"${file.name}\" from Favorites",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onProperties = { propertiesTarget = file; showMenuFor = null },
                        )
                    }
                }
            } else
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Empty directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val shown = shownEntries
                    items(shown, key = { it.absolutePath }) { file ->
                        val isFav = remember(file.absolutePath, favTick) {
                            FavoritesStore.isFavorite(context, file.absolutePath)
                        }
                        FileItemRow(
                            file = file,
                            showActions = !pickMode,
                            compact = compactRows,
                            // COMPACT view mode → dense single-line rows (~4× more per screen).
                            dense = viewMode == FmViewMode.COMPACT,
                            selectionMode = selectionMode,
                            selected = file.absolutePath in selectedPaths,
                            onLongPress = {
                                // In selection mode a long-press toggles; otherwise it opens the
                                // per-item context menu (matching the grid tiles).
                                if (!pickMode) {
                                    if (selectionMode) {
                                        selectedPaths = if (file.absolutePath in selectedPaths)
                                            selectedPaths - file.absolutePath
                                        else selectedPaths + file.absolutePath
                                    } else {
                                        showMenuFor = file
                                    }
                                }
                            },
                            onToggleSelect = {
                                selectedPaths = if (file.absolutePath in selectedPaths)
                                    selectedPaths - file.absolutePath
                                else selectedPaths + file.absolutePath
                            },
                            onTap = {
                                if (file.isDirectory) loadDirectory(file)
                                else if (pickMode) {
                                    if (matchesPickExt(file)) {
                                        pickPrefs.edit().putString("lastFilePickerDir", currentDir.absolutePath).apply()
                                        onPick?.invoke(file)
                                    }
                                }
                                else openWith(file)
                            },
                            onMenu = { showMenuFor = file },
                            menuExpanded = showMenuFor == file,
                            onDismissMenu = { showMenuFor = null },
                            onSelect = {
                                selectionMode = true
                                selectedPaths = selectedPaths + file.absolutePath
                                showMenuFor = null
                            },
                            onOpenWith = { openWith(file) },
                            onInstallApk = { installApk(file) },
                            onShare = { shareFile(file) },
                            onCopy = { clipboardFiles = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboardFiles = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onFastExtract = { launchFastExtract(file) },
                            onUnpack = { launchUnpack(file) },
                            isFavorite = isFav,
                            onToggleFavorite = {
                                val nowFav = FavoritesStore.toggle(context, file.absolutePath)
                                favTick++
                                showMenuFor = null
                                Toast.makeText(
                                    context,
                                    if (nowFav) "Added \"${file.name}\" to Favorites"
                                    else "Removed \"${file.name}\" from Favorites",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onProperties = { propertiesTarget = file; showMenuFor = null },
                        )
                    }
                }
            }
            // material3 1.2.0's PullToRefreshContainer draws its indicator even at rest;
            // only show it while the user is actively pulling or a refresh is running.
            if (pullState.verticalOffset > 0.5f || pullState.isRefreshing) {
                PullToRefreshContainer(
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        }
            } // end content Box (beside the rail)
        } // end rail + content Row
        // (New Folder moved into the top toolbar; the bottom bar was removed to reclaim its strip.)
    }
}

// The context-menu item list shared by the list rows (FileItemRow) and the grid tiles
// (FileGridTile), so both open the identical menu. Gating (isDir/canRun/isInno/looksLikeArchive) is
// recomputed from [file] here — one source of truth for what each item shows. Every item dismisses
// the menu first, then runs its action.
@Composable
private fun FileContextMenuItems(
    file: File,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onOpenWith: () -> Unit,
    onInstallApk: () -> Unit,
    onShare: () -> Unit,
    onUnpack: () -> Unit,
    onFastExtract: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProperties: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    val isDir = file.isDirectory
    // Enter multi-select on this item (replaces the old long-press-only entry point).
    DropdownMenuItem(
        text = { Text("Select") },
        leadingIcon = { Icon(Icons.Filled.Checklist, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onSelect() },
    )
    MenuItemDivider()
    // Properties: basic info + Read-only / Hidden toggles, for ANY file or folder (handy for config
    // files like .txt/.cfg/.ini). Kept near the top since it's a common reason to open this menu.
    DropdownMenuItem(
        text = { Text("Properties") },
        leadingIcon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onProperties() },
    )
    MenuItemDivider()
    // Favorites are directories — only folders get the pin toggle.
    if (isDir) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = { onToggleFavorite() },
        )
        MenuItemDivider()
    }
    // Files (not folders) can be handed to another app. An .apk gets a dedicated "Install" action.
    if (!isDir) {
        if (file.name.lowercase().endsWith(".apk")) {
            DropdownMenuItem(
                text = { Text("Install APK") },
                leadingIcon = { Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onDismissMenu(); onInstallApk() },
            )
            MenuItemDivider()
        }
        DropdownMenuItem(
            text = { Text("Open with…") },
            leadingIcon = { Icon(Icons.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onOpenWith() },
        )
        MenuItemDivider()
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Filled.Share, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onShare() },
        )
        MenuItemDivider()
    }
    // The SINGLE extraction action: the bundled 7-Zip engine handles a strict superset
    // of everything the old in-app extractor did (zip, 7z, tar, gzip, bzip2, xz, zstd)
    // PLUS disc images (ISO/UDF), RAR, cab, wim, split volumes and 80 GB+ single files.
    // For an InnoSetup repack (Setup.exe + Setup-*.bin) it becomes "Unpack / Install…",
    // where the screen decides between 7-Zip payload extraction and running Setup.exe in
    // a container (FreeArc repacks). The screen also content-sniffs (`7zz l`) so a file
    // is judged by content, not extension.
    val isInno = com.the412banner.bfe.unpack.SevenZip.isInnoSetup(file)
    // Content-aware: extension OR a cheap magic-byte sniff, so a .wcp/.bin/renamed
    // archive with an unlisted extension still gets the option (menu opens per row,
    // so this reads only a few header bytes on demand — never `7zz l` per entry).
    if (isInno || com.the412banner.bfe.unpack.SevenZip.looksLikeArchive(file)) {
        DropdownMenuItem(
            text = { Text(if (isInno) "Unpack / Install…" else "Unpack Archive…") },
            leadingIcon = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onUnpack() },
        )
        MenuItemDivider()
        // Convenience: one tap, no screen — pre-fill defaults (new sibling folder, Auto
        // power) and start straight into the progress pill. Same engines/throughput.
        DropdownMenuItem(
            text = { Text("Fast Extract") },
            leadingIcon = { Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onFastExtract() },
        )
        MenuItemDivider()
    }
    DropdownMenuItem(
        text = { Text("Rename") },
        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onRename() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Copy") },
        leadingIcon = { Icon(Icons.Filled.FileCopy, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onCopy() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Cut") },
        leadingIcon = { Icon(Icons.Filled.ContentCut, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onCut() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Delete") },
        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onDelete() },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FileItemRow(
    file: File,
    showActions: Boolean = true,
    compact: Boolean = false,
    // COMPACT view mode: render a dense single-line row (no card, ~22dp icon, thin divider) instead of
    // the tall card — ~4× more items per screen. Selection/long-press/multi-select still work.
    dense: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onTap: () -> Unit,
    onMenu: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onSelect: () -> Unit = {},
    onOpenWith: () -> Unit,
    onInstallApk: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onUnpack: () -> Unit = {},
    onFastExtract: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onProperties: () -> Unit = {},
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val isDir = file.isDirectory
    val isExe = !isDir && file.name.lowercase().let { it.endsWith(".exe") || it.endsWith(".bat") || it.endsWith(".msi") || it.endsWith(".sh") }
    // Image files show a real thumbnail instead of the generic file icon (handy when picking a
    // wallpaper/icon). Coil sizes the decode to the 36dp slot and caches it, so scrolling stays smooth.
    val isImage = !isDir && file.extension.lowercase() in IMAGE_THUMB_EXTS

    // For real PE executables, try to pull out the embedded application icon (async, off the main thread).
    var exeIcon by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    if (!isDir && file.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.absolutePath) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(file) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }

    // ── Compact (dense) row ── minimal single-line row, ~28dp tall, with a thin divider.
    if (dense) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else onTap() },
                        onLongClick = onLongPress,
                    )
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                    )
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            ) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                when {
                    exeIcon != null -> Image(bitmap = exeIcon!!, contentDescription = null, modifier = Modifier.size(22.dp))
                    isExe -> Icon(Icons.Filled.Terminal, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                    isDir -> Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    isImage -> AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                        error = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    else -> Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isDir) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = StringUtils.formatBytes(file.length()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                if (showActions) {
                    Box {
                        Icon(
                            Icons.Filled.MoreVert,
                            "Actions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(18.dp)
                                .clickable { onMenu() },
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = onDismissMenu,
                            modifier = Modifier.outlinedMenuCard(),
                        ) {
                            FileContextMenuItems(
                                file = file,
                                isFavorite = isFavorite,
                                onSelect = onSelect,
                                onOpenWith = onOpenWith,
                                onInstallApk = onInstallApk,
                                onShare = onShare,
                                onUnpack = onUnpack,
                                onFastExtract = onFastExtract,
                                onRename = onRename,
                                onCopy = onCopy,
                                onCut = onCut,
                                onDelete = onDelete,
                                onToggleFavorite = onToggleFavorite,
                                onProperties = onProperties,
                                onDismissMenu = onDismissMenu,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(
                // In selection mode a tap toggles instead of opening, so you can rattle through a
                // folder without long-pressing every single row.
                onClick = { if (selectionMode) onToggleSelect() else onTap() },
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 8.dp),
        ) {
            if (selectionMode) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.width(4.dp))
            }
            when {
                // Show the executable's own embedded icon when we managed to extract one.
                exeIcon != null -> Image(
                    bitmap = exeIcon!!,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
                isExe -> Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
                isDir -> Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
                // Real image preview. Falls back to the generic file icon while loading or on decode failure.
                isImage -> AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                    error = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                else -> Icon(
                    imageVector = Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (!isDir) append(StringUtils.formatBytes(file.length())).append("  \u2022  ")
                        append(dateFormat.format(Date(file.lastModified())))
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            if (showActions) Box {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, "Actions", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu,
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    FileContextMenuItems(
                        file = file,
                        isFavorite = isFavorite,
                        onSelect = onSelect,
                        onOpenWith = onOpenWith,
                        onInstallApk = onInstallApk,
                        onShare = onShare,
                        onUnpack = onUnpack,
                        onFastExtract = onFastExtract,
                        onRename = onRename,
                        onCopy = onCopy,
                        onCut = onCut,
                        onDelete = onDelete,
                        onToggleFavorite = onToggleFavorite,
                        onProperties = onProperties,
                        onDismissMenu = onDismissMenu,
                    )
                }
            }
        }
    }
}

// Dedicated Favorites list that replaces the file list while the star toggle is on.
// Reads the store keyed on [favTick] so it recomposes after any pin/unpin.
@Composable
private fun FavoritesList(
    currentDir: File,
    favTick: Int,
    onPinCurrent: () -> Unit,
    onJump: (File) -> Unit,
    onUnpin: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val favorites = remember(favTick) {
        FavoritesStore.list(context).map(::File).filter { it.exists() }
    }
    val currentAlreadyPinned = remember(favTick, currentDir.absolutePath) {
        FavoritesStore.isFavorite(context, currentDir.absolutePath)
    }

    LazyColumn(modifier = modifier) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Favorites",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onPinCurrent,
                    enabled = !currentAlreadyPinned,
                ) {
                    Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Pin current folder",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No favorites yet — pin a folder with its ⋮ menu to jump back here fast.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            items(favorites, key = { it.absolutePath }) { file ->
                val loc = remember(file.absolutePath) {
                    describeLocation(file)
                }
                FavoriteCard(
                    file = file,
                    loc = loc,
                    onJump = { onJump(file) },
                    onUnpin = { onUnpin(file) },
                )
            }
        }
    }
}

// A single favourite — matches the FileItemRow card style (surfaceContainer + outline +
// RoundedCornerShape(10.dp)). Shows the folder name, a coloured drive badge + origin text,
// and the full display path; tapping jumps into it, the filled star unpins.
@Composable
private fun FavoriteCard(
    file: File,
    loc: FavLocation,
    onJump: () -> Unit,
    onUnpin: () -> Unit,
) {
    val (badgeBg, badgeFg) = badgeColors(loc)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onJump),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // Origin line: coloured drive badge + source description.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = loc.driveLabel,
                        color = badgeFg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (loc.storage) {
                            FavStorage.INTERNAL -> "Internal storage"
                            FavStorage.SD -> "SD card"
                            FavStorage.OTHER -> "Storage"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = loc.displayPath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Remove from favorites",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * One entry in the File Manager's grid view: a big thumbnail with the name under it.
 *
 * Deliberately drops size and date — at this width they truncate to noise. The grid is for
 * recognising things by sight (screenshots, covers, game folders); the list stays the view for
 * reading details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridTile(
    file: File,
    selectionMode: Boolean,
    selected: Boolean,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onTap: () -> Unit,
    onMenu: () -> Unit,
    menuExpanded: Boolean = false,
    onDismissMenu: () -> Unit = {},
    isFavorite: Boolean = false,
    onSelect: () -> Unit = {},
    onOpenWith: () -> Unit = {},
    onInstallApk: () -> Unit = {},
    onShare: () -> Unit = {},
    onUnpack: () -> Unit = {},
    onFastExtract: () -> Unit = {},
    onRename: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onProperties: () -> Unit = {},
) {
    val isDir = file.isDirectory
    val isImage = !isDir && file.extension.lowercase() in IMAGE_THUMB_EXTS
    var exeIcon by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    if (!isDir && file.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.absolutePath) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(file) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // The tile has no ⋮ button — long-press opens this menu, anchored to the Box around the Card.
    Box {
        Card(
            modifier = Modifier
                .padding(4.dp)
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onTap() },
                    onLongClick = onLongPress,
                ),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainer,
            ),
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    when {
                        exeIcon != null -> Image(bitmap = exeIcon!!, contentDescription = null, modifier = Modifier.size(48.dp))
                        isDir -> Icon(
                            Icons.Filled.Folder, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        isImage -> AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                            error = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        else -> Icon(
                            Icons.Filled.InsertDriveFile, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    if (selectionMode) {
                        androidx.compose.material3.Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            modifier = Modifier.outlinedMenuCard(),
        ) {
            FileContextMenuItems(
                file = file,
                isFavorite = isFavorite,
                onSelect = onSelect,
                onOpenWith = onOpenWith,
                onInstallApk = onInstallApk,
                onShare = onShare,
                onUnpack = onUnpack,
                onFastExtract = onFastExtract,
                onRename = onRename,
                onCopy = onCopy,
                onCut = onCut,
                onDelete = onDelete,
                onToggleFavorite = onToggleFavorite,
                onProperties = onProperties,
                onDismissMenu = onDismissMenu,
            )
        }
    }
}

/**
 * Properties sheet for a single file or folder: basic info plus a Read-only toggle. The toggle
 * applies immediately (off the main thread) and refreshes the listing via [onChanged]. State is
 * keyed on the file so it always reflects the entry it was opened for.
 */
@Composable
private fun FilePropertiesDialog(
    file: File,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var attrs by remember(file.absolutePath) { mutableStateOf<FileAttrState?>(null) }
    // Guards against a second toggle landing while the first is still being applied off-thread.
    var busy by remember(file.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(file.absolutePath) {
        attrs = withContext(Dispatchers.IO) { readFileAttrs(file) }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ── Basic info ──
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                PropertyLine("Location", file.parent ?: "—")
                PropertyLine(
                    "Type",
                    if (file.isDirectory) "Folder"
                    else file.extension.uppercase().let { if (it.isBlank()) "File" else "$it file" },
                )
                if (!file.isDirectory) PropertyLine("Size", StringUtils.formatBytes(file.length()))
                PropertyLine("Modified", dateFormat.format(Date(file.lastModified())))

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))

                val state = attrs
                // ── Read-only ── checked when the owner can't write.
                AttributeToggleRow(
                    label = "Read-only",
                    description = "Stops apps from overwriting or deleting this file.",
                    checked = state?.readOnly == true,
                    enabled = state != null && !busy,
                    onToggle = { want ->
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { setReadOnly(file, want) }
                            busy = false
                            if (ok) {
                                attrs = attrs?.copy(readOnly = want)
                                onChanged()
                            } else {
                                Toast.makeText(context, "Couldn't change Read-only", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// One "label: value" line in the Properties info block.
@Composable
private fun PropertyLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// A labelled attribute switch with a one-line description, used by the Properties sheet.
@Composable
private fun AttributeToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onToggle,
        )
    }
}
