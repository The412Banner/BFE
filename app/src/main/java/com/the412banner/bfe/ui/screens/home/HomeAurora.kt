package com.the412banner.bfe.ui.screens.home

import android.content.res.Configuration
import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.server.ServerManager
import com.the412banner.bfe.ui.LaunchOrigin
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.components.BrandWordmark
import com.the412banner.bfe.ui.components.CapsuleShape
import com.the412banner.bfe.ui.components.GlassIconButton
import com.the412banner.bfe.ui.components.GlassPillBar
import com.the412banner.bfe.ui.components.GlassPillRail
import com.the412banner.bfe.ui.components.SectionLabel
import com.the412banner.bfe.ui.components.pressScale
import com.the412banner.bfe.ui.components.staggeredEnter
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.ui.screens.MenuItemDivider
import com.the412banner.bfe.ui.screens.describeLocation
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.glass
import java.io.File

/**
 * Home in Aurora Glass: the same tiles, stats and favourites as Classic, grouped under calm section
 * labels (Storage · Library · Connect · Favorites) on glass over the backdrop, with the wordmark and a
 * one-line summary up top and the actions in the floating pill (portrait) or pill rail (landscape).
 * Tiles keep Classic's compact metrics (107 dp tall, 36 dp icon circle) so the grid stays dense.
 *
 * Opening a tile records where it was ([LaunchOrigin]) so the next screen grows out of it.
 */
@Composable
internal fun AuroraHome(
    tiles: List<HomeTile>,
    favorites: List<File>,
    stats: HomeStats,
    onOpenFolder: (FmLocationRequest) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val root = remember { CoordsHolder() }
    // Tiles fade in, staggered, only as Home first appears (not when one scrolls back into view).
    val bornAt = remember { SystemClock.uptimeMillis() }
    fun launchFrom(c: CoordsHolder) {
        val r = root.coords
        val t = c.coords
        if (r != null && t != null && r.isAttached && t.isAttached) {
            LaunchOrigin.from(r.localPositionOf(t, Offset(t.size.width / 2f, t.size.height / 2f)), r.size.width.toFloat(), r.size.height.toFloat())
        }
    }

    // Same tiles, same order, grouped.
    val storageKeys = setOf("main", "system", "downloads")
    val connectKeys = setOf("cloud", "remote", "network")
    val storage = tiles.filter { it.key in storageKeys || it.key.startsWith("vol:") }
    val connect = tiles.filter { it.key in connectKeys }
    val library = tiles.filter { it !in storage && it !in connect }

    Box(Modifier.fillMaxSize().onGloballyPositioned { root.coords = it }) {
        Row(Modifier.fillMaxSize()) {
            if (landscape) Spacer(Modifier.width(76.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AuroraHomeHeader(stats = stats, onOpenServer = onOpenServer)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val columns = if (landscape) (maxWidth / 96.dp).toInt().coerceIn(6, 12)
                    else (maxWidth / 96.dp).toInt().coerceIn(4, 6)
                    // Entrance order for the staggered fade-in: tiles in reading order.
                    val order = remember(tiles) { tiles.mapIndexed { i, t -> t.key to i }.toMap() }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = if (landscape) 16.dp else 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        listOf("Storage" to storage, "Library" to library, "Connect" to connect).forEach { (label, group) ->
                            if (group.isEmpty()) return@forEach
                            item(key = "label:$label", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(label) }
                            items(group, key = { it.key }) { tile ->
                                AuroraHomeTile(
                                    tile,
                                    Modifier.staggeredEnter(order[tile.key] ?: 0, Unit, enabled = SystemClock.uptimeMillis() - bornAt < 700),
                                    onLaunch = { launchFrom(it) },
                                )
                            }
                        }
                        item(key = "label:favorites", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Favorites") }
                        item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
                            AuroraFavoritesRow(favorites, onOpen = { holder, dir -> launchFrom(holder); onOpenFolder(FmLocationRequest.folder(dir)) })
                        }
                    }
                }
            }
        }
        val files = remember { CoordsHolder() }
        val nav: @Composable () -> Unit = {
            GlassIconButton(Icons.Filled.Home, "Home", onClick = {}, selected = true)
            GlassIconButton(
                Icons.Filled.FolderOpen, "Browse files",
                onClick = { launchFrom(files); onOpenFiles() },
                modifier = Modifier.onGloballyPositioned { files.coords = it },
            )
            GlassIconButton(Icons.Filled.Settings, "Settings", onClick = onOpenSettings)
            HomeMoreButton(onRefresh = onRefresh, onOpenSettings = onOpenSettings)
        }
        if (landscape) {
            GlassPillRail(Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) { nav() }
        } else {
            GlassPillBar(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { nav() }
        }
    }
}

/** Holds a node's coordinates without making them observable state (they change every layout). */
internal class CoordsHolder { var coords: LayoutCoordinates? = null }

/** Wordmark, a one-line summary of the storage, and the live "Sharing" chip while the server runs. */
@Composable
private fun AuroraHomeHeader(stats: HomeStats, onOpenServer: () -> Unit) {
    val context = LocalContext.current
    val server by ServerManager.state.collectAsState()
    val summary = remember(stats.main, stats.removable) {
        val vols = listOfNotNull(stats.main?.let { it.used to it.total }) + stats.removable.filter { it.total > 0 }.map { it.used to it.total }
        val count = 1 + stats.removable.size
        val used = vols.sumOf { it.first }
        buildString {
            append("Home · $count storage${if (count == 1) "" else "s"}")
            if (used > 0) append(" · ${Formatter.formatShortFileSize(context, used)} used")
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BrandWordmark(fontSize = 21.sp)
            Spacer(Modifier.height(2.dp))
            Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(server.anyRunning, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            SharingChip(onClick = onOpenServer)
        }
    }
}

/** "● Sharing" — the network file server is on; tap for its screen. */
@Composable
internal fun SharingChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val ok = androidx.compose.ui.graphics.Color(0xFF4ADE80)
    val pulse = if (reduced) 1f else rememberInfiniteTransition(label = "sharing")
        .animateFloat(1f, 0.45f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "dot").value
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressScale(interaction, 0.95f)
            .glass(CapsuleShape, GlassLevel.Panel)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(ok))
        Spacer(Modifier.width(7.dp))
        Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text("Sharing", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The pill's ⋮: the rest of Home's menu (Refresh, Check for updates, About). */
@Composable
private fun HomeMoreButton(onRefresh: () -> Unit, onOpenSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val checkForUpdates = rememberUpdateCheck()
    Box {
        GlassIconButton(Icons.Filled.MoreVert, "More", onClick = { menu = true })
        BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menu = false; onRefresh() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Check for updates") },
                leadingIcon = { Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menu = false; checkForUpdates() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("About") },
                leadingIcon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menu = false; showAbout = true },
            )
        }
    }
    if (showAbout) HomeAboutDialog(onDismiss = { showAbout = false }, onOpenSettings = onOpenSettings)
}

/**
 * One Home tile on glass: same content and metrics as Classic's (107 dp, 36 dp icon circle, two-line
 * stats, usage bar for storage), pressed tiles dip slightly, and the tile reports its position so the
 * screen it opens can grow from it.
 */
@Composable
private fun AuroraHomeTile(tile: HomeTile, modifier: Modifier = Modifier, onLaunch: (CoordsHolder) -> Unit) {
    val holder = remember { CoordsHolder() }
    val interaction = remember { MutableInteractionSource() }
    val dark = LocalGlass.current.palette.dark
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(107.dp)
            .onGloballyPositioned { holder.coords = it }
            .pressScale(interaction, 0.96f)
            .glass(Glass.Tile, GlassLevel.Panel)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onLaunch(holder); tile.onClick() },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(tile.style.color.copy(alpha = if (dark) 0.20f else 0.15f)),
            ) {
                Icon(tile.style.icon, null, tint = if (dark) lerp(tile.style.color, androidx.compose.ui.graphics.Color.White, 0.25f) else tile.style.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tile.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tile.stats ?: "…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tile.fraction?.let { f ->
                Spacer(Modifier.weight(1f))
                UsageBar(f, tile.style.color, Modifier.fillMaxWidth(0.8f))
            }
        }
    }
}

/** A slim rounded usage bar: the tile's colour fading toward the accent. */
@Composable
private fun UsageBar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(color, lerp(color, accent, 0.5f)))),
        )
    }
}

/** Favourite folders as glass cards in a scrolling row; a calm hint when there are none. */
@Composable
private fun AuroraFavoritesRow(favorites: List<File>, onOpen: (CoordsHolder, File) -> Unit) {
    if (favorites.isEmpty()) {
        Text(
            "Add a folder with its ⋮ menu → Add to Favorites in the file manager to pin it here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(favorites, key = { it.absolutePath }) { dir ->
            val where = remember(dir.absolutePath) { describeLocation(dir).driveLabel }
            val holder = remember { CoordsHolder() }
            val interaction = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 220.dp)
                    .onGloballyPositioned { holder.coords = it }
                    .pressScale(interaction, 0.96f)
                    .glass(Glass.Chip, GlassLevel.Panel)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onOpen(holder, dir) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
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

/**
 * First run in Aurora: the All-files-access explanation as one glass card over the backdrop, with the
 * wordmark and a single accent button. Same text and the same grant flow as Classic's.
 */
@Composable
internal fun AuroraWelcome(onAllow: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .staggeredEnter(0, Unit)
                .glass(Glass.Card, GlassLevel.Raised, glow = LocalGlass.current.glow.copy(alpha = 0.18f))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            com.the412banner.bfe.ui.icons.BfeIcon(com.the412banner.bfe.ui.icons.FileIcons.FOLDER_OPEN, size = 80.dp)
            Spacer(Modifier.height(14.dp))
            Text("Welcome to", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            BrandWordmark(fontSize = 22.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Browse and manage your files, and extract archives, disc images and GOG / Inno Setup " +
                    "installers with the built-in 7-Zip, innoextract and unarc engines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The extraction engines write straight to storage, so BFE needs access to all files. " +
                    "Nothing leaves your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            com.the412banner.bfe.ui.components.AccentButton("Allow access to all files", onClick = onAllow)
        }
    }
}
