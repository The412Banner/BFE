package com.the412banner.bfe.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.the412banner.bfe.storage.DocumentsProviderApps
import com.the412banner.bfe.storage.DocProviderApp
import com.the412banner.bfe.storage.InstalledApp
import com.the412banner.bfe.storage.PinnedLocation
import com.the412banner.bfe.storage.PinnedStorage
import com.the412banner.bfe.storage.RootAccess
import com.the412banner.bfe.storage.RootBackend
import com.the412banner.bfe.storage.SafBackend
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.ui.screens.AddAppStorageDialog
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.util.AppSnackbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.the412banner.bfe.ui.components.pressScale
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home's "App storage" tiles: other apps' storage pinned through Android's document access (SAF) or,
 * with root, an app's private data folder. The same pins the file manager's drive menu lists. Home's
 * ＋ button and the trailing ＋ tile add one with the "Add app storage" picker; a new pin shows up here
 * as a tile without leaving Home. Tap opens it in the file manager; long-press offers to remove it.
 */
@Stable
internal class HomeAppStorage(
    val pins: List<PinnedLocation>,
    val add: () -> Unit,
    val open: (PinnedLocation) -> Unit,
    val remove: (PinnedLocation) -> Unit,
)

@Composable
internal fun rememberHomeAppStorage(refreshKey: Any, onOpen: (FmLocationRequest) -> Unit): HomeAppStorage {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val pins = remember(tick, refreshKey) { PinnedStorage.list(context) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<PinnedLocation?>(null) }
    var pendingLabel by remember { mutableStateOf("App storage") }
    var pendingPkg by remember { mutableStateOf<String?>(null) }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            PinnedStorage.add(context, pendingLabel, uri, pendingPkg)
            tick++
            AppSnackbar.show(context, "Added $pendingLabel to Home")
        }
    }

    fun pickSaf(provider: DocProviderApp?) {
        pendingLabel = provider?.label ?: "App storage"
        pendingPkg = provider?.packageName
        val initial = provider?.let { DocumentsProviderApps.initialTreeUri(context, it.authority) }
        runCatching { treeLauncher.launch(initial) }.onFailure { AppSnackbar.show(context, "No folder picker available") }
    }

    // Root: pin /data/data/<pkg> straight away (no folder picker), plus its external data folder if any.
    fun pickRoot(app: InstalledApp) {
        scope.launch {
            if (!withContext(Dispatchers.IO) { RootAccess.ensure() }) {
                AppSnackbar.show(context, "Root access unavailable — grant BFE root in Magisk to browse app data", long = true)
                return@launch
            }
            PinnedStorage.addRoot(context, app.label, "/data/data/${app.packageName}", app.packageName)
            val ext = "/storage/emulated/0/Android/data/${app.packageName}"
            if (withContext(Dispatchers.IO) { RootBackend.stat(ext)?.isDir == true }) {
                PinnedStorage.addRoot(context, "${app.label} — external data", ext, app.packageName)
            }
            tick++
            AppSnackbar.show(context, "Added ${app.label} to Home")
        }
    }

    fun open(p: PinnedLocation) {
        scope.launch {
            if (p.isRoot) {
                if (!withContext(Dispatchers.IO) { RootAccess.ensure() }) {
                    AppSnackbar.show(context, "Root access unavailable — grant BFE root in Magisk to browse app data", long = true)
                    return@launch
                }
                val root = withContext(Dispatchers.IO) { p.path?.let { RootBackend.stat(it) } }
                if (root != null && root.isDir) onOpen(FmLocationRequest.alt(root, p.label))
                else AppSnackbar.show(context, "Can't open ${p.label} — ${p.path} isn't a folder", long = true)
            } else {
                val uri = p.treeUri ?: return@launch
                val root = withContext(Dispatchers.IO) { SafBackend.rootLoc(context, uri, p.label) }
                if (root != null) onOpen(FmLocationRequest.alt(root, p.label))
                else AppSnackbar.show(context, "Can't open ${p.label} — access may have been revoked", long = true)
            }
        }
    }

    if (showAdd) {
        AddAppStorageDialog(
            onDismiss = { showAdd = false },
            onPick = { provider -> showAdd = false; pickSaf(provider) },
            onPickRoot = { app -> showAdd = false; pickRoot(app) },
        )
    }
    confirmRemove?.let { p ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${p.label}?") },
            text = {
                Text(
                    "It comes off Home and the file manager's storage list. Nothing in the app's storage is deleted; " +
                        "you can add it again any time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { PinnedStorage.remove(context, p); tick++; confirmRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }

    return HomeAppStorage(
        pins = pins,
        add = { showAdd = true },
        open = { open(it) },
        remove = { confirmRemove = it },
    )
}

/** A pinned app-storage tile: the app's own icon, its name and what kind of access it is. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppStorageTileCard(pin: PinnedLocation, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pin.packageName) {
        value = withContext(Dispatchers.IO) {
            pin.packageName?.let { pkg ->
                runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }
        }
    }
    HomeTileFrame(onClick = onClick, onLongClick = onLongClick) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            val bmp = icon
            if (bmp != null) Image(bmp, null, modifier = Modifier.size(32.dp))
            else FileTypeIcon(pin.label, isDir = true, size = 32.dp)
        }
        Spacer(Modifier.height(4.dp))
        HomeTileLabel(pin.label)
        Spacer(Modifier.height(2.dp))
        HomeTileStats(if (pin.isRoot) "App data · root" else "App storage")
    }
}

/** The trailing ＋ tile: adds another app's storage to Home. */
@Composable
internal fun AddAppStorageTile(onClick: () -> Unit) {
    HomeTileFrame(onClick = onClick, onLongClick = null, dashed = true) {
        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        HomeTileLabel("Add app storage")
    }
}

/**
 * Shared tile chrome for Home: the same shape and border for app-storage and dashboard ([wide])
 * tiles; dashboard tiles pass a taller [height]. Aurora Glass: a glass tile that dips when pressed;
 * the ＋ tile is a clear tile with a dashed accent rim.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeTileFrame(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    dashed: Boolean = false,
    wide: Boolean = false,
    height: Dp = HOME_TILE_HEIGHT,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isAurora) {
        val interaction = remember { MutableInteractionSource() }
        val accent = MaterialTheme.colorScheme.primary
        Column(
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = if (dashed || wide) Arrangement.Center else Arrangement.Top,
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .pressScale(interaction, 0.96f)
                .then(
                    if (dashed) Modifier.clip(Glass.Tile).drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            accent.copy(alpha = 0.6f),
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(18.dp.toPx()),
                            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                        )
                    } else Modifier.glass(Glass.Tile, GlassLevel.Panel),
                )
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = if (wide) 8.dp else 4.dp, vertical = 8.dp),
        ) { content() }
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dashed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, if (dashed) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline),
    ) {
        Column(
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = if (dashed || wide) Arrangement.Center else Arrangement.Top,
            modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 8.dp else 4.dp, vertical = 8.dp),
        ) { content() }
    }
}

@Composable
internal fun HomeTileLabel(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.onSurface,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
)

@Composable
internal fun HomeTileStats(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize = 9.5.sp,
    lineHeight = 11.5.sp,
    textAlign = TextAlign.Center,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
)

internal val HOME_TILE_HEIGHT = 107.dp
