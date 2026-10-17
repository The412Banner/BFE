package com.the412banner.bfe.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.core.ApkIconLoader
import com.the412banner.bfe.core.PeIconExtractor
import com.the412banner.bfe.core.StorageRoot
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.server.ServerManager
import com.the412banner.bfe.storage.ChildCount
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.storage.PinnedLocation
import com.the412banner.bfe.ui.components.AccentButton
import com.the412banner.bfe.ui.components.AccentFab
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.components.BrandWordmark
import com.the412banner.bfe.ui.components.CapsuleShape
import com.the412banner.bfe.ui.components.GlassChip
import com.the412banner.bfe.ui.components.GlassIconButton
import com.the412banner.bfe.ui.components.GlassPillBar
import com.the412banner.bfe.ui.components.GlassPillRail
import com.the412banner.bfe.ui.components.ShimmerProgressBar
import com.the412banner.bfe.ui.components.pressScale
import com.the412banner.bfe.ui.components.staggeredEnter
import com.the412banner.bfe.ui.icons.BfeIcon
import com.the412banner.bfe.ui.icons.FileIcons
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.ui.screens.home.SharingChip
import com.the412banner.bfe.ui.theme.BfeMotion
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.grainBrush
import com.the412banner.bfe.ui.theme.motionSpring
import com.the412banner.bfe.ui.theme.motionTween
import com.the412banner.bfe.ui.theme.rimBrush
import com.the412banner.bfe.util.DateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import kotlin.math.abs

/*
 * The file manager in Aurora Glass. FileManagerScreen / BrowserPane own all state and actions (shared
 * with Classic); this file is only how they are drawn in Aurora:
 *  - each pane is a glass card; the active one brightens, gains an accent rim and a soft accent glow;
 *  - a pane header with the drive chip (Internal ▾ → the same drive menu as Classic), tappable
 *    breadcrumbs, the Favorites star and the view-mode button;
 *  - folder changes slide in from the side you are going (into a folder → from the right, up → from
 *    the left) with a staggered fade-in of the first rows; sorting and filtering re-flow with
 *    animateItemPlacement;
 *  - the toolbar floats at the bottom as a glass pill in portrait (thumb reach) and as a vertical pill
 *    rail on the left in landscape; multi-select actions rise as a second pill above it;
 *  - the dual-pane divider is a glass grip: drag anywhere 20–80 %, it settles on ⅓ · ½ · ⅔ when
 *    released near one (or flicked), double-tap = 50/50.
 */

// ── Shared models (filled in by BrowserPane / FileManagerScreen) ──

/** Scroll position of the folder a pane is leaving (list + grid), see BrowserPane's listing effect. */
internal data class PaneScroll(val listIndex: Int = 0, val listOffset: Int = 0, val gridIndex: Int = 0, val gridOffset: Int = 0)

enum class SelTone { NORMAL, ACCENT, DANGER }

/** One multi-select action in the floating selection pill. */
class SelAction(val label: String, val icon: ImageVector, val enabled: Boolean, val tone: SelTone = SelTone.NORMAL, val onClick: () -> Unit)

/** A pane's multi-select state for the floating selection pill (same actions as Classic's selection bar). */
class PaneSelectionUi(
    val count: Int,
    val allSelected: Boolean,
    /** The other pane's folder, the target of the → actions (null when single pane). */
    val targetName: String?,
    val actions: List<SelAction>,
    val onToggleAll: () -> Unit,
    val onDone: () -> Unit,
) {
    companion object {
        /**
         * BrowserPane republishes its selection after every recomposition; only a change in what the
         * pill SHOWS counts as a change, so publishing never feeds back into another recomposition.
         * (The actions read the pane's live state when tapped, so keeping an earlier instance is safe.)
         */
        val SameDisplay = object : androidx.compose.runtime.SnapshotMutationPolicy<PaneSelectionUi?> {
            override fun equivalent(a: PaneSelectionUi?, b: PaneSelectionUi?): Boolean =
                if (a == null || b == null) a === b
                else a.count == b.count && a.allSelected == b.allSelected && a.targetName == b.targetName &&
                    a.actions.size == b.actions.size &&
                    a.actions.indices.all { i -> a.actions[i].label == b.actions[i].label && a.actions[i].enabled == b.actions[i].enabled && a.actions[i].tone == b.actions[i].tone }
        }
    }
}

/** Everything a row or tile can do for one entry (the same callbacks Classic's rows get). */
internal class FileRowActions(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
    val onToggleSelect: () -> Unit,
    val onMenu: () -> Unit,
    val onDismissMenu: () -> Unit,
    val onSelect: () -> Unit,
    val onOpenWith: () -> Unit,
    val onInstallApk: () -> Unit,
    val onShare: () -> Unit,
    val onUnpack: () -> Unit,
    val onFastExtract: () -> Unit,
    val onCompress: () -> Unit,
    val onCloneApk: () -> Unit,
    val onEditApk: () -> Unit,
    val onSignApk: () -> Unit,
    val onConvert: () -> Unit,
    val onRename: () -> Unit,
    val onCopy: () -> Unit,
    val onCut: () -> Unit,
    val onDelete: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onProperties: () -> Unit,
    val onBrowse: () -> Unit,
    val onExtractTo: () -> Unit,
)

/** The entry's ⋮ menu: exactly Classic's items (FileContextMenuItems). */
@Composable
private fun FileRowMenu(file: Loc, isFavorite: Boolean, a: FileRowActions) {
    FileContextMenuItems(
        file = file, isFavorite = isFavorite, onSelect = a.onSelect, onOpenWith = a.onOpenWith,
        onInstallApk = a.onInstallApk, onShare = a.onShare, onUnpack = a.onUnpack, onFastExtract = a.onFastExtract,
        onCompress = a.onCompress, onCloneApk = a.onCloneApk, onEditApk = a.onEditApk, onSignApk = a.onSignApk,
        onConvert = a.onConvert, onRename = a.onRename, onCopy = a.onCopy, onCut = a.onCut, onDelete = a.onDelete,
        onToggleFavorite = a.onToggleFavorite, onProperties = a.onProperties, onDismissMenu = a.onDismissMenu,
        onBrowse = a.onBrowse, onExtractTo = a.onExtractTo,
    )
}

/**
 * Screen-level data the pane headers need (drive menu) and the space the floating chrome takes at the
 * bottom, which lists add as content padding so their last rows can scroll clear of it.
 */
@Stable
internal class FmChrome {
    var drives: List<StorageRoot> = emptyList()
    var pinned: List<PinnedLocation> = emptyList()
    var onOpenPinned: (PinnedLocation) -> Unit = {}
    var onAddAppStorage: () -> Unit = {}
    var onUnpin: (PinnedLocation) -> Unit = {}
    var listBottomPadding by mutableStateOf(12.dp)
}

internal val LocalFmChrome = staticCompositionLocalOf { FmChrome() }

/** Whether the pane being drawn is the active one (its header chip takes the accent). */
internal val LocalPaneActive = compositionLocalOf { true }

// ── The pane card ──

/**
 * A pane as a glass card. Going active animates (350 ms) from resting glass to brighter glass with an
 * accent rim and a soft accent halo — all read in the draw phase, so the pane itself never recomposes
 * for the animation.
 */
@Composable
internal fun Modifier.auroraPaneCard(active: Boolean): Modifier {
    val g = LocalGlass.current
    val accent = MaterialTheme.colorScheme.primary
    val reduced = LocalReducedMotion.current
    val progress = animateFloatAsState(if (active) 1f else 0f, motionTween(reduced, 350), label = "paneActive")
    val shape = Glass.Pane
    val dark = g.palette.dark
    return this
        .drawWithCache {
            val inner = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val step = 3.dp.toPx()
            val rings = (1..5).map { i ->
                val s = step * i
                s to Path().apply { addOutline(shape.createOutline(Size(size.width + 2 * s, size.height + 2 * s), layoutDirection, this@drawWithCache)) }
            }
            onDrawBehind {
                val k = progress.value
                if (k > 0.01f) clipPath(inner, ClipOp.Difference) {
                    rings.forEach { (s, path) -> translate(-s, -s) { drawPath(path, accent.copy(alpha = 0.09f * k)) } }
                }
            }
        }
        .clip(shape)
        .drawBehind {
            val k = progress.value
            drawRect(lerp(g.panel, g.raised, k))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.04f else 0.25f), 0.4f to Color.Transparent))
            drawRect(grainBrush, alpha = g.noiseAlpha)
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = Stroke(width = 2.dp.toPx())
            val rim = g.rimBrush()
            onDrawWithContent {
                drawContent()
                val k = progress.value
                // 2 dp centred on the clipped edge = a 1 dp rim inside the card.
                drawOutline(outline, rim, style = stroke)
                if (k > 0.01f) drawOutline(outline, accent.copy(alpha = 0.55f * k), style = stroke)
            }
        }
}

// ── One pane ──

/** One pane's content in Aurora: header, search, paste / progress capsules and the animated listing. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AuroraPaneLayout(
    modifier: Modifier,
    paneState: PaneState,
    currentLoc: Loc,
    currentFile: File?,
    entries: List<Loc>,
    entriesFor: String?,
    loading: Boolean,
    loadError: String?,
    highlightId: String?,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    showActions: Boolean,
    menuFor: Loc?,
    favTick: Int,
    isFavorite: (Loc) -> Boolean,
    actionsFor: (Loc) -> FileRowActions,
    pickMode: Boolean,
    pickDirMode: Boolean,
    pickerTitle: String?,
    onPickFolder: (File) -> Unit,
    clipboard: List<Loc>,
    isCut: Boolean,
    onPaste: () -> Unit,
    onCancelPaste: () -> Unit,
    operationRunning: Boolean,
    operationLabel: String,
    operationProgress: Float?,
    onCancelOperation: (() -> Unit)?,
    pullState: PullToRefreshState,
    listState: LazyListState,
    gridState: LazyGridState,
    exitScroll: PaneScroll,
    archivePane: ArchivePaneState,
    onNavigate: (File) -> Unit,
    onPinCurrent: () -> Unit,
    onJumpFavorite: (File) -> Unit,
    onUnpinFavorite: (File) -> Unit,
    dualPane: Boolean = false,
) {
    val reduced = LocalReducedMotion.current
    val g = LocalGlass.current
    Column(modifier) {
        if (pickMode && !pickerTitle.isNullOrEmpty()) {
            Text(
                pickerTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        if (pickDirMode && currentFile != null) {
            AccentButton(
                "Select this folder",
                onClick = { onPickFolder(currentFile) },
                icon = Icons.Filled.Check,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        AuroraPaneHeader(paneState, currentLoc, currentFile, dualPane, onNavigate)
        AnimatedVisibility(
            visible = paneState.showSearch && !paneState.showFavorites,
            enter = if (reduced) EnterTransition.None else expandVertically(tween(260, easing = BfeMotion.EmphasizedDecelerate)) + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically(tween(200, easing = BfeMotion.EmphasizedAccelerate)) + fadeOut(),
        ) {
            GlassSearchField(
                value = paneState.searchQuery,
                onValueChange = { paneState.searchQuery = it },
                placeholder = "Filter this folder",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        CapsuleSlot(visible = clipboard.isNotEmpty()) {
            val what = if (clipboard.size == 1) clipboard.first().name else "${clipboard.size} items"
            InfoCapsule(
                icon = Icons.Filled.ContentPaste,
                text = "Paste $what${if (isCut) " (move)" else ""} here",
                actionLabel = "Cancel",
                onAction = onCancelPaste,
                onClick = onPaste,
            )
        }
        CapsuleSlot(visible = operationRunning) {
            OperationCapsule(operationLabel, operationProgress, onCancelOperation)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = paneState.showFavorites,
                transitionSpec = {
                    if (reduced) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(320, easing = BfeMotion.EmphasizedDecelerate)) { it / 14 }) togetherWith
                        fadeOut(tween(160))
                },
                label = "paneFavorites",
            ) { fav ->
                if (fav) {
                    AuroraFavoritesList(currentFile, favTick, onPinCurrent, onJumpFavorite, onUnpinFavorite)
                } else {
                    Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
                        val shownEntries = if (paneState.searchQuery.isBlank()) entries
                        else entries.filter { it.name.contains(paneState.searchQuery, ignoreCase = true) }
                        val inArchive = currentLoc as? Loc.ArchiveLoc
                        when {
                            loading && entries.isEmpty() ->
                                if (inArchive != null) ArchiveOpeningIndicator(archivePane, inArchive.archive.name, onCancel = { paneState.closeArchive(inArchive.archive) })
                                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                                }
                            loadError != null && entries.isEmpty() -> PaneMessage(
                                icon = Icons.Filled.FolderOff,
                                title = loadError,
                                detail = if (inArchive != null) "BFE's 7-Zip couldn't open ${inArchive.archive.name}. It may be damaged, incomplete, or a format 7-Zip can extract but not list."
                                else "It may need permissions BFE doesn't have, or it was removed.",
                                actionLabel = if (inArchive != null) "Close archive" else "Try again",
                                onAction = { if (inArchive != null) paneState.closeArchive(inArchive.archive) else paneState.requestReload() },
                            )
                            entries.isNotEmpty() && shownEntries.isEmpty() -> PaneMessage(
                                icon = Icons.Filled.SearchOff,
                                title = "No matches",
                                detail = "Nothing in this folder matches “${paneState.searchQuery}”.",
                                actionLabel = "Clear filter",
                                onAction = { paneState.searchQuery = "" },
                            )
                            else -> AnimatedListing(
                                paneState = paneState,
                                dirId = entriesFor ?: currentLoc.id,
                                entries = shownEntries,
                                empty = entries.isEmpty(),
                                highlightId = highlightId,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                showActions = showActions,
                                menuFor = menuFor,
                                favTick = favTick,
                                isFavorite = isFavorite,
                                actionsFor = actionsFor,
                                listState = listState,
                                gridState = gridState,
                                exitScroll = exitScroll,
                                dualPane = dualPane,
                            )
                        }
                        if (pullState.verticalOffset > 0.5f || pullState.isRefreshing) {
                            PullToRefreshContainer(
                                state = pullState,
                                containerColor = g.menu,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A folder listing, keyed by folder: changing folder slides the new one in from the side you're heading. */
@Immutable
private class Listing(val dirId: String, val depth: Int, val entries: List<Loc>)

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
private fun AnimatedListing(
    paneState: PaneState,
    dirId: String,
    entries: List<Loc>,
    empty: Boolean,
    highlightId: String?,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    showActions: Boolean,
    menuFor: Loc?,
    favTick: Int,
    isFavorite: (Loc) -> Boolean,
    actionsFor: (Loc) -> FileRowActions,
    listState: LazyListState,
    gridState: LazyGridState,
    exitScroll: PaneScroll,
    dualPane: Boolean,
) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val slidePx = with(density) { 36.dp.roundToPx() }
    // Depth = how far down the location is (folders below the volume root + archive / app-storage levels).
    val depth = paneState.path.count { it == '/' } + paneState.altStack.size
    val listing = remember(dirId, entries) { Listing(dirId, depth, entries) }
    val chrome = LocalFmChrome.current
    AnimatedContent(
        targetState = listing,
        contentKey = { it.dirId },
        transitionSpec = {
            val t = if (reduced) EnterTransition.None togetherWith ExitTransition.None else {
                // Into a folder: arrives from the right. Up / back: from the left.
                val dir = if (targetState.depth >= initialState.depth) 1 else -1
                (slideInHorizontally(tween(340, easing = BfeMotion.EmphasizedDecelerate)) { dir * slidePx } + fadeIn(tween(280, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(240, easing = BfeMotion.EmphasizedAccelerate)) { -dir * slidePx } + fadeOut(tween(180)))
            }
            t using SizeTransform(clip = false)
        },
        label = "folder",
    ) { l ->
        // Exactly one live list drives the pane's hoisted list/grid state (so refreshes keep their place
        // and the BrowserPane effects can scroll it). A folder that is sliding away switches to a copy
        // frozen where it was, so it never jumps to the top on its way out.
        val exiting = transition.targetState == EnterExitState.PostExit
        val frozenList = remember(exiting) { if (exiting) LazyListState(exitScroll.listIndex, exitScroll.listOffset) else null }
        val frozenGrid = remember(exiting) { if (exiting) LazyGridState(exitScroll.gridIndex, exitScroll.gridOffset) else null }
        val bornAt = remember { android.os.SystemClock.uptimeMillis() }
        // Rows fade in, staggered, only while the folder is arriving (not when scrolled into view later).
        fun stagger() = android.os.SystemClock.uptimeMillis() - bornAt < 500
        val bottom = chrome.listBottomPadding
        if (l.entries.isEmpty() && empty) {
            PaneMessage(icon = Icons.Filled.FolderOpen, iconRes = FileIcons.FOLDER_OPEN, title = "This folder is empty")
        } else if (paneState.viewMode.shownIn(dualPane) == FmViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                state = frozenGrid ?: gridState,
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = bottom + 6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(l.entries, key = { _, it -> it.id }) { index, file ->
                    val fav = remember(file.id, favTick) { isFavorite(file) }
                    AuroraGridTile(
                        file = file,
                        actions = actionsFor(file),
                        isFavorite = fav,
                        showActions = showActions,
                        selectionMode = selectionMode,
                        selected = file.id in selectedIds,
                        highlighted = file.id == highlightId,
                        menuExpanded = menuFor == file,
                        modifier = Modifier.animateItemPlacement().staggeredEnter(index, l.dirId, enabled = stagger()),
                    )
                }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            // Measured once per pane (not per row): how much date fits beside a row's name / detail.
            val paneWidth = maxWidth
            LazyColumn(
                state = frozenList ?: listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(l.entries, key = { _, it -> it.id }) { index, file ->
                    val fav = remember(file.id, favTick) { isFavorite(file) }
                    AuroraFileRow(
                        file = file,
                        actions = actionsFor(file),
                        isFavorite = fav,
                        showActions = showActions,
                        compact = paneState.compactRows,
                        dense = paneState.viewMode == FmViewMode.COMPACT,
                        selectionMode = selectionMode,
                        selected = file.id in selectedIds,
                        highlighted = file.id == highlightId,
                        menuExpanded = menuFor == file,
                        paneWidth = paneWidth,
                        modifier = Modifier.animateItemPlacement().staggeredEnter(index, l.dirId, enabled = stagger()),
                    )
                }
            }
        }
    }
}

// ── Pane header ──

/**
 * The glass header of a pane: drive chip (the same drive menu as Classic, acting on this pane),
 * breadcrumbs (tap any folder to jump there; inside an archive the archive's crumbs glow in the second
 * accent), the Favorites star and the view-mode button. In Favorites mode it names the list instead.
 */
@Composable
private fun AuroraPaneHeader(paneState: PaneState, currentLoc: Loc, currentFile: File?, dualPane: Boolean, onNavigate: (File) -> Unit) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 240.dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                if (paneState.showFavorites) {
                    Icon(Icons.Filled.Star, null, tint = cs.primary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Favorites", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onSurface, modifier = Modifier.weight(1f))
                    GlassIconButton(Icons.Filled.Close, "Hide favorites", onClick = { paneState.showFavorites = false }, size = 34.dp, iconSize = 18.dp)
                } else {
                    DriveChip(paneState, iconOnly = narrow)
                    PaneCrumbs(paneState, currentFile, onNavigate, Modifier.weight(1f))
                    GlassIconButton(Icons.Filled.StarBorder, "Show favorites", onClick = { paneState.showFavorites = true }, size = 34.dp, iconSize = 18.dp)
                    GlassIconButton(
                        when (paneState.viewMode.shownIn(dualPane)) {
                            FmViewMode.GRID -> Icons.Filled.GridView
                            FmViewMode.LIST -> Icons.Filled.ViewList
                            FmViewMode.COMPACT -> Icons.Filled.DensitySmall
                        },
                        "View mode",
                        onClick = {
                            // Grid → List → Compact → Grid (List ⇄ Compact in dual pane), for both panes
                            // at once (they share the mode); also the default for a fresh start.
                            paneState.viewMode = paneState.viewMode.nextIn(dualPane)
                            prefs.edit().putString("fmViewMode", paneState.viewMode.name.lowercase()).apply()
                        },
                        size = 34.dp,
                        iconSize = 18.dp,
                    )
                }
            }
        }
        if (currentLoc is Loc.ArchiveLoc && !paneState.showFavorites) {
            Row(Modifier.padding(start = 14.dp, end = 12.dp, bottom = 4.dp)) { ArchiveCrumbBadge(currentLoc) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(g.border))
    }
}

/** "Internal ▾": the pane's drive / pinned storage, opening the drive menu for THIS pane. */
@Composable
private fun DriveChip(paneState: PaneState, iconOnly: Boolean) {
    val chrome = LocalFmChrome.current
    val active = LocalPaneActive.current
    val cs = MaterialTheme.colorScheme
    val g = LocalGlass.current
    val reduced = LocalReducedMotion.current
    var open by remember { mutableStateOf(false) }
    val driveLabel = if (paneState.isAlt && !paneState.isArchive) paneState.altLabel else describeLocation(File(paneState.path)).driveLabel
    val icon = when {
        paneState.isArchive -> Icons.Filled.Archive
        paneState.isAlt && chrome.pinned.any { it.label == paneState.altLabel && it.isRoot } -> Icons.Filled.Security
        paneState.isAlt -> Icons.Filled.Cloud
        describeLocation(File(paneState.path)).storage == FavStorage.SD -> Icons.Filled.SdStorage
        else -> Icons.Filled.Storage
    }
    val fill by animateColorAsState(if (active) g.selection else g.raised, motionTween(reduced, BfeMotion.MEDIUM), label = "chipFill")
    val ink by animateColorAsState(if (active) cs.primary else cs.onSurface, motionTween(reduced, BfeMotion.MEDIUM), label = "chipInk")
    val interaction = remember { MutableInteractionSource() }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(30.dp)
                .pressScale(interaction, 0.95f)
                .clip(CapsuleShape)
                .background(fill)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { open = true }
                .semantics { contentDescription = "Drive: $driveLabel" }
                .padding(start = 9.dp, end = 6.dp),
        ) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
            if (!iconOnly) {
                Spacer(Modifier.width(5.dp))
                Text(driveLabel, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        }
        BfeDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FmDriveMenuItems(paneState, chrome.drives, chrome.pinned, chrome.onOpenPinned, chrome.onAddAppStorage, chrome.onUnpin, onDone = { open = false })
        }
    }
}

/** Breadcrumbs that scroll sideways and keep the deepest folder in view. */
@Composable
private fun PaneCrumbs(paneState: PaneState, currentFile: File?, onNavigate: (File) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val locDisplayPath = currentFile?.absolutePath
        ?: (paneState.altLabel + paneState.altStack.drop(1).joinToString("") { " / ${it.name}" })
    val crumbs = remember(locDisplayPath, paneState.rootPath, paneState.altStack) { paneCrumbs(paneState, currentFile, locDisplayPath, onNavigate) }
    val scroll = rememberScrollState()
    LaunchedEffect(locDisplayPath, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    // Inside an archive, every crumb after the first ("…/folder") is inside the archive.
    val archiveFrom = if (paneState.isArchive) 1 else Int.MAX_VALUE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = 4.dp)
            // Crumbs scrolled off to the left fade out instead of being cut.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (scroll.value > 0) drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 14.dp.toPx() / size.width to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            }
            .horizontalScroll(scroll),
    ) {
        crumbs.forEachIndexed { i, (label, onClick) ->
            val last = i == crumbs.lastIndex
            val inArchive = i >= archiveFrom
            if (i > 0) Text("›", color = cs.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 1.dp))
            Text(
                text = label,
                color = when { inArchive -> cs.tertiary; last -> cs.onSurface; else -> cs.onSurfaceVariant },
                fontSize = 12.5.sp,
                fontWeight = if (last || inArchive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (inArchive) Modifier.background(cs.tertiary.copy(alpha = 0.13f)) else Modifier)
                    .then(if (!last && onClick != null) Modifier.clickable { onClick() } else Modifier)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Rows and tiles ──

/**
 * What a row shows for an entry: the embedded exe / apk icon when found, thumbnail kinds, the detail
 * (item count or size) and the last change as date + time / date only (DateText, phone's 12/24 h).
 */
private class EntryVisual(val exeIcon: ImageBitmap?, val isImage: Boolean, val isVideo: Boolean, val thumbModel: Any, val detail: String, val date: String, val dateOnly: String, val time: String)

@Composable
private fun rememberEntryVisual(file: Loc, apkIconPx: Int): EntryVisual {
    val context = LocalContext.current
    val isDir = file.isDir
    val modified = file.lastModified
    val dateLabel = if (modified > 0) DateText.dateTime(context, modified) else ""
    val dateOnlyLabel = if (modified > 0) DateText.date(context, modified) else ""
    val timeLabel = if (modified > 0) DateText.time(context, modified) else ""
    // Folders show how many entries they hold, counted lazily off the main thread (see ChildCount).
    var itemCount by remember(file.id, modified) { mutableStateOf(if (isDir) ChildCount.cached(file) else null) }
    if (isDir && itemCount == null) {
        LaunchedEffect(file.id, modified) { itemCount = ChildCount.count(context, file) }
    }
    val countLabel = itemCount?.takeIf { it >= 0 }?.let { if (it == 1) "1 item" else "$it items" }
    val detail = if (isDir) countLabel.orEmpty() else StringUtils.formatBytes(file.size)
    val ext = File(file.name).extension.lowercase()
    var exeIcon by remember(file.id) { mutableStateOf<ImageBitmap?>(null) }
    val exeFile = (file as? Loc.FileLoc)?.file
    if (exeFile != null && exeFile.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.id) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(exeFile) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    if (!isDir && ApkIconLoader.isApk(file.name)) {
        LaunchedEffect(file.id, file.lastModified, file.size) {
            val bmp = withContext(Dispatchers.IO) { ApkIconLoader.load(context, file, apkIconPx) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    val thumbModel: Any = (file as? Loc.FileLoc)?.file ?: (file as? Loc.SafLoc)?.docUri ?: ""
    return EntryVisual(exeIcon, !isDir && ext in IMAGE_THUMB_EXTS, !isDir && ext in VIDEO_THUMB_EXTS, thumbModel, detail, dateLabel, dateOnlyLabel, timeLabel)
}

/** An entry's picture in a fixed [size] box: exe/apk icon, image/video thumbnail, or BFE's type icon. */
@Composable
private fun EntryIcon(file: Loc, v: EntryVisual, size: Dp, corner: Dp, badge: Boolean) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            v.exeIcon != null -> Image(bitmap = v.exeIcon, contentDescription = null, modifier = Modifier.size(size * 0.86f))
            v.isImage || v.isVideo -> FileThumb(file.name, v.thumbModel, v.isVideo, size = size, corner = corner, badge = badge)
            else -> FileTypeIcon(file.name, file.isDir, size = size)
        }
    }
}

/** The round selection mark: a ring that fills with the accent and springs a check in. */
@Composable
private fun SelectionMark(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val fill by animateColorAsState(if (checked) cs.primary else Color.Transparent, motionTween(reduced, BfeMotion.SHORT), label = "markFill")
    val ring by animateColorAsState(if (checked) cs.primary else cs.onSurfaceVariant, motionTween(reduced, BfeMotion.SHORT), label = "markRing")
    val tick by animateFloatAsState(if (checked) 1f else 0.3f, motionSpring(reduced, dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "markTick")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape),
    ) {
        Icon(
            Icons.Filled.Check, null, tint = cs.onPrimary,
            modifier = Modifier.size(size * 0.66f).graphicsLayer { scaleX = tick; scaleY = tick; alpha = if (checked) 1f else 0f },
        )
    }
}

/**
 * A list row on glass (List and Compact view): no card per row — rows sit directly on the pane's glass,
 * and selection / the just-created highlight is a rounded accent wash that fades in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuroraFileRow(
    file: Loc,
    actions: FileRowActions,
    isFavorite: Boolean,
    showActions: Boolean,
    compact: Boolean,
    dense: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    menuExpanded: Boolean,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val v = rememberEntryVisual(file, 96)
    // Card rows in a narrow pane (dual pane on a phone): short date over time in small type and a slimmer
    // ⋮, so the name keeps most of the row.
    val narrow = !dense && paneWidth < NARROW_ROW_WIDTH
    val context = LocalContext.current
    val columnDate = if (narrow && file.lastModified > 0) DateText.shortDate(context, file.lastModified) else v.dateOnly
    // Single-line rows: in a narrow pane the time drops off first, then the date, so the name keeps its room.
    val rowDate = when {
        paneWidth >= 410.dp -> v.date
        paneWidth >= 340.dp -> v.dateOnly
        else -> ""
    }
    val wash by animateColorAsState(if (selected || highlighted) g.selection else Color.Transparent, motionTween(reduced, BfeMotion.SHORT), label = "rowWash")
    val iconSize = when { dense -> 22.dp; compact -> 26.dp; narrow -> 30.dp; else -> 34.dp }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(Glass.Row)
            .background(wash)
            .combinedClickable(
                // In selection mode a tap toggles, so a folder can be rattled through without long-presses.
                onClick = { if (selectionMode) actions.onToggleSelect() else actions.onTap() },
                onLongClick = actions.onLongPress,
            )
            .padding(start = 10.dp, end = if (showActions) 0.dp else 10.dp, top = if (dense) 4.dp else if (compact) 4.dp else 7.dp, bottom = if (dense) 4.dp else if (compact) 4.dp else 7.dp),
    ) {
        AnimatedVisibility(
            visible = selectionMode,
            enter = if (reduced) EnterTransition.None else expandHorizontally(tween(260, easing = BfeMotion.EmphasizedDecelerate)) + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkHorizontally(tween(200)) + fadeOut(),
        ) {
            SelectionMark(selected, Modifier.padding(end = 10.dp), size = if (dense) 18.dp else 22.dp)
        }
        EntryIcon(file, v, iconSize, corner = if (dense) 4.dp else 8.dp, badge = !dense && !compact)
        Spacer(Modifier.width(when { narrow -> 8.dp; dense -> 10.dp; else -> 12.dp }))
        if (dense) {
            Box(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(file.name, color = cs.onSurface, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val right = listOf(v.detail, rowDate).filter { it.isNotEmpty() }.joinToString("  ·  ")
                    if (right.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(right, color = cs.onSurfaceVariant, fontSize = 10.5.sp, maxLines = 1)
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = cs.onSurface,
                    fontSize = if (compact || narrow) 13.sp else 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = if (file.name.startsWith(".")) 0.7f else 1f },
                )
                Text(v.detail, color = cs.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Date over time, right-aligned beside the name and detail lines (as in Classic).
            if (columnDate.isNotEmpty()) {
                Spacer(Modifier.width(if (narrow) 6.dp else 8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(columnDate, color = cs.onSurfaceVariant, fontSize = if (narrow) 10.sp else 11.sp, maxLines = 1)
                    Text(v.time, color = cs.onSurfaceVariant.copy(alpha = 0.8f), fontSize = if (narrow) 10.sp else 11.sp, maxLines = 1)
                }
            }
        }
        if (showActions) {
            Box {
                GlassIconButton(
                    Icons.Filled.MoreVert, "Actions",
                    onClick = actions.onMenu,
                    size = if (dense || narrow) 30.dp else 38.dp,
                    iconSize = when { dense -> 16.dp; narrow -> 18.dp; else -> 19.dp },
                )
                BfeDropdownMenu(expanded = menuExpanded, onDismissRequest = actions.onDismissMenu) {
                    FileRowMenu(file, isFavorite, actions)
                }
            }
        }
    }
}

/**
 * A grid tile on glass: a fixed 56 dp picture box and a name that is always two lines tall (minLines =
 * 2), so every tile in a row has the same height whatever the names; ⋮ in the corner, the selection
 * mark top-left while selecting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuroraGridTile(
    file: Loc,
    actions: FileRowActions,
    isFavorite: Boolean,
    showActions: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    menuExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val v = rememberEntryVisual(file, 144)
    val on = selected || highlighted
    val fill by animateColorAsState(if (on) g.selection else g.panel, motionTween(reduced, BfeMotion.SHORT), label = "tileFill")
    val rim by animateColorAsState(if (on) cs.primary.copy(alpha = 0.5f) else g.border, motionTween(reduced, BfeMotion.SHORT), label = "tileRim")
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.padding(4.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interaction, 0.96f)
                .clip(shape)
                .background(fill)
                .border(1.dp, rim, shape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { if (selectionMode) actions.onToggleSelect() else actions.onTap() },
                    onLongClick = actions.onLongPress,
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                EntryIcon(file, v, 56.dp, corner = 10.dp, badge = true)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                file.name,
                color = cs.onSurface,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                minLines = 2,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = selectionMode,
            enter = if (reduced) EnterTransition.None else scaleIn(motionSpring(reduced, 0.6f)) + fadeIn(),
            exit = if (reduced) ExitTransition.None else scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) { SelectionMark(selected, size = 20.dp) }
        if (showActions && !selectionMode) {
            GlassIconButton(
                Icons.Filled.MoreVert, "Actions", onClick = actions.onMenu,
                size = 30.dp, iconSize = 17.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
        BfeDropdownMenu(expanded = menuExpanded, onDismissRequest = actions.onDismissMenu) {
            FileRowMenu(file, isFavorite, actions)
        }
    }
}

// ── Favorites ──

/** The pane's Favorites list on glass: pin the current folder, jump to or unpin a favourite. */
@Composable
private fun AuroraFavoritesList(
    currentDir: File?,
    favTick: Int,
    onPinCurrent: () -> Unit,
    onJump: (File) -> Unit,
    onUnpin: (File) -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val favorites = remember(favTick) { com.the412banner.bfe.util.FavoritesStore.list(context).map(::File).filter { it.exists() } }
    val currentAlreadyPinned = remember(favTick, currentDir?.absolutePath) {
        currentDir != null && com.the412banner.bfe.util.FavoritesStore.isFavorite(context, currentDir.absolutePath)
    }
    val bottom = LocalFmChrome.current.listBottomPadding
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = bottom + 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
                GlassChip(
                    "Pin current folder",
                    onClick = { if (!currentAlreadyPinned && currentDir != null) onPinCurrent() },
                    leading = { Icon(Icons.Filled.PushPin, null, tint = if (currentAlreadyPinned) cs.onSurfaceVariant else cs.primary, modifier = Modifier.size(15.dp)) },
                    modifier = Modifier.graphicsLayer { alpha = if (currentAlreadyPinned || currentDir == null) 0.5f else 1f },
                )
            }
        }
        if (favorites.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No favorites yet — pin a folder with its ⋮ menu to jump back here fast.",
                        color = cs.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
            }
        } else {
            items(favorites, key = { it.absolutePath }) { file ->
                val loc = remember(file.absolutePath) { describeLocation(file) }
                val (badgeBg, badgeFg) = badgeColors(loc)
                val interaction = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .pressScale(interaction, 0.98f)
                        .glass(Glass.Chip, GlassLevel.Raised)
                        .clickable(interactionSource = interaction, indication = null) { onJump(file) }
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    FileTypeIcon(file.name, isDir = true, size = 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, color = cs.onSurface, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                loc.driveLabel, color = badgeFg, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(badgeBg).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(loc.displayPath, color = cs.onSurfaceVariant, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    GlassIconButton(Icons.Filled.Star, "Remove from favorites", onClick = { onUnpin(file) }, tint = cs.primary, size = 40.dp, iconSize = 20.dp)
                }
            }
        }
    }
}

// ── Capsules ──

/** Expands a capsule in under the header and collapses it away when done. */
@Composable
private fun CapsuleSlot(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) EnterTransition.None else expandVertically(motionSpring(reduced, 0.85f, Spring.StiffnessMediumLow)) + fadeIn(tween(220, delayMillis = 60)),
        exit = if (reduced) ExitTransition.None else shrinkVertically(tween(240, easing = BfeMotion.EmphasizedAccelerate)) + fadeOut(tween(160)),
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { content() }
    }
}

/** A tappable glass capsule with an icon, a line of text and an optional trailing action. */
@Composable
private fun InfoCapsule(icon: ImageVector, text: String, actionLabel: String?, onAction: (() -> Unit)?, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val g = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), GlassLevel.Raised, fill = lerp(g.raised, g.selection, 0.5f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(icon, null, tint = cs.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = cs.onSurface, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel, color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CapsuleShape).clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** A pane's running copy / move / delete / extract: label, percentage, Cancel and the shimmer bar. */
@Composable
private fun OperationCapsule(label: String, progress: Float?, onCancel: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), GlassLevel.Raised)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pct = if (progress != null) "  ${(progress * 100).toInt()}%" else ""
            Text("$label$pct", color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (onCancel != null) {
                Text(
                    "Cancel", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CapsuleShape).clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        ShimmerProgressBar(progress, Modifier.padding(end = 8.dp))
    }
}

/** A glass search capsule (auto-focused when it opens). */
@Composable
internal fun GlassSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(42.dp)
            .glass(CapsuleShape, GlassLevel.Raised)
            .padding(start = 14.dp, end = 4.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, fontSize = 13.5.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = 13.5.sp),
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (value.isNotEmpty()) {
            GlassIconButton(Icons.Filled.Close, "Clear filter", onClick = { onValueChange("") }, size = 34.dp, iconSize = 16.dp)
        }
    }
}

// ── Screen-level chrome ──

/**
 * The file manager's top line in Aurora: the small wordmark, the ACTIVE pane's folder (it slides when
 * it changes), its drive and free space, and the "Sharing" chip while the network server runs.
 */
@Composable
internal fun AuroraFmHeader(active: PaneState, pickMode: Boolean, onOpenServer: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val server by ServerManager.state.collectAsState()
    val driveLabel = if (active.isAlt && !active.isArchive) active.altLabel else describeLocation(File(active.path)).driveLabel
    val title = when {
        active.showFavorites -> "Favorites"
        active.isAlt -> active.currentLoc.name.ifBlank { active.altLabel }
        active.path == active.rootPath -> driveLabel
        else -> File(active.path).name.ifBlank { active.path }
    }
    val free = remember(active.path, active.isAlt, active.reloadTick) {
        if (active.isAlt) 0L else runCatching { File(active.path).usableSpace }.getOrDefault(0L)
    }
    val subtitle = when {
        active.isArchive -> "${(active.altStack.firstOrNull() as? Loc.ArchiveLoc)?.archive?.name ?: "Archive"} · read-only"
        free > 0 -> "$driveLabel · ${StringUtils.formatBytes(free)} free"
        else -> driveLabel
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (!pickMode) BrandWordmark(fontSize = 11.5.sp)
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    if (reduced) EnterTransition.None togetherWith ExitTransition.None
                    else (slideInVertically(tween(300, easing = BfeMotion.EmphasizedDecelerate)) { it / 2 } + fadeIn(tween(240))) togetherWith
                        (slideOutVertically(tween(200, easing = BfeMotion.EmphasizedAccelerate)) { -it / 2 } + fadeOut(tween(150)))
                },
                label = "fmTitle",
            ) { t ->
                Text(t, color = cs.onSurface, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, color = cs.onSurfaceVariant, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onOpenServer != null) {
            AnimatedVisibility(server.anyRunning, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                SharingChip(onClick = onOpenServer)
            }
        }
    }
}

/**
 * The floating chrome: in portrait the pill toolbar at the bottom with the selection pill rising above
 * it; in landscape the vertical pill rail at the left and the selection pill along the bottom. Every
 * button does exactly what the Classic toolbar's does, on the ACTIVE pane.
 */
@Composable
internal fun AuroraFmChrome(
    active: PaneState,
    selection: PaneSelectionUi?,
    landscape: Boolean,
    dualPane: Boolean,
    onToggleDualPane: () -> Unit,
    pickMode: Boolean,
    prefs: android.content.SharedPreferences,
    onHome: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val browsing = !active.showFavorites
    val canNew = browsing && !pickMode && !active.isArchive
    // Keep the last selection while the pill animates away.
    var shownSelection by remember { mutableStateOf<PaneSelectionUi?>(null) }
    if (selection != null) shownSelection = selection

    val buttons: @Composable (vertical: Boolean, size: Dp) -> Unit = { vertical, size ->
        GlassIconButton(
            Icons.Filled.ArrowBack, "Back",
            onClick = {
                if (active.isAlt) active.altUp()
                else {
                    val parent = File(active.path).parentFile
                    if (active.canGoUp && parent != null && parent.exists()) active.onOpenDir(parent)
                }
            },
            enabled = active.canGoUp,
            size = size,
        )
        if (onHome != null) GlassIconButton(Icons.Filled.Home, "Home", onClick = onHome, size = size)
        if (vertical && !pickMode) GlassIconButton(Icons.Filled.ViewColumn, if (dualPane) "Single pane" else "Dual pane", onClick = onToggleDualPane, selected = dualPane, size = size)
        GlassIconButton(
            Icons.Filled.Search, "Search",
            onClick = { active.showSearch = !active.showSearch; if (!active.showSearch) active.searchQuery = "" },
            selected = active.showSearch,
            enabled = browsing,
            size = size,
        )
        if (!pickMode) {
            var newMenu by remember { mutableStateOf(false) }
            Box(Modifier.padding(if (vertical) PaddingValues(vertical = 3.dp) else PaddingValues(horizontal = 3.dp))) {
                AccentFab(Icons.Filled.Add, "New folder or file", onClick = { newMenu = true }, enabled = canNew, size = size + 4.dp)
                BfeDropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) { FmNewMenuItems(active, onDone = { newMenu = false }) }
            }
        }
        var sortMenu by remember { mutableStateOf(false) }
        Box {
            GlassIconButton(Icons.Filled.Sort, "Sort", onClick = { sortMenu = true }, enabled = browsing, size = size)
            BfeDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) { FmSortMenuItems(active, prefs, onDone = { sortMenu = false }) }
        }
        if (!vertical && !pickMode) GlassIconButton(Icons.Filled.ViewColumn, if (dualPane) "Single pane" else "Dual pane", onClick = onToggleDualPane, selected = dualPane, size = size)
        if (!pickMode) {
            var tools by remember { mutableStateOf(false) }
            Box {
                GlassIconButton(Icons.Filled.MoreVert, "More", onClick = { tools = true }, size = size)
                BfeDropdownMenu(expanded = tools, onDismissRequest = { tools = false }) { FmToolsMenuItems(active, browsing, onOpenSettings, onDone = { tools = false }) }
            }
        }
    }

    val selEnter = if (reduced) EnterTransition.None else
        slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { it + it / 2 } + fadeIn(tween(200))
    val selExit = if (reduced) ExitTransition.None else
        slideOutVertically(tween(260, easing = BfeMotion.EmphasizedAccelerate)) { it + it / 2 } + fadeOut(tween(180))

    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        if (landscape) {
            val size = ((maxHeight - 40.dp) / 8.4f).coerceIn(34.dp, 42.dp)
            GlassPillRail(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) { buttons(true, size) }
            AnimatedVisibility(
                visible = selection != null,
                enter = selEnter, exit = selExit,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 88.dp, end = 12.dp, bottom = 10.dp),
            ) { shownSelection?.let { SelectionPill(it, wide = true) } }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                AnimatedVisibility(visible = selection != null, enter = selEnter, exit = selExit) {
                    shownSelection?.let { SelectionPill(it, wide = false, modifier = Modifier.padding(bottom = 8.dp)) }
                }
                val count = 4 + (if (onHome != null) 1 else 0) + (if (!pickMode) 2 else 0)
                val size = ((availableWidth - 24.dp - 16.dp - 8.dp) / (count + 0.3f)).coerceIn(36.dp, 44.dp)
                GlassPillBar { buttons(false, size) }
            }
        }
    }
}

/**
 * The multi-select pill: "N selected", All / None, the target of the → actions, then the actions as
 * icon-over-label cells (portrait: up to five a row; landscape: one row).
 */
@Composable
private fun SelectionPill(sel: PaneSelectionUi, wide: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .then(if (wide) Modifier else Modifier.fillMaxWidth())
            .glass(RoundedCornerShape(24.dp), GlassLevel.Floating, haze = com.the412banner.bfe.ui.theme.LocalHazeState.current)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Filled.Close, "Done", onClick = sel.onDone, size = 32.dp, iconSize = 17.dp)
            Spacer(Modifier.width(4.dp))
            AnimatedContent(targetState = sel.count, label = "selCount", transitionSpec = {
                (slideInVertically { if (targetState > initialState) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (targetState > initialState) -it else it } + fadeOut())
            }) { n -> Text("$n selected", color = cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(8.dp))
            Text(
                if (sel.allSelected) "None" else "All",
                color = cs.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CapsuleShape).clickable(onClick = sel.onToggleAll).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            if (!wide) {
                Spacer(Modifier.weight(1f))
                sel.targetName?.let { Text("to ", color = cs.onSurfaceVariant, fontSize = 12.sp); Text(it, color = cs.primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp).padding(end = 8.dp)) }
            } else {
                Spacer(Modifier.width(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    sel.actions.forEach { a -> SelActionCell(a, Modifier.width(72.dp)) }
                }
            }
        }
        if (!wide) {
            sel.actions.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    row.forEach { a -> SelActionCell(a, Modifier.weight(1f)) }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SelActionCell(a: SelAction, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val tint = when (a.tone) { SelTone.DANGER -> cs.error; SelTone.ACCENT -> cs.primary; SelTone.NORMAL -> cs.primary }
    val ink = when (a.tone) { SelTone.DANGER -> cs.error; else -> cs.onSurface }
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(14.dp))
            .graphicsLayer { alpha = if (a.enabled) 1f else 0.38f }
            .clickable(interactionSource = interaction, indication = null, enabled = a.enabled, role = Role.Button, onClick = a.onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Icon(a.icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(a.label, color = ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (a.tone == SelTone.ACCENT) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/**
 * The glass grip between the two panes. Drag freely (20–80 %); on release it settles with a spring on
 * ⅓, ½ or ⅔ when it is near one or was flicked toward one, else stays put. Double-tap = 50/50. The
 * grip brightens to the accent and grows while it is being dragged.
 */
@Composable
internal fun AuroraPaneDivider(
    fraction: Float,
    totalPx: Float,
    onDelta: (Float) -> Unit,
    onFraction: (Float) -> Unit,
    onSettled: (Float) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val cs = MaterialTheme.colorScheme
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val grow by animateFloatAsState(if (dragging) 1f else 0f, motionSpring(reduced, dampingRatio = 0.55f), label = "grip")
    val current = rememberUpdatedState(fraction)
    val snaps = remember { listOf(1f / 3f, 0.5f, 2f / 3f) }
    fun settleTo(target: Float, velocity: Float) {
        scope.launch {
            if (reduced) onFraction(target)
            else animate(current.value, target, initialVelocity = velocity, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)) { v, _ -> onFraction(v) }
            onSettled(target)
        }
    }
    val dragState = rememberDraggableState { dx -> onDelta(dx / totalPx) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(14.dp)
            .fillMaxHeight()
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                onDragStopped = { velocityPx ->
                    val f = current.value
                    val v = velocityPx / totalPx
                    val target = when {
                        v > 0.9f -> snaps.firstOrNull { it > f + 0.01f } ?: f
                        v < -0.9f -> snaps.lastOrNull { it < f - 0.01f } ?: f
                        else -> snaps.minByOrNull { abs(it - f) }?.takeIf { abs(it - f) < 0.05f } ?: f
                    }
                    if (target != f) settleTo(target, v) else onSettled(f)
                },
            )
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { settleTo(0.5f, 0f) }) }
            .semantics { contentDescription = "Resize panes" },
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(44.dp)
                .graphicsLayer { val s = 1f + 0.3f * grow; scaleX = s; scaleY = s }
                .clip(CapsuleShape)
                .drawBehind {
                    drawRect(lerp(g.palette.ink.copy(alpha = 0.35f), cs.primary, grow))
                },
        )
    }
}


@Preview(name = "Aurora selection pill + capsules (dark)", widthDp = 380, heightDp = 360)
@Composable
private fun SelectionPillPreview() {
    com.the412banner.bfe.ui.theme.BfeTheme(style = com.the412banner.bfe.ui.theme.UiStyle.AURORA) {
        Box(Modifier.size(380.dp, 360.dp)) {
            com.the412banner.bfe.ui.theme.AuroraBackdrop(Modifier.matchParentSize())
            Column(Modifier.padding(12.dp)) {
                InfoCapsule(Icons.Filled.ContentPaste, "Paste 3 items here (move)", "Cancel", onAction = {}, onClick = {})
                Spacer(Modifier.height(8.dp))
                OperationCapsule("Copying 2/5 — Game.iso", 0.42f, onCancel = {})
                Spacer(Modifier.height(8.dp))
                GlassSearchField("", {}, "Filter this folder")
            }
            SelectionPill(
                PaneSelectionUi(
                    count = 3, allSelected = false, targetName = "Download",
                    actions = listOf(
                        SelAction("Copy", Icons.Filled.FileCopy, true) {},
                        SelAction("Cut", Icons.Filled.ContentCut, true) {},
                        SelAction("Copy →", Icons.Filled.FileCopy, true, SelTone.ACCENT) {},
                        SelAction("Move →", Icons.Filled.ContentCut, true, SelTone.ACCENT) {},
                        SelAction("Share", Icons.Filled.Share, false) {},
                        SelAction("Compress", Icons.Filled.Archive, true) {},
                        SelAction("Delete", Icons.Filled.Delete, true, SelTone.DANGER) {},
                    ),
                    onToggleAll = {}, onDone = {},
                ),
                wide = false,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}
