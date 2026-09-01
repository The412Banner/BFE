package com.the412banner.bfe.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
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
import androidx.core.graphics.drawable.toBitmap
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
import com.the412banner.bfe.core.StorageRoot
import com.the412banner.bfe.core.StorageRoots
import com.the412banner.bfe.ui.components.CollapsibleRail
import com.the412banner.bfe.ui.components.RailItem
import com.the412banner.bfe.ui.components.RailLink
import com.the412banner.bfe.ui.components.RailSection
import com.the412banner.bfe.ui.components.rememberRailState
import com.the412banner.bfe.storage.DocProviderApp
import com.the412banner.bfe.storage.DocumentsProviderApps
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.storage.PinnedLocation
import com.the412banner.bfe.storage.PinnedStorage
import com.the412banner.bfe.storage.SafBackend
import com.the412banner.bfe.storage.Storage
import com.the412banner.bfe.storage.StorageTransfer
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

private fun comparatorFor(sortBy: String, desc: Boolean): Comparator<Loc> {
    val inner: Comparator<Loc> = when (sortBy) {
        "date" -> compareBy { it.lastModified }
        // Directory size is meaningless, so folders sort by name within the size ordering
        // instead of pretending to have one.
        "size" -> compareBy { if (it.isDir) -1L else it.size }
        // Group by file type (extension), then by name within each type.
        "type" -> compareBy<Loc> { File(it.name).extension.lowercase() }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    val directed = if (desc) inner.reversed() else inner
    return compareBy<Loc> { if (it.isDir) 0 else 1 }.then(directed)
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
 * All the per-pane state the [BrowserPane] and the single shared toolbar coordinate on. The shared
 * toolbar reads and mutates the ACTIVE pane's PaneState (its path/drive, view mode, sort, search,
 * favourites, new-folder), and BrowserPane reacts — so view mode and sort are PER PANE and toggling
 * one never syncs the other. The navigation hooks ([onOpenDir]/[onOpenDrive]) are wired by BrowserPane
 * because they need its coroutine scope + pick filtering; they're plain (non-observable) vars.
 */
class PaneState(
    initialPath: String,
    initialRootPath: String = initialPath,
    initialViewMode: FmViewMode = FmViewMode.LIST,
    initialSortBy: String = "name",
    initialSortDesc: Boolean = false,
    initialShowHidden: Boolean = true,
    initialCompactRows: Boolean = false,
) {
    var path by mutableStateOf(initialPath)             // current File directory (File mode)
    var rootPath by mutableStateOf(initialRootPath)     // up/back floor (the File drive root)
    // SAF navigation: non-empty ⇒ browsing a pinned SAF tree; last() = current dir, first() = root.
    // Empty ⇒ ordinary File mode (path/rootPath above). Not persisted across process death.
    var safStack by mutableStateOf<List<Loc.SafLoc>>(emptyList())
    var safLabel by mutableStateOf("")                  // the pinned location's friendly label
    var reloadTick by mutableStateOf(0)
    // Per-pane browse controls, driven by the shared toolbar for whichever pane is active.
    var viewMode by mutableStateOf(initialViewMode)
    var sortBy by mutableStateOf(initialSortBy)
    var sortDesc by mutableStateOf(initialSortDesc)
    var showHidden by mutableStateOf(initialShowHidden)
    var compactRows by mutableStateOf(initialCompactRows)
    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var showFavorites by mutableStateOf(false)
    var showNewFolderDialog by mutableStateOf(false)
    // File-mode navigation hooks (wired by BrowserPane; navigation needs its scope + pick filtering).
    var onOpenDir: (File) -> Unit = {}
    var onOpenDrive: (File) -> Unit = {}
    fun requestReload() { reloadTick++ }

    /** Whether this pane is currently browsing a SAF ("app storage") tree rather than a File dir. */
    val isSaf: Boolean get() = safStack.isNotEmpty()

    /** The pane's current directory, as a backend-agnostic [Loc]. */
    val currentLoc: Loc get() = if (isSaf) safStack.last() else Loc.FileLoc(File(path))

    val canGoUp: Boolean get() = if (isSaf) true else path != rootPath

    fun openSafRoot(root: Loc.SafLoc, label: String) { safStack = listOf(root); safLabel = label }
    fun openSafInto(child: Loc.SafLoc) { safStack = safStack + child }
    fun exitSaf() { safStack = emptyList(); safLabel = "" }
    /** SAF back: up one level, or exit SAF back to the File location the pane was at. */
    fun safUp() { if (safStack.size > 1) safStack = safStack.dropLast(1) else exitSaf() }
}

private val PaneStateSaver = listSaver<PaneState, String>(
    save = {
        listOf(
            it.path, it.rootPath, it.viewMode.name, it.sortBy,
            it.sortDesc.toString(), it.showHidden.toString(), it.compactRows.toString(),
        )
    },
    restore = {
        PaneState(it[0], it[1], FmViewMode.valueOf(it[2]), it[3], it[4].toBoolean(), it[5].toBoolean(), it[6].toBoolean())
    },
)

@Composable
fun rememberPaneState(
    key: String,
    initialPath: String,
    initialViewMode: FmViewMode,
    initialSortBy: String,
    initialSortDesc: Boolean,
    initialShowHidden: Boolean,
    initialCompactRows: Boolean,
): PaneState = rememberSaveable(key = key, saver = PaneStateSaver) {
    PaneState(initialPath, initialPath, initialViewMode, initialSortBy, initialSortDesc, initialShowHidden, initialCompactRows)
}

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
 * The File Manager entry point — the classic commander layout: ONE full-width toolbar at the top,
 * and one or two file panes below it. The persisted split toggle switches between a single pane and
 * two side-by-side panes (in both orientations). Exactly one pane is active (accent-bordered, focused
 * by a tap); the shared toolbar reflects and controls ONLY the active pane, and its "Copy →"/"Move →"
 * and extraction target the OTHER pane. View mode and sort are per pane. Pick mode uses a single pane.
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

    // GLOBAL defaults — used ONLY to initialize a pane's per-pane view/sort. Toggling in the toolbar
    // updates just the active pane (and rewrites the default for the NEXT fresh pane), never the other.
    val defViewMode = remember {
        when (prefs.getString("fmViewMode", null)) {
            "list" -> FmViewMode.LIST
            "compact" -> FmViewMode.COMPACT
            "grid" -> FmViewMode.GRID
            else -> if (prefs.getBoolean("fmGridView", true)) FmViewMode.GRID else FmViewMode.LIST
        }
    }
    val defSortBy = remember { prefs.getString("fmSortBy", "name") ?: "name" }
    val defSortDesc = remember { prefs.getBoolean("fmSortDesc", false) }
    val defHidden = remember { prefs.getBoolean("fmShowHidden", true) }
    val defCompact = remember { prefs.getBoolean("fmCompactRows", false) }

    val leftPane = rememberPaneState("fm_pane_left", startPath, defViewMode, defSortBy, defSortDesc, defHidden, defCompact)
    val rightPane = rememberPaneState("fm_pane_right", startPath, defViewMode, defSortBy, defSortDesc, defHidden, defCompact)

    var dualPane by rememberSaveable { mutableStateOf(prefs.getBoolean("fmDualPane", false)) }
    var activeIndex by rememberSaveable { mutableStateOf(0) }
    // Dual-pane never applies while picking a file/folder — a picker stays single-pane.
    val effectiveDual = dualPane && !pickMode
    val toggleDual: () -> Unit = {
        dualPane = !dualPane
        prefs.edit().putBoolean("fmDualPane", dualPane).apply()
        activeIndex = 0
    }
    val active = if (effectiveDual && activeIndex == 1) rightPane else leftPane

    // Drive list for the toolbar's dropdown; re-enumerated when we come back to the screen.
    var storageTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) storageTick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val drives = remember(storageTick) { StorageRoots.list(context) }

    // ── Pinned SAF "app storage" locations + the "Add app storage" picker (shared by the toolbar
    //    dropdown in portrait and the side rail in landscape) ──
    val scope = rememberCoroutineScope()
    var pinnedTick by remember { mutableIntStateOf(0) }
    val pinned = remember(pinnedTick) { PinnedStorage.list(context) }
    var showAddStorage by remember { mutableStateOf(false) }
    var pendingLabel by remember { mutableStateOf("App storage") }
    var pendingPkg by remember { mutableStateOf<String?>(null) }
    val activeState = rememberUpdatedState(active)

    // Open a pinned SAF tree in the ACTIVE pane (resolving its root document off the main thread).
    fun openPinned(p: PinnedLocation) {
        scope.launch {
            val root = withContext(Dispatchers.IO) { SafBackend.rootLoc(context, p.treeUri, p.label) }
            if (root != null) activeState.value.openSafRoot(root, p.label)
            else Toast.makeText(context, "Can't open ${p.label} — access may have been revoked", Toast.LENGTH_LONG).show()
        }
    }

    // ACTION_OPEN_DOCUMENT_TREE result → take a persistable grant, PIN it, and open it in the active pane.
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            PinnedStorage.add(context, pendingLabel, uri, pendingPkg)
            pinnedTick++
            scope.launch {
                val root = withContext(Dispatchers.IO) { SafBackend.rootLoc(context, uri, pendingLabel) }
                if (root != null) activeState.value.openSafRoot(root, pendingLabel)
            }
        }
    }
    fun launchTreePicker(provider: DocProviderApp?) {
        pendingLabel = provider?.label ?: "App storage"
        pendingPkg = provider?.packageName
        val initial = provider?.let { DocumentsProviderApps.initialTreeUri(context, it.authority) }
        runCatching { treeLauncher.launch(initial) }
            .onFailure { Toast.makeText(context, "No folder picker available", Toast.LENGTH_SHORT).show() }
    }

    val accent = MaterialTheme.colorScheme.primary
    val idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    // One dual-pane column: an activate-on-touch + accent-bordered box hosting an independent pane.
    @Composable
    fun PaneColumn(index: Int, state: PaneState, other: PaneState, modifier: Modifier) {
        val isActive = activeIndex == index
        Box(
            modifier = modifier
                .activateOnTouch(isActive) { activeIndex = index }
                .border(2.dp, if (isActive) accent else idleBorder),
        ) {
            BrowserPane(
                paneState = state,
                dualPane = true,
                otherPaneDir = { other.currentLoc },
                onRequestOtherReload = { other.requestReload() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ── "Add app storage" picker dialog (lists installed DocumentsProviders) ──
    if (showAddStorage) {
        AddAppStorageDialog(
            onDismiss = { showAddStorage = false },
            onPick = { provider -> showAddStorage = false; launchTreePicker(provider) },
        )
    }

    // Landscape gets the side nav rail back (STORAGE + pinned app storage + "Add app storage");
    // portrait relies on the toolbar's drive dropdown. Every rail action targets the ACTIVE pane.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showSideRail = landscape && !pickMode

    Row(modifier = Modifier.fillMaxSize()) {
        if (showSideRail) {
            val railState = rememberRailState("filemanager")
            val storageItems = buildList {
                add(RailItem("Internal", Icons.Filled.Smartphone, false) { active.exitSaf(); active.onOpenDrive(File("/storage/emulated/0")) })
                drives.filter { it.removable }.forEach { d ->
                    add(RailItem(d.label, Icons.Filled.SdStorage, false) { if (d.readable) { active.exitSaf(); active.onOpenDrive(d.dir) } })
                }
                pinned.forEach { p ->
                    add(RailItem(p.label, Icons.Filled.Cloud, active.isSaf && active.safLabel == p.label) { openPinned(p) })
                }
                add(RailItem("Add app storage", Icons.Filled.Add, false) { showAddStorage = true })
            }
            CollapsibleRail(
                state = railState,
                title = "Files",
                sections = listOf(RailSection("STORAGE", storageItems)),
                outlinedItems = true,
            )
        }
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            // ── ONE shared toolbar, full width, bound to the active pane ──
            SharedToolbar(
                active = active,
                drives = drives,
                pinned = pinned,
                prefs = prefs,
                dualPane = effectiveDual,
                onToggleDualPane = toggleDual,
                pickMode = pickMode,
                onOpenPinned = { openPinned(it) },
                onAddAppStorage = { showAddStorage = true },
                onUnpin = { p -> PinnedStorage.remove(context, p.treeUri); pinnedTick++ },
            )

            if (!effectiveDual) {
                BrowserPane(
                    paneState = leftPane,
                    dualPane = false,
                    otherPaneDir = { null },
                    onRequestOtherReload = {},
                    pickMode = pickMode,
                    pickDirMode = pickDirMode,
                    pickExtensions = pickExtensions,
                    initialDir = initialDir,
                    pickerTitle = pickerTitle,
                    onPick = onPick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PaneColumn(0, leftPane, rightPane, Modifier.weight(1f).fillMaxHeight())
                    PaneColumn(1, rightPane, leftPane, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * "Add app storage" picker: lists installed DocumentsProviders (SAF storage apps) with icon + label +
 * package. Tapping one launches ACTION_OPEN_DOCUMENT_TREE seeded at that provider; "Choose a folder…"
 * opens the plain system picker. The result (a tree Uri) is pinned + granted by the caller.
 */
@Composable
private fun AddAppStorageDialog(onDismiss: () -> Unit, onPick: (DocProviderApp?) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps = remember { DocumentsProviderApps.list(context) }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app storage") },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Pick an app that provides storage, then choose a folder to grant BFE access to.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (apps.isEmpty()) {
                    Text("No storage apps found — you can still pick any folder below.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                apps.forEach { app ->
                    val iconBmp = remember(app.packageName) {
                        runCatching { pm.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap() }.getOrNull()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp),
                    ) {
                        if (iconBmp != null) Image(bitmap = iconBmp, contentDescription = null, modifier = Modifier.size(28.dp))
                        else Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 10.dp),
                ) {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Choose a folder…", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The single shared toolbar (classic commander chrome). Full width, above the pane(s), it reflects
 * and controls ONLY the [active] pane: up/back, drive dropdown, New Folder, view-mode cycle, search,
 * sort, favourites, and the dual-pane split toggle. View-mode and sort mutate the active pane's
 * [PaneState] (per-pane; toggling never syncs the other pane) and also rewrite the persisted global
 * default for the next fresh pane. Navigation goes through the active pane's wired hooks.
 */
@Composable
private fun SharedToolbar(
    active: PaneState,
    drives: List<StorageRoot>,
    pinned: List<PinnedLocation>,
    prefs: android.content.SharedPreferences,
    dualPane: Boolean,
    onToggleDualPane: () -> Unit,
    pickMode: Boolean,
    onOpenPinned: (PinnedLocation) -> Unit,
    onAddAppStorage: () -> Unit,
    onUnpin: (PinnedLocation) -> Unit,
) {
    val context = LocalContext.current
    var showDriveMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val driveLabel = if (active.isSaf) active.safLabel else describeLocation(File(active.path)).driveLabel
    val folderName = if (active.isSaf) active.currentLoc.name else File(active.path).name.ifBlank { active.path }
    val driveChipAlpha = if (active.showFavorites) 0.45f else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Up / back (operates on the active pane).
        IconButton(
            onClick = {
                if (active.isSaf) active.safUp()
                else {
                    val parent = File(active.path).parentFile
                    if (active.canGoUp && parent != null && parent.exists()) active.onOpenDir(parent)
                }
            },
            enabled = active.canGoUp,
        ) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
        }

        // Drive selector.
        Box {
            val driveChipShape = RoundedCornerShape(8.dp)
            Text(
                text = "  $driveLabel  ▾",
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
                            active.exitSaf()
                            if (drive.readable) active.onOpenDrive(drive.dir)
                            else Toast.makeText(context, "${drive.label} is mounted but not readable right now", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                // Pinned SAF "app storage" locations, then the "+ Add app storage" entry.
                pinned.forEach { p ->
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        leadingIcon = { Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close, "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp).clickable { showDriveMenu = false; onUnpin(p) },
                            )
                        },
                        onClick = { showDriveMenu = false; onOpenPinned(p) },
                    )
                }
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Add app storage") },
                    leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    onClick = { showDriveMenu = false; onAddAppStorage() },
                )
            }
        }
        Spacer(Modifier.width(4.dp))

        // Current folder name (or the Favorites label), full-width — the toolbar spans the whole screen.
        if (active.showFavorites) {
            Text(
                "★ Favorites",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                folderName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (!active.showFavorites) {
            if (!pickMode) {
                OutlinedButton(
                    onClick = { active.showNewFolderDialog = true },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("New Folder", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                }
            }
            // View-mode cycle: Grid → List → Compact → Grid. Per-pane; also persists the global default.
            IconButton(onClick = {
                active.viewMode = when (active.viewMode) {
                    FmViewMode.GRID -> FmViewMode.LIST
                    FmViewMode.LIST -> FmViewMode.COMPACT
                    FmViewMode.COMPACT -> FmViewMode.GRID
                }
                prefs.edit().putString("fmViewMode", active.viewMode.name.lowercase()).apply()
            }) {
                Icon(
                    when (active.viewMode) {
                        FmViewMode.GRID -> Icons.Filled.GridView
                        FmViewMode.LIST -> Icons.Filled.ViewList
                        FmViewMode.COMPACT -> Icons.Filled.DensitySmall
                    },
                    "View mode",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { active.showSearch = !active.showSearch; if (!active.showSearch) active.searchQuery = "" }) {
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
                                text = { Text(if (active.sortBy == key) "$label  ${if (active.sortDesc) "↓" else "↑"}" else label) },
                                onClick = {
                                    if (active.sortBy == key) active.sortDesc = !active.sortDesc
                                    else { active.sortBy = key; active.sortDesc = false }
                                    prefs.edit().putString("fmSortBy", active.sortBy).putBoolean("fmSortDesc", active.sortDesc).apply()
                                    showSortMenu = false
                                    active.requestReload()
                                },
                            )
                        }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    DropdownMenuItem(
                        text = { Text(if (active.compactRows) "Comfortable rows" else "Compact rows") },
                        onClick = {
                            active.compactRows = !active.compactRows
                            prefs.edit().putBoolean("fmCompactRows", active.compactRows).apply()
                            showSortMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (active.showHidden) "Hide hidden files" else "Show hidden files") },
                        onClick = {
                            active.showHidden = !active.showHidden
                            prefs.edit().putBoolean("fmShowHidden", active.showHidden).apply()
                            showSortMenu = false
                            active.requestReload()
                        },
                    )
                }
            }
        }
        // Favorites toggle (always visible).
        IconButton(onClick = { active.showFavorites = !active.showFavorites }) {
            if (active.showFavorites) {
                Icon(Icons.Filled.Star, "Hide favorites", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.StarBorder, "Show favorites", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Dual-pane (split) toggle.
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
}

/**
 * One independent file-browser instance: just a PATH/location bar and the file list (NO toolbar — the
 * shared toolbar above controls it). Its current directory + a reload signal + its per-pane browse
 * state live in [paneState] so the shared toolbar (bound to the active pane) can read and drive it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserPane(
    paneState: PaneState,
    // Dual-pane wiring. In single-pane mode: dualPane=false, otherPaneDir returns null, no-op reload.
    dualPane: Boolean = false,
    otherPaneDir: () -> Loc? = { null },
    onRequestOtherReload: () -> Unit = {},
    modifier: Modifier = Modifier,
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
    fun matchesPickExt(loc: Loc): Boolean {
        if (lowerExts.isEmpty()) return true
        val name = loc.name.lowercase()
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

    // The pane's current directory as a backend-agnostic Loc. All the list/row/op code works on Loc;
    // File ops delegate to the same java.io.File / FileUtils code (unchanged), SAF plugs in the same
    // way. currentFile is non-null only in File mode, for the File-only extras (free space, favourites).
    val currentLoc: Loc = paneState.currentLoc
    val currentFile: File? = (currentLoc as? Loc.FileLoc)?.file
    var entries by remember { mutableStateOf<List<Loc>>(emptyList()) }
    var selectedEntry by remember { mutableStateOf<Loc?>(null) }
    var showMenuFor by remember { mutableStateOf<Loc?>(null) }
    // Clipboard holds a LIST so one paste can carry a whole selection.
    var clipboard by remember { mutableStateOf<List<Loc>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }
    // Multi-select, keyed by loc.id (path for File, doc-uri for SAF) so a reload keeps the selection.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingConflict by remember { mutableStateOf<Loc?>(null) }
    var conflictChoice by remember { mutableStateOf<ConflictChoice?>(null) }
    var conflictApplyToAll by remember { mutableStateOf(false) }
    var operationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<List<Loc>>(emptyList()) }
    var renameTarget by remember { mutableStateOf<Loc?>(null) }
    var propertiesTarget by remember { mutableStateOf<Loc?>(null) }
    var isOperationRunning by remember { mutableStateOf(false) }
    var operationLabel by remember { mutableStateOf("") }
    var operationDeterminate by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var favTick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // ── Reactive listing ── re-lists whenever the current dir, sort, hidden filter or a reload signal
    // changes. Navigation is pure state mutation (paneState.path / safStack); this effect turns that
    // into a fresh listing. Scroll resets only when the DIRECTORY changes, not on sort/refresh.
    val lastLocId = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentLoc.id, paneState.sortBy, paneState.sortDesc, paneState.showHidden, paneState.reloadTick) {
        val loc = paneState.currentLoc
        val reset = lastLocId.value != loc.id
        lastLocId.value = loc.id
        if (pickMode) (loc as? Loc.FileLoc)?.let { pickPrefs.edit().putString("lastFilePickerDir", it.file.absolutePath).apply() }
        loading = true
        loadError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                Storage.backend(loc).listChildren(context, loc)
                    // Dir-pick mode: folders only. File-pick: folders + matching files. Else: all.
                    .filter { if (pickDirMode) it.isDir else !pickMode || it.isDir || matchesPickExt(it) }
                    // Dotfiles are noise in a storage root — a toggle rather than a permanent filter.
                    .filter { paneState.showHidden || !it.name.startsWith(".") }
                    .sortedWith(comparatorFor(paneState.sortBy, paneState.sortDesc))
            }
        }
        entries = result.getOrNull() ?: emptyList()
        loadError = if (result.isFailure) "Couldn't read this folder." else null
        loading = false
        if (reset) runCatching { listState.scrollToItem(0) }
    }

    // Pull-to-refresh → re-list, keeping scroll.
    if (pullState.isRefreshing) {
        LaunchedEffect(true) { paneState.requestReload(); pullState.endRefresh() }
    }

    // One-shot: if the saved File path no longer exists, fall back to a sensible start dir.
    LaunchedEffect(Unit) {
        if (!paneState.isSaf && !File(paneState.path).isDirectory) {
            paneState.path = rootDir.absolutePath
            paneState.rootPath = rootDir.absolutePath
        }
    }

    // File-mode navigation into a directory (the effect re-lists on the paneState.path change).
    fun navFileDir(dir: File) { paneState.path = dir.absolutePath }

    // Jump to a File drive's root (exits any SAF browse); pins the back floor to that root.
    fun openDrive(dir: File) {
        paneState.exitSaf()
        paneState.rootPath = dir.absolutePath
        paneState.path = dir.absolutePath
    }

    // Wire the File-mode navigation hooks the shared toolbar uses (back / drive dropdown).
    paneState.onOpenDir = { dir -> navFileDir(dir) }
    paneState.onOpenDrive = { dir -> openDrive(dir) }

    // Navigate into a directory entry, whichever backend it belongs to.
    fun openLoc(loc: Loc) {
        when (loc) {
            is Loc.FileLoc -> navFileDir(loc.file)
            is Loc.SafLoc -> paneState.openSafInto(loc)
        }
    }

    // Favourites are File-only (they store absolute paths). SAF entries just get a no-op star.
    fun isLocFav(loc: Loc): Boolean =
        (loc as? Loc.FileLoc)?.let { FavoritesStore.isFavorite(context, it.file.absolutePath) } ?: false
    fun toggleFav(loc: Loc): Boolean {
        val f = (loc as? Loc.FileLoc)?.file ?: return false
        return FavoritesStore.toggle(context, f.absolutePath)
    }

    // System/gesture Back: close Favorites first; else go up (SAF pops/exits; File climbs to parent).
    BackHandler(enabled = paneState.showFavorites || paneState.canGoUp) {
        if (paneState.showFavorites) { paneState.showFavorites = false; return@BackHandler }
        if (paneState.isSaf) {
            paneState.safUp()
        } else {
            val here = File(paneState.path)
            val parent = here.parentFile
            if (here != File(paneState.rootPath) && parent != null && parent.exists()) paneState.path = parent.absolutePath
        }
    }

    // A non-colliding child name for [name] within [dir] ("foo.txt" -> "foo (1).txt"). Backend-aware;
    // does IPC for SAF, so callers invoke it off the main thread.
    fun uniqueName(dir: Loc, name: String): String {
        val backend = Storage.backend(dir)
        if (backend.childNamed(context, dir, name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var cand = "$base ($i)$ext"
        while (backend.childNamed(context, dir, cand) != null) { i++; cand = "$base ($i)$ext" }
        return cand
    }

    // A shareable content:// Uri for [loc]: a FileProvider uri for File, or the SAF doc Uri directly.
    fun viewUri(loc: Loc): Uri? = when (loc) {
        is Loc.FileLoc -> runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", loc.file)
        }.getOrNull()
        is Loc.SafLoc -> loc.docUri
    }

    // "Open with": hand the file to another app through a plain ACTION_VIEW chooser (best-effort).
    fun openWith(loc: Loc) {
        runCatching {
            val uri = viewUri(loc) ?: throw IllegalStateException()
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Open with"))
        }.onFailure { Toast.makeText(context, "No app can open ${loc.name}", Toast.LENGTH_SHORT).show() }
    }

    // Share a file to another app via ACTION_SEND.
    fun shareFile(loc: Loc) {
        runCatching {
            val uri = viewUri(loc) ?: throw IllegalStateException()
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share ${loc.name}"))
        }.onFailure { Toast.makeText(context, "Couldn't share ${loc.name}", Toast.LENGTH_SHORT).show() }
    }

    // Share several files at once via ACTION_SEND_MULTIPLE (used by the selection action bar).
    fun shareFiles(locs: List<Loc>) {
        val files = locs.filter { !it.isDir }
        if (files.isEmpty()) return
        if (files.size == 1) { shareFile(files.first()); return }
        runCatching {
            val uris = ArrayList(files.mapNotNull { viewUri(it) })
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share ${files.size} files"))
        }.onFailure { Toast.makeText(context, "Couldn't share the selection", Toast.LENGTH_SHORT).show() }
    }

    // Install an .apk via the system package installer (needs REQUEST_INSTALL_PACKAGES).
    fun installApk(loc: Loc) {
        runCatching {
            val uri = viewUri(loc) ?: throw IllegalStateException()
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure { Toast.makeText(context, "Couldn't start the installer for ${loc.name}", Toast.LENGTH_SHORT).show() }
    }

    // In dual-pane mode, extraction defaults into the OTHER pane's directory when it's a File dir (the
    // native extractors need a real path); single-pane keeps the engine's own sibling-folder default.
    fun extractDest(): File? = if (dualPane) (otherPaneDir() as? Loc.FileLoc)?.file else null

    // Archive extraction is File-only (the native engines need a real path); guard SAF entries.
    fun launchFastExtract(loc: Loc) {
        showMenuFor = null
        val file = (loc as? Loc.FileLoc)?.file ?: run {
            Toast.makeText(context, "Extraction from app storage isn't supported yet", Toast.LENGTH_SHORT).show(); return
        }
        val dest = extractDest()
        scope.launch {
            when (val o = com.the412banner.bfe.unpack.FastExtract.start(context, file, dest)) {
                is com.the412banner.bfe.unpack.FastExtract.Outcome.Started -> {
                    Toast.makeText(context, "Unpacking ${o.name}…", Toast.LENGTH_SHORT).show()
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

    fun launchUnpack(loc: Loc) {
        showMenuFor = null
        val file = (loc as? Loc.FileLoc)?.file ?: return
        context.startActivity(
            com.the412banner.bfe.UnpackArchiveActivity.intent(context, file.absolutePath, extractDest()?.absolutePath)
        )
    }

    fun performDelete(loc: Loc) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Deleting..."
            val ok = withContext(Dispatchers.IO) { Storage.backend(loc).delete(context, loc) }
            isOperationRunning = false
            paneState.requestReload()
            if (!ok) Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Waits for the user to answer the conflict dialog for [loc]; null if they cancelled it. */
    suspend fun askConflict(loc: Loc): ConflictChoice? {
        pendingConflict = loc
        conflictChoice = null
        while (pendingConflict != null && conflictChoice == null) kotlinx.coroutines.delay(50)
        return conflictChoice
    }

    // Copy/move [sources] into [dstDir]. File→File keeps the fast FileUtils path (unchanged); anything
    // touching a SAF side goes through StorageTransfer (recursive stream copy). [onDone] refreshes the
    // OTHER pane after a cross-pane op; the source pane always refreshes (a move removes items from it).
    fun performCopyMove(sources: List<Loc>, dstDir: Loc, cut: Boolean, onDone: () -> Unit = {}) {
        if (sources.isEmpty()) return
        val dstBackend = Storage.backend(dstDir)
        operationJob = scope.launch {
            operationProgress = 0f
            operationDeterminate = false
            operationLabel = if (cut) "Moving..." else "Copying..."
            isOperationRunning = true

            var applyToAll: ConflictChoice? = null
            var failed = 0; var skipped = 0; var done = 0

            for (src in sources) {
                val srcFile = (src as? Loc.FileLoc)?.file
                val dstFile = (dstDir as? Loc.FileLoc)?.file
                // Pasting a File folder into itself/its subtree would recurse forever.
                if (srcFile != null && dstFile != null && src.isDir && isWithin(dstFile, srcFile)) { failed++; continue }
                // Moving a File into the folder it already sits in is a no-op.
                if (cut && srcFile != null && dstFile != null && srcFile.parentFile?.absolutePath == dstFile.absolutePath) { skipped++; continue }

                var targetName = src.name
                val exists = withContext(Dispatchers.IO) { dstBackend.childNamed(context, dstDir, targetName) != null }
                if (exists) {
                    val choice = applyToAll ?: askConflict(src)?.also { if (conflictApplyToAll) applyToAll = it }
                        ?: run { skipped++; null } ?: continue
                    when (choice) {
                        ConflictChoice.OVERWRITE, ConflictChoice.MERGE -> Unit
                        ConflictChoice.KEEP_BOTH -> targetName = withContext(Dispatchers.IO) { uniqueName(dstDir, src.name) }
                        ConflictChoice.SKIP -> { skipped++; continue }
                    }
                }

                operationLabel = buildString {
                    append(if (cut) "Moving" else "Copying")
                    if (sources.size > 1) append(" ${done + 1}/${sources.size}")
                    append(" — ").append(src.name)
                }
                val name = targetName
                val ok = if (srcFile != null && dstFile != null) {
                    // Fast File→File path (unchanged behaviour), with the per-item progress bar.
                    operationDeterminate = true
                    var lastPct = -1
                    val onProgress = FileUtils.ProgressCallback { copied, total ->
                        val pct = if (total > 0) ((copied * 100) / total).toInt() else 100
                        if (pct != lastPct) { lastPct = pct; operationProgress = pct / 100f }
                    }
                    val target = File(dstFile, name)
                    withContext(Dispatchers.IO) {
                        if (cut) FileUtils.moveWithProgress(srcFile, target, onProgress)
                        else FileUtils.copyWithProgress(srcFile, target, onProgress)
                    }
                } else {
                    // Cross-backend / SAF: recursive stream copy (indeterminate progress).
                    operationDeterminate = false
                    withContext(Dispatchers.IO) {
                        if (cut) StorageTransfer.moveInto(context, src, dstDir, name)
                        else StorageTransfer.copyInto(context, src, dstDir, name)
                    }
                }
                if (ok) done++ else failed++
            }

            isOperationRunning = false
            operationDeterminate = false
            operationJob = null
            selectionMode = false
            selectedIds = emptySet()
            paneState.requestReload()
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
        val sources = clipboard
        val cut = isCutOperation
        clipboard = emptyList()
        isCutOperation = false
        performCopyMove(sources, currentLoc, cut)
    }

    // Cross-pane: copy or move the current selection into the OTHER pane's directory, then refresh it.
    fun crossPaneTransfer(cut: Boolean) {
        val target = otherPaneDir() ?: return
        val sources = entries.filter { it.id in selectedIds }
        performCopyMove(sources, target, cut) { onRequestOtherReload() }
    }

    fun performRename(loc: Loc, newName: String) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Renaming..."
            val backend = Storage.backend(loc)
            val ok = withContext(Dispatchers.IO) {
                if (backend.childNamed(context, currentLoc, newName) != null) false
                else backend.rename(context, loc, newName) != null
            }
            isOperationRunning = false
            paneState.requestReload()
            if (!ok) Toast.makeText(context, "Couldn't rename to \"$newName\"", Toast.LENGTH_SHORT).show()
        }
    }

    fun createFolder(parent: Loc, name: String) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Creating folder..."
            val backend = Storage.backend(parent)
            val ok = withContext(Dispatchers.IO) {
                if (backend.childNamed(context, parent, name) != null) false
                else backend.createFolder(context, parent, name) != null
            }
            isOperationRunning = false
            paneState.requestReload()
            if (!ok) Toast.makeText(context, "Could not create \"$name\"", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Dialogs ──

    if (paneState.showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        OutlinedAlertDialog(
            onDismissRequest = { paneState.showNewFolderDialog = false },
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
                    paneState.showNewFolderDialog = false
                    if (folderName.isNotBlank()) createFolder(currentLoc, folderName)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { paneState.showNewFolderDialog = false }) { Text("Cancel") } },
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

    // Properties (File only — the Read-only toggle is a chmod; SAF documents have no such attribute).
    (propertiesTarget as? Loc.FileLoc)?.let { fileLoc ->
        FilePropertiesDialog(
            file = fileLoc.file,
            onDismiss = { propertiesTarget = null },
            onChanged = { paneState.requestReload() },
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
                    selectedIds = emptySet()
                    operationJob = scope.launch {
                        isOperationRunning = true
                        var failed = 0
                        victims.forEachIndexed { i, f ->
                            operationLabel = "Deleting ${i + 1}/${victims.size} — ${f.name}"
                            if (!withContext(Dispatchers.IO) { Storage.backend(f).delete(context, f) }) failed++
                        }
                        isOperationRunning = false
                        operationJob = null
                        paneState.requestReload()
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
        val isDir = conflict.isDir
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
                    if (clipboard.size > 1) {
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

    Column(modifier = modifier) {
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
        // ── Dir-pick action bar: confirm the currently-browsed folder (File locations only) ──
        if (pickDirMode && currentFile != null) {
            Button(
                onClick = { onPick?.invoke(currentFile) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select this folder")
            }
        }
        // ── Search field ── (toggled by the shared toolbar) filters the current folder only.
        if (paneState.showSearch && !paneState.showFavorites) {
            OutlinedTextField(
                value = paneState.searchQuery,
                onValueChange = { paneState.searchQuery = it },
                singleLine = true,
                placeholder = { Text("Filter this folder", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Free space (File volumes only — SAF providers don't report it) + the current path/breadcrumb.
        val freeSpace = remember(currentLoc.id, entries) {
            currentFile?.let { runCatching { it.usableSpace }.getOrDefault(0L) } ?: 0L
        }
        val locDisplayPath = currentFile?.absolutePath
            ?: (paneState.safLabel + paneState.safStack.drop(1).joinToString("") { " / ${it.name}" })
        if (!paneState.showFavorites) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(
                    // Elided from the LEFT: the deepest part of a path is the informative part, so
                    // when it doesn't fit we drop the /storage/emulated/0 prefix, not the tail.
                    text = elidePathStart(locDisplayPath, 52),
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
                    "${selectedIds.size} selected",
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
                            selectedIds = if (selectedIds.size == entries.size) emptySet()
                            else entries.map { it.id }.toSet()
                        },
                        contentPadding = selBarPadding,
                    ) { Text(if (selectedIds.size == entries.size) "None" else "All", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = {
                            clipboard = entries.filter { it.id in selectedIds }
                            isCutOperation = false
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        contentPadding = selBarPadding,
                    ) { Text("Copy", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = {
                            clipboard = entries.filter { it.id in selectedIds }
                            isCutOperation = true
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        contentPadding = selBarPadding,
                    ) { Text("Cut", fontSize = 12.sp) }
                    // Cross-pane: copy / move the selection straight into the OTHER pane's directory.
                    if (dualPane && otherPaneDir() != null) {
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { crossPaneTransfer(cut = false) },
                            contentPadding = selBarPadding,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        ) { Text("Copy →", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { crossPaneTransfer(cut = true) },
                            contentPadding = selBarPadding,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        ) { Text("Move →", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedIds.any { p -> entries.any { it.id == p && !it.isDir } },
                        onClick = {
                            shareFiles(entries.filter { it.id in selectedIds })
                        },
                        contentPadding = selBarPadding,
                    ) { Text("Share", fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { pendingBulkDelete = entries.filter { it.id in selectedIds } },
                        contentPadding = selBarPadding,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { selectionMode = false; selectedIds = emptySet() },
                        contentPadding = selBarPadding,
                    ) { Text("Done", fontSize = 12.sp) }
                }
            }
        }

        // ── Paste banner ──
        if (clipboard.isNotEmpty()) {
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
                val what = if (clipboard.size == 1) clipboard.first().name
                else "${clipboard.size} items"
                Text(
                    "Paste $what${if (isCutOperation) " (move)" else ""} here",
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { clipboard = emptyList(); isCutOperation = false }) {
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
                            paneState.requestReload()
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

        // ── Content: favorites list OR file list (fills the height below the shared toolbar) ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // ── Favorites list OR file list ──
        if (paneState.showFavorites) {
            FavoritesList(
                currentDir = currentFile,
                favTick = favTick,
                onPinCurrent = {
                    currentFile?.let {
                        FavoritesStore.add(context, it.absolutePath)
                        favTick++
                        Toast.makeText(context, "Added \"${it.name}\" to Favorites", Toast.LENGTH_SHORT).show()
                    }
                },
                onJump = { dir ->
                    paneState.showFavorites = false
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
            val shownEntries = if (paneState.searchQuery.isBlank()) entries
            else entries.filter { it.name.contains(paneState.searchQuery, ignoreCase = true) }

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
            } else if (paneState.viewMode == FmViewMode.GRID && entries.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(shownEntries, key = { it.id }) { file ->
                        val isFav = remember(file.id, favTick) { isLocFav(file) }
                        FileGridTile(
                            file = file,
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            onLongPress = {
                                if (!pickMode) {
                                    // In selection mode a long-press toggles; otherwise it opens the
                                    // same context menu the list rows show (the tile has no ⋮ button).
                                    if (selectionMode) {
                                        selectedIds = if (file.id in selectedIds)
                                            selectedIds - file.id
                                        else selectedIds + file.id
                                    } else {
                                        showMenuFor = file
                                    }
                                }
                            },
                            onToggleSelect = {
                                selectedIds = if (file.id in selectedIds)
                                    selectedIds - file.id
                                else selectedIds + file.id
                            },
                            onTap = {
                                if (file.isDir) openLoc(file)
                                else if (pickMode) {
                                    if (matchesPickExt(file)) {
                                        currentFile?.let { pickPrefs.edit().putString("lastFilePickerDir", it.absolutePath).apply() }
                                        (file as? Loc.FileLoc)?.file?.let { f -> onPick?.invoke(f) }
                                    }
                                } else openWith(file)
                            },
                            onMenu = { showMenuFor = file },
                            menuExpanded = showMenuFor == file,
                            onDismissMenu = { showMenuFor = null },
                            isFavorite = isFav,
                            onSelect = {
                                selectionMode = true
                                selectedIds = selectedIds + file.id
                                showMenuFor = null
                            },
                            onOpenWith = { openWith(file) },
                            onInstallApk = { installApk(file) },
                            onShare = { shareFile(file) },
                            onUnpack = { launchUnpack(file) },
                            onFastExtract = { launchFastExtract(file) },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onCopy = { clipboard = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboard = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onToggleFavorite = {
                                val nowFav = toggleFav(file)
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
                    items(shown, key = { it.id }) { file ->
                        val isFav = remember(file.id, favTick) { isLocFav(file) }
                        FileItemRow(
                            file = file,
                            showActions = !pickMode,
                            compact = paneState.compactRows,
                            // COMPACT view mode → dense single-line rows (~4× more per screen).
                            dense = paneState.viewMode == FmViewMode.COMPACT,
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            onLongPress = {
                                // In selection mode a long-press toggles; otherwise it opens the
                                // per-item context menu (matching the grid tiles).
                                if (!pickMode) {
                                    if (selectionMode) {
                                        selectedIds = if (file.id in selectedIds)
                                            selectedIds - file.id
                                        else selectedIds + file.id
                                    } else {
                                        showMenuFor = file
                                    }
                                }
                            },
                            onToggleSelect = {
                                selectedIds = if (file.id in selectedIds)
                                    selectedIds - file.id
                                else selectedIds + file.id
                            },
                            onTap = {
                                if (file.isDir) openLoc(file)
                                else if (pickMode) {
                                    if (matchesPickExt(file)) {
                                        currentFile?.let { pickPrefs.edit().putString("lastFilePickerDir", it.absolutePath).apply() }
                                        (file as? Loc.FileLoc)?.file?.let { f -> onPick?.invoke(f) }
                                    }
                                }
                                else openWith(file)
                            },
                            onMenu = { showMenuFor = file },
                            menuExpanded = showMenuFor == file,
                            onDismissMenu = { showMenuFor = null },
                            onSelect = {
                                selectionMode = true
                                selectedIds = selectedIds + file.id
                                showMenuFor = null
                            },
                            onOpenWith = { openWith(file) },
                            onInstallApk = { installApk(file) },
                            onShare = { shareFile(file) },
                            onCopy = { clipboard = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboard = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onFastExtract = { launchFastExtract(file) },
                            onUnpack = { launchUnpack(file) },
                            isFavorite = isFav,
                            onToggleFavorite = {
                                val nowFav = toggleFav(file)
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
        } // end content Box
    }
}

// The context-menu item list shared by the list rows (FileItemRow) and the grid tiles
// (FileGridTile), so both open the identical menu. Gating (isDir/canRun/isInno/looksLikeArchive) is
// recomputed from [file] here — one source of truth for what each item shows. Every item dismisses
// the menu first, then runs its action.
@Composable
private fun FileContextMenuItems(
    file: Loc,
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
    val isDir = file.isDir
    // File-specific extras (properties, favourites, archive extraction) are for direct-File entries;
    // SAF ("app storage") entries only get the backend-agnostic actions.
    val fileLoc = (file as? Loc.FileLoc)?.file
    // Enter multi-select on this item (replaces the old long-press-only entry point).
    DropdownMenuItem(
        text = { Text("Select") },
        leadingIcon = { Icon(Icons.Filled.Checklist, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onSelect() },
    )
    MenuItemDivider()
    // Properties (File only — the Read-only toggle is a chmod).
    if (fileLoc != null) {
        DropdownMenuItem(
            text = { Text("Properties") },
            leadingIcon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onProperties() },
        )
        MenuItemDivider()
    }
    // Favorites are File directories — only folders on real storage get the pin toggle.
    if (isDir && fileLoc != null) {
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
    val isInno = fileLoc != null && com.the412banner.bfe.unpack.SevenZip.isInnoSetup(fileLoc)
    // Content-aware: extension OR a cheap magic-byte sniff, so a .wcp/.bin/renamed
    // archive with an unlisted extension still gets the option (menu opens per row,
    // so this reads only a few header bytes on demand — never `7zz l` per entry).
    // Archive extraction is File-only (the native engines need a real path).
    if (fileLoc != null && (isInno || com.the412banner.bfe.unpack.SevenZip.looksLikeArchive(fileLoc))) {
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
    file: Loc,
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
    val isDir = file.isDir
    val isExe = !isDir && file.name.lowercase().let { it.endsWith(".exe") || it.endsWith(".bat") || it.endsWith(".msi") || it.endsWith(".sh") }
    // Image files show a real thumbnail instead of the generic file icon (handy when picking a
    // wallpaper/icon). Coil sizes the decode to the 36dp slot and caches it, so scrolling stays smooth.
    val isImage = !isDir && File(file.name).extension.lowercase() in IMAGE_THUMB_EXTS

    // For real PE executables, try to pull out the embedded application icon (async, off the main thread).
    var exeIcon by remember(file.id) { mutableStateOf<ImageBitmap?>(null) }
    val exeFile = (file as? Loc.FileLoc)?.file
    if (exeFile != null && exeFile.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.id) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(exeFile) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // Thumbnail source: the File for direct storage, the SAF doc Uri otherwise (Coil handles both).
    val thumbModel: Any = (file as? Loc.FileLoc)?.file ?: (file as Loc.SafLoc).docUri

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
                        model = thumbModel,
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
                        text = StringUtils.formatBytes(file.size),
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
                    model = thumbModel,
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
                        if (!isDir) append(StringUtils.formatBytes(file.size)).append("  \u2022  ")
                        append(dateFormat.format(Date(file.lastModified)))
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
    currentDir: File?,
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
    // Pin-current is available only on a real File location (SAF trees are pinned via "Add app storage").
    val currentAlreadyPinned = remember(favTick, currentDir?.absolutePath) {
        currentDir != null && FavoritesStore.isFavorite(context, currentDir.absolutePath)
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
    file: Loc,
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
    val isDir = file.isDir
    val isImage = !isDir && File(file.name).extension.lowercase() in IMAGE_THUMB_EXTS
    var exeIcon by remember(file.id) { mutableStateOf<ImageBitmap?>(null) }
    val exeFile = (file as? Loc.FileLoc)?.file
    if (exeFile != null && exeFile.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.id) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(exeFile) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // Thumbnail source: the File for direct storage, the SAF doc Uri otherwise (Coil handles both).
    val thumbModel: Any = (file as? Loc.FileLoc)?.file ?: (file as Loc.SafLoc).docUri
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
                            model = thumbModel,
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
