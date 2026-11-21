package com.the412banner.bfe.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.cloud.CloudSection
import com.the412banner.bfe.cloud.CloudSetup
import com.the412banner.bfe.cloud.RemoteShortcuts
import com.the412banner.bfe.core.StorageRoots
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.storage.PinnedStorage
import com.the412banner.bfe.storage.RemoteBackend
import com.the412banner.bfe.storage.RootAccess
import com.the412banner.bfe.storage.RootBackend
import com.the412banner.bfe.storage.SafBackend
import com.the412banner.bfe.ui.components.BfePage
import com.the412banner.bfe.ui.screens.FileManagerScreen
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.FavoritesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One starting place in the picker, as on Home: [open] resolves where the browser opens (null = can't). */
private class Place(
    val key: String,
    val label: String,
    val detail: String?,
    val icon: ImageVector,
    val open: suspend () -> FmLocationRequest?,
)

private class PlaceSection(val title: String, val places: List<Place>, val empty: String? = null)

/**
 * BFE's own "choose a folder" page for destinations: first the places Home shows (main storage, SD
 * cards and USB drives, Downloads, Favorites, System through root, pinned app storage, cloud accounts
 * and remote connections), then the file manager's folder-pick mode inside the chosen one, with New
 * folder and "Select this folder". [onPick] gets the folder and a plain name for it ("NAS/Games",
 * "/storage/emulated/0/Games").
 *
 * [deviceOnly] keeps it to folders on this device (the network server can only share those).
 */
@Composable
fun LocationPickerOverlay(
    title: String,
    deviceOnly: Boolean = false,
    onPick: (Loc, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Where the browser opens (the chosen place); null = the places list.
    var browsing by remember { mutableStateOf<FmLocationRequest?>(null) }

    val sections = remember(deviceOnly) {
        val drives = runCatching { StorageRoots.list(context) }.getOrDefault(emptyList())
        val main = drives.firstOrNull { !it.removable }?.dir ?: File("/storage/emulated/0")
        fun folder(dir: File) = FmLocationRequest.folder(dir)
        buildList {
            add(PlaceSection("This device", buildList {
                add(Place("main", "Main storage", main.absolutePath, Icons.Filled.Storage) { folder(main) })
                drives.filter { it.removable }.forEach { d ->
                    add(Place("vol:${d.dir.path}", d.label, if (d.readable) d.dir.absolutePath else "Not readable right now", Icons.Filled.SdStorage) {
                        if (d.readable) FmLocationRequest.folder(d.dir, d.dir) else null
                    })
                }
                val downloads = HomeStatsStore.downloadsDir()
                add(Place("downloads", "Downloads", downloads.absolutePath, Icons.Filled.Download) { folder(downloads) })
            }))
            val favorites = FavoritesStore.list(context).map(::File).filter { it.isDirectory }
            if (favorites.isNotEmpty()) {
                add(PlaceSection("Favorites", favorites.map { f ->
                    Place("fav:${f.path}", f.name.ifBlank { f.path }, f.absolutePath, Icons.Filled.Star) { folder(f) }
                }))
            }
            if (!deviceOnly) {
                add(PlaceSection("System", listOf(
                    Place("system", "System", "Root access", Icons.Filled.Security) {
                        withContext(Dispatchers.IO) {
                            if (RootAccess.ensure()) RootBackend.stat("/")?.takeIf { it.isDir } else null
                        }?.let { FmLocationRequest.alt(it, "System") }
                    },
                )))
                val pinned = PinnedStorage.list(context)
                if (pinned.isNotEmpty()) {
                    add(PlaceSection("App storage", pinned.map { p ->
                        Place("pin:${p.key}", p.label, if (p.isRoot) "App data · root" else "App storage", if (p.isRoot) Icons.Filled.Security else Icons.Filled.Folder) {
                            withContext(Dispatchers.IO) {
                                if (p.isRoot) {
                                    if (RootAccess.ensure()) p.path?.let { RootBackend.stat(it) }?.takeIf { it.isDir } else null
                                } else p.treeUri?.let { SafBackend.rootLoc(context, it, p.label) }
                            }?.let { FmLocationRequest.alt(it, p.label) }
                        }
                    }))
                }
                val remotes = RemoteShortcuts.list(context)
                fun remotePlaces(section: CloudSection) = remotes.filter { it.section == section }.map { r ->
                    Place("rc:${r.name}", r.name, r.type.uppercase(), if (section == CloudSection.REMOTE) Icons.Filled.Lan else Icons.Filled.Cloud) {
                        FmLocationRequest.alt(RemoteBackend.rootLoc(r.name, CloudSetup.startPath(context, r.name)), r.name)
                    }
                }
                add(PlaceSection("Cloud", remotePlaces(CloudSection.CLOUD), empty = "No cloud accounts yet (Home › Cloud)"))
                add(PlaceSection("Remote", remotePlaces(CloudSection.REMOTE), empty = "No connections yet (Home › Remote)"))
            }
        }
    }

    BackHandler { if (browsing != null) browsing = null else onDismiss() }
    BfePage {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 2.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cancel") }
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (browsing != null) {
                    Text(
                        "Places",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { browsing = null }.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
            val b = browsing
            if (b == null) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sections.forEach { section ->
                        item(key = "h:${section.title}") {
                            Text(
                                section.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
                            )
                        }
                        if (section.places.isEmpty() && section.empty != null) {
                            item(key = "e:${section.title}") {
                                Text(
                                    section.empty,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(section.places, key = { it.key }) { place ->
                            PlaceRow(place) {
                                scope.launch {
                                    val req = runCatching { place.open() }.getOrNull()
                                    if (req == null) {
                                        AppSnackbar.show(context, "Can't open ${place.label}", long = true)
                                    } else {
                                        browsing = req
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                FileManagerScreen(
                    pickMode = true,
                    pickDirMode = true,
                    openRequest = b,
                    altRootIsFloor = true,
                    onBackAtRoot = { browsing = null },
                    // Device-only pickers take the plain folder pick (its button only shows in device folders).
                    onPick = if (deviceOnly) ({ f: File -> onPick(Loc.FileLoc(f), f.absolutePath) }) else null,
                    onPickLoc = if (deviceOnly) null else onPick,
                )
            }
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(place.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(place.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            place.detail?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
