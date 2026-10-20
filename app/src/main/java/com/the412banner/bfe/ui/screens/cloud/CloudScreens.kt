@file:OptIn(ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.cloud

import com.the412banner.bfe.ui.components.bfeTopBarColors
import com.the412banner.bfe.ui.components.bfeCardColors
import com.the412banner.bfe.ui.components.bfeScreenColor
import androidx.compose.material3.TopAppBarDefaults
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.icons.BfeIcon
import com.the412banner.bfe.cloud.CloudSection
import com.the412banner.bfe.cloud.CloudServices
import com.the412banner.bfe.cloud.CloudSetup
import com.the412banner.bfe.cloud.RcAbout
import com.the412banner.bfe.cloud.RcProvider
import com.the412banner.bfe.cloud.RcRemote
import com.the412banner.bfe.cloud.RcloneConfigFile
import com.the412banner.bfe.cloud.RcloneDaemon
import com.the412banner.bfe.cloud.RcloneManager
import com.the412banner.bfe.cloud.RemoteShortcuts
import com.the412banner.bfe.storage.RemoteBackend
import com.the412banner.bfe.ui.screens.home.FmLocationRequest
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.outlinedMenuCard
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cloud storage accounts (Google Drive, OneDrive, Dropbox, MEGA, S3…): the Home screen's Cloud tile.
 * Everything runs on rclone ([RcloneManager] downloads it on first use, [RcloneDaemon] runs it).
 */
@Composable
fun CloudScreen(onBack: () -> Unit, onOpenInFiles: ((FmLocationRequest) -> Unit)? = null) =
    RcloneSection(CloudSection.CLOUD, onBack, onOpenInFiles)

/**
 * Connections to servers (SFTP, FTP, SMB, WebDAV): the Home screen's Remote tile. [onOpenInFiles] shows
 * a connection (or one of its folders) in the file manager, where it copies and pastes like any folder.
 */
@Composable
fun RemoteScreen(onBack: () -> Unit, onOpenInFiles: ((FmLocationRequest) -> Unit)? = null) =
    RcloneSection(CloudSection.REMOTE, onBack, onOpenInFiles)

internal fun sectionTitle(section: CloudSection) = if (section == CloudSection.CLOUD) "Cloud storage" else "Remote connections"

/** In-memory caches shared by the screens: quota per remote, and rclone's backend list (~340 KB of JSON). */
internal object CloudCache {
    private val quota = HashMap<String, Pair<RcAbout?, Long>>()
    @Volatile var providers: List<RcProvider>? = null

    fun cachedQuota(name: String): RcAbout? = synchronized(quota) { quota[name]?.first }

    suspend fun quota(ctx: android.content.Context, name: String): RcAbout? {
        synchronized(quota) { quota[name] }?.let { (v, t) -> if (System.currentTimeMillis() - t < 5 * 60_000) return v }
        val v = withContext(Dispatchers.IO) { runCatching { RcloneDaemon.api(ctx).about(name) }.getOrNull() }
        synchronized(quota) { quota[name] = v to System.currentTimeMillis() }
        return v
    }

    fun forget(name: String) = synchronized(quota) { quota.remove(name) }

    suspend fun providers(ctx: android.content.Context): List<RcProvider> =
        providers ?: withContext(Dispatchers.IO) { RcloneDaemon.api(ctx).providers() }.also { providers = it }
}

private val REMOTE_NAME = Regex("^[\\w\\p{L}\\p{N}.+@]+(?:[ -]+[\\w\\p{L}\\p{N}.+@-]+)*$")

/** Why [name] can't be used as a remote name, or null if it can. */
internal fun nameProblem(name: String, taken: Collection<String>): String? = when {
    name.isBlank() -> "Give it a name"
    !REMOTE_NAME.matches(name) -> "Letters, numbers, spaces and . _ - + @ only"
    taken.any { it.equals(name, ignoreCase = true) } -> "You already have one called that"
    else -> null
}

internal fun uniqueName(base: String, taken: Collection<String>): String {
    val clean = base.replace(Regex("[^\\w\\p{L}\\p{N}.+@ -]"), "").trim().ifEmpty { "remote" }
    if (taken.none { it.equals(clean, ignoreCase = true) }) return clean
    var i = 2
    while (taken.any { it.equals("$clean $i", ignoreCase = true) }) i++
    return "$clean $i"
}

@Composable
private fun RcloneSection(section: CloudSection, onBack: () -> Unit, onOpenInFiles: ((FmLocationRequest) -> Unit)?) {
    val context = LocalContext.current
    // Settle installed / not installed before the first frame (two file checks), so an installed
    // engine never flashes the download page.
    remember { RcloneManager.refresh(context) }
    val install by RcloneManager.state.collectAsState()
    val holder = remember { "screen-${section.name}-${System.nanoTime()}" }
    DisposableEffect(holder) {
        RcloneDaemon.retain(holder)
        onDispose { RcloneDaemon.release(holder) }
    }

    var openRemote by remember { mutableStateOf<RcRemote?>(null) }
    var adding by rememberSaveable(section) { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<CloudSetup.Session?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showTransfers by remember { mutableStateOf(false) }
    val session by CloudSetup.session.collectAsState()

    LaunchedEffect(session?.phase) {
        val s = session ?: return@LaunchedEffect
        if (s.phase is CloudSetup.Phase.Done) {
            AppSnackbar.show(context, if (s.isNew) "Connected ${s.name}" else "Signed in to ${s.name} again")
            CloudSetup.dismiss()
            adding = false
            prefill = null
            CloudCache.forget(s.name)
            reloadKey++
        }
    }

    // The screen itself (not an overlay): clear in Aurora so the shared backdrop shows through.
    Surface(modifier = Modifier.fillMaxSize(), color = bfeScreenColor(MaterialTheme.colorScheme.background)) {
        val remote = openRemote
        when {
            install !is RcloneManager.State.Installed -> InstallGate(section, install, onBack)
            remote != null -> RemoteBrowser(
                remote = remote,
                onBack = { openRemote = null; reloadKey++ },
                onTransfers = { showTransfers = true },
                onOpenInFiles = onOpenInFiles,
            )
            adding -> AddAccountPage(
                section = section,
                prefill = prefill,
                onBack = { adding = false; prefill = null },
            )
            else -> AccountsPage(
                section = section,
                reloadKey = reloadKey,
                onBack = onBack,
                onOpen = { openRemote = it },
                onAdd = { prefill = null; adding = true },
                onTransfers = { showTransfers = true },
                onOpenInFiles = onOpenInFiles,
            )
        }
    }
    session?.let { s ->
        SetupDialogs(s, onEdit = { prefill = s; adding = true; CloudSetup.dismiss() })
    }
    if (showTransfers) TransfersSheet(onDismiss = { showTransfers = false })
}

// ── First use: the rclone download ───────────────────────────────────────

@Composable
private fun InstallGate(section: CloudSection, state: RcloneManager.State, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    val local = remember(state) { RcloneManager.localCopy() }
    val mb = { b: Long -> String.format(java.util.Locale.ENGLISH, "%.1f", b / 1048576.0) }
    Scaffold(
        containerColor = bfeScreenColor(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                colors = bfeTopBarColors(TopAppBarDefaults.topAppBarColors()),
                title = { Text(sectionTitle(section)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (section == CloudSection.CLOUD) Icons.Filled.Cloud else Icons.Filled.Lan, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(sectionTitle(section), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (section == CloudSection.CLOUD)
                    "Browse, download and upload files on Google Drive, OneDrive, Dropbox, Box, pCloud, MEGA, Proton Drive, " +
                        "Yandex Disk, Nextcloud, ownCloud and S3 storage (Amazon, Backblaze B2, Wasabi, Cloudflare R2, MinIO)."
                else
                    "Browse, download and upload files on your own servers and network shares: SFTP, FTP/FTPS, SMB " +
                        "(Windows shares, NAS) and WebDAV.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Card(
                colors = bfeCardColors(CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("One-time download", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "BFE connects through rclone, a free open-source (MIT) sync engine. It isn't built into BFE, " +
                            "to keep the app small: it's downloaded once (${mb(RcloneManager.GZ_SIZE)} MB, " +
                            "${mb(RcloneManager.BIN_SIZE)} MB installed) from BFE's GitHub releases and checked " +
                            "against a pinned SHA-256 before it ever runs.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your sign-ins are stored only on this device, and files go straight between this device and the service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            when (state) {
                is RcloneManager.State.Downloading -> {
                    val frac = if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (state.fromLocal) "Copying " else "Downloading ") + "${mb(state.bytes)} of ${mb(state.total)} MB",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { RcloneManager.cancel() }) { Text("Cancel") }
                }
                RcloneManager.State.Verifying -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Checking and installing…", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    if (state is RcloneManager.State.Failed) {
                        Text(state.reason, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(onClick = { RcloneManager.install(context) }) {
                        Text(if (state is RcloneManager.State.Failed) "Try again" else "Download rclone (${mb(RcloneManager.GZ_SIZE)} MB)")
                    }
                    if (local != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { RcloneManager.install(context, local) }) { Text("Install from the Download folder") }
                    }
                }
            }
        }
    }
}

// ── Accounts list ────────────────────────────────────────────────────────

@Composable
private fun AccountsPage(
    section: CloudSection,
    reloadKey: Int,
    onBack: () -> Unit,
    onOpen: (RcRemote) -> Unit,
    onAdd: () -> Unit,
    onTransfers: () -> Unit,
    onOpenInFiles: ((FmLocationRequest) -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var remotes by remember { mutableStateOf<List<RcRemote>?>(null) }
    var allNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var providers by remember { mutableStateOf(CloudCache.providers) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<RcRemote?>(null) }
    var removeTarget by remember { mutableStateOf<RcRemote?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    BackHandler { onBack() }

    LaunchedEffect(reloadKey, tick) {
        error = null
        try {
            val api = RcloneDaemon.api(context)
            val all = withContext(Dispatchers.IO) { api.remotes() }
            allNames = all.map { it.name }
            // The file manager's drive menu lists these without having to start rclone.
            RemoteShortcuts.save(context, all)
            val mine = all.filter { CloudServices.sectionOf(it.type, it.params) == section }
            remotes = mine
            // Accounts made through "More services" are labelled with rclone's backend description.
            if (providers == null && mine.any { CloudServices.forRemote(it) == null }) {
                providers = runCatching { CloudCache.providers(context) }.getOrNull()
            }
        } catch (e: Exception) {
            error = CloudSetup.friendly(e.message) + RcloneDaemon.logTail().lineSequence().lastOrNull()?.let { if (it.isBlank()) "" else "\n$it" }.orEmpty()
            if (remotes == null) remotes = emptyList()
        }
    }

    Scaffold(
        containerColor = bfeScreenColor(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                colors = bfeTopBarColors(TopAppBarDefaults.topAppBarColors()),
                title = { Text(sectionTitle(section)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    TransfersButton(onTransfers)
                    IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        BfeDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.outlinedMenuCard()) {
                            DropdownMenuItem(
                                text = { Text("About rclone") },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { showMenu = false; showAbout = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove the rclone engine") },
                                leadingIcon = { Icon(Icons.Filled.CloudOff, null) },
                                onClick = { showMenu = false; confirmUninstall = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(if (section == CloudSection.CLOUD) "Add account" else "Add connection") },
            )
        },
        bottomBar = { CloudTransferPill(onClick = onTransfers) },
    ) { pad ->
        val list = remotes
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                list == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Starting rclone…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                list.isEmpty() && error != null -> EmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "rclone isn't responding",
                    text = error.orEmpty(),
                    action = "Retry",
                    onAction = { RcloneDaemon.stopNow(); tick++ },
                )
                list.isEmpty() -> EmptyState(
                    icon = if (section == CloudSection.CLOUD) Icons.Filled.Cloud else Icons.Filled.Lan,
                    title = if (section == CloudSection.CLOUD) "No cloud accounts yet" else "No connections yet",
                    text = if (section == CloudSection.CLOUD) "Add Google Drive, OneDrive, Dropbox, MEGA, S3 and more."
                    else "Add an SFTP, FTP, SMB or WebDAV server.",
                    action = if (section == CloudSection.CLOUD) "Add account" else "Add connection",
                    onAction = onAdd,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.name }) { r ->
                        AccountCard(
                            remote = r,
                            providers = providers,
                            onOpen = {
                                if (r.type in CloudServices.OAUTH_TYPES && !r.hasToken) CloudSetup.reconnect(context, r) else onOpen(r)
                            },
                            onRename = { renameTarget = r },
                            onReconnect = { CloudSetup.reconnect(context, r) },
                            onRemove = { removeTarget = r },
                            onOpenInFiles = onOpenInFiles?.let { open ->
                                {
                                    val start = CloudSetup.startPath(context, r.name)
                                    open(FmLocationRequest.alt(RemoteBackend.rootLoc(r.name, start), r.name))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { r ->
        var name by remember(r) { mutableStateOf(r.name) }
        val problem = if (name.trim() == r.name) null else nameProblem(name.trim(), allNames)
        OutlinedAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    isError = problem != null,
                    supportingText = if (problem != null) { { Text(problem) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(enabled = problem == null && name.trim() != r.name, onClick = {
                    val new = name.trim()
                    renameTarget = null
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            runCatching {
                                // rclone must be up: it has to see the change right away, and confirms it.
                                RcloneConfigFile.renameSection(context, RcloneDaemon.api(context), r.name, new)
                                CloudSetup.renameStartPath(context, r.name, new)
                            }
                        }
                        res.exceptionOrNull()?.let { AppSnackbar.show(context, "Couldn't rename: ${it.message}") }
                        CloudCache.forget(r.name)
                        tick++
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    removeTarget?.let { r ->
        OutlinedAlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${r.name}?") },
            text = { Text("BFE forgets this account and its sign-in. Nothing on the account itself is changed or deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            runCatching {
                                RcloneDaemon.api(context).deleteRemote(r.name)
                                CloudSetup.setStartPath(context, r.name, "")
                            }
                        }
                        res.exceptionOrNull()?.let { AppSnackbar.show(context, "Couldn't remove: ${it.message}") }
                        CloudCache.forget(r.name)
                        tick++
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
        )
    }

    if (showAbout) {
        val version by produceState("…") {
            value = withContext(Dispatchers.IO) { runCatching { RcloneDaemon.api(context).version() }.getOrDefault("not running") }
        }
        OutlinedAlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Filled.Verified, null) },
            title = { Text("rclone") },
            text = {
                Text(
                    "Engine: rclone $version (a trimmed Android build: ${RcloneManager.BIN_SIZE / 1048576} MB, " +
                        "SHA-256 ${RcloneManager.BIN_SHA256.take(12)}…)\n\n" +
                        "rclone is free software by Nick Craig-Wood and contributors, MIT licensed. Its notice and the " +
                        "licences of the libraries it uses are in Settings → Open-source licenses.\n\n" +
                        "Your accounts are kept in BFE's private storage on this device, encrypted with BFE's " +
                            "credential key (see Settings → Security).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rclone.org"))) }
                }) { Text("rclone.org") }
            },
            dismissButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } },
        )
    }

    if (confirmUninstall) {
        OutlinedAlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Remove the rclone engine?") },
            text = {
                Text(
                    "Frees ${RcloneManager.BIN_SIZE / 1048576} MB. Your accounts stay saved; Cloud and Remote offer the " +
                        "download again next time.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmUninstall = false; RcloneManager.uninstall(context) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountCard(
    remote: RcRemote,
    providers: List<RcProvider>?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onReconnect: () -> Unit,
    onRemove: () -> Unit,
    onOpenInFiles: (() -> Unit)?,
) {
    val context = LocalContext.current
    val (icon, color) = remember(remote) { CloudServices.look(remote) }
    val label = CloudServices.labelFor(remote, providers)
    val oauth = remote.type in CloudServices.OAUTH_TYPES
    val needsSignIn = oauth && !remote.hasToken
    val about by produceState(CloudCache.cachedQuota(remote.name), remote.name, needsSignIn) {
        if (!needsSignIn) value = CloudCache.quota(context, remote.name)
    }
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = bfeCardColors(CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            ServiceAvatar(icon, color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(remote.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (needsSignIn) "$label  •  Sign-in needed" else label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (needsSignIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                about?.let { a -> QuotaLine(a) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options") }
                BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.outlinedMenuCard()) {
                    if (onOpenInFiles != null && !needsSignIn) {
                        DropdownMenuItem(
                            text = { Text("Open in file manager") },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                            onClick = { menu = false; onOpenInFiles() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { menu = false; onRename() },
                    )
                    if (oauth) {
                        DropdownMenuItem(
                            text = { Text("Sign in again") },
                            leadingIcon = { Icon(Icons.Filled.Login, null) },
                            onClick = { menu = false; onReconnect() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaLine(a: RcAbout) {
    val text = when {
        a.total > 0 && a.used >= 0 -> "${StringUtils.formatBytes(a.used)} of ${StringUtils.formatBytes(a.total)} used"
        a.used >= 0 && a.free >= 0 -> "${StringUtils.formatBytes(a.used)} used  •  ${StringUtils.formatBytes(a.free)} free"
        a.used >= 0 -> "${StringUtils.formatBytes(a.used)} used"
        a.free >= 0 -> "${StringUtils.formatBytes(a.free)} free"
        else -> return
    }
    Spacer(Modifier.height(6.dp))
    if (a.total > 0 && a.used >= 0) {
        LinearProgressIndicator(
            progress = { (a.used.toFloat() / a.total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    // BFE artwork to show instead of [icon] (full colour, slightly faded).
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (iconRes != null) BfeIcon(iconRes, size = 64.dp, alpha = 0.85f)
        else Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}
