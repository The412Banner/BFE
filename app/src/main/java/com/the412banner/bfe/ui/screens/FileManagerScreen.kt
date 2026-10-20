package com.the412banner.bfe.ui.screens

import android.os.Build
import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.the412banner.bfe.core.FileUtils
import com.the412banner.bfe.core.ApkIconLoader
import com.the412banner.bfe.core.PeIconExtractor
import com.the412banner.bfe.core.StorageRoot
import com.the412banner.bfe.core.StorageRoots
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.components.CollapsibleRail
import com.the412banner.bfe.ui.icons.BfeIcon
import com.the412banner.bfe.ui.icons.FileIcons
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.ui.icons.fileIconPainter
import com.the412banner.bfe.ui.components.RailItem
import com.the412banner.bfe.ui.components.RailLink
import com.the412banner.bfe.ui.components.RailSection
import com.the412banner.bfe.ui.components.rememberRailState
import com.the412banner.bfe.storage.ArchiveBackend
import com.the412banner.bfe.storage.ArchiveIndexCache
import com.the412banner.bfe.storage.ChildCount
import com.the412banner.bfe.storage.DocProviderApp
import com.the412banner.bfe.storage.InstalledApp
import com.the412banner.bfe.storage.InstalledApps
import com.the412banner.bfe.storage.RootAccess
import com.the412banner.bfe.storage.RootBackend
import com.the412banner.bfe.storage.DocumentsProviderApps
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.storage.PinnedLocation
import com.the412banner.bfe.storage.PinnedStorage
import com.the412banner.bfe.storage.SafBackend
import com.the412banner.bfe.storage.Storage
import com.the412banner.bfe.storage.StorageTransfer
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.DateText
import com.the412banner.bfe.util.FavoritesStore
import com.the412banner.bfe.ui.screens.home.FmLocationRequest
import com.the412banner.bfe.ui.screens.home.HomePrefs
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.ui.theme.LocalHazeState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.the412banner.bfe.media.MediaChanges
import com.the412banner.bfe.media.MediaLaunch
import com.the412banner.bfe.media.MediaRef
import com.the412banner.bfe.media.MediaSiblings
import com.the412banner.bfe.media.MediaType
import com.the412banner.bfe.media.MediaTypes
import com.the412banner.bfe.util.naturalCompare
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
/**
 * A file waiting in the Open with sheet: its share Uri and the detected MIME type. [fromArchive] is set
 * when [loc] is BFE's cache copy of an entry inside an archive (the sheet then offers archive actions).
 */
private data class OpenWithRequest(val loc: Loc, val uri: Uri, val mime: String, val fromArchive: Loc.ArchiveLoc? = null)

enum class ConflictChoice { OVERWRITE, MERGE, KEEP_BOTH, SKIP }

/**
 * The three file-list view modes, cycled by the toolbar's view button and persisted as `fmViewMode`.
 * GRID = thumbnail tiles; LIST = the tall cards; COMPACT = dense single-line rows (~4× more per screen).
 */
enum class FmViewMode { GRID, LIST, COMPACT }

/**
 * Dual pane has no grid: half-width tiles are too small to be useful, so a grid pane shows as the list
 * there. The pane keeps its grid setting and gets it back in single pane.
 */
internal fun FmViewMode.shownIn(dualPane: Boolean): FmViewMode =
    if (dualPane && this == FmViewMode.GRID) FmViewMode.LIST else this

/** The view-mode button's cycle: Grid → List → Compact → Grid, or List ⇄ Compact in dual pane. */
internal fun FmViewMode.nextIn(dualPane: Boolean): FmViewMode = when (shownIn(dualPane)) {
    FmViewMode.GRID -> FmViewMode.LIST
    FmViewMode.LIST -> FmViewMode.COMPACT
    FmViewMode.COMPACT -> if (dualPane) FmViewMode.LIST else FmViewMode.GRID
}

/**
 * Below this width (a dual-pane half on a phone) the card rows go narrow: short date over time in small
 * type, a slimmer ⋮ and tighter padding, so the name keeps most of the row.
 */
internal val NARROW_ROW_WIDTH = 400.dp

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

/**
 * The pane's clickable breadcrumb: drive (or pinned location) first, then one crumb per folder down to
 * the current one; inside an archive "…/folder › Game.zip › inner". Each crumb carries what tapping it
 * does (null = not tappable). Shared by both looks' pane headers.
 */
internal fun paneCrumbs(paneState: PaneState, currentFile: File?, locDisplayPath: String, navFileDir: (File) -> Unit): List<Pair<String, (() -> Unit)?>> =
    if (currentFile != null) {
        val root = File(paneState.rootPath)
        val rootAbs = root.absolutePath
        val curAbs = currentFile.absolutePath
        val rootPrefix = rootAbs.trimEnd('/') + "/"
        val rel = if (curAbs == rootAbs) "" else curAbs.removePrefix(rootPrefix)
        if (curAbs != rootAbs && !curAbs.startsWith(rootPrefix)) {
            listOf<Pair<String, (() -> Unit)?>>(elidePathStart(locDisplayPath, 52) to null)
        } else {
            val out = mutableListOf<Pair<String, (() -> Unit)?>>(
                describeLocation(root).driveLabel.ifBlank { root.name.ifBlank { "/" } } to { navFileDir(root) }
            )
            var acc = root
            rel.split('/').filter { it.isNotEmpty() }.forEach { seg ->
                acc = File(acc, seg)
                val target = acc
                out.add(seg to { navFileDir(target) })
            }
            out
        }
    } else if (paneState.isArchive) {
        // …/folder › Game.zip › inner › dir — the first crumb leaves the archive.
        val stack = paneState.altStack
        listOf<Pair<String, (() -> Unit)?>>("\u2026/" + File(paneState.path).name.ifBlank { "/" } to { paneState.exitAlt() }) +
            stack.mapIndexed { i, loc -> loc.name to { paneState.altStack = stack.take(i + 1) } }
    } else {
        val stack = paneState.altStack
        stack.mapIndexed { i, loc ->
            (if (i == 0) paneState.altLabel else loc.name) to { paneState.altStack = stack.take(i + 1) }
        }
    }

/**
 * "Natural" name order, ignoring case: runs of digits compare as numbers, so "Disc 2" sorts before
 * "Disc 10" and "file9" before "file10".
 */
private val NaturalNameOrder = Comparator<Loc> { a, b -> naturalCompare(a.name, b.name) }


/**
 * Folders always come first, each group in its own order: folders by [folderSortBy] ("name" | "date"),
 * files by [sortBy] ("name" | "date" | "size" | "type"). Ties fall back to the natural name order.
 */
private fun comparatorFor(sortBy: String, desc: Boolean, folderSortBy: String, folderDesc: Boolean): Comparator<Loc> {
    val files: Comparator<Loc> = when (sortBy) {
        "date" -> compareBy<Loc> { it.lastModified }
        "size" -> compareBy<Loc> { it.size }
        // Group by file type (extension), then by name within each type.
        "type" -> compareBy<Loc> { File(it.name).extension.lowercase() }
        else -> NaturalNameOrder
    }.let { if (desc) it.reversed() else it }.then(NaturalNameOrder)
    val folders: Comparator<Loc> = (if (folderSortBy == "date") compareBy<Loc> { it.lastModified } else NaturalNameOrder)
        .let { if (folderDesc) it.reversed() else it }.then(NaturalNameOrder)
    return Comparator { a, b ->
        when {
            a.isDir != b.isDir -> if (a.isDir) -1 else 1
            a.isDir -> folders.compare(a, b)
            else -> files.compare(a, b)
        }
    }
}

// Image extensions that get a real thumbnail (via Coil) instead of the generic file icon.
internal val IMAGE_THUMB_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
// Videos get a frame grabbed by Coil's VideoFrameDecoder (Android's own media stack, so what it can't
// decode simply keeps the video type icon).
internal val VIDEO_THUMB_EXTS = setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov", "ts", "avi", "wmv", "flv", "mpg", "mpeg")

// After "New file…", these get an immediate "Open with…" offer (a text editor is the obvious next step).
private val TEXT_LIKE_EXTS = setOf("txt", "md", "ini", "cfg", "conf", "json", "xml", "yml", "yaml", "log", "csv", "bat", "sh", "reg", "properties", "toml", "html", "htm", "kt", "java", "py", "js", "css", "gradle", "lua")

// Color-only sweep: the former card-fill / card-stroke / divider / icon-blue / icon-white
// constants were rerouted onto MaterialTheme.colorScheme tokens (surface / outline / primary /
// onSurface) at their use sites so a theme preset/accent recolors them.

// True when [child] is [ancestor] itself or lives anywhere inside it.
private fun isWithin(child: File, ancestor: File): Boolean {
    val c = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
    val a = runCatching { ancestor.canonicalPath }.getOrDefault(ancestor.absolutePath)
    return c == a || c.startsWith(a + File.separator)
}

/**
 * A pack (Compress…) job a pane started and is waiting on: [tmpArchive] is the cache temp the engine
 * writes when [dest] is SAF/root (null = written in place), [stagedInputs] the temp copy of SAF/root
 * sources (null = File inputs handed over directly), [toOther] = refresh the other pane on DONE.
 */
private class PendingPack(val jobId: Long, val tmpArchive: File?, val dest: Loc, val stagedInputs: File?, val toOther: Boolean)

/**
 * An APK clone/edit/sign job a pane started: [tmpOut] is the cache temp the job writes when [dest]
 * is SAF/root (null = written in place), [stagedSource] a temp copy of a SAF/root source APK.
 */
private class PendingApk(val jobId: Long, val tmpOut: File?, val dest: Loc, val stagedSource: File?)

/** A running video conversion this pane started: [tmpDir] = cache temp holding the outputs when [dest] is SAF/root. */
private class PendingConvert(val jobId: Long, val tmpDir: File?, val dest: Loc, val stagedInputs: File?, val toOther: Boolean)

/** What the APK editor is open for: the parsed [meta], clone-vs-edit, split APKs to merge, base file, destination dir. */
private class ApkEditorTarget(val meta: com.the412banner.bfe.apk.ApkMeta, val kind: com.the412banner.bfe.apk.ApkJobKind, val base: File, val splits: List<File>, val dest: Loc, val staged: File?)

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
internal fun badgeColors(loc: FavLocation): Pair<Color, Color> {
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
 * favourites, new-folder), and BrowserPane reacts — so sort is PER PANE and toggling it never syncs
 * the other, while the view mode is one state both panes share, so a split always shows both panes
 * the same way. The navigation hooks ([onOpenDir]/[onOpenDrive]) are wired by BrowserPane
 * because they need its coroutine scope + pick filtering; they're plain (non-observable) vars.
 */
class PaneState(
    initialPath: String,
    initialRootPath: String = initialPath,
    viewModeState: MutableState<FmViewMode> = mutableStateOf(FmViewMode.LIST),
    initialSortBy: String = "name",
    initialSortDesc: Boolean = false,
    initialShowHidden: Boolean = true,
    initialCompactRows: Boolean = false,
    initialFolderSortBy: String = "name",
    initialFolderSortDesc: Boolean = false,
) {
    var path by mutableStateOf(initialPath)             // current File directory (File mode)
    var rootPath by mutableStateOf(initialRootPath)     // up/back floor (the File drive root)
    // SAF navigation: non-empty ⇒ browsing a pinned SAF tree; last() = current dir, first() = root.
    // Empty ⇒ ordinary File mode (path/rootPath above). Not persisted across process death.
    var altStack by mutableStateOf<List<Loc>>(emptyList())
    var altLabel by mutableStateOf("")                  // the pinned location's friendly label
    var reloadTick by mutableStateOf(0)
    // Browse controls, driven by the shared toolbar for whichever pane is active. All are per pane
    // except the view mode, which is the screen's one state shared with the other pane.
    var viewMode by viewModeState
    // File order (sortBy/sortDesc) and folder order (folderSortBy/folderDesc); folders always list first.
    var sortBy by mutableStateOf(initialSortBy)
    var sortDesc by mutableStateOf(initialSortDesc)
    var folderSortBy by mutableStateOf(initialFolderSortBy)
    var folderSortDesc by mutableStateOf(initialFolderSortDesc)
    var showHidden by mutableStateOf(initialShowHidden)
    var compactRows by mutableStateOf(initialCompactRows)
    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var showFavorites by mutableStateOf(false)
    var showNewFolderDialog by mutableStateOf(false)
    var showNewFileDialog by mutableStateOf(false)
    // File-mode navigation hooks (wired by BrowserPane; navigation needs its scope + pick filtering).
    var onOpenDir: (File) -> Unit = {}
    var onOpenDrive: (File) -> Unit = {}
    // APK-tools hooks (wired by BrowserPane, invoked from the shared toolbar's overflow menu).
    var onCloneInstalledApp: () -> Unit = {}
    var onSigningKeys: () -> Unit = {}
    // Aurora Glass: this pane's multi-select actions, published by BrowserPane for the floating
    // selection pill (null when not selecting), and which side of a dual split the pane is on.
    var selectionUi by mutableStateOf<PaneSelectionUi?>(null, PaneSelectionUi.SameDisplay)
    var isRightPane by mutableStateOf(false)
    fun requestReload() { reloadTick++ }

    /** Whether this pane is currently browsing a SAF ("app storage") tree rather than a File dir. */
    val isAlt: Boolean get() = altStack.isNotEmpty()

    /** The pane's current directory, as a backend-agnostic [Loc]. */
    val currentLoc: Loc get() = if (isAlt) altStack.last() else Loc.FileLoc(File(path))

    val canGoUp: Boolean get() = if (isAlt) true else path != rootPath

    fun openAltRoot(root: Loc, label: String) { altStack = listOf(root); altLabel = label }
    /** Whether the pane is showing the inside of an archive (read-only). */
    val isArchive: Boolean get() = altStack.lastOrNull() is Loc.ArchiveLoc
    /**
     * Browse an archive in place: from a File folder it starts an archive stack (Back walks its inner
     * folders, then returns to the folder); inside an archive a nested one is pushed on top.
     */
    fun openArchive(root: Loc.ArchiveLoc) { if (isArchive) altStack = altStack + root else { altStack = listOf(root); altLabel = "" } }
    /** Leave [archive] (and anything opened inside it), back to where the pane was before it. */
    fun closeArchive(archive: java.io.File) {
        val keep = altStack.takeWhile { !(it is Loc.ArchiveLoc && it.archive == archive) }
        if (keep.isEmpty()) exitAlt() else altStack = keep
    }
    fun openAltInto(child: Loc) { altStack = altStack + child }
    fun exitAlt() { altStack = emptyList(); altLabel = "" }
    /** SAF back: up one level, or exit SAF back to the File location the pane was at. */
    fun altUp() { if (altStack.size > 1) altStack = altStack.dropLast(1) else exitAlt() }
}

// The view mode isn't saved here: it's the screen's shared state, saved once by FileManagerScreen.
private fun paneStateSaver(viewMode: MutableState<FmViewMode>) = listSaver<PaneState, String>(
    save = {
        listOf(
            it.path, it.rootPath, it.sortBy,
            it.sortDesc.toString(), it.showHidden.toString(), it.compactRows.toString(),
            it.folderSortBy, it.folderSortDesc.toString(),
        )
    },
    restore = {
        PaneState(
            it[0], it[1], viewMode, it[2], it[3].toBoolean(), it[4].toBoolean(), it[5].toBoolean(),
            it[6], it[7].toBoolean(),
        )
    },
)

@Composable
fun rememberPaneState(
    key: String,
    initialPath: String,
    viewMode: MutableState<FmViewMode>,
    initialSortBy: String,
    initialSortDesc: Boolean,
    initialShowHidden: Boolean,
    initialCompactRows: Boolean,
    initialFolderSortBy: String,
    initialFolderSortDesc: Boolean,
): PaneState = rememberSaveable(key = key, saver = paneStateSaver(viewMode)) {
    PaneState(
        initialPath, initialPath, viewMode, initialSortBy, initialSortDesc, initialShowHidden, initialCompactRows,
        initialFolderSortBy, initialFolderSortDesc,
    )
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
 * and extraction target the OTHER pane. Sort is per pane; the view mode is shared, so both panes always
 * look the same. Pick mode uses a single pane.
 */
@Composable
fun FileManagerScreen(
    pickMode: Boolean = false,
    pickDirMode: Boolean = false,
    pickExtensions: List<String> = emptyList(),
    initialDir: File? = null,
    pickerTitle: String? = null,
    onPick: ((File) -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    // Home dashboard wiring (all off in pickers): a location to jump the ACTIVE pane to, the toolbar's
    // Home button, and what Back does once the active pane is at its storage root.
    openRequest: FmLocationRequest? = null,
    onHome: (() -> Unit)? = null,
    onBackAtRoot: (() -> Unit)? = null,
    // Aurora Glass: the "Sharing" status chip opens the Access-from-network screen.
    onOpenServer: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val startPath = remember {
        (initialDir?.takeIf { it.isDirectory } ?: File("/storage/emulated/0")).absolutePath
    }

    // GLOBAL defaults — used ONLY to initialize the shared view and each pane's sort. A sort change in
    // the toolbar updates just the active pane (and rewrites the default for the NEXT fresh pane).
    val defViewMode = remember {
        when (prefs.getString("fmViewMode", null)) {
            "list" -> FmViewMode.LIST
            "compact" -> FmViewMode.COMPACT
            "grid" -> FmViewMode.GRID
            else -> if (prefs.getBoolean("fmGridView", true)) FmViewMode.GRID else FmViewMode.LIST
        }
    }
    // Default order: folders A→Z, then files newest first.
    val defSortBy = remember { prefs.getString("fmFileSortBy", "date") ?: "date" }
    val defSortDesc = remember { prefs.getBoolean("fmFileSortDesc", true) }
    val defFolderSortBy = remember { prefs.getString("fmFolderSortBy", "name") ?: "name" }
    val defFolderSortDesc = remember { prefs.getBoolean("fmFolderSortDesc", false) }
    val defHidden = remember { prefs.getBoolean("fmShowHidden", true) }
    val defCompact = remember { prefs.getBoolean("fmCompactRows", false) }

    // One view mode for both panes: the view button sets it for both, so a split always matches.
    val sharedViewMode = rememberSaveable { mutableStateOf(defViewMode) }
    val leftPane = rememberPaneState("fm_pane_left", startPath, sharedViewMode, defSortBy, defSortDesc, defHidden, defCompact, defFolderSortBy, defFolderSortDesc)
    val rightPane = rememberPaneState("fm_pane_right", startPath, sharedViewMode, defSortBy, defSortDesc, defHidden, defCompact, defFolderSortBy, defFolderSortDesc)

    var dualPane by rememberSaveable { mutableStateOf(prefs.getBoolean("fmDualPane", false)) }
    var splitFraction by remember { mutableFloatStateOf(prefs.getFloat("fmSplitFraction", 0.5f).coerceIn(0.2f, 0.8f)) }
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

    // ── Home dashboard hooks ── each request is a new object, so asking for the same folder twice
    // still moves the pane. This Back handler is registered before the panes' own, so it only runs
    // when the active pane can't go up any further. The last folder feeds Settings → Start on.
    LaunchedEffect(openRequest) { openRequest?.applyTo(activeState.value) }
    if (onBackAtRoot != null) BackHandler(enabled = !active.canGoUp && !active.showFavorites) { onBackAtRoot() }
    if (!pickMode) LaunchedEffect(active, active.path) { HomePrefs.setLastFolder(context, active.path) }

    // Open a pinned location (SAF tree or root path) in the ACTIVE pane, resolving it off the main
    // thread. Root pins request root lazily (Magisk prompts once); denied/unavailable ⇒ a clear
    // message and nothing else — the app keeps working without root.
    fun openPinned(p: PinnedLocation) {
        scope.launch {
            if (p.isRoot) {
                val ok = withContext(Dispatchers.IO) { RootAccess.ensure() }
                if (!ok) {
                    AppSnackbar.show(context, "Root access unavailable — grant BFE root in Magisk to browse app data", long = true)
                    return@launch
                }
                val root = withContext(Dispatchers.IO) { RootBackend.stat(p.path ?: return@withContext null) }
                if (root != null && root.isDir) activeState.value.openAltRoot(root, p.label)
                else AppSnackbar.show(context, "Can't open ${p.label} — ${p.path} isn't a directory", long = true)
            } else {
                val uri = p.treeUri ?: return@launch
                val root = withContext(Dispatchers.IO) { SafBackend.rootLoc(context, uri, p.label) }
                if (root != null) activeState.value.openAltRoot(root, p.label)
                else AppSnackbar.show(context, "Can't open ${p.label} — access may have been revoked", long = true)
            }
        }
    }

    // Root "All apps" pick: pin /data/data/<pkg> IMMEDIATELY (no folder picker — root needs no SAF
    // consent) plus the app's external data dir when it exists, then open the private dir.
    fun pinRootApp(app: InstalledApp) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { RootAccess.ensure() }
            if (!ok) {
                AppSnackbar.show(context, "Root access unavailable — grant BFE root in Magisk to browse app data", long = true)
                return@launch
            }
            val priv = "/data/data/${app.packageName}"
            val ext = "/storage/emulated/0/Android/data/${app.packageName}"
            PinnedStorage.addRoot(context, app.label, priv, app.packageName)
            val hasExt = withContext(Dispatchers.IO) { RootBackend.stat(ext)?.isDir == true }
            if (hasExt) PinnedStorage.addRoot(context, "${app.label} — external data", ext, app.packageName)
            pinnedTick++
            val root = withContext(Dispatchers.IO) { RootBackend.stat(priv) }
            if (root != null && root.isDir) activeState.value.openAltRoot(root, app.label)
            else AppSnackbar.show(context, "Can't open ${app.label}'s data directory", long = true)
        }
    }

    // ACTION_OPEN_DOCUMENT_TREE result → take a persistable grant, PIN it, and open it in the active pane.
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            PinnedStorage.add(context, pendingLabel, uri, pendingPkg)
            pinnedTick++
            scope.launch {
                val root = withContext(Dispatchers.IO) { SafBackend.rootLoc(context, uri, pendingLabel) }
                if (root != null) activeState.value.openAltRoot(root, pendingLabel)
            }
        }
    }
    fun launchTreePicker(provider: DocProviderApp?) {
        pendingLabel = provider?.label ?: "App storage"
        pendingPkg = provider?.packageName
        val initial = provider?.let { DocumentsProviderApps.initialTreeUri(context, it.authority) }
        runCatching { treeLauncher.launch(initial) }
            .onFailure { AppSnackbar.show(context, "No folder picker available") }
    }

    val accent = MaterialTheme.colorScheme.primary
    val idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    val aurora = isAurora
    LaunchedEffect(rightPane) { rightPane.isRightPane = true }

    // One dual-pane column: an activate-on-touch + accent-bordered box hosting an independent pane.
    // Aurora Glass: a glass card whose active state brightens it, rims it in the accent and adds a glow.
    @Composable
    fun PaneColumn(index: Int, state: PaneState, other: PaneState, modifier: Modifier) {
        val isActive = activeIndex == index
        Box(
            modifier = modifier
                .activateOnTouch(isActive) { activeIndex = index }
                .then(if (aurora) Modifier.auroraPaneCard(isActive) else Modifier.border(2.dp, if (isActive) accent else idleBorder)),
        ) {
            CompositionLocalProvider(LocalPaneActive provides isActive) {
                BrowserPane(
                    paneState = state,
                    dualPane = true,
                    // A pane showing an archive is read-only, so it's never a destination for the other pane.
                    otherPaneDir = { other.currentLoc.takeUnless { it is Loc.ArchiveLoc } },
                    onRequestOtherReload = { other.requestReload() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ── Aurora Glass chrome state: the drive menu data the pane headers open, the blur source for the
    //    floating pills, and how much room those pills take at the bottom of the lists ──
    val fmChrome = remember { FmChrome() }
    fmChrome.drives = drives
    fmChrome.pinned = pinned
    fmChrome.onOpenPinned = { openPinned(it) }
    fmChrome.onAddAppStorage = { showAddStorage = true }
    fmChrome.onUnpin = { p -> PinnedStorage.remove(context, p); pinnedTick++ }
    val hazeState = remember { HazeState() }
    val otherPane = if (active === leftPane) rightPane else leftPane
    // The selection pill follows the active pane, or the other one if only it is selecting.
    val selectionUi = active.selectionUi ?: if (effectiveDual) otherPane.selectionUi else null

    // ── "Add app storage" picker dialog (lists installed DocumentsProviders) ──
    if (showAddStorage) {
        AddAppStorageDialog(
            onDismiss = { showAddStorage = false },
            onPick = { provider -> showAddStorage = false; launchTreePicker(provider) },
            onPickRoot = { app -> showAddStorage = false; pinRootApp(app) },
        )
    }

    // Landscape gets the side nav rail back (STORAGE + pinned app storage + "Add app storage");
    // portrait relies on the toolbar's drive dropdown. Every rail action targets the ACTIVE pane.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showSideRail = landscape && !pickMode

    if (aurora) {
        val reserve = when {
            landscape -> if (selectionUi != null) 76.dp else 12.dp
            selectionUi != null -> 78.dp + if (selectionUi.actions.size > 5) 140.dp else 92.dp
            else -> 78.dp
        }
        SideEffect { fmChrome.listBottomPadding = reserve }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalFmChrome provides fmChrome) {
    Row(modifier = Modifier.fillMaxSize().then(if (aurora) Modifier.haze(hazeState) else Modifier)) {
        if (showSideRail && !aurora) {
            val railState = rememberRailState("filemanager")
            val storageItems = buildList {
                add(RailItem("Internal", Icons.Filled.Smartphone, false) { active.exitAlt(); active.onOpenDrive(File("/storage/emulated/0")) })
                drives.filter { it.removable }.forEach { d ->
                    add(RailItem(d.label, Icons.Filled.SdStorage, false) { if (d.readable) { active.exitAlt(); active.onOpenDrive(d.dir) } })
                }
                pinned.forEach { p ->
                    add(RailItem(p.label, if (p.isRoot) Icons.Filled.Security else Icons.Filled.Cloud, active.isAlt && active.altLabel == p.label) { openPinned(p) })
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
        // Aurora's landscape pill rail floats at the left; leave it room.
        if (aurora && landscape) Spacer(Modifier.width(76.dp))
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            // ── ONE shared toolbar, full width, bound to the active pane ──
            // (Aurora: a quiet header here; the controls float in the pill / rail below.)
            if (aurora) AuroraFmHeader(active = active, pickMode = pickMode, onOpenServer = onOpenServer)
            else SharedToolbar(
                active = active,
                drives = drives,
                pinned = pinned,
                prefs = prefs,
                dualPane = effectiveDual,
                onToggleDualPane = toggleDual,
                pickMode = pickMode,
                onOpenPinned = { openPinned(it) },
                onAddAppStorage = { showAddStorage = true },
                onUnpin = { p -> PinnedStorage.remove(context, p); pinnedTick++ },
                onOpenSettings = onOpenSettings,
                onHome = onHome,
                selection = selectionUi,
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
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .then(if (aurora) Modifier.padding(start = if (landscape) 2.dp else 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp).auroraPaneCard(true) else Modifier),
                )
            } else {
                // Drag the handle between the panes to resize them (20–80 %); double-tap resets to
                // 50/50. The split is remembered across launches. Panes sit side by side in both
                // orientations (portrait splits down the middle).
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .then(if (aurora) Modifier.padding(start = if (landscape) 2.dp else 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp) else Modifier),
                ) {
                    val totalPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    Row(modifier = Modifier.fillMaxSize()) {
                        PaneColumn(0, leftPane, rightPane, Modifier.weight(splitFraction).fillMaxHeight())
                        if (aurora) AuroraPaneDivider(
                            fraction = splitFraction,
                            totalPx = totalPx,
                            onDelta = { d -> splitFraction = (splitFraction + d).coerceIn(0.2f, 0.8f) },
                            onFraction = { f -> splitFraction = f.coerceIn(0.2f, 0.8f) },
                            onSettled = { f -> prefs.edit().putFloat("fmSplitFraction", f.coerceIn(0.2f, 0.8f)).apply() },
                        ) else PaneDivider(
                            onDrag = { dx -> splitFraction = (splitFraction + dx / totalPx).coerceIn(0.2f, 0.8f) },
                            onDragEnd = { prefs.edit().putFloat("fmSplitFraction", splitFraction).apply() },
                            onReset = { splitFraction = 0.5f; prefs.edit().putFloat("fmSplitFraction", 0.5f).apply() },
                        )
                        PaneColumn(1, rightPane, leftPane, Modifier.weight(1f - splitFraction).fillMaxHeight())
                    }
                }
            }
        }
    }
    } // LocalFmChrome
    // ── Aurora Glass: the floating pill toolbar (portrait) / pill rail (landscape) + selection pill ──
    if (aurora) {
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            AuroraFmChrome(
                active = active,
                selection = selectionUi,
                landscape = landscape,
                dualPane = effectiveDual,
                onToggleDualPane = toggleDual,
                pickMode = pickMode,
                prefs = prefs,
                onHome = onHome,
                onOpenSettings = onOpenSettings,
            )
        }
    }
    }
}

/**
 * "Add app storage" picker with two sections:
 *  • **Storage apps (SAF)** — installed DocumentsProviders (icon + label + package). Tapping one opens
 *    ACTION_OPEN_DOCUMENT_TREE seeded at that provider's ROOT, so the user only confirms "Use this
 *    folder" (that confirmation is Android's own SAF consent step — unavoidable).
 *  • **All apps (root)** — EVERY installed app, with a filter box. Tapping one pins and opens
 *    `/data/data/<pkg>` IMMEDIATELY — no folder picker, root needs no SAF consent (Magisk prompts
 *    once for root the first time).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddAppStorageDialog(
    onDismiss: () -> Unit,
    onPick: (DocProviderApp?) -> Unit,
    onPickRoot: (InstalledApp) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val safApps = remember { DocumentsProviderApps.list(context) }
    var rootTab by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val allApps = remember(rootTab) { if (rootTab) InstalledApps.list(context) else emptyList() }
    val shownApps = remember(allApps, filter) {
        if (filter.isBlank()) allApps
        else allApps.filter { it.label.contains(filter, true) || it.packageName.contains(filter, true) }
    }

    @Composable
    fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp) {
        val bmp = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap() }.getOrNull() }
        if (bmp != null) Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size))
        else Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size))
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app storage") },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                // Section switch.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !rootTab, onClick = { rootTab = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Storage apps", fontSize = 12.sp) }
                    SegmentedButton(selected = rootTab, onClick = { rootTab = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("All apps (root)", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                if (!rootTab) {
                    Text(
                        "Pick an app that provides storage. Android will then ask you to confirm the folder " +
                            "(the picker opens at the app's root — just tap \"Use this folder\"). That confirmation " +
                            "is Android's consent step; the root section below skips it entirely.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (safApps.isEmpty()) {
                            Text("No storage apps found — you can still pick any folder below.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        safApps.forEach { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp),
                            ) {
                                AppIcon(app.packageName, 28.dp)
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
                            BfeIcon(FileIcons.FOLDER, size = 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Choose a folder…", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                        }
                    }
                } else {
                    Text(
                        "Pick any app — its private data (/data/data) is pinned and opened immediately, no " +
                            "folder picker. Needs root (Magisk will ask once). Files BFE writes there are handed " +
                            "back to the app's own user so the app keeps working.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it }, singleLine = true,
                        placeholder = { Text("Filter apps", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(shownApps, key = { it.packageName }) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onPickRoot(app) }.padding(vertical = 8.dp),
                            ) {
                                AppIcon(app.packageName, 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
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
 * sort, favourites, the dual-pane split toggle and the tools overflow. View-mode and sort mutate the
 * active pane's [PaneState] (per-pane; toggling never syncs the other pane) and also rewrite the
 * persisted global default for the next fresh pane. Navigation goes through the active pane's hooks.
 *
 * NEVER CLIPS. The toolbar measures its own width (BoxWithConstraints, not orientation):
 *  - when every control fits, it renders the classic single row (wide screens: unchanged);
 *  - otherwise the ESSENTIALS stay on row one (back, drive chip, folder name, search, view-mode,
 *    split) and the rest (New Folder, sort, favourites, overflow) wrap onto a FlowRow below;
 *  - below [NARROW_TOOLBAR_DP] the two text pills collapse to icons (drive icon + caret, and an
 *    icon-only New Folder), each still a 48dp target with a content description.
 */
private val NARROW_TOOLBAR_DP = 400.dp

@OptIn(ExperimentalLayoutApi::class)
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
    onOpenSettings: () -> Unit,
    onHome: (() -> Unit)? = null,
    // A pane's multi-select: the ⋮ becomes a ☰ with the selection actions (no extra bar, so the list
    // keeps its size) and the folder name gives way to the count.
    selection: PaneSelectionUi? = null,
) {
    val context = LocalContext.current
    var showDriveMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    // Inside an archive the drive is still the File drive the archive sits on.
    val driveLabel = if (active.isAlt && !active.isArchive) active.altLabel else describeLocation(File(active.path)).driveLabel
    val folderName = when {
        active.isAlt -> active.currentLoc.name
        // At a drive root the chip already names the drive; its raw folder name ("0", "1A2B-3C4D") means nothing.
        active.path == active.rootPath -> ""
        else -> File(active.path).name.ifBlank { active.path }
    }
    val driveChipAlpha = if (active.showFavorites) 0.45f else 1f
    val driveIcon = when {
        active.isArchive -> Icons.Filled.Archive
        active.isAlt && pinned.any { it.label == active.altLabel && it.isRoot } -> Icons.Filled.Security
        active.isAlt -> Icons.Filled.Cloud
        describeLocation(File(active.path)).storage == FavStorage.SD -> Icons.Filled.SdStorage
        else -> Icons.Filled.Storage
    }

    // Which controls are present, in the order they appear (browse controls hide in Favorites).
    val browsing = !active.showFavorites
    val showNewFolder = browsing && !pickMode && !active.isArchive
    val showSplitAndTools = !pickMode

    // ── The individual controls, shared by the one-row and wrapped layouts ──

    @Composable
    fun HomeButton() {
        if (onHome != null) IconButton(onClick = onHome) {
            Icon(Icons.Filled.Home, "Home", tint = MaterialTheme.colorScheme.primary)
        }
    }

    @Composable
    fun BackButton() {
        IconButton(
            onClick = {
                if (active.isAlt) active.altUp()
                else {
                    val parent = File(active.path).parentFile
                    if (active.canGoUp && parent != null && parent.exists()) active.onOpenDir(parent)
                }
            },
            enabled = active.canGoUp,
        ) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
        }
    }

    @Composable
    fun DriveChip(iconOnly: Boolean) {
        Box {
            val driveChipShape = RoundedCornerShape(8.dp)
            if (iconOnly) {
                // Narrow: icon + caret in a full 48dp touch target, same outline as the label chip.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                        .clip(driveChipShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), driveChipShape)
                        .clickable { showDriveMenu = true }
                        .semantics { contentDescription = "Drive: $driveLabel" },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(driveIcon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = driveChipAlpha), modifier = Modifier.size(18.dp))
                        Text("▾", color = MaterialTheme.colorScheme.onBackground.copy(alpha = driveChipAlpha), fontSize = 11.sp)
                    }
                }
            } else {
                Text(
                    text = "  $driveLabel  ▾",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = driveChipAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(driveChipShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), driveChipShape)
                        .clickable { showDriveMenu = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            BfeDropdownMenu(
                expanded = showDriveMenu,
                onDismissRequest = { showDriveMenu = false },
                modifier = Modifier.outlinedMenuCard(),
            ) {
                FmDriveMenuItems(active, drives, pinned, onOpenPinned, onAddAppStorage, onUnpin, onDone = { showDriveMenu = false })
            }
        }
    }

    @Composable
    fun FolderName(modifier: Modifier) {
        // Current folder name (or the Favorites label) — takes whatever width the row has left.
        if (selection != null) {
            Text(
                "${selection.count} selected",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        } else if (active.showFavorites) {
            Text(
                "★ Favorites",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        } else {
            Text(
                folderName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        }
    }

    @Composable
    fun NewButton(iconOnly: Boolean) {
        // "New ▾" — Folder or File. The narrow icon-only form opens the same two-item menu.
        var showNewMenu by remember { mutableStateOf(false) }
        Box {
            if (iconOnly) {
                IconButton(onClick = { showNewMenu = true }) {
                    Icon(Icons.Filled.CreateNewFolder, "New folder or file", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                OutlinedButton(
                    onClick = { showNewMenu = true },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("New ▾", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                }
            }
            BfeDropdownMenu(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }, modifier = Modifier.outlinedMenuCard()) {
                FmNewMenuItems(active, onDone = { showNewMenu = false })
            }
        }
    }

    @Composable
    fun ViewModeButton() {
        // View-mode cycle: Grid → List → Compact → Grid (List ⇄ Compact in dual pane), for both panes
        // at once (they share the mode); also persists the global default.
        IconButton(onClick = {
            active.viewMode = active.viewMode.nextIn(dualPane)
            prefs.edit().putString("fmViewMode", active.viewMode.name.lowercase()).apply()
        }) {
            Icon(
                when (active.viewMode.shownIn(dualPane)) {
                    FmViewMode.GRID -> Icons.Filled.GridView
                    FmViewMode.LIST -> Icons.Filled.ViewList
                    FmViewMode.COMPACT -> Icons.Filled.DensitySmall
                },
                "View mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    fun SearchButton() {
        IconButton(onClick = { active.showSearch = !active.showSearch; if (!active.showSearch) active.searchQuery = "" }) {
            Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    fun SortButton() {
        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Filled.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BfeDropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                FmSortMenuItems(active, prefs, onDone = { showSortMenu = false })
            }
        }
    }

    @Composable
    fun FavoritesButton() {
        IconButton(onClick = { active.showFavorites = !active.showFavorites }) {
            if (active.showFavorites) {
                Icon(Icons.Filled.Star, "Hide favorites", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.StarBorder, "Show favorites", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    fun SplitButton() {
        IconButton(onClick = onToggleDualPane) {
            Icon(
                Icons.Filled.ViewColumn,
                if (dualPane) "Single pane" else "Dual pane",
                tint = if (dualPane) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    fun ToolsOverflow() {
        if (selection != null) {
            SelectionMenuButton(selection)
            return
        }
        // Overflow: New, APK tools that aren't tied to a file in the list, and Settings / About.
        Box {
            IconButton(onClick = { showTools = true }) {
                Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BfeDropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }, modifier = Modifier.outlinedMenuCard()) {
                FmToolsMenuItems(active, browsing, onOpenSettings, onDone = { showTools = false })
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val width = maxWidth
        val narrow = width < NARROW_TOOLBAR_DP
        // Estimate the fixed widths (48dp icon buttons; text pills from their label length) and keep
        // the classic single row whenever it all fits with room for a readable folder name.
        val chipDp = if (narrow) 48 else (driveLabel.length * 8 + 36).coerceAtLeast(60)
        val newFolderDp = if (!showNewFolder) 0 else if (narrow) 48 else 84
        val iconButtons = (if (onHome != null) 1 else 0) /* home */ + 1 /* back */ + (if (browsing) 3 else 0) /* view, search, sort */ + 1 /* favourites */ + (if (showSplitAndTools) 2 else 0)
        val fixedDp = 48 * iconButtons + chipDp + 4 + newFolderDp
        val fitsOneRow = fixedDp + 72 <= width.value

        if (fitsOneRow) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HomeButton()
                BackButton()
                DriveChip(iconOnly = narrow)
                Spacer(Modifier.width(4.dp))
                FolderName(Modifier.weight(1f))
                if (browsing) {
                    if (showNewFolder) NewButton(iconOnly = narrow)
                    ViewModeButton()
                    SearchButton()
                    SortButton()
                }
                FavoritesButton()
                if (showSplitAndTools) {
                    SplitButton()
                    ToolsOverflow()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row one: the essentials (never wrapped): back, drive, name, search, view-mode, split.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    HomeButton()
                    BackButton()
                    DriveChip(iconOnly = narrow)
                    Spacer(Modifier.width(4.dp))
                    FolderName(Modifier.weight(1f))
                    if (browsing) {
                        SearchButton()
                        ViewModeButton()
                    }
                    if (showSplitAndTools) SplitButton()
                }
                // The rest wraps onto as many rows as it needs — nothing is ever cut off.
                FlowRow(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showNewFolder) Box(modifier = Modifier.align(Alignment.CenterVertically)) { NewButton(iconOnly = narrow) }
                    if (browsing) Box(modifier = Modifier.align(Alignment.CenterVertically)) { SortButton() }
                    Box(modifier = Modifier.align(Alignment.CenterVertically)) { FavoritesButton() }
                    if (showSplitAndTools) Box(modifier = Modifier.align(Alignment.CenterVertically)) { ToolsOverflow() }
                }
            }
        }
    }
}

/**
 * Classic's multi-select menu: the ☰ that stands in for the toolbar's ⋮ while items are picked. Opens
 * the same outlined dropdown as a row's ⋮ — select all / none, the single item's own extras when only
 * one is picked, the selection actions, Delete, then Done (leave multi-select).
 */
@Composable
private fun SelectionMenuButton(sel: PaneSelectionUi) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    @Composable
    fun Item(label: String, icon: ImageVector, enabled: Boolean = true, tone: SelTone = SelTone.NORMAL, action: () -> Unit) {
        val color = when (tone) {
            SelTone.DANGER -> cs.error
            SelTone.ACCENT -> cs.primary
            SelTone.NORMAL -> cs.onSurface
        }
        DropdownMenuItem(
            text = { Text(label, color = if (enabled) color else color.copy(alpha = 0.38f)) },
            leadingIcon = {
                val tint = if (tone == SelTone.DANGER) cs.error else cs.primary
                Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
            },
            enabled = enabled,
            onClick = { open = false; action() },
        )
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Menu, "Selection actions", tint = cs.primary)
        }
        BfeDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.outlinedMenuCard()) {
            Item(if (sel.allSelected) "Select none" else "Select all", if (sel.allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll, action = sel.onToggleAll)
            if (sel.single.isNotEmpty()) {
                MenuItemDivider()
                sel.single.forEach { a -> Item(a.label, a.icon, a.enabled, a.tone, a.onClick) }
            }
            val (danger, rest) = sel.actions.partition { it.tone == SelTone.DANGER }
            if (rest.isNotEmpty()) {
                MenuItemDivider()
                rest.forEach { a -> Item(a.label, a.icon, a.enabled, a.tone, a.onClick) }
            }
            if (danger.isNotEmpty()) {
                MenuItemDivider()
                danger.forEach { a -> Item(a.label, a.icon, a.enabled, a.tone, a.onClick) }
            }
            MenuItemDivider()
            Item("Done", Icons.Filled.Close, action = sel.onDone)
        }
    }
}

/**
 * One independent file-browser instance: just a PATH/location bar and the file list (NO toolbar — the
 * shared toolbar above controls it). Its current directory + a reload signal + its per-pane browse
 * state live in [paneState] so the shared toolbar (bound to the active pane) can read and drive it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // The location [entries] were listed from (Aurora animates folder changes off it).
    var entriesFor by remember { mutableStateOf<String?>(null) }
    var exitScroll by remember { mutableStateOf(PaneScroll()) }
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
    // Hoisted like listState so a refresh keeps the grid where it was and a new folder starts at the top.
    val gridState = rememberLazyGridState()
    val pullState = rememberPullToRefreshState()
    var favTick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var openWithReq by remember { mutableStateOf<OpenWithRequest?>(null) }
    // Archive browsing: opening state, password prompt, Extract to… (ArchivePane.kt).
    val archivePane = rememberArchivePaneState()

    // ── Reactive listing ── re-lists whenever the current dir, sort, hidden filter or a reload signal
    // changes. Navigation is pure state mutation (paneState.path / altStack); this effect turns that
    // into a fresh listing. Scroll resets only when the DIRECTORY changes, not on sort/refresh.
    val lastLocId = remember { mutableStateOf<String?>(null) }
    // A just-created entry to reveal after the next listing: tinted like a selection (without entering
    // selection mode) and scrolled into view. Cleared on navigation.
    var highlightId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentLoc.id, paneState.sortBy, paneState.sortDesc, paneState.folderSortBy, paneState.folderSortDesc, paneState.showHidden, paneState.reloadTick) {
        val loc = paneState.currentLoc
        val reset = lastLocId.value != loc.id
        lastLocId.value = loc.id
        if (pickMode) (loc as? Loc.FileLoc)?.let { pickPrefs.edit().putString("lastFilePickerDir", it.file.absolutePath).apply() }
        loading = true
        loadError = null
        // An archive is listed from its index: build that first (it can take a while for a big or
        // compressed archive, may ask for a password, and is cancelled by leaving the archive).
        if (loc is Loc.ArchiveLoc) {
            if (reset && ArchiveIndexCache.peek(loc.archive) == null) entries = emptyList()
            when (val o = archivePane.ensureOpen(loc)) {
                is ArchivePaneState.Open.Ready -> o.warning?.let { AppSnackbar.show(context, "${loc.archive.name}: $it", long = true) }
                ArchivePaneState.Open.Cancelled -> { loading = false; paneState.closeArchive(loc.archive); return@LaunchedEffect }
                is ArchivePaneState.Open.Failed -> { entries = emptyList(); entriesFor = loc.id; loadError = o.message; loading = false; return@LaunchedEffect }
            }
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                Storage.backend(loc).listChildren(context, loc)
                    // Dir-pick mode: folders only. File-pick: folders + matching files. Else: all.
                    .filter { if (pickDirMode) it.isDir else !pickMode || it.isDir || matchesPickExt(it) }
                    // Dotfiles are noise in a storage root — a toggle rather than a permanent filter.
                    .filter { paneState.showHidden || !it.name.startsWith(".") }
                    .sortedWith(comparatorFor(paneState.sortBy, paneState.sortDesc, paneState.folderSortBy, paneState.folderSortDesc))
            }
        }
        entries = result.getOrNull() ?: emptyList()
        entriesFor = loc.id
        loadError = if (result.isFailure) "Couldn't read this folder" else null
        loading = false
        if (reset) {
            highlightId = null
            // Where the folder we're leaving was scrolled to, so Aurora's outgoing list can slide away
            // from there on its own copy while the hoisted states reset for the new folder.
            exitScroll = PaneScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
            runCatching { listState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        }
        highlightId?.let { id ->
            val idx = entries.indexOfFirst { it.id == id }
            if (idx >= 0) {
                runCatching { listState.animateScrollToItem(idx) }
                runCatching { gridState.animateScrollToItem(idx) }
            }
        }
    }

    // Back in the foreground (from another app, or from BFE's own Unpack / viewer screens): re-list the
    // folder in place — same scroll position, no spinner — so files added, changed or removed meanwhile
    // show up without scrolling to the top to pull-refresh. A folder that vanished meanwhile falls back
    // to its nearest existing parent. Archive panes list a fixed index, so they're left alone.
    val paneLifecycle = LocalLifecycleOwner.current
    DisposableEffect(paneLifecycle, paneState) {
        var wasStopped = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_START -> if (wasStopped) {
                    wasStopped = false
                    if (!paneState.isAlt && !File(paneState.path).isDirectory) {
                        var dir: File? = File(paneState.path).parentFile
                        while (dir != null && !dir.isDirectory) dir = dir.parentFile
                        paneState.path = dir?.absolutePath?.takeIf { it.startsWith(paneState.rootPath) } ?: paneState.rootPath
                    } else if (!paneState.isArchive) {
                        paneState.requestReload()
                    }
                }
                else -> Unit
            }
        }
        paneLifecycle.lifecycle.addObserver(obs)
        onDispose { paneLifecycle.lifecycle.removeObserver(obs) }
    }

    // The photo viewer deleted (or re-saved) files. It's a translucent window, so this pane is never
    // stopped behind it and the refresh above doesn't run: re-list when the viewer reports a change.
    val seenMediaTick = remember { mutableIntStateOf(MediaChanges.tick) }
    LaunchedEffect(MediaChanges.tick) {
        if (MediaChanges.tick != seenMediaTick.intValue) {
            seenMediaTick.intValue = MediaChanges.tick
            if (!paneState.isArchive) paneState.requestReload()
        }
    }

    // Pull-to-refresh → re-list, keeping scroll.
    if (pullState.isRefreshing) {
        LaunchedEffect(true) { paneState.requestReload(); pullState.endRefresh() }
    }

    // One-shot: if the saved File path no longer exists, fall back to a sensible start dir.
    LaunchedEffect(Unit) {
        if (!paneState.isAlt && !File(paneState.path).isDirectory) {
            paneState.path = rootDir.absolutePath
            paneState.rootPath = rootDir.absolutePath
        }
    }

    // File-mode navigation into a directory (the effect re-lists on the paneState.path change).
    fun navFileDir(dir: File) { paneState.path = dir.absolutePath }

    // Jump to a File drive's root (exits any SAF browse); pins the back floor to that root.
    fun openDrive(dir: File) {
        paneState.exitAlt()
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
            is Loc.SafLoc, is Loc.RootLoc, is Loc.ArchiveLoc -> paneState.openAltInto(loc)
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
        if (paneState.isAlt) {
            paneState.altUp()
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

    // A shareable content:// Uri for [loc]: a FileProvider uri for File, the SAF doc Uri directly, or —
    // for a root file (unreadable by other apps) — a copy staged into our cache. Blocking for root;
    // callers run it on IO.
    fun viewUri(loc: Loc): Uri? = when (loc) {
        is Loc.FileLoc -> runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", loc.file)
        }.getOrNull()
        is Loc.SafLoc -> loc.docUri
        is Loc.RootLoc -> runCatching {
            val dir = File(context.cacheDir, "rootshare").apply { mkdirs() }
            val staged = File(dir, loc.name)
            RootBackend.openInputStream(context, loc)?.use { i -> staged.outputStream().use { o -> i.copyTo(o) } }
                ?: return@runCatching null
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", staged)
        }.getOrNull()
        // An entry inside an archive has no Uri until it's extracted (withArchiveCopies below).
        is Loc.ArchiveLoc -> null
    }

    // Resolve the share Uri off the main thread (root staging copies), then hand it to [use].
    fun withViewUri(loc: Loc, failMsg: String, use: (Uri) -> Unit) {
        scope.launch {
            val uri = withContext(Dispatchers.IO) { viewUri(loc) }
            if (uri == null) { AppSnackbar.show(context, failMsg); return@launch }
            runCatching { use(uri) }.onFailure { AppSnackbar.show(context, failMsg) }
        }
    }

    // ── Archive browsing (read-only; ArchivePane.kt + storage/ArchiveBackend.kt) ──
    // Open the archive [file] as a folder in this pane (on top of the current archive if nested).
    fun browseArchive(file: File) {
        showMenuFor = null
        paneState.openArchive(ArchiveBackend.rootLoc(file))
    }

    // Pull the files among [locs] out of their archive into BFE's cache — progress and Cancel on the
    // strip — then hand the copies to [then]. Copies already in the cache are used straight away.
    fun withArchiveCopies(locs: List<Loc.ArchiveLoc>, then: (List<File>) -> Unit) {
        val files = locs.filter { !it.isDir }
        if (files.isEmpty()) return
        val cached = files.map { archivePane.cachedCopy(it) }
        if (cached.all { it != null }) { then(cached.filterNotNull()); return }
        operationJob = scope.launch {
            operationProgress = 0f
            operationDeterminate = true
            operationLabel = if (files.size == 1) "Extracting ${files[0].name}…" else "Extracting ${files.size} files…"
            isOperationRunning = true
            val out = try {
                archivePane.extractForView(files) { pct -> operationProgress = pct / 100f }
            } finally {
                isOperationRunning = false
                operationDeterminate = false
                operationJob = null
            }
            if (!out.isNullOrEmpty()) then(out)
        }
    }

    // A tap on a file inside an archive: extract it to the cache, then BFE's Open with sheet for the copy.
    fun openArchiveEntry(loc: Loc.ArchiveLoc) = withArchiveCopies(listOf(loc)) { files ->
        val copy = Loc.FileLoc(files.first())
        withViewUri(copy, "Couldn't open ${loc.name}") { uri ->
            openWithReq = OpenWithRequest(copy, uri, guessMime(copy.name, context.contentResolver.getType(uri)), fromArchive = loc)
        }
    }

    // An archive inside the archive: pull it out, then browse the copy.
    fun browseArchiveEntry(loc: Loc.ArchiveLoc) = withArchiveCopies(listOf(loc)) { files -> browseArchive(files.first()) }

    // "Open with": BFE's own sheet (OpenWithSheet) — built-in actions for this file plus every app
    // that can VIEW it (⋮ → Open with; a tap lands here when BFE has no viewer of its own). The Uri is
    // resolved first (root files are staged to cache), then the sheet is shown by the block near the
    // other dialogs below.
    fun openWith(loc: Loc) {
        if (loc is Loc.ArchiveLoc) { openArchiveEntry(loc); return }
        withViewUri(loc, "Couldn't open ${loc.name}") { uri ->
            openWithReq = OpenWithRequest(loc, uri, guessMime(loc.name, context.contentResolver.getType(uri)))
        }
    }

    // "View" / "Play": BFE's photo viewer or player. A file of this folder pages through the folder's
    // files of the same kind in the order the pane lists them; a cache copy (archive entry, root file)
    // opens on its own through the Uri the sheet already has.
    fun openMedia(loc: Loc, type: MediaType, uri: Uri, single: Boolean) {
        scope.launch {
            val (items, index) = withContext(Dispatchers.IO) {
                val inListing = !single && (loc is Loc.FileLoc || loc is Loc.SafLoc) && entries.any { it.id == loc.id }
                if (!inListing) {
                    listOf(MediaRef(uri = uri.toString(), name = loc.name, path = (loc as? Loc.FileLoc)?.file?.absolutePath, size = loc.size)) to 0
                } else {
                    val (same, i) = MediaSiblings.pick(entries.filter { !it.isDir }, type, loc.id, { it.name }, { it.id })
                    same.map { l ->
                        when (l) {
                            is Loc.FileLoc -> MediaRef.of(l.file)
                            is Loc.SafLoc -> MediaRef(uri = l.docUri.toString(), name = l.name, size = l.size, modified = l.lastModified)
                            else -> MediaRef(uri = uri.toString(), name = l.name)
                        }
                    } to i
                }
            }
            MediaLaunch.open(context, type, items, index)
        }
    }

    // Share a file to another app via ACTION_SEND.
    fun shareFile(loc: Loc) {
        if (loc is Loc.ArchiveLoc) { withArchiveCopies(listOf(loc)) { shareFile(Loc.FileLoc(it.first())) }; return }
        withViewUri(loc, "Couldn't share ${loc.name}") { uri ->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share ${loc.name}"))
        }
    }

    // Share several files at once via ACTION_SEND_MULTIPLE (used by the selection action bar).
    fun shareFiles(locs: List<Loc>) {
        val files = locs.filter { !it.isDir }
        if (files.isEmpty()) return
        // Entries inside an archive are shared as extracted copies.
        val archived = files.filterIsInstance<Loc.ArchiveLoc>()
        if (archived.isNotEmpty()) {
            withArchiveCopies(archived) { copies -> shareFiles(files.filter { it !is Loc.ArchiveLoc } + copies.map { Loc.FileLoc(it) }) }
            return
        }
        if (files.size == 1) { shareFile(files.first()); return }
        scope.launch {
            val uris = withContext(Dispatchers.IO) { ArrayList(files.mapNotNull { viewUri(it) }) }
            runCatching {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share ${files.size} files"))
            }.onFailure { AppSnackbar.show(context, "Couldn't share the selection") }
        }
    }

    // Install an .apk via the system package installer (needs REQUEST_INSTALL_PACKAGES).
    fun installApk(loc: Loc) = withViewUri(loc, "Couldn't start the installer for ${loc.name}") { uri ->
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // openOnTap's routing for a real file, or for the cache copy of an archive entry ([fromArchive]).
    fun openBuiltInOrSheet(loc: Loc, fromArchive: Loc.ArchiveLoc?) {
        val f = (loc as? Loc.FileLoc)?.file
        if (loc.name.lowercase().endsWith(".apk")) { installApk(loc); return }
        scope.launch {
            // Archives by EXTENSION only: .docx / .odt / .epub / .apk are zips too and must not open as
            // folders. Inno / GOG installers are unpack-only, so they get the sheet (Unpack / Install).
            val browsable = f != null && withContext(Dispatchers.IO) {
                com.the412banner.bfe.unpack.SevenZip.isSupported(f) && !com.the412banner.bfe.unpack.SevenZip.isInnoSetup(f)
            }
            if (browsable) { browseArchive(f!!); return@launch }
            withViewUri(loc, "Couldn't open ${loc.name}") { uri ->
                val mime = guessMime(loc.name, context.contentResolver.getType(uri))
                val media = MediaTypes.typeOf(loc.name, mime)
                when {
                    com.the412banner.bfe.HtmlViewerActivity.isHtmlName(loc.name) ->
                        context.startActivity(com.the412banner.bfe.HtmlViewerActivity.intent(context, f?.absolutePath, uri, loc.name))
                    media != null -> openMedia(loc, media, uri, single = fromArchive != null)
                    // PDF, Office / OpenDocument, EPUB, Markdown, RTF, CSV and text. Old binary .doc / .xls /
                    // .ppt can't be shown, so they get the sheet's apps instead of an "unsupported" page.
                    com.the412banner.bfe.DocumentViewer.isDocumentName(loc.name, mime) &&
                        !com.the412banner.bfe.DocumentViewer.isLegacyOfficeName(loc.name, mime) ->
                        com.the412banner.bfe.DocumentViewer.open(context, f?.absolutePath, uri, loc.name, mime)
                    else -> openWithReq = OpenWithRequest(loc, uri, mime, fromArchive = fromArchive)
                }
            }
        }
    }

    // A plain tap on a file: BFE opens it itself when it can — a web page in the HTML viewer, photos /
    // videos / music in the viewer or player, documents and text in the document viewer, an archive or
    // disc image browsed like a folder, an APK in the installer. Anything else gets the Open-with sheet,
    // which ⋮ → Open with shows for every file.
    fun openOnTap(loc: Loc) {
        if (loc is Loc.ArchiveLoc) {
            withArchiveCopies(listOf(loc)) { files -> openBuiltInOrSheet(Loc.FileLoc(files.first()), fromArchive = loc) }
            return
        }
        openBuiltInOrSheet(loc, fromArchive = null)
    }

    // Extraction into a SAF/root pane: the native engines need a real app-owned path, so we extract
    // into a temp dir under our cache and, once the job reports DONE, stream-copy the result into the
    // target location (root writes get chown'd to the app uid by RootBackend), then delete the temp.
    var pendingExtractCopy by remember { mutableStateOf<Pair<File, Loc>?>(null) }

    // In dual-pane mode, extraction defaults into the OTHER pane: directly when it's a File dir, via a
    // temp dir + post-copy when it's SAF/root. Single-pane keeps the engine's sibling-folder default.
    fun extractDest(): File? {
        if (!dualPane) return null
        return when (val other = otherPaneDir()) {
            null -> null
            is Loc.FileLoc -> other.file
            else -> File(context.cacheDir, "extract-${System.currentTimeMillis()}").also {
                it.mkdirs()
                pendingExtractCopy = it to other
            }
        }
    }

    val unpackState by com.the412banner.bfe.unpack.UnpackManager.state.collectAsState()
    LaunchedEffect(unpackState.phase, pendingExtractCopy) {
        val pending = pendingExtractCopy ?: return@LaunchedEffect
        val (tmp, target) = pending
        when (unpackState.phase) {
            com.the412banner.bfe.unpack.UnpackPhase.DONE -> {
                if (!unpackState.destPath.startsWith(tmp.absolutePath)) return@LaunchedEffect
                pendingExtractCopy = null
                isOperationRunning = true
                operationDeterminate = false
                operationLabel = "Copying extracted files into ${target.name}…"
                val ok = withContext(Dispatchers.IO) {
                    val extracted = File(unpackState.destPath)
                    val r = StorageTransfer.copyInto(context, Loc.FileLoc(extracted), target, extracted.name)
                    tmp.deleteRecursively()
                    r
                }
                isOperationRunning = false
                onRequestOtherReload()
                AppSnackbar.show(context, if (ok) "Extracted into ${target.name}" else "Some extracted files couldn't be copied", long = true)
            }
            com.the412banner.bfe.unpack.UnpackPhase.ERROR, com.the412banner.bfe.unpack.UnpackPhase.CANCELLED -> {
                if (unpackState.destPath.startsWith(tmp.absolutePath)) {
                    pendingExtractCopy = null
                    withContext(Dispatchers.IO) { tmp.deleteRecursively() }
                }
            }
            else -> Unit
        }
    }

    // Archive extraction is File-only (the native engines need a real path); guard SAF entries.
    fun launchFastExtract(loc: Loc) {
        showMenuFor = null
        val file = (loc as? Loc.FileLoc)?.file ?: run {
            AppSnackbar.show(context, "Extraction from app storage isn't supported yet"); return
        }
        val dest = extractDest()
        scope.launch {
            when (val o = com.the412banner.bfe.unpack.FastExtract.start(context, file, dest)) {
                is com.the412banner.bfe.unpack.FastExtract.Outcome.Started -> {
                    AppSnackbar.show(context, "Unpacking ${o.name}…")
                    if (dualPane) onRequestOtherReload()
                }
                com.the412banner.bfe.unpack.FastExtract.Outcome.Busy ->
                    AppSnackbar.show(context, "Another unpack is already in progress")
                is com.the412banner.bfe.unpack.FastExtract.Outcome.NotArchive ->
                    AppSnackbar.show(context, "Not a recognized archive — nothing to unpack")
                is com.the412banner.bfe.unpack.FastExtract.Outcome.OpenScreen -> {
                    o.toast?.let { AppSnackbar.show(context, it) }
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

    // ── Compress (create archive) ──
    // The items the open Compress dialog is for (empty = no dialog).
    var compressTargets by remember { mutableStateOf<List<Loc>>(emptyList()) }
    // A running pack job this pane started: (jobId, temp archive or null when written in place,
    // destination dir, staged-input temp dir or null, refresh-other-pane?). On DONE the temp archive
    // is stream-copied into a SAF/root destination (root writes get chown'd by RootBackend), then
    // the temps are deleted and the destination pane re-listed — mirroring extraction-into-SAF/root.
    var pendingPack by remember { mutableStateOf<PendingPack?>(null) }

    val packState by com.the412banner.bfe.pack.PackManager.state.collectAsState()
    LaunchedEffect(packState.phase, packState.jobId, pendingPack) {
        val p = pendingPack ?: return@LaunchedEffect
        if (packState.jobId != p.jobId) return@LaunchedEffect
        when (packState.phase) {
            com.the412banner.bfe.pack.PackPhase.DONE -> {
                pendingPack = null
                var ok = true
                if (p.tmpArchive != null) {
                    isOperationRunning = true
                    operationDeterminate = false
                    operationLabel = "Copying ${p.tmpArchive.name} into ${p.dest.name}…"
                    ok = withContext(Dispatchers.IO) {
                        val name = uniqueName(p.dest, p.tmpArchive.name)
                        val r = StorageTransfer.copyInto(context, Loc.FileLoc(p.tmpArchive), p.dest, name)
                        p.tmpArchive.parentFile?.deleteRecursively()
                        r
                    }
                    isOperationRunning = false
                }
                withContext(Dispatchers.IO) { p.stagedInputs?.deleteRecursively() }
                if (p.toOther) onRequestOtherReload() else paneState.requestReload()
                AppSnackbar.show(context, if (ok) "Created ${packState.archiveName}" else "Archive was built but couldn't be copied into ${p.dest.name}", long = true)
            }
            com.the412banner.bfe.pack.PackPhase.ERROR, com.the412banner.bfe.pack.PackPhase.CANCELLED -> {
                pendingPack = null
                withContext(Dispatchers.IO) {
                    p.tmpArchive?.parentFile?.deleteRecursively()
                    p.stagedInputs?.deleteRecursively()
                }
                if (packState.phase == com.the412banner.bfe.pack.PackPhase.ERROR) {
                    AppSnackbar.show(context, "Compression failed: ${packState.errorTail?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: "unknown error"}", long = true)
                }
            }
            else -> Unit
        }
    }

    // Kick off a pack job for [sources] per the dialog's [req]. The engines need real paths: File
    // inputs are handed over directly; SAF/root inputs are first staged into a cache temp dir (a
    // stream copy, deleted when the job ends). A SAF/root DESTINATION gets the archive built in a
    // cache temp file and copied over on DONE (see the effect above).
    fun startCompress(req: CompressRequest, sources: List<Loc>) {
        compressTargets = emptyList()
        if (sources.isEmpty()) return
        com.the412banner.bfe.pack.PackService.busyReason()?.let {
            AppSnackbar.show(context, it); return
        }
        val dest: Loc = if (req.toOtherPane) (otherPaneDir() ?: currentLoc) else currentLoc
        scope.launch {
            var staged: File? = null
            isOperationRunning = true
            operationDeterminate = false
            operationLabel = "Preparing ${sources.size} item${if (sources.size == 1) "" else "s"}…"
            val prepared: Result<List<File>> = withContext(Dispatchers.IO) {
                runCatching {
                    val direct = sources.mapNotNull { (it as? Loc.FileLoc)?.file }
                    if (direct.size == sources.size) return@runCatching direct
                    // Mixed/SAF/root selection → stage everything so the archive layout matches.
                    val dir = File(context.cacheDir, "pack-src-${System.currentTimeMillis()}").apply { mkdirs() }
                    staged = dir
                    val stagedDir = Loc.FileLoc(dir)
                    for (s in sources) {
                        if (!StorageTransfer.copyInto(context, s, stagedDir, s.name)) throw java.io.IOException("Couldn't read ${s.name}")
                    }
                    dir.listFiles()?.toList() ?: emptyList()
                }.onFailure { staged?.deleteRecursively() }
            }
            val preparedFiles = prepared.getOrNull()
            if (preparedFiles.isNullOrEmpty()) {
                isOperationRunning = false
                AppSnackbar.show(context, prepared.exceptionOrNull()?.message ?: "Couldn't prepare the selection", long = true)
                return@launch
            }

            // Where the engine writes: straight into a File destination (uniquified so an existing
            // archive is never clobbered/updated), or a cache temp for SAF/root.
            val (outFile, tmpArchive) = withContext(Dispatchers.IO) {
                when (dest) {
                    is Loc.FileLoc -> File(dest.file, uniqueName(dest, req.fileName)) to null
                    else -> {
                        val t = File(File(context.cacheDir, "pack-out-${System.currentTimeMillis()}").apply { mkdirs() }, req.fileName)
                        t to t
                    }
                }
            }
            // .wcp: build profile.json now (needs the pack's file list → IO).
            val profileJson: String? = req.wcp?.let { m ->
                withContext(Dispatchers.IO) {
                    val planned = com.the412banner.bfe.pack.TarZstPack.planInputs(preparedFiles, wcpContents = true)
                    val rel = com.the412banner.bfe.pack.TarZstPack.listRelativeFiles(planned)
                    com.the412banner.bfe.pack.WcpProfile(
                        type = m.type, versionName = m.versionName, versionCode = m.versionCode,
                        description = m.description,
                        files = com.the412banner.bfe.pack.WcpProfile.deriveFiles(m.type, rel),
                        wineBinPath = m.binPath, wineLibPath = m.libPath, winePrefixPack = m.prefixPack,
                    ).toJson()
                }
            }
            isOperationRunning = false
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val jobId = com.the412banner.bfe.pack.PackService.start(
                context, req.format, outFile, preparedFiles, req.level, req.password,
                mmt = (cores / 2).coerceAtLeast(1), profileJson = profileJson,
            )
            pendingPack = PendingPack(jobId, tmpArchive, dest, staged, req.toOtherPane)
            selectionMode = false
            selectedIds = emptySet()
            AppSnackbar.show(context, "Compressing to ${req.fileName}…")
        }
    }

    fun performDelete(loc: Loc) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Deleting..."
            val ok = withContext(Dispatchers.IO) { Storage.backend(loc).delete(context, loc) }
            isOperationRunning = false
            paneState.requestReload()
            if (!ok) AppSnackbar.show(context, "Delete failed")
        }
    }

    /** Waits for the user to answer the conflict dialog for [loc]; null if they cancelled it. */
    suspend fun askConflict(loc: Loc): ConflictChoice? {
        pendingConflict = loc
        conflictChoice = null
        while (pendingConflict != null && conflictChoice == null) kotlinx.coroutines.delay(50)
        return conflictChoice
    }

    // Extract archive entries into [dstDir]: Extract to…, Copy → from an archive pane, or pasting
    // entries copied inside an archive. Same conflict choices as a paste; 7zz does the extraction.
    fun extractFromArchive(sources: List<Loc>, dstDir: Loc, onDone: () -> Unit = {}) {
        val items = sources.filterIsInstance<Loc.ArchiveLoc>()
        if (items.isEmpty()) return
        if (dstDir is Loc.ArchiveLoc) { AppSnackbar.show(context, ArchiveBackend.READ_ONLY_MESSAGE); return }
        operationJob = scope.launch {
            operationProgress = 0f
            operationDeterminate = false
            operationLabel = "Extracting…"
            isOperationRunning = true
            val dstBackend = Storage.backend(dstDir)
            var applyToAll: ConflictChoice? = null
            var skipped = 0
            val plan = ArrayList<ArchivePaneState.Item>()
            for (src in items) {
                var target = src.name
                var replace = false
                val exists = withContext(Dispatchers.IO) { dstBackend.childNamed(context, dstDir, target) != null }
                if (exists) {
                    val choice = applyToAll ?: askConflict(src)?.also { if (conflictApplyToAll) applyToAll = it }
                        ?: run { skipped++; null } ?: continue
                    when (choice) {
                        ConflictChoice.OVERWRITE, ConflictChoice.MERGE -> replace = true
                        ConflictChoice.KEEP_BOTH -> target = withContext(Dispatchers.IO) { uniqueName(dstDir, src.name) }
                        ConflictChoice.SKIP -> { skipped++; continue }
                    }
                }
                plan.add(ArchivePaneState.Item(src, target, replace))
            }
            val msg = try {
                if (plan.isEmpty()) null else archivePane.extractTo(plan, dstDir) { label, fraction ->
                    operationLabel = label
                    operationDeterminate = fraction != null
                    if (fraction != null) operationProgress = fraction
                }
            } finally {
                isOperationRunning = false
                operationDeterminate = false
                operationJob = null
            }
            selectionMode = false
            selectedIds = emptySet()
            paneState.requestReload()
            onDone()
            val text = listOfNotNull(msg, if (skipped > 0) "$skipped skipped" else null).joinToString(", ")
            if (text.isNotEmpty()) AppSnackbar.show(context, text, long = true)
        }
    }

    // Copy/move [sources] into [dstDir]. File→File keeps the fast FileUtils path (unchanged); anything
    // touching a SAF side goes through StorageTransfer (recursive stream copy). [onDone] refreshes the
    // OTHER pane after a cross-pane op; the source pane always refreshes (a move removes items from it).
    fun performCopyMove(sources: List<Loc>, dstDir: Loc, cut: Boolean, onDone: () -> Unit = {}) {
        if (sources.isEmpty()) return
        // Archives are read-only: nothing goes into one, and copying out of one is an extraction.
        if (dstDir is Loc.ArchiveLoc) { AppSnackbar.show(context, ArchiveBackend.READ_ONLY_MESSAGE); return }
        if (sources.any { it is Loc.ArchiveLoc }) { extractFromArchive(sources, dstDir, onDone); return }
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
            if (message != null) AppSnackbar.show(context, message)
        }
    }

    // Within-pane clipboard paste: copy/move the clipboard into the current directory.
    fun performPaste() {
        if (currentLoc is Loc.ArchiveLoc) { AppSnackbar.show(context, ArchiveBackend.READ_ONLY_MESSAGE); return }
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
            if (!ok) AppSnackbar.show(context, "Couldn't rename to \"$newName\"")
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
            if (!ok) AppSnackbar.show(context, "Could not create \"$name\"")
        }
    }


    // ── Convert to MP4 (bundled ffmpeg) ──
    var convertTargets by remember { mutableStateOf<List<Loc>>(emptyList()) }
    var pendingConvert by remember { mutableStateOf<PendingConvert?>(null) }

    fun startConvert(choice: ConvertChoice, sources: List<Loc>) {
        convertTargets = emptyList()
        if (sources.isEmpty()) return
        com.the412banner.bfe.video.ConvertService.busyReason()?.let { AppSnackbar.show(context, it); return }
        val dest: Loc = if (choice.toOtherPane) (otherPaneDir() ?: currentLoc) else currentLoc
        scope.launch {
            var staged: File? = null
            isOperationRunning = true; operationDeterminate = false
            operationLabel = "Preparing ${sources.size} video${if (sources.size == 1) "" else "s"}…"
            // ffmpeg needs real paths: File inputs go straight in; SAF/root inputs are staged first.
            val inputs: List<File>? = withContext(Dispatchers.IO) {
                runCatching {
                    sources.map { src ->
                        (src as? Loc.FileLoc)?.file ?: run {
                            val dir = staged ?: File(context.cacheDir, "convert-src-${System.currentTimeMillis()}").apply { mkdirs() }.also { staged = it }
                            if (!StorageTransfer.copyInto(context, src, Loc.FileLoc(dir), src.name)) throw java.io.IOException("Couldn't read ${src.name}")
                            File(dir, src.name)
                        }
                    }
                }.onFailure { staged?.deleteRecursively() }.getOrNull()
            }
            if (inputs == null) { isOperationRunning = false; AppSnackbar.show(context, "Couldn't prepare the selection", long = true); return@launch }
            val (outDir, tmpDir) = withContext(Dispatchers.IO) {
                when (dest) {
                    is Loc.FileLoc -> dest.file to null
                    else -> File(context.cacheDir, "convert-out-${System.currentTimeMillis()}").apply { mkdirs() }.let { it to it }
                }
            }
            // Never clobber: uniquify each output name against the real destination.
            val names = withContext(Dispatchers.IO) {
                val used = HashSet<String>()
                choice.outputNames.map { n ->
                    var u = if (dest is Loc.FileLoc) uniqueName(dest, n) else n
                    var i = 1
                    while (!used.add(u)) { u = n.substringBeforeLast('.') + " (${i++}).mp4" }
                    u
                }
            }
            isOperationRunning = false
            val jobId = com.the412banner.bfe.video.ConvertService.start(
                context,
                com.the412banner.bfe.video.ConvertRequest(inputs.map { it.absolutePath }, names, outDir.absolutePath, choice.quality, choice.resolution, choice.keepAudio),
            )
            pendingConvert = PendingConvert(jobId, tmpDir, dest, staged, choice.toOtherPane)
            selectionMode = false; selectedIds = emptySet()
            AppSnackbar.show(context, "Converting ${inputs.size} video${if (inputs.size == 1) "" else "s"} — see the pill for progress")
        }
    }

    val convertState by com.the412banner.bfe.video.ConvertManager.state.collectAsState()
    LaunchedEffect(convertState.phase, convertState.jobId, pendingConvert) {
        val p = pendingConvert ?: return@LaunchedEffect
        if (convertState.jobId != p.jobId) return@LaunchedEffect
        when (convertState.phase) {
            com.the412banner.bfe.video.ConvertPhase.DONE, com.the412banner.bfe.video.ConvertPhase.ERROR, com.the412banner.bfe.video.ConvertPhase.CANCELLED -> {
                pendingConvert = null
                var copyFailed = 0
                if (p.tmpDir != null && convertState.outputs.isNotEmpty()) {
                    isOperationRunning = true; operationDeterminate = false
                    operationLabel = "Copying converted videos into ${p.dest.name}…"
                    copyFailed = withContext(Dispatchers.IO) {
                        var f = 0
                        for (o in convertState.outputs) {
                            val file = File(o)
                            if (!StorageTransfer.copyInto(context, Loc.FileLoc(file), p.dest, uniqueName(p.dest, file.name))) f++
                        }
                        f
                    }
                    isOperationRunning = false
                }
                withContext(Dispatchers.IO) { p.tmpDir?.deleteRecursively(); p.stagedInputs?.deleteRecursively() }
                if (p.toOther) onRequestOtherReload() else paneState.requestReload()
                val msg = when (convertState.phase) {
                    com.the412banner.bfe.video.ConvertPhase.DONE -> "Converted ${convertState.done} video${if (convertState.done == 1) "" else "s"}" +
                        (if (convertState.failed > 0) ", ${convertState.failed} failed" else "") + (if (copyFailed > 0) ", $copyFailed couldn't be copied" else "")
                    com.the412banner.bfe.video.ConvertPhase.CANCELLED -> "Conversion cancelled"
                    else -> "Conversion failed: ${convertState.errorTail ?: "ffmpeg error"}"
                }
                AppSnackbar.show(context, msg, long = true)
            }
            else -> Unit
        }
    }

    // ── APK tools: clone / edit / sign ──
    var apkEditor by remember { mutableStateOf<ApkEditorTarget?>(null) }
    var apkSignTarget by remember { mutableStateOf<Pair<File, Loc>?>(null) }   // (apk, destination dir)
    var showKeyManager by remember { mutableStateOf(false) }
    var showInstalledPicker by remember { mutableStateOf(false) }
    var pendingApk by remember { mutableStateOf<PendingApk?>(null) }
    var apkResult by remember { mutableStateOf<Pair<com.the412banner.bfe.apk.ApkJobReport, Loc>?>(null) }
    paneState.onCloneInstalledApp = { showInstalledPicker = true }
    paneState.onSigningKeys = {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { com.the412banner.bfe.apk.SigningKeys.builtIn(context) } }
            showKeyManager = true
        }
    }

    // A SAF/root .apk source is staged into the cache first (ARSCLib/apksig need a real path).
    suspend fun realApkFile(loc: Loc): Pair<File, File?>? = withContext(Dispatchers.IO) {
        when (loc) {
            is Loc.FileLoc -> loc.file to null
            else -> {
                val dir = File(context.cacheDir, "apk-src-${System.currentTimeMillis()}").apply { mkdirs() }
                if (StorageTransfer.copyInto(context, loc, Loc.FileLoc(dir), loc.name)) File(dir, loc.name) to dir else null
            }
        }
    }

    // Parse an APK (+ optional splits) and open the editor for it; dest = where the output lands.
    fun openApkEditor(base: File, splits: List<File>, kind: com.the412banner.bfe.apk.ApkJobKind, dest: Loc, staged: File?) {
        scope.launch {
            isOperationRunning = true; operationDeterminate = false; operationLabel = "Reading ${base.name}…"
            val meta = withContext(Dispatchers.IO) {
                runCatching {
                    com.the412banner.bfe.apk.SigningKeys.builtIn(context)   // mint the test key off the UI thread
                    com.the412banner.bfe.apk.ApkRewriter.inspect(base, splits)
                }
            }
            isOperationRunning = false
            meta.onSuccess { apkEditor = ApkEditorTarget(it, kind, base, splits, dest, staged) }
                .onFailure { AppSnackbar.show(context, "Couldn't read ${base.name}: ${it.message}", long = true) }
        }
    }

    fun openApkEditorFor(loc: Loc, kind: com.the412banner.bfe.apk.ApkJobKind) {
        showMenuFor = null
        scope.launch {
            val (file, staged) = realApkFile(loc) ?: run { AppSnackbar.show(context, "Couldn't read ${loc.name}"); return@launch }
            openApkEditor(file, emptyList(), kind, currentLoc, staged)
        }
    }

    fun openInstalledClone(app: com.the412banner.bfe.storage.InstalledApp) {
        showInstalledPicker = false
        val info = runCatching { context.packageManager.getApplicationInfo(app.packageName, 0) }.getOrNull() ?: run {
            AppSnackbar.show(context, "Can't read ${app.label}'s APK"); return
        }
        val base = File(info.sourceDir)
        val splits = info.splitSourceDirs?.map { File(it) }?.filter { it.isFile } ?: emptyList()
        // An archive pane is read-only: the clone lands in the folder the archive sits in.
        val dest = if (currentLoc is Loc.ArchiveLoc) Loc.FileLoc(File(paneState.path)) else currentLoc
        openApkEditor(base, splits, com.the412banner.bfe.apk.ApkJobKind.CLONE, dest, null)
    }

    fun openSign(loc: Loc) {
        showMenuFor = null
        scope.launch {
            val (file, staged) = realApkFile(loc) ?: run { AppSnackbar.show(context, "Couldn't read ${loc.name}"); return@launch }
            withContext(Dispatchers.IO) { runCatching { com.the412banner.bfe.apk.SigningKeys.builtIn(context) } }
            apkSignTarget = file to currentLoc
            if (staged != null) pendingApk = PendingApk(0L, null, currentLoc, staged)   // remembered for cleanup
        }
    }

    // Where the job writes: in place for a File destination (name uniquified), else a cache temp
    // that's copied into the SAF/root folder on DONE.
    suspend fun apkOutput(dest: Loc, name: String): Pair<File, File?> = withContext(Dispatchers.IO) {
        when (dest) {
            is Loc.FileLoc -> File(dest.file, uniqueName(dest, name)) to null
            else -> File(File(context.cacheDir, "apk-out-${System.currentTimeMillis()}").apply { mkdirs() }, name).let { it to it }
        }
    }

    fun startApkJob(kind: com.the412banner.bfe.apk.ApkJobKind, base: File, splits: List<File>, edits: com.the412banner.bfe.apk.ApkEdits?, outName: String, dest: Loc, key: com.the412banner.bfe.apk.KeyRef, schemes: com.the412banner.bfe.apk.SignSchemes, install: Boolean, staged: File?) {
        com.the412banner.bfe.apk.ApkJobService.busyReason()?.let { AppSnackbar.show(context, it); return }
        scope.launch {
            val (out, tmp) = apkOutput(dest, outName)
            val jobId = com.the412banner.bfe.apk.ApkJobService.start(
                context,
                com.the412banner.bfe.apk.ApkJobRequest(kind, base.absolutePath, splits.map { it.absolutePath }, out.absolutePath, edits, key, schemes, install),
            )
            pendingApk = PendingApk(jobId, tmp, dest, staged)
            AppSnackbar.show(context, "${kind.name.lowercase().replaceFirstChar { it.uppercase() }} job started — see the pill for progress")
        }
    }

    val apkState by com.the412banner.bfe.apk.ApkJobManager.state.collectAsState()
    LaunchedEffect(apkState.stage, apkState.jobId, pendingApk) {
        val p = pendingApk ?: return@LaunchedEffect
        if (p.jobId == 0L || apkState.jobId != p.jobId) return@LaunchedEffect
        when (apkState.stage) {
            com.the412banner.bfe.apk.ApkJobStage.DONE -> {
                pendingApk = null
                val report = apkState.report ?: return@LaunchedEffect
                var resultLoc: Loc? = null
                if (p.tmpOut != null) {
                    isOperationRunning = true; operationDeterminate = false
                    operationLabel = "Copying ${p.tmpOut.name} into ${p.dest.name}…"
                    resultLoc = withContext(Dispatchers.IO) {
                        val name = uniqueName(p.dest, p.tmpOut.name)
                        val ok = StorageTransfer.copyInto(context, Loc.FileLoc(p.tmpOut), p.dest, name)
                        val loc = if (ok) Storage.backend(p.dest).childNamed(context, p.dest, name) else null
                        p.tmpOut.parentFile?.deleteRecursively()
                        loc
                    }
                    isOperationRunning = false
                    if (resultLoc == null) AppSnackbar.show(context, "APK was built but couldn't be copied into ${p.dest.name}", long = true)
                } else resultLoc = Loc.FileLoc(File(report.outputPath))
                withContext(Dispatchers.IO) { p.stagedSource?.deleteRecursively() }
                paneState.requestReload()
                resultLoc?.let { loc ->
                    apkResult = report to loc
                    if (apkState.installAfter) installApk(loc)
                }
            }
            com.the412banner.bfe.apk.ApkJobStage.ERROR, com.the412banner.bfe.apk.ApkJobStage.CANCELLED -> {
                pendingApk = null
                withContext(Dispatchers.IO) { p.tmpOut?.parentFile?.deleteRecursively(); p.stagedSource?.deleteRecursively() }
                if (apkState.stage == com.the412banner.bfe.apk.ApkJobStage.ERROR) AppSnackbar.show(context, "APK job failed: ${apkState.error ?: "unknown error"}", long = true)
            }
            else -> Unit
        }
    }

    // Create an EMPTY file in [parent] via the backend: File → the path is opened+truncated once so it
    // exists; SAF → createDocument; root → SuFile stream whose close() chowns it back to the sandbox
    // app. A name collision is uniquified ("name (1).ext") rather than refused. [onCreated] gets the
    // new entry after the pane re-lists.
    var newFileOpenOffer by remember { mutableStateOf<Loc?>(null) }
    fun createFile(parent: Loc, rawName: String) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Creating file..."
            val backend = Storage.backend(parent)
            val created: Loc? = withContext(Dispatchers.IO) {
                runCatching {
                    val name = uniqueName(parent, rawName)
                    // SAF providers force the extension to match the MIME type they're given, so derive
                    // it from the name ("new file.txt" + octet-stream would come back as .txt.bin).
                    val mime = android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
                    val loc = backend.createFile(context, parent, name, mime) ?: return@runCatching null
                    // Materialise it (FileBackend/RootBackend create on stream open; SAF already exists).
                    backend.openOutputStream(context, loc)?.close()
                    backend.childNamed(context, parent, name) ?: loc
                }.getOrNull()
            }
            isOperationRunning = false
            if (created == null) {
                AppSnackbar.show(context, "Could not create \"$rawName\"")
            } else {
                highlightId = created.id
                paneState.requestReload()
                // Text-like files get a one-tap jump into an editor.
                val ext = created.name.substringAfterLast('.', "").lowercase()
                if (ext in TEXT_LIKE_EXTS) newFileOpenOffer = created
                else AppSnackbar.show(context, "Created ${created.name}")
            }
        }
    }

    // ── Dialogs ──

    if (paneState.showNewFileDialog) {
        var fileName by remember { mutableStateOf("new file.txt") }
        val problem = when {
            fileName.isBlank() -> "Name is empty"
            fileName.contains('/') -> "A name can't contain /"
            fileName.trim() == "." || fileName.trim() == ".." -> "Not a valid name"
            else -> null
        }
        OutlinedAlertDialog(
            onDismissRequest = { paneState.showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File name (with extension)") },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = { if (problem != null) Text(problem, fontSize = 11.sp) },
                )
            },
            confirmButton = {
                TextButton(enabled = problem == null, onClick = {
                    paneState.showNewFileDialog = false
                    createFile(currentLoc, fileName.trim())
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { paneState.showNewFileDialog = false }) { Text("Cancel") } },
        )
    }

    newFileOpenOffer?.let { created ->
        OutlinedAlertDialog(
            onDismissRequest = { newFileOpenOffer = null },
            title = { Text("Created ${created.name}") },
            text = { Text("Open it in a text editor now?", fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { newFileOpenOffer = null; openWith(created) }) { Text("Open with…") } },
            dismissButton = { TextButton(onClick = { newFileOpenOffer = null }) { Text("Not now") } },
        )
    }

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

    // ── Convert to MP4 dialog ──
    if (convertTargets.isNotEmpty()) {
        val targets = convertTargets
        val (bink2, convertible) = remember(targets) {
            targets.partition { t ->
                com.the412banner.bfe.video.VideoFormats.isBink2Name(t.name) ||
                    ((t as? Loc.FileLoc)?.file?.let { com.the412banner.bfe.video.VideoFormats.isBink2Magic(it) } ?: false)
            }
        }
        val other = if (dualPane) otherPaneDir() else null
        ConvertDialog(
            inputNames = convertible.map { it.name },
            skippedBink2 = bink2.map { it.name },
            otherPaneName = other?.name?.ifBlank { "/" },
            ffmpegMissing = !com.the412banner.bfe.video.Ffmpeg.isAvailable(context),
            onDismiss = { convertTargets = emptyList() },
            onConfirm = { choice -> startConvert(choice, convertible) },
        )
    }

    // ── APK tools dialogs ──
    apkEditor?.let { t ->
        val defName = (if (t.kind == com.the412banner.bfe.apk.ApkJobKind.CLONE) t.meta.label.ifBlank { t.base.nameWithoutExtension } + "-clone" else t.base.nameWithoutExtension + "-edited")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".apk"
        ApkEditorDialog(
            meta = t.meta, kind = t.kind, defaultOutputName = defName,
            onDismiss = { apkEditor = null; t.staged?.let { d -> scope.launch { withContext(Dispatchers.IO) { d.deleteRecursively() } } } },
            onManageKeys = { showKeyManager = true },
            onConfirm = { r ->
                apkEditor = null
                startApkJob(t.kind, t.base, t.splits, r.edits, r.outputName, t.dest, r.key, r.schemes, r.installAfter, t.staged)
            },
        )
    }
    apkSignTarget?.let { (file, dest) ->
        SignApkDialog(
            fileName = file.name, defaultOutputName = file.nameWithoutExtension + "-signed.apk",
            onDismiss = { apkSignTarget = null },
            onManageKeys = { showKeyManager = true },
            onConfirm = { outName, key, schemes ->
                apkSignTarget = null
                val staged = pendingApk?.takeIf { it.jobId == 0L }?.stagedSource
                pendingApk = null
                startApkJob(com.the412banner.bfe.apk.ApkJobKind.SIGN, file, emptyList(), null, outName, dest, key, schemes, false, staged)
            },
        )
    }
    if (showKeyManager) KeyManagerDialog(onDismiss = { showKeyManager = false })
    if (showInstalledPicker) InstalledAppPickerDialog(onDismiss = { showInstalledPicker = false }, onPick = { openInstalledClone(it) })
    apkResult?.let { (report, loc) ->
        ApkResultDialog(report = report, onInstall = { apkResult = null; installApk(loc) }, onDismiss = { apkResult = null })
    }

    // Compress… — name prefilled from the single item (minus its extension) or the current folder.
    if (compressTargets.isNotEmpty()) {
        val targets = compressTargets
        val defaultBase = remember(targets) {
            val single = targets.singleOrNull()
            when {
                single == null -> currentLoc.name.ifBlank { "archive" }
                single.isDir -> single.name
                else -> single.name.substringBeforeLast('.', single.name).ifBlank { single.name }
            }
        }
        val other = if (dualPane) otherPaneDir() else null
        CompressDialog(
            defaultBase = defaultBase,
            itemCount = targets.size,
            otherPaneName = other?.name?.ifBlank { "/" },
            onDismiss = { compressTargets = emptyList() },
            onConfirm = { req -> startCompress(req, targets) },
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
    // …and the read-only details of an archive or an entry inside one.
    (propertiesTarget as? Loc.ArchiveLoc)?.let { ArchivePropertiesDialog(it, onDismiss = { propertiesTarget = null }) }

    // Archive password prompt + Extract to… (default: the other pane, else next to the archive).
    ArchiveDialogs(
        state = archivePane,
        hereDir = if (paneState.isArchive) File(paneState.path) else null,
        outerArchive = (paneState.altStack.firstOrNull() as? Loc.ArchiveLoc)?.archive,
        otherPane = if (dualPane) otherPaneDir() else null,
        onExtract = { items, dest -> extractFromArchive(items, dest) { if (dualPane) onRequestOtherReload() } },
    )

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
                            AppSnackbar.show(context, "$failed couldn't be deleted")
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

    // ── Open with sheet ── built-in actions for this file type first, then the viewer apps.
    openWithReq?.let { req ->
        val loc = req.loc
        val actions = remember(req) {
            val f = (loc as? Loc.FileLoc)?.file
            val lower = loc.name.lowercase()
            val fromArchive = req.fromArchive
            buildList {
                // Documents, text and logs: BFE's document viewer first (a real path when there is one,
                // else the Uri; archive entries open as their cache copy).
                documentSheetAction(context, loc.name, req.mime) {
                    com.the412banner.bfe.DocumentViewer.open(context, f?.absolutePath, req.uri, loc.name, req.mime)
                }?.let { add(it) }
                // Web pages open in BFE's own viewer first (scripts on, relative files load).
                if (com.the412banner.bfe.HtmlViewerActivity.isHtmlName(loc.name)) {
                    add(SheetAction(
                        "View page",
                        SheetIcons.Web,
                        component = android.content.ComponentName(context, com.the412banner.bfe.HtmlViewerActivity::class.java),
                        componentLabel = context.getString(com.the412banner.bfe.R.string.html_viewer_label),
                    ) {
                        context.startActivity(com.the412banner.bfe.HtmlViewerActivity.intent(context, f?.absolutePath, req.uri, loc.name))
                    })
                }
                // Photos, videos and music: BFE's own viewer / player first.
                MediaTypes.typeOf(loc.name, req.mime)?.let { type ->
                    add(mediaSheetAction(context, type) { openMedia(loc, type, req.uri, single = fromArchive != null) })
                }
                if (fromArchive != null) {
                    // BFE's cache copy of an entry inside an archive: browse it when it's an archive
                    // itself, install an APK, extract the entry somewhere real, or look at its details.
                    if (f != null && com.the412banner.bfe.unpack.SevenZip.looksLikeArchive(f)) add(SheetAction("Open as folder", SheetIcons.Browse) { browseArchive(f) })
                    if (lower.endsWith(".apk")) add(SheetAction("Install", SheetIcons.Install) { installApk(loc) })
                    add(SheetAction("Extract to\u2026", SheetIcons.ExtractTo) { archivePane.extractTargets = listOf(fromArchive) })
                    add(SheetAction("Properties", SheetIcons.Properties) { propertiesTarget = fromArchive })
                    return@buildList
                }
                val isInno = f != null && com.the412banner.bfe.unpack.SevenZip.isInnoSetup(f)
                if (f != null && (isInno || com.the412banner.bfe.unpack.SevenZip.looksLikeArchive(f))) {
                    // First for any archive 7-Zip lists (InnoSetup/FreeArc installers are unpack-only).
                    if (!isInno) add(SheetAction("Open as folder", SheetIcons.Browse) { browseArchive(f) })
                    add(SheetAction(if (dualPane) "Extract to the other pane" else "Extract here", SheetIcons.ExtractHere) { launchFastExtract(loc) })
                    add(SheetAction(if (isInno) "Unpack / Install\u2026" else "Extract to\u2026", SheetIcons.ExtractTo) { launchUnpack(loc) })
                }
                if (lower.endsWith(".apk")) {
                    add(SheetAction("Install", SheetIcons.Install) { installApk(loc) })
                    add(SheetAction("Clone APK\u2026", Icons.Filled.FileCopy) { openApkEditorFor(loc, com.the412banner.bfe.apk.ApkJobKind.CLONE) })
                }
                if (com.the412banner.bfe.video.VideoFormats.isVideoName(loc.name) || com.the412banner.bfe.video.VideoFormats.isBink2Name(loc.name)) {
                    add(SheetAction("Convert to MP4\u2026", SheetIcons.Convert) { convertTargets = listOf(loc) })
                }
                if (f != null) add(SheetAction("Properties", SheetIcons.Properties) { propertiesTarget = loc })
            }
        }
        OpenWithSheet(
            file = loc,
            uri = req.uri,
            detectedMime = req.mime,
            actions = actions,
            // Root files and archive entries are cache copies, so a write grant would edit the copy.
            allowWrite = loc !is Loc.RootLoc && req.fromArchive == null,
            onDismiss = { openWithReq = null },
        )
    }

    // The selection actions: Aurora's floating pill and Classic's toolbar ☰ menu both show these.
    val selection: PaneSelectionUi? = if (!selectionMode) null else {
        fun picked() = entries.filter { it.id in selectedIds }
        val any = selectedIds.isNotEmpty()
        val other = if (dualPane) otherPaneDir() else null
        val arrow = if (paneState.isRightPane) "\u2190" else "\u2192"
        val acts = buildList {
            add(SelAction("Copy", Icons.Filled.FileCopy, any) {
                clipboard = picked(); isCutOperation = false; selectionMode = false; selectedIds = emptySet()
            })
            if (paneState.isArchive) add(SelAction("Extract\u2026", Icons.Filled.Unarchive, any, SelTone.ACCENT) {
                archivePane.extractTargets = picked().filterIsInstance<Loc.ArchiveLoc>()
            })
            else add(SelAction("Cut", Icons.Filled.ContentCut, any) {
                clipboard = picked(); isCutOperation = true; selectionMode = false; selectedIds = emptySet()
            })
            if (other != null) {
                add(SelAction(if (paneState.isArchive) "Extract $arrow" else "Copy $arrow", Icons.Filled.FileCopy, any, SelTone.ACCENT) { crossPaneTransfer(cut = false) })
                if (!paneState.isArchive) add(SelAction("Move $arrow", Icons.Filled.ContentCut, any, SelTone.ACCENT) { crossPaneTransfer(cut = true) })
            }
            add(SelAction("Share", Icons.Filled.Share, entries.any { !it.isDir && it.id in selectedIds }) { shareFiles(picked()) })
            if (!pickMode && !paneState.isArchive) {
                add(SelAction("Compress", Icons.Filled.Archive, any) { compressTargets = picked() })
                fun videoSel() = entries.filter { it.id in selectedIds && !it.isDir && (com.the412banner.bfe.video.VideoFormats.isVideoName(it.name) || com.the412banner.bfe.video.VideoFormats.isBink2Name(it.name)) }
                if (videoSel().isNotEmpty()) add(SelAction("Convert", Icons.Filled.Movie, true) { convertTargets = videoSel() })
            }
            if (!paneState.isArchive) add(SelAction("Delete", Icons.Filled.Delete, any, SelTone.DANGER) { pendingBulkDelete = picked() })
        }
        // Exactly one item picked: its own extras too (Classic's ☰ menu lists them first).
        val one = picked().singleOrNull()
        val single = if (one == null) emptyList() else buildList {
            if (!one.isDir) add(SelAction("Open with…", Icons.Filled.OpenInNew, true) { picked().singleOrNull()?.let { openWith(it) } })
            if (!paneState.isArchive && !pickMode) add(SelAction("Rename", Icons.Filled.Edit, true) { renameTarget = picked().singleOrNull() })
            if (one is Loc.FileLoc || one is Loc.ArchiveLoc) add(SelAction("Properties", Icons.Filled.Info, true) { propertiesTarget = picked().singleOrNull() })
        }
        PaneSelectionUi(
            count = selectedIds.size,
            allSelected = entries.isNotEmpty() && selectedIds.size == entries.size,
            targetName = other?.name?.ifBlank { "/" },
            actions = acts,
            onToggleAll = { selectedIds = if (selectedIds.size == entries.size) emptySet() else entries.map { it.id }.toSet() },
            onDone = { selectionMode = false; selectedIds = emptySet() },
            single = single,
        )
    }
    SideEffect { paneState.selectionUi = selection }
    DisposableEffect(paneState) { onDispose { paneState.selectionUi = null } }
    // Classic has no selection bar to tap Done on: Back leaves multi-select.
    BackHandler(enabled = selectionMode && !isAurora) { selectionMode = false; selectedIds = emptySet() }

    // ── Aurora Glass layout (FileManagerAurora.kt). Same state, same actions; Classic below. ──
    if (isAurora) {
        // Everything a row / tile can do, built once per entry from this pane's actions.
        fun actionsFor(file: Loc) = FileRowActions(
            onTap = {
                if (file.isDir) openLoc(file)
                else if (pickMode) {
                    if (matchesPickExt(file)) {
                        currentFile?.let { pickPrefs.edit().putString("lastFilePickerDir", it.absolutePath).apply() }
                        (file as? Loc.FileLoc)?.file?.let { f -> onPick?.invoke(f) }
                    }
                } else openOnTap(file)
            },
            onLongPress = {
                // Long-press starts multi-select on this item (or toggles it when already selecting).
                if (!pickMode) {
                    if (!selectionMode) selectionMode = true
                    selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id
                }
            },
            onToggleSelect = { selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id },
            onMenu = { showMenuFor = file },
            onDismissMenu = { showMenuFor = null },
            onSelect = { selectionMode = true; selectedIds = selectedIds + file.id; showMenuFor = null },
            onOpenWith = { openWith(file) },
            onInstallApk = { installApk(file) },
            onShare = { shareFile(file) },
            onUnpack = { launchUnpack(file) },
            onFastExtract = { launchFastExtract(file) },
            onCompress = { showMenuFor = null; compressTargets = listOf(file) },
            onCloneApk = { openApkEditorFor(file, com.the412banner.bfe.apk.ApkJobKind.CLONE) },
            onEditApk = { openApkEditorFor(file, com.the412banner.bfe.apk.ApkJobKind.EDIT) },
            onSignApk = { openSign(file) },
            onConvert = { showMenuFor = null; convertTargets = listOf(file) },
            onRename = { renameTarget = file; showMenuFor = null },
            onCopy = { clipboard = listOf(file); isCutOperation = false; showMenuFor = null },
            onCut = { clipboard = listOf(file); isCutOperation = true; showMenuFor = null },
            onDelete = { selectedEntry = file; showMenuFor = null },
            onToggleFavorite = {
                val nowFav = toggleFav(file)
                favTick++
                showMenuFor = null
                AppSnackbar.show(context, if (nowFav) "Added \"${file.name}\" to Favorites" else "Removed \"${file.name}\" from Favorites")
            },
            onProperties = { propertiesTarget = file; showMenuFor = null },
            onBrowse = { if (file is Loc.ArchiveLoc) browseArchiveEntry(file) else (file as? Loc.FileLoc)?.let { browseArchive(it.file) } },
            onExtractTo = { showMenuFor = null; (file as? Loc.ArchiveLoc)?.let { archivePane.extractTargets = listOf(it) } },
        )

        AuroraPaneLayout(
            modifier = modifier,
            paneState = paneState,
            currentLoc = currentLoc,
            currentFile = currentFile,
            entries = entries,
            entriesFor = entriesFor,
            loading = loading,
            loadError = loadError,
            highlightId = highlightId,
            selectionMode = selectionMode,
            selectedIds = selectedIds,
            showActions = !pickMode,
            menuFor = showMenuFor,
            favTick = favTick,
            isFavorite = { isLocFav(it) },
            actionsFor = ::actionsFor,
            pickMode = pickMode,
            pickDirMode = pickDirMode,
            pickerTitle = pickerTitle,
            onPickFolder = { f -> onPick?.invoke(f) },
            clipboard = clipboard,
            isCut = isCutOperation,
            onPaste = { performPaste() },
            onCancelPaste = { clipboard = emptyList(); isCutOperation = false },
            operationRunning = isOperationRunning,
            operationLabel = operationLabel,
            operationProgress = if (operationDeterminate) operationProgress else null,
            onCancelOperation = if (operationJob != null) ({
                operationJob?.cancel()
                operationJob = null
                isOperationRunning = false
                operationDeterminate = false
                paneState.requestReload()
            }) else null,
            dualPane = dualPane,
            pullState = pullState,
            listState = listState,
            gridState = gridState,
            exitScroll = exitScroll,
            archivePane = archivePane,
            onNavigate = { navFileDir(it) },
            onPinCurrent = {
                currentFile?.let {
                    FavoritesStore.add(context, it.absolutePath)
                    favTick++
                    AppSnackbar.show(context, "Added \"${it.name}\" to Favorites")
                }
            },
            onJumpFavorite = { dir -> paneState.showFavorites = false; openDrive(dir) },
            onUnpinFavorite = { dir ->
                FavoritesStore.remove(context, dir.absolutePath)
                favTick++
                AppSnackbar.show(context, "Removed \"${dir.name}\" from Favorites")
            },
        )
        return
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
                BfeIcon(FileIcons.FOLDER, size = 18.dp)
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
            ?: (paneState.altLabel + paneState.altStack.drop(1).joinToString("") { " / ${it.name}" })
        if (!paneState.showFavorites) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                // Clickable breadcrumb: drive (or pinned location) first, then one tappable crumb per
                // folder down to the current one. Scrolls sideways and keeps the deepest crumb in view;
                // the path takes all remaining width so free space stays pinned to the right edge.
                val crumbs: List<Pair<String, (() -> Unit)?>> = remember(locDisplayPath, paneState.rootPath, paneState.altStack) {
                    paneCrumbs(paneState, currentFile, locDisplayPath, ::navFileDir)
                }
                val crumbScroll = rememberScrollState()
                LaunchedEffect(locDisplayPath, crumbScroll.maxValue) { crumbScroll.scrollTo(crumbScroll.maxValue) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).horizontalScroll(crumbScroll),
                ) {
                    if (paneState.isArchive) {
                        BfeIcon(FileIcons.FOLDER_OPEN, size = 16.dp)
                        Spacer(Modifier.width(2.dp))
                    }
                    crumbs.forEachIndexed { i, (label, onClick) ->
                        val last = i == crumbs.lastIndex
                        if (i > 0) {
                            Text("\u203A", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(
                            text = label,
                            color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .then(if (!last && onClick != null) Modifier.clickable { onClick() } else Modifier)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                }
                if (currentLoc is Loc.ArchiveLoc) {
                    ArchiveCrumbBadge(currentLoc)
                } else if (freeSpace > 0) {
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
                        AppSnackbar.show(context, "Added \"${it.name}\" to Favorites")
                    }
                },
                onJump = { dir ->
                    paneState.showFavorites = false
                    openDrive(dir)
                },
                onUnpin = { dir ->
                    FavoritesStore.remove(context, dir.absolutePath)
                    favTick++
                    AppSnackbar.show(context, "Removed \"${dir.name}\" from Favorites")
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
        // ── File list (pull down to refresh) ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection),
        ) {
            // Measured once per pane (not per row): a dual-pane half on a phone gets the narrow card rows.
            val narrowRows = maxWidth < NARROW_ROW_WIDTH
            val shownEntries = if (paneState.searchQuery.isBlank()) entries
            else entries.filter { it.name.contains(paneState.searchQuery, ignoreCase = true) }

            val inArchive = currentLoc as? Loc.ArchiveLoc
            if (loading && entries.isEmpty()) {
                if (inArchive != null) {
                    // Opening an archive (listing it, or unpacking a .tar.gz/.wcp's inner tar) can take a while.
                    ArchiveOpeningIndicator(archivePane, inArchive.archive.name, onCancel = { paneState.closeArchive(inArchive.archive) })
                } else Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (loadError != null && entries.isEmpty()) {
                PaneMessage(
                    icon = Icons.Filled.FolderOff,
                    title = loadError ?: "Couldn't read this folder",
                    detail = if (inArchive != null) "BFE's 7-Zip couldn't open ${inArchive.archive.name}. It may be damaged, incomplete, or a format 7-Zip can extract but not list."
                        else "It may need permissions BFE doesn't have, or it was removed.",
                    actionLabel = if (inArchive != null) "Close archive" else "Try again",
                    onAction = { if (inArchive != null) paneState.closeArchive(inArchive.archive) else paneState.requestReload() },
                )
            } else if (entries.isNotEmpty() && shownEntries.isEmpty()) {
                PaneMessage(
                    icon = Icons.Filled.SearchOff,
                    title = "No matches",
                    detail = "Nothing in this folder matches \u201C${paneState.searchQuery}\u201D.",
                    actionLabel = "Clear filter",
                    onAction = { paneState.searchQuery = "" },
                )
            } else if (paneState.viewMode.shownIn(dualPane) == FmViewMode.GRID && entries.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(shownEntries, key = { it.id }) { file ->
                        val isFav = remember(file.id, favTick) { isLocFav(file) }
                        FileGridTile(
                            file = file,
                            showActions = !pickMode,
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds || file.id == highlightId,
                            onLongPress = {
                                // Long-press starts multi-select on this item (or toggles it when
                                // already selecting); the action menu lives on ⋮ / Open with.
                                if (!pickMode) {
                                    if (!selectionMode) selectionMode = true
                                    selectedIds = if (file.id in selectedIds)
                                        selectedIds - file.id
                                    else selectedIds + file.id
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
                                } else openOnTap(file)
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
                            onCompress = { showMenuFor = null; compressTargets = listOf(file) },
                            onCloneApk = { openApkEditorFor(file, com.the412banner.bfe.apk.ApkJobKind.CLONE) },
                            onEditApk = { openApkEditorFor(file, com.the412banner.bfe.apk.ApkJobKind.EDIT) },
                            onSignApk = { openSign(file) },
                            onConvert = { showMenuFor = null; convertTargets = listOf(file) },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onCopy = { clipboard = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboard = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onToggleFavorite = {
                                val nowFav = toggleFav(file)
                                favTick++
                                showMenuFor = null
                                AppSnackbar.show(context, if (nowFav) "Added \"${file.name}\" to Favorites"
                                    else "Removed \"${file.name}\" from Favorites")
                            },
                            onProperties = { propertiesTarget = file; showMenuFor = null },
                            onBrowse = { if (file is Loc.ArchiveLoc) browseArchiveEntry(file) else (file as? Loc.FileLoc)?.let { browseArchive(it.file) } },
                            onExtractTo = { showMenuFor = null; (file as? Loc.ArchiveLoc)?.let { archivePane.extractTargets = listOf(it) } },
                        )
                    }
                }
            } else
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (entries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize()) {
                            PaneMessage(icon = Icons.Filled.FolderOpen, iconRes = FileIcons.FOLDER_OPEN, title = "This folder is empty")
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
                            narrow = narrowRows,
                            // COMPACT view mode → dense single-line rows (~4× more per screen).
                            dense = paneState.viewMode == FmViewMode.COMPACT,
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds || file.id == highlightId,
                            onLongPress = {
                                // Long-press starts multi-select on this item (or toggles it when
                                // already selecting); the action menu lives on ⋮ / Open with.
                                if (!pickMode) {
                                    if (!selectionMode) selectionMode = true
                                    selectedIds = if (file.id in selectedIds)
                                        selectedIds - file.id
                                    else selectedIds + file.id
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
                                else openOnTap(file)
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
                            onCompress = { showMenuFor = null; compressTargets = listOf(file) },
                            onCloneApk = { openApkEditorFor(file, com.the412banner.bfe.apk.ApkJobKind.CLONE) },
                            onEditApk = { openApkEditorFor(file, com.the412banner.bfe.apk.ApkJobKind.EDIT) },
                            onSignApk = { openSign(file) },
                            onConvert = { showMenuFor = null; convertTargets = listOf(file) },
                            onUnpack = { launchUnpack(file) },
                            isFavorite = isFav,
                            onToggleFavorite = {
                                val nowFav = toggleFav(file)
                                favTick++
                                showMenuFor = null
                                AppSnackbar.show(context, if (nowFav) "Added \"${file.name}\" to Favorites"
                                    else "Removed \"${file.name}\" from Favorites")
                            },
                            onProperties = { propertiesTarget = file; showMenuFor = null },
                            onBrowse = { if (file is Loc.ArchiveLoc) browseArchiveEntry(file) else (file as? Loc.FileLoc)?.let { browseArchive(it.file) } },
                            onExtractTo = { showMenuFor = null; (file as? Loc.ArchiveLoc)?.let { archivePane.extractTargets = listOf(it) } },
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
// (FileGridTile), so both open the identical menu from their ⋮ / menu affordance.
// Grouped into sections (open · share/info · type-specific tools · organise · edit) with a divider
// between sections only, so the long menus stay scannable; the DropdownMenu scrolls when it has to.
// Gating (isDir/isInno/looksLikeArchive/apk/video) is recomputed from [file] here — one source of
// truth for what each item shows. Every item dismisses the menu first, then runs its action.
@Composable
internal fun FileContextMenuItems(
    file: Loc,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onOpenWith: () -> Unit,
    onInstallApk: () -> Unit,
    onShare: () -> Unit,
    onUnpack: () -> Unit,
    onFastExtract: () -> Unit,
    onCompress: () -> Unit,
    onCloneApk: () -> Unit,
    onEditApk: () -> Unit,
    onSignApk: () -> Unit,
    onConvert: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProperties: () -> Unit,
    onDismissMenu: () -> Unit,
    onBrowse: () -> Unit = {},
    onExtractTo: () -> Unit = {},
) {
    // Inside an archive: the read-only archive menu instead (ArchivePane.kt).
    if (file is Loc.ArchiveLoc) {
        ArchiveEntryMenuItems(file, onOpenWith, onBrowse, onExtractTo, onShare, onProperties, onSelect, onCopy, onDismissMenu)
        return
    }
    val isDir = file.isDir
    // File-specific extras (properties, favourites, archive extraction) are for direct-File entries;
    // SAF ("app storage") entries only get the backend-agnostic actions.
    val fileLoc = (file as? Loc.FileLoc)?.file
    val isApk = !isDir && file.name.lowercase().endsWith(".apk")
    // Content-aware archive check: extension OR a cheap magic-byte sniff (a few header bytes, read only
    // when the menu opens — never `7zz l` per entry). Extraction is File-only (engines need a real path).
    // For an InnoSetup repack the screen decides between payload extraction and running Setup.exe.
    val isInno = fileLoc != null && com.the412banner.bfe.unpack.SevenZip.isInnoSetup(fileLoc)
    val isArchive = fileLoc != null && (isInno || com.the412banner.bfe.unpack.SevenZip.looksLikeArchive(fileLoc))
    val isVideo = !isDir && (com.the412banner.bfe.video.VideoFormats.isVideoName(file.name) || com.the412banner.bfe.video.VideoFormats.isBink2Name(file.name))

    @Composable
    fun Item(label: String, icon: ImageVector, danger: Boolean = false, dismiss: Boolean = true, action: () -> Unit) {
        val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        DropdownMenuItem(
            text = { Text(label, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(icon, null, tint = tint) },
            onClick = { if (dismiss) onDismissMenu(); action() },
        )
    }

    // ── Open ──
    if (!isDir) {
        Item("Open with\u2026", Icons.Filled.OpenInNew, action = onOpenWith)
        MenuItemDivider()
    }
    // ── Share / info ──
    if (!isDir) Item("Share", Icons.Filled.Share, action = onShare)
    if (fileLoc != null) Item("Properties", Icons.Filled.Info, action = onProperties)
    if (isDir && fileLoc != null) {
        // Favourites toggle dismisses itself (it posts a snackbar naming the folder).
        Item(
            if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            dismiss = false,
            action = onToggleFavorite,
        )
    }
    if (!isDir || fileLoc != null) MenuItemDivider()
    // ── Type-specific tools ──
    if (isApk) {
        Item("Install APK", Icons.Filled.Android, action = onInstallApk)
        Item("Clone APK\u2026", Icons.Filled.FileCopy, action = onCloneApk)
        Item("Edit APK\u2026", Icons.Filled.Edit, action = onEditApk)
        Item("Sign APK\u2026", Icons.Filled.Security, action = onSignApk)
    }
    if (isArchive) {
        // Browse it like a folder (read-only). InnoSetup/FreeArc installers are unpack-only.
        if (!isInno) Item("Open as folder", Icons.Filled.FolderOpen, action = onBrowse)
        Item("Fast Extract", Icons.Filled.Bolt, action = onFastExtract)
        Item(if (isInno) "Unpack / Install\u2026" else "Unpack Archive\u2026", Icons.Filled.Unarchive, action = onUnpack)
    }
    if (isVideo) Item("Convert to MP4\u2026", Icons.Filled.Movie, action = onConvert)
    // Create an archive from this item (any backend: SAF/root items are staged to a temp dir first).
    Item("Compress\u2026", Icons.Filled.Archive, action = onCompress)
    MenuItemDivider()
    // ── Organise ──
    Item("Select", Icons.Filled.Checklist, action = onSelect)
    Item("Rename", Icons.Filled.Edit, action = onRename)
    Item("Copy", Icons.Filled.FileCopy, action = onCopy)
    Item("Cut", Icons.Filled.ContentCut, action = onCut)
    MenuItemDivider()
    Item("Delete", Icons.Filled.Delete, danger = true, action = onDelete)
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
    // Narrow pane (dual pane on a phone): short date over time in small type, a slimmer ⋮ and tighter
    // padding, so the name keeps most of the row.
    narrow: Boolean = false,
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
    onCompress: () -> Unit = {},
    onCloneApk: () -> Unit = {},
    onEditApk: () -> Unit = {},
    onSignApk: () -> Unit = {},
    onConvert: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onProperties: () -> Unit = {},
    onBrowse: () -> Unit = {},
    onExtractTo: () -> Unit = {},
) {
    // Last change, as date + time in the phone's 12/24-hour style ("Oct 13, 2026 · 3:42 PM").
    val context = LocalContext.current
    val isDir = file.isDir
    val modified = file.lastModified
    val dateLabel = if (modified > 0) DateText.dateTime(context, modified) else ""
    val dateOnlyLabel = if (modified > 0) DateText.date(context, modified) else ""
    val timeLabel = if (modified > 0) DateText.time(context, modified) else ""
    // The card's date column: the short numeric date in a narrow pane ("3/16/26"), the medium one otherwise.
    val columnDateLabel = if (narrow && modified > 0) DateText.shortDate(context, modified) else dateOnlyLabel
    // Folders show how many entries they hold. Counted lazily for the rows actually composed, off the
    // main thread and cached by path + mtime (see ChildCount); blank until ready, date-only when the
    // folder can't be read.
    val rowContext = LocalContext.current
    var itemCount by remember(file.id, modified) { mutableStateOf(if (isDir) ChildCount.cached(file) else null) }
    if (isDir && itemCount == null) {
        LaunchedEffect(file.id, modified) { itemCount = ChildCount.count(rowContext, file) }
    }
    val countLabel = itemCount?.takeIf { it >= 0 }?.let { if (it == 1) "1 item" else "$it items" }
    // Left half of the detail line: item count for folders, size for files.
    val detailLabel = if (isDir) countLabel.orEmpty() else StringUtils.formatBytes(file.size)
    // Image files show a real thumbnail instead of the generic file icon (handy when picking a
    // wallpaper/icon). Coil sizes the decode to the 36dp slot and caches it, so scrolling stays smooth.
    val isImage = !isDir && File(file.name).extension.lowercase() in IMAGE_THUMB_EXTS
    val isVideo = !isDir && File(file.name).extension.lowercase() in VIDEO_THUMB_EXTS

    // For real PE executables, try to pull out the embedded application icon (async, off the main thread).
    var exeIcon by remember(file.id) { mutableStateOf<ImageBitmap?>(null) }
    val exeFile = (file as? Loc.FileLoc)?.file
    if (exeFile != null && exeFile.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.id) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(exeFile) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // And for .apk files the app's launcher icon — same async slot (any backend; see ApkIconLoader).
    if (!isDir && ApkIconLoader.isApk(file.name)) {
        val ctx = LocalContext.current
        LaunchedEffect(file.id, file.lastModified, file.size) {
            val bmp = withContext(Dispatchers.IO) { ApkIconLoader.load(ctx, file, 96) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // Thumbnail source: the File for direct storage, the SAF doc Uri otherwise (Coil handles both).
    val thumbModel: Any = (file as? Loc.FileLoc)?.file ?: (file as? Loc.SafLoc)?.docUri ?: ""

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
                    isImage || isVideo -> FileThumb(file.name, thumbModel, isVideo, size = 22.dp, corner = 4.dp, badge = false)
                    else -> FileTypeIcon(file.name, isDir, size = 22.dp)
                }
                Spacer(Modifier.width(10.dp))
                // One-line detail on the right: "N items · date" (folders) or "size · date" (files).
                // In a narrow pane the time drops off first, then the date, so the name keeps its room.
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val rowDate = when {
                        maxWidth >= 330.dp -> dateLabel
                        maxWidth >= 260.dp -> dateOnlyLabel
                        else -> ""
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = file.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val right = listOf(detailLabel, rowDate)
                            .filter { it.isNotEmpty() }.joinToString("  \u00B7  ")
                        if (right.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = right,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
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
                        BfeDropdownMenu(
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
                                onCompress = onCompress,
                                onCloneApk = onCloneApk,
                                onEditApk = onEditApk,
                                onSignApk = onSignApk,
                                onConvert = onConvert,
                                onRename = onRename,
                                onCopy = onCopy,
                                onCut = onCut,
                                onDelete = onDelete,
                                onToggleFavorite = onToggleFavorite,
                                onProperties = onProperties,
                                onDismissMenu = onDismissMenu,
                                onBrowse = onBrowse,
                                onExtractTo = onExtractTo,
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
            .padding(horizontal = if (narrow) 6.dp else 12.dp, vertical = 3.dp)
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
            modifier = Modifier.padding(
                start = if (narrow) 8.dp else 12.dp,
                end = if (narrow) 4.dp else 12.dp,
                top = if (compact) 3.dp else 8.dp,
                bottom = if (compact) 3.dp else 8.dp,
            ),
        ) {
            if (selectionMode) {
                // A narrow row can't spare the checkbox's 48 dp touch target (a tap on the row toggles anyway).
                if (narrow) Icon(
                    if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                ) else {
                    androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(4.dp))
                }
            }
            val iconSize = when { compact -> 24.dp; narrow -> 30.dp; else -> 36.dp }
            when {
                // Show the executable's own embedded icon when we managed to extract one.
                exeIcon != null -> Image(
                    bitmap = exeIcon!!,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
                // Real image / video-frame preview. Falls back to the type icon while loading or on failure.
                isImage || isVideo -> FileThumb(file.name, thumbModel, isVideo, size = if (narrow) 30.dp else 36.dp, corner = 6.dp, badge = !compact)
                // BFE's folder / file-type artwork (special folders by name, files by extension).
                else -> FileTypeIcon(file.name, isDir, size = iconSize)
            }
            Spacer(Modifier.width(if (narrow) 8.dp else 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = if (narrow) 13.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Detail line: item count (folders) or size (files).
                Text(
                    text = detailLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Date over time, right-aligned beside the name and detail lines.
            if (columnDateLabel.isNotEmpty()) {
                Spacer(Modifier.width(if (narrow) 6.dp else 8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = columnDateLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (narrow) 10.sp else 11.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = timeLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = if (narrow) 10.sp else 11.sp,
                        maxLines = 1,
                    )
                }
            }
            if (showActions) Box {
                if (narrow) Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMenu)
                        .semantics { contentDescription = "Actions" },
                ) {
                    Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                } else IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, "Actions", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                BfeDropdownMenu(
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
                        onCompress = onCompress,
                        onCloneApk = onCloneApk,
                        onEditApk = onEditApk,
                        onSignApk = onSignApk,
                        onConvert = onConvert,
                        onRename = onRename,
                        onCopy = onCopy,
                        onCut = onCut,
                        onDelete = onDelete,
                        onToggleFavorite = onToggleFavorite,
                        onProperties = onProperties,
                        onDismissMenu = onDismissMenu,
                        onBrowse = onBrowse,
                        onExtractTo = onExtractTo,
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
            FileTypeIcon(file.name, isDir = true, size = 36.dp)
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
 * An image or video-frame preview in a [size] slot (video: the frame ~1 s in, plus a small play badge
 * once it has loaded). BFE's type icon shows while it loads and stays if the file can't be decoded.
 */
@Composable
internal fun FileThumb(name: String, model: Any, video: Boolean, size: Dp, corner: Dp, badge: Boolean) {
    val context = LocalContext.current
    val request = remember(model, video) {
        if (video) ImageRequest.Builder(context).data(model).videoFrameMillis(1_000).build() else model
    }
    var loaded by remember(model) { mutableStateOf(false) }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = fileIconPainter(name, false),
            error = fileIconPainter(name, false),
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(corner)),
        )
        if (video && badge && loaded) {
            Icon(
                Icons.Filled.PlayArrow, null,
                tint = Color.White,
                modifier = Modifier
                    .size(size * 0.42f)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(size * 0.05f),
            )
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
    // ⋮ in the tile's corner opens the action menu (long-press now multi-selects). Off in pick mode.
    showActions: Boolean = true,
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
    onCompress: () -> Unit = {},
    onCloneApk: () -> Unit = {},
    onEditApk: () -> Unit = {},
    onSignApk: () -> Unit = {},
    onConvert: () -> Unit = {},
    onRename: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onProperties: () -> Unit = {},
    onBrowse: () -> Unit = {},
    onExtractTo: () -> Unit = {},
) {
    val isDir = file.isDir
    val isImage = !isDir && File(file.name).extension.lowercase() in IMAGE_THUMB_EXTS
    val isVideo = !isDir && File(file.name).extension.lowercase() in VIDEO_THUMB_EXTS
    var exeIcon by remember(file.id) { mutableStateOf<ImageBitmap?>(null) }
    val exeFile = (file as? Loc.FileLoc)?.file
    if (exeFile != null && exeFile.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.id) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(exeFile) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    if (!isDir && ApkIconLoader.isApk(file.name)) {
        val ctx = LocalContext.current
        LaunchedEffect(file.id, file.lastModified, file.size) {
            val bmp = withContext(Dispatchers.IO) { ApkIconLoader.load(ctx, file, 144) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // Thumbnail source: the File for direct storage, the SAF doc Uri otherwise (Coil handles both).
    val thumbModel: Any = (file as? Loc.FileLoc)?.file ?: (file as? Loc.SafLoc)?.docUri ?: ""
    // The action menu is anchored to the Box around the Card (opened from the corner ⋮).
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
                        isImage || isVideo -> FileThumb(file.name, thumbModel, isVideo, size = 56.dp, corner = 6.dp, badge = true)
                        // The artwork carries its own keyline padding, so it fills the whole 56dp box.
                        else -> FileTypeIcon(file.name, isDir, size = 56.dp)
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
                // Always two lines tall (minLines) so every tile in a row is the same height and the
                // icons line up, whether the name wraps or not.
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    minLines = 2,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showActions && !selectionMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onMenu() }
                    .semantics { contentDescription = "Actions" },
            ) {
                Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        BfeDropdownMenu(
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
                onCompress = onCompress,
                onCloneApk = onCloneApk,
                onEditApk = onEditApk,
                onSignApk = onSignApk,
                onConvert = onConvert,
                onRename = onRename,
                onCopy = onCopy,
                onCut = onCut,
                onDelete = onDelete,
                onToggleFavorite = onToggleFavorite,
                onProperties = onProperties,
                onDismissMenu = onDismissMenu,
                onBrowse = onBrowse,
                onExtractTo = onExtractTo,
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
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val created = remember(file.absolutePath) { DateText.createdMillis(file) }
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
                // Full path including the name; tap copies it to the Android clipboard. Android 13+
                // shows its own copy preview, so only older versions get our confirmation (ours would
                // otherwise sit under this dialog's scrim, half covered by the system preview).
                PropertyLine("Path", file.absolutePath, maxLines = 4) {
                    clipboard.setText(AnnotatedString(file.absolutePath))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) AppSnackbar.show(context, "Path copied")
                }
                PropertyLine(
                    "Type",
                    if (file.isDirectory) "Folder"
                    else file.extension.uppercase().let { if (it.isBlank()) "File" else "$it file" },
                )
                if (!file.isDirectory) PropertyLine("Size", StringUtils.formatBytes(file.length()))
                created?.let { PropertyLine("Created", DateText.dateTime(context, it)) }
                PropertyLine("Modified", DateText.dateTime(context, file.lastModified()))

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
                                AppSnackbar.show(context, "Couldn't change Read-only")
                            }
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// One "label: value" line in the Properties info block. With [onClick] the row is tappable and
// the value is drawn in the accent colour so it reads as an action.
@Composable
private fun PropertyLine(label: String, value: String, maxLines: Int = 2, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = maxLines,
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

/** Centered empty / error / no-results state for a pane: icon, a short title, optional detail + action. */
@Composable
internal fun PaneMessage(
    icon: ImageVector,
    title: String,
    // BFE artwork to show instead of [icon] (drawn full colour, slightly faded).
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (iconRes != null) BfeIcon(iconRes, size = 64.dp, alpha = 0.85f)
        else Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (detail != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The draggable handle between the two panes: a thin rule with a grip, 12dp wide to be easy to hit.
 * Horizontal drags resize; a double-tap restores the even split.
 */
@Composable
private fun PaneDivider(onDrag: (Float) -> Unit, onDragEnd: () -> Unit, onReset: () -> Unit) {
    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { onDrag(it) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(12.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .draggable(
                state = dragState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                onDragStopped = { onDragEnd() },
            )
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }
            .semantics { contentDescription = "Resize panes" },
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}
