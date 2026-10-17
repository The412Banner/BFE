@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.home

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.BuildConfig
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.RootAccess
import com.the412banner.bfe.storage.RootBackend
import com.the412banner.bfe.ui.components.BRAND_NAME
import com.the412banner.bfe.ui.components.BrandWordmark
import com.the412banner.bfe.ui.screens.MenuItemDivider
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.categories.CountSize
import com.the412banner.bfe.ui.screens.categories.MediaCategory
import com.the412banner.bfe.ui.screens.describeLocation
import com.the412banner.bfe.ui.screens.outlinedMenuCard
import com.the412banner.bfe.update.UpdateManager
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.FavoritesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat

/** A tile's identity: its icon and colour (fixed, like the drive badges — they say WHAT, not the theme). */
internal data class TileStyle(val icon: ImageVector, val color: Color)

internal object HomeTileStyles {
    val Main = TileStyle(Icons.Filled.Smartphone, Color(0xFF2563EB))
    val Sd = TileStyle(Icons.Filled.SdStorage, Color(0xFF16A34A))
    val System = TileStyle(Icons.Filled.Memory, Color(0xFF64748B))
    val Downloads = TileStyle(Icons.Filled.Download, Color(0xFF0D9488))
    val Images = TileStyle(Icons.Filled.Image, Color(0xFFD97706))
    val Audio = TileStyle(Icons.Filled.MusicNote, Color(0xFF9333EA))
    val Videos = TileStyle(Icons.Filled.VideoLibrary, Color(0xFFDC2626))
    val Documents = TileStyle(Icons.Filled.Description, Color(0xFF6366F1))
    val Apps = TileStyle(Icons.Filled.Android, Color(0xFF65A30D))
    val Recent = TileStyle(Icons.Filled.FiberNew, Color(0xFFDB2777))
    val Cloud = TileStyle(Icons.Filled.Cloud, Color(0xFF0284C7))
    val Remote = TileStyle(Icons.Filled.Dns, Color(0xFF0E7490))
    val Network = TileStyle(Icons.Filled.Wifi, Color(0xFFEA580C))

    fun of(cat: MediaCategory): TileStyle = when (cat) {
        MediaCategory.IMAGES -> Images
        MediaCategory.AUDIO -> Audio
        MediaCategory.VIDEOS -> Videos
        MediaCategory.DOCUMENTS -> Documents
        MediaCategory.RECENT -> Recent
    }
}

/** One cell of the grid: what it shows and what a tap does. */
private class HomeTile(
    val key: String,
    val label: String,
    val style: TileStyle,
    val stats: String?,
    val fraction: Float? = null,
    val onClick: () -> Unit,
)

/**
 * BFE's first screen: a grid of rounded tiles (4 columns in portrait, more in landscape) for storage,
 * file categories, apps and the network features, each with a live stats line, then a Favorites row.
 * Numbers come from [HomeStatsStore], measured off the main thread and refreshed whenever Home is
 * shown or the app resumes; a tile shows "…" until its number is in.
 */
@Composable
fun HomeScreen(
    onOpenFolder: (FmLocationRequest) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenCategory: (MediaCategory) -> Unit,
    onOpenApps: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by HomeStatsStore.stats.collectAsState()

    // Re-measure whenever Home is (re)shown or BFE comes back to the foreground (ON_RESUME also fires
    // right away when the observer is added to an already-resumed screen).
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                HomeStatsStore.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val favorites = remember(resumeTick) { FavoritesStore.list(context).map(::File).filter { it.isDirectory } }

    // "System" browses "/" through root when BFE has (or is now granted) it, else read-only as a plain folder.
    fun openSystem() {
        scope.launch {
            val root = withContext(Dispatchers.IO) {
                if (RootAccess.available == false) null
                else if (RootAccess.ensure()) RootBackend.stat("/") else null
            }
            if (root != null && root.isDir) onOpenFolder(FmLocationRequest.alt(root, "System"))
            else onOpenFolder(FmLocationRequest.folder(File("/"), File("/")))
        }
    }

    val counts = remember { NumberFormat.getIntegerInstance() }
    fun sizeAndCount(cs: CountSize?, noun: String): String? = cs?.let {
        "${StringUtils.formatBytes(it.bytes)}\n${counts.format(it.count)} $noun${if (it.count == 1) "" else "s"}"
    }
    // Capacity reads like Android's own Settings → Storage (decimal units: a 256 GB phone says 256 GB).
    fun capacity(used: Long, total: Long): String =
        "${Formatter.formatShortFileSize(context, used)} / ${Formatter.formatShortFileSize(context, total)}"

    val tiles = buildList {
        val main = stats.main
        add(HomeTile("main", "Main storage", HomeTileStyles.Main, main?.let { capacity(it.used, it.total) }, main?.fraction) {
            onOpenFolder(FmLocationRequest.folder(main?.dir ?: File("/storage/emulated/0")))
        })
        stats.removable.forEach { v ->
            add(HomeTile("vol:${v.dir.path}", v.label, HomeTileStyles.Sd,
                if (v.total > 0) capacity(v.used, v.total) else if (!v.readable) "Not readable" else null,
                v.fraction.takeIf { v.total > 0 }) {
                if (v.readable) onOpenFolder(FmLocationRequest.folder(v.dir, v.dir))
                else AppSnackbar.show(context, "${v.label} is mounted but not readable right now")
            })
        }
        add(HomeTile("system", "System", HomeTileStyles.System, if (stats.root == true) "Root access" else "Read-only") { openSystem() })
        add(HomeTile("downloads", "Downloads", HomeTileStyles.Downloads,
            stats.downloads?.let { "${StringUtils.formatBytes(it.bytes)}\n${counts.format(it.count)} item${if (it.count == 1) "" else "s"}" }) {
            onOpenFolder(FmLocationRequest.folder(HomeStatsStore.downloadsDir()))
        })
        listOf(MediaCategory.IMAGES, MediaCategory.AUDIO, MediaCategory.VIDEOS, MediaCategory.DOCUMENTS).forEach { cat ->
            add(HomeTile(cat.name, cat.title, HomeTileStyles.of(cat), mediaStats(stats, cat) { sizeAndCount(it, "file") }) { onOpenCategory(cat) })
        }
        val apps = stats.apps
        add(HomeTile("apps", "Apps", HomeTileStyles.Apps, apps?.let {
            val n = "${counts.format(it.count)} app${if (it.count == 1) "" else "s"}"
            if (it.bytes != null) "${StringUtils.formatBytes(it.bytes)}\n$n" else n
        }) { onOpenApps() })
        add(HomeTile(MediaCategory.RECENT.name, MediaCategory.RECENT.title, HomeTileStyles.Recent,
            mediaStats(stats, MediaCategory.RECENT) { sizeAndCount(it, "file") }) { onOpenCategory(MediaCategory.RECENT) })
        add(HomeTile("cloud", "Cloud", HomeTileStyles.Cloud, "Cloud storage") { onOpenCloud() })
        add(HomeTile("remote", "Remote", HomeTileStyles.Remote, "SFTP · SMB · WebDAV") { onOpenRemote() })
        add(HomeTile("network", "Access from network", HomeTileStyles.Network, "HTTP · FTP") { onOpenServer() })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val appStorage = rememberHomeAppStorage(refreshKey = resumeTick, onOpen = onOpenFolder)
        HomeTopBar(
            onOpenFiles = onOpenFiles,
            onAddAppStorage = appStorage.add,
            onOpenSettings = onOpenSettings,
            onRefresh = { HomeStatsStore.refresh(context, force = true) },
        )

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            // Cells are sized for the small app-storage tiles; dashboard tiles take two cells, so the
            // count stays even (no half-empty rows).
            val cells = if (landscape) (maxWidth / 96.dp).toInt().coerceIn(6, 12)
            else (maxWidth / 76.dp).toInt().coerceIn(4, 8)
            val columns = cells - cells % 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(tiles, key = { it.key }, span = { GridItemSpan(2) }) { tile -> HomeTileCard(tile) }

                // Other apps' storage pinned to Home, then a ＋ tile to add another.
                item(key = "appstorage-header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "App storage",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
                items(appStorage.pins, key = { "pin:" + it.key }) { pin ->
                    AppStorageTileCard(pin, onClick = { appStorage.open(pin) }, onLongClick = { appStorage.remove(pin) })
                }
                item(key = "appstorage-add") { AddAppStorageTile(onClick = appStorage.add) }

                item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
                    FavoritesRow(favorites, onOpen = { onOpenFolder(FmLocationRequest.folder(it)) })
                }
            }
        }
    }
}

private fun mediaStats(stats: HomeStats, cat: MediaCategory, fmt: (CountSize?) -> String?): String? =
    if (!stats.media.containsKey(cat)) null else stats.media[cat]?.let { fmt(it) } ?: "Unavailable"

@Composable
private fun HomeTopBar(onOpenFiles: () -> Unit, onAddAppStorage: () -> Unit, onOpenSettings: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // "Check for updates" reports the outcome here; an available update opens the usual update dialog.
    var checkAsked by remember { mutableStateOf(false) }
    val check by UpdateManager.check.collectAsState()
    LaunchedEffect(check, checkAsked) {
        if (!checkAsked) return@LaunchedEffect
        when (check) {
            is UpdateManager.CheckState.UpToDate -> { checkAsked = false; AppSnackbar.show(context, "$BRAND_NAME ${BuildConfig.VERSION_NAME} is the latest version") }
            UpdateManager.CheckState.Failed -> { checkAsked = false; AppSnackbar.show(context, "Couldn't reach GitHub. Check your connection.") }
            is UpdateManager.CheckState.Available -> checkAsked = false
            else -> Unit
        }
    }

    TopAppBar(
        title = { BrandWordmark() },
        actions = {
            IconButton(onClick = onAddAppStorage) {
                Icon(Icons.Filled.Add, "Add app storage", tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.outlinedMenuCard()) {
                    DropdownMenuItem(
                        text = { Text("Open file manager") },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; onOpenFiles() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; onRefresh() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; onOpenSettings() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Check for updates") },
                        leadingIcon = { Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; checkAsked = true; UpdateManager.checkNow(context) },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("About") },
                        leadingIcon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { menu = false; showAbout = true },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )

    if (showAbout) {
        OutlinedAlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Filled.Info, null) },
            title = { Text("$BRAND_NAME ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "A file manager and archive tool for Android. Free software under the GPL-3.0.\n\n" +
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}). Licenses, release notes and " +
                        "update options are in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { UpdateManager.openUrl(context, UpdateManager.REPO_PAGE) }) { Text("Source code") }
                    TextButton(onClick = { showAbout = false; onOpenSettings() }) { Text("Settings") }
                }
            },
        )
    }
}

/** A rounded tile: icon in a tinted circle, label, a two-line stats line and (storage) a usage bar. */
@Composable
private fun HomeTileCard(tile: HomeTile) {
    HomeTileFrame(onClick = tile.onClick, onLongClick = null, wide = true) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(tile.style.color.copy(alpha = 0.16f)),
            ) {
                Icon(tile.style.icon, null, tint = tile.style.color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tile.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // One line where it fits: "2.22 GB · 5,342 files".
                    tile.stats?.replace("\n", " · ") ?: "…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        tile.fraction?.let { f ->
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { f },
                color = tile.style.color,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Favourite folders (the file manager's ★ list) as a scrolling row; a hint when there are none. */
@Composable
private fun FavoritesRow(favorites: List<File>, onOpen: (File) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Favorites", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        }
        if (favorites.isEmpty()) {
            Text(
                "Add a folder with its ⋮ menu → Add to Favorites in the file manager to pin it here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(favorites, key = { it.absolutePath }) { dir ->
                val where = remember(dir.absolutePath) { describeLocation(dir).driveLabel }
                Card(
                    modifier = Modifier.widthIn(min = 120.dp, max = 220.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(dir) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        FileTypeIcon(dir.name, isDir = true, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(dir.name.ifBlank { dir.path }, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(where, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
