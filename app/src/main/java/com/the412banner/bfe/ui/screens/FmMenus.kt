package com.the412banner.bfe.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.cloud.CloudSection
import com.the412banner.bfe.cloud.CloudSetup
import com.the412banner.bfe.cloud.RemoteShortcuts
import com.the412banner.bfe.core.StorageRoot
import com.the412banner.bfe.storage.RemoteBackend
import com.the412banner.bfe.storage.PinnedLocation
import com.the412banner.bfe.util.AppSnackbar

/*
 * The file manager's menus, item for item: the drive menu, New ▾, Sort and the tools overflow (⋮).
 * Both looks open exactly these items — Classic from SharedToolbar, Aurora Glass from the pane header's
 * drive chip and the floating pill / rail — so the two can never drift apart. Every item calls
 * [onDone] (closing its menu) before or after its action, as the toolbar always did.
 */

/**
 * Drives, pinned app storage (with ✕ to unpin), the Cloud / Remote accounts (opened as folders through
 * rclone, see [com.the412banner.bfe.storage.RemoteBackend]), then "Add app storage". Acts on [active].
 */
@Composable
internal fun FmDriveMenuItems(
    active: PaneState,
    drives: List<StorageRoot>,
    pinned: List<PinnedLocation>,
    onOpenPinned: (PinnedLocation) -> Unit,
    onAddAppStorage: () -> Unit,
    onUnpin: (PinnedLocation) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
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
                onDone()
                active.exitAlt()
                if (drive.readable) active.onOpenDrive(drive.dir)
                else AppSnackbar.show(context, "${drive.label} is mounted but not readable right now")
            },
        )
    }
    // Pinned SAF "app storage" locations, then the "+ Add app storage" entry.
    pinned.forEach { p ->
        MenuItemDivider()
        DropdownMenuItem(
            text = { Text(p.label) },
            leadingIcon = { Icon(if (p.isRoot) Icons.Filled.Security else Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                Icon(
                    Icons.Filled.Close, "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).clickable { onDone(); onUnpin(p) },
                )
            },
            onClick = { onDone(); onOpenPinned(p) },
        )
    }
    // Remotes (SMB, SFTP, FTP, WebDAV, cloud accounts) as they were last listed on the Cloud / Remote
    // screens; each opens at its start folder, with rclone started on demand.
    val remotes = remember { RemoteShortcuts.list(context) }
    remotes.forEach { r ->
        MenuItemDivider()
        DropdownMenuItem(
            text = { Text(r.name) },
            leadingIcon = {
                Icon(if (r.section == CloudSection.REMOTE) Icons.Filled.Lan else Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            },
            onClick = {
                onDone()
                active.showFavorites = false
                active.openAltRoot(RemoteBackend.rootLoc(r.name, CloudSetup.startPath(context, r.name)), r.name)
            },
        )
    }
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Add app storage") },
        leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
        onClick = { onDone(); onAddAppStorage() },
    )
}

/** New ▾: Folder or File… in [active]. */
@Composable
internal fun FmNewMenuItems(active: PaneState, onDone: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Folder") },
        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDone(); active.showNewFolderDialog = true },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("File…") },
        leadingIcon = { Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDone(); active.showNewFileDialog = true },
    )
}

/** Sort: folder order, file order, row density and hidden files for [active] (also the next pane's defaults). */
@Composable
internal fun FmSortMenuItems(active: PaneState, prefs: android.content.SharedPreferences, onDone: () -> Unit) {
    // Folders are always listed first; each group has its own order.
    @Composable
    fun SortHeader(text: String) = Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
    @Composable
    fun SortChoice(label: String, checked: Boolean, onPick: () -> Unit) = DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            else Spacer(Modifier.size(24.dp))
        },
        onClick = {
            onPick()
            prefs.edit()
                .putString("fmFolderSortBy", active.folderSortBy).putBoolean("fmFolderSortDesc", active.folderSortDesc)
                .putString("fmFileSortBy", active.sortBy).putBoolean("fmFileSortDesc", active.sortDesc)
                .apply()
            onDone()
            active.requestReload()
        },
    )
    SortHeader("Folders")
    listOf(
        Triple("Name (A → Z)", "name", false), Triple("Name (Z → A)", "name", true),
        Triple("Newest first", "date", true), Triple("Oldest first", "date", false),
    ).forEach { (label, key, desc) ->
        SortChoice(label, active.folderSortBy == key && active.folderSortDesc == desc) {
            active.folderSortBy = key; active.folderSortDesc = desc
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    SortHeader("Files")
    listOf(
        Triple("Name (A → Z)", "name", false), Triple("Name (Z → A)", "name", true),
        Triple("Newest first", "date", true), Triple("Oldest first", "date", false),
        Triple("Largest first", "size", true), Triple("Smallest first", "size", false),
        Triple("Type", "type", false),
    ).forEach { (label, key, desc) ->
        SortChoice(label, active.sortBy == key && active.sortDesc == desc) {
            active.sortBy = key; active.sortDesc = desc
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    DropdownMenuItem(
        text = { Text(if (active.compactRows) "Comfortable rows" else "Compact rows") },
        onClick = {
            active.compactRows = !active.compactRows
            prefs.edit().putBoolean("fmCompactRows", active.compactRows).apply()
            onDone()
        },
    )
    DropdownMenuItem(
        text = { Text(if (active.showHidden) "Hide hidden files" else "Show hidden files") },
        onClick = {
            active.showHidden = !active.showHidden
            prefs.edit().putBoolean("fmShowHidden", active.showHidden).apply()
            onDone()
            active.requestReload()
        },
    )
}

/** The tools overflow (⋮): New folder / file (when browsing a writable folder), APK tools, Settings. */
@Composable
internal fun FmToolsMenuItems(active: PaneState, browsing: Boolean, onOpenSettings: () -> Unit, onDone: () -> Unit) {
    if (browsing && !active.isArchive) {
        DropdownMenuItem(
            text = { Text("New folder…") },
            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDone(); active.showNewFolderDialog = true },
        )
        MenuItemDivider()
        DropdownMenuItem(
            text = { Text("New file…") },
            leadingIcon = { Icon(Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDone(); active.showNewFileDialog = true },
        )
        MenuItemDivider()
    }
    DropdownMenuItem(
        text = { Text("Clone installed app…") },
        leadingIcon = { Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDone(); active.onCloneInstalledApp() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Signing keys…") },
        leadingIcon = { Icon(Icons.Filled.Security, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDone(); active.onSigningKeys() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Settings") },
        leadingIcon = { Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDone(); onOpenSettings() },
    )
}
