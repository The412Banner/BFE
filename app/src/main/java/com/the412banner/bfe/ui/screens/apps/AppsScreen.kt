@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.the412banner.bfe.apk.ApkJobKind
import com.the412banner.bfe.apk.ApkJobManager
import com.the412banner.bfe.apk.ApkJobReport
import com.the412banner.bfe.apk.ApkJobRequest
import com.the412banner.bfe.apk.ApkJobService
import com.the412banner.bfe.apk.ApkJobStage
import com.the412banner.bfe.apk.ApkMeta
import com.the412banner.bfe.apk.ApkRewriter
import com.the412banner.bfe.apk.SigningKeys
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.RootAccess
import com.the412banner.bfe.storage.RootBackend
import com.the412banner.bfe.ui.screens.ApkEditorDialog
import com.the412banner.bfe.ui.screens.ApkResultDialog
import com.the412banner.bfe.ui.screens.KeyManagerDialog
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.home.FmLocationRequest
import com.the412banner.bfe.ui.screens.home.FolderPickerOverlay
import com.the412banner.bfe.ui.screens.outlinedMenuCard
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** The parsed APK the clone editor is open for. */
private class CloneTarget(val meta: ApkMeta, val base: File, val splits: List<File>)

private fun fileUri(context: Context, file: File): Uri? = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun installApk(context: Context, file: File) {
    val uri = fileUri(context, file) ?: run { AppSnackbar.show(context, "Couldn't open ${file.name}"); return }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure { AppSnackbar.show(context, "Couldn't start the installer for ${file.name}") }
}

/**
 * Installed apps: icon, name, version, size and install/update date; User / System / All filter,
 * search, sort. Tapping an app opens its action sheet — Open, App info, Uninstall, Back up APK (a
 * split install becomes a `.apks` bundle), Share APK, Clone… (BFE's APK cloner) and, with root,
 * Browse app data in the file manager.
 *
 * Full sizes need Usage access (or root); until then each row shows its APK size and a banner offers
 * the grant.
 */
@Composable
fun AppsScreen(onBack: () -> Unit, onBrowseData: (FmLocationRequest) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }

    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var sizes by remember { mutableStateOf<Map<String, AppSize>>(emptyMap()) }
    var sizeSource by remember { mutableStateOf(SizeSource.NONE) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(prefs.getString("appsFilter", "user") ?: "user") }
    var sortBy by remember { mutableStateOf(prefs.getString("appsSort", "name") ?: "name") }
    var sortDesc by remember { mutableStateOf(prefs.getBoolean("appsSortDesc", false)) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sheetFor by remember { mutableStateOf<AppEntry?>(null) }
    var backupFor by remember { mutableStateOf<AppEntry?>(null) }
    var pickBackupDir by remember { mutableStateOf(false) }
    var backupDir by remember { mutableStateOf(AppBackups.dir(context)) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    val backup by AppBackups.progress.collectAsState()

    // ── Load the list, then sizes (progressively) ──
    LaunchedEffect(reloadTick) {
        val list = withContext(Dispatchers.IO) { AppsRepository.list(context) }
        apps = list
        val source = withContext(Dispatchers.IO) { AppsRepository.sizeSource(context) }
        sizeSource = source
        if (source != SizeSource.NONE) {
            // User apps first: they're what the default filter shows.
            val ordered = list.sortedBy { it.isSystem }
            withContext(Dispatchers.IO) { AppsRepository.loadSizes(context, ordered, source) { sizes = it } }
        }
    }
    // Back from Settings (Usage access granted?) or the uninstaller: re-read.
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
        if (showSearch) { showSearch = false; query = "" } else onBack()
    }

    fun sizeOf(a: AppEntry): Long = sizes[a.packageName]?.total ?: a.apkBytes
    val shown = remember(apps, sizes, filter, sortBy, sortDesc, query) {
        val base = apps.orEmpty().filter {
            when (filter) {
                "system" -> it.isSystem
                "all" -> true
                else -> !it.isSystem
            }
        }.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
        val cmp: Comparator<AppEntry> = when (sortBy) {
            "size" -> compareBy { sizeOf(it) }
            "installed" -> compareBy { it.firstInstall }
            "updated" -> compareBy { it.lastUpdate }
            else -> compareBy { it.label.lowercase() }
        }
        base.sortedWith(if (sortDesc) cmp.reversed() else cmp)
    }

    // ── Clone… (BFE's APK cloner, the same editor + job the file manager uses) ──
    var cloneTarget by remember { mutableStateOf<CloneTarget?>(null) }
    var showKeys by remember { mutableStateOf(false) }
    var cloneJobId by remember { mutableLongStateOf(0L) }
    var cloneResult by remember { mutableStateOf<ApkJobReport?>(null) }

    fun startClone(app: AppEntry) {
        scope.launch {
            busyLabel = "Reading ${app.label}…"
            val base = File(app.sourceDir)
            val splits = app.splitDirs.map(::File).filter { it.isFile }
            val meta = withContext(Dispatchers.IO) {
                runCatching {
                    SigningKeys.builtIn(context)   // mint the test key off the UI thread
                    ApkRewriter.inspect(base, splits)
                }
            }
            busyLabel = null
            meta.onSuccess { cloneTarget = CloneTarget(it, base, splits) }
                .onFailure { AppSnackbar.show(context, "Couldn't read ${app.label}: ${it.message}", long = true) }
        }
    }

    val apkState by ApkJobManager.state.collectAsState()
    LaunchedEffect(apkState.stage, apkState.jobId) {
        if (cloneJobId == 0L || apkState.jobId != cloneJobId) return@LaunchedEffect
        when (apkState.stage) {
            ApkJobStage.DONE -> {
                cloneJobId = 0L
                val report = apkState.report ?: return@LaunchedEffect
                if (apkState.installAfter) installApk(context, File(report.outputPath)) else cloneResult = report
            }
            ApkJobStage.ERROR, ApkJobStage.CANCELLED -> {
                cloneJobId = 0L
                if (apkState.stage == ApkJobStage.ERROR) AppSnackbar.show(context, "Clone failed: ${apkState.error ?: "unknown error"}", long = true)
            }
            else -> Unit
        }
    }

    cloneTarget?.let { t ->
        val defName = (t.meta.label.ifBlank { t.base.nameWithoutExtension } + "-clone").replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".apk"
        ApkEditorDialog(
            meta = t.meta,
            kind = ApkJobKind.CLONE,
            defaultOutputName = defName,
            onDismiss = { cloneTarget = null },
            onManageKeys = { showKeys = true },
            onConfirm = { r ->
                cloneTarget = null
                val busy = ApkJobService.busyReason()
                if (busy != null) AppSnackbar.show(context, busy)
                else scope.launch {
                    // Clones land in the backup folder (Download/BFE Backups unless changed).
                    val dir = AppBackups.dir(context)
                    val out = withContext(Dispatchers.IO) {
                        dir.mkdirs()
                        var f = File(dir, r.outputName)
                        var i = 1
                        while (f.exists()) { f = File(dir, r.outputName.removeSuffix(".apk") + " ($i).apk"); i++ }
                        f
                    }
                    cloneJobId = ApkJobService.start(
                        context,
                        ApkJobRequest(ApkJobKind.CLONE, t.base.absolutePath, t.splits.map { it.absolutePath }, out.absolutePath, r.edits, r.key, r.schemes, r.installAfter),
                    )
                    AppSnackbar.show(context, "Clone started — saving to ${dir.name}; see the pill for progress")
                }
            },
        )
    }
    if (showKeys) KeyManagerDialog(onDismiss = { showKeys = false })
    cloneResult?.let { report ->
        ApkResultDialog(report = report, onInstall = { cloneResult = null; installApk(context, File(report.outputPath)) }, onDismiss = { cloneResult = null })
    }

    // ── Back up APK: where to (default Download/BFE Backups), then go ──
    backupFor?.let { app ->
        OutlinedAlertDialog(
            onDismissRequest = { backupFor = null },
            icon = { Icon(Icons.Filled.Backup, null) },
            title = { Text("Back up ${app.label}") },
            text = {
                Column {
                    Text(AppBackups.fileName(app), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (app.isSplit) {
                        Text(
                            "Split install (${app.splitDirs.size + 1} APKs): saved as one .apks bundle that split installers (SAI and others) can install.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Save to", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(backupDir.absolutePath.removePrefix("/storage/emulated/0/"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { pickBackupDir = true }) { Text("Change folder…") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    backupFor = null
                    AppBackups.backup(context, app, backupDir) { f ->
                        AppSnackbar.show(context, if (f != null) "Saved ${f.name} to ${f.parentFile?.name}" else "Backup of ${app.label} failed", long = true)
                    }
                }) { Text("Back up") }
            },
            dismissButton = { TextButton(onClick = { backupFor = null }) { Text("Cancel") } },
        )
    }

    sheetFor?.let { app ->
        AppActionsSheet(
            app = app,
            size = sizes[app.packageName],
            sizeSource = sizeSource,
            onDismiss = { sheetFor = null },
            onBackup = {
                if (backup != null) AppSnackbar.show(context, "A backup is already running") else backupFor = app
            },
            onShare = {
                if (backup != null) AppSnackbar.show(context, "A backup is already running")
                else AppBackups.stageForShare(context, app) { f ->
                    val uri = f?.let { fileUri(context, it) }
                    if (f == null || uri == null) AppSnackbar.show(context, "Couldn't prepare ${app.label}'s APK")
                    else runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = if (app.isSplit) "application/octet-stream" else "application/vnd.android.package-archive"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share ${f.name}"))
                    }
                }
            },
            onClone = { startClone(app) },
            onBrowseData = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { RootAccess.ensure() }
                    if (!ok) {
                        AppSnackbar.show(context, "Root access unavailable — grant BFE root in Magisk to browse app data", long = true)
                        return@launch
                    }
                    val root = withContext(Dispatchers.IO) { RootBackend.stat("/data/data/${app.packageName}") }
                    if (root != null && root.isDir) onBrowseData(FmLocationRequest.alt(root, app.label))
                    else AppSnackbar.show(context, "Can't open ${app.label}'s data directory", long = true)
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppsTopBar(
                subtitle = apps?.let { "${shown.size} app${if (shown.size == 1) "" else "s"}" + if (sizeSource != SizeSource.NONE && sizes.isNotEmpty()) " · ${StringUtils.formatBytes(shown.sumOf { sizeOf(it) })}" else "" },
                sortBy = sortBy,
                sortDesc = sortDesc,
                onBack = onBack,
                onSearch = { showSearch = !showSearch; if (!showSearch) query = "" },
                onSort = { key ->
                    if (sortBy == key) sortDesc = !sortDesc else { sortBy = key; sortDesc = key != "name" }
                    prefs.edit().putString("appsSort", sortBy).putBoolean("appsSortDesc", sortDesc).apply()
                },
            )
            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search apps", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            val filters = listOf("user" to "User", "system" to "System", "all" to "All")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                filters.forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = filter == key,
                        onClick = { filter = key; prefs.edit().putString("appsFilter", key).apply() },
                        shape = SegmentedButtonDefaults.itemShape(i, filters.size),
                    ) { Text(label, fontSize = 13.sp) }
                }
            }

            if (sizeSource == SizeSource.NONE && apps != null) {
                SizeAccessBanner(
                    onGrant = { AppsRepository.openUsageAccessSettings(context) },
                    showRoot = RootAccess.available != false,
                    onRoot = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { RootAccess.ensure() }
                            if (ok) reloadTick++ else AppSnackbar.show(context, "Root access unavailable")
                        }
                    },
                )
            }

            // Running backup / share staging, or reading an APK for the cloner.
            val progressLabel = backup?.let { "Backing up ${it.label}…  ${(it.done * 100 / it.total.coerceAtLeast(1)).toInt()}%" } ?: busyLabel
            if (progressLabel != null) {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(progressLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val b = backup
                    if (b != null) LinearProgressIndicator(progress = { b.done.toFloat() / b.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(4.dp))
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val list = apps
                when {
                    list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    shown.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (query.isBlank()) "No apps here" else "No apps match “$query”", fontSize = 15.sp)
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            AppRow(app = app, size = sizes[app.packageName], showSystemBadge = filter == "all", onClick = { sheetFor = app })
                        }
                    }
                }
            }
        }

        if (pickBackupDir) {
            FolderPickerOverlay(
                title = "Save backups to…",
                start = backupDir,
                onPick = { dir -> pickBackupDir = false; backupDir = dir; AppBackups.setDir(context, dir) },
                onDismiss = { pickBackupDir = false },
            )
        }
    }
}

@Composable
private fun AppsTopBar(
    subtitle: String?,
    sortBy: String,
    sortDesc: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSort: (String) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text("Apps")
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, modifier = Modifier.outlinedMenuCard()) {
                    listOf("name" to "Name", "size" to "Size", "installed" to "Install date", "updated" to "Update date").forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(if (sortBy == key) "$label  ${if (sortDesc) "↓" else "↑"}" else label) },
                            onClick = { sortMenu = false; onSort(key) },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun SizeAccessBanner(onGrant: () -> Unit, showRoot: Boolean, onRoot: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Sizes shown are APK only", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "Allow Usage access to see each app's full size (app + data + cache). Android only shares other apps' " +
                    "storage use with apps you allow there" + if (showRoot) ", or with root." else ".",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrant) { Text("Allow usage access", fontSize = 13.sp) }
                if (showRoot) OutlinedButton(onClick = onRoot) { Text("Use root", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String, sizeDp: Int) {
    val context = LocalContext.current
    var icon by remember(pkg) { mutableStateOf<ImageBitmap?>(AppsRepository.cachedIcon(pkg)) }
    if (icon == null) LaunchedEffect(pkg) { icon = withContext(Dispatchers.IO) { AppsRepository.loadIcon(context, pkg, 96) } }
    val b = icon
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    else Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sizeDp.dp))
}

@Composable
private fun AppRow(app: AppEntry, size: AppSize?, showSystemBadge: Boolean, onClick: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AppIcon(app.packageName, 40)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (showSystemBadge && app.isSystem) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "System",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        app.versionName.takeIf { it.isNotBlank() }?.let { "v$it" },
                        size?.let { StringUtils.formatBytes(it.total) } ?: "APK ${StringUtils.formatBytes(app.apkBytes)}",
                    ).joinToString("  ·  "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (app.wasUpdated) "Updated ${dateFormat.format(Date(app.lastUpdate))}" else "Installed ${dateFormat.format(Date(app.firstInstall))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The per-app actions, under a header with the app's details. */
@Composable
private fun AppActionsSheet(
    app: AppEntry,
    size: AppSize?,
    sizeSource: SizeSource,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onShare: () -> Unit,
    onClone: () -> Unit,
    onBrowseData: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val launchIntent = remember(app.packageName) { context.packageManager.getLaunchIntentForPackage(app.packageName) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, 48)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            val details = buildList {
                add("Version ${app.versionName.ifBlank { "—" }} (${app.versionCode})" + if (app.isSystem) " · System app" else "")
                add(
                    when {
                        size != null && sizeSource == SizeSource.USAGE_ACCESS ->
                            "${StringUtils.formatBytes(size.total)} — app ${StringUtils.formatBytes(size.app)}, data ${StringUtils.formatBytes(size.data - size.cache)}, cache ${StringUtils.formatBytes(size.cache)}"
                        size != null -> "${StringUtils.formatBytes(size.total)} — APK ${StringUtils.formatBytes(size.app)}, data ${StringUtils.formatBytes(size.data)}"
                        else -> "APK ${StringUtils.formatBytes(app.apkBytes)}"
                    }
                )
                if (app.isSplit) add("Split install · ${app.splitDirs.size + 1} APKs")
                add("Installed ${dateFormat.format(Date(app.firstInstall))}" + if (app.wasUpdated) " · updated ${dateFormat.format(Date(app.lastUpdate))}" else "")
            }
            details.forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))

            @Composable
            fun Action(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismiss(); onClick() }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                ) {
                    Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }

            if (launchIntent != null) Action("Open", Icons.Filled.OpenInNew) {
                runCatching { context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { AppSnackbar.show(context, "Couldn't open ${app.label}") }
            }
            Action("App info", Icons.Filled.Info) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            Action("Back up APK", Icons.Filled.Backup, onClick = onBackup)
            Action("Share APK", Icons.Filled.Share, onClick = onShare)
            Action("Clone…", Icons.Filled.FileCopy, onClick = onClone)
            // Root is only requested when this is tapped; once root is known to be missing it's hidden.
            if (RootAccess.available != false) Action("Browse app data (root)", Icons.Filled.Security, onClick = onBrowseData)
            // A system app that was never updated can't be uninstalled; an updated one can drop its updates.
            if (!app.isSystem || app.wasUpdated) {
                Action(if (app.isSystem) "Uninstall updates" else "Uninstall", Icons.Filled.Delete, danger = true) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { AppSnackbar.show(context, "Couldn't start the uninstaller") }
                }
            }
        }
    }
}
