package com.the412banner.bfe.ui.screens.server

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.the412banner.bfe.core.StorageRoots
import com.the412banner.bfe.server.LanAddresses
import com.the412banner.bfe.server.ServerActivity
import com.the412banner.bfe.server.ServerManager
import com.the412banner.bfe.server.ServerPrefs
import com.the412banner.bfe.server.ServerService
import com.the412banner.bfe.server.ServerSettings
import com.the412banner.bfe.server.ServerState
import com.the412banner.bfe.server.SharedFolder
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.DirPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * "Access from network": share this phone's files with other devices on the same Wi-Fi (or on the
 * phone's own hotspot) through a web browser (HTTP) or an FTP client. Each protocol has its own big
 * Start / Stop; both run in [ServerService] so they keep going with BFE in the background.
 *
 * What's shared, the credentials and the screen-off option apply to running servers immediately;
 * ports are edited while that server is stopped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val st by ServerManager.state.collectAsState()
    var settings by remember { mutableStateOf(ServerPrefs.load(context)) }

    fun commit(next: ServerSettings) {
        settings = next
        ServerPrefs.save(context, next)
        ServerService.applySettings(context)
    }

    // Where the servers would be reachable, refreshed while the screen is open (also shown before Start).
    var lan by remember { mutableStateOf<List<LanAddresses.LanAddress>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            lan = withContext(Dispatchers.IO) { LanAddresses.detect(context) }
            delay(3000)
        }
    }
    val addresses = if (st.anyRunning) st.addresses else lan

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Access from network") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            st.shareError?.let { WarnCard { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            when {
                st.waitingForNetwork -> WarnCard(Icons.Filled.WifiOff) {
                    Text("Paused: no Wi-Fi", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Connect to Wi-Fi or turn on this phone's hotspot. Sharing resumes by itself.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                lan.isEmpty() && !st.anyRunning -> WarnCard(Icons.Filled.WifiOff) {
                    Text("Not on Wi-Fi", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Join the same Wi-Fi as the other device, or turn on this phone's hotspot and connect the " +
                            "other device to it. BFE never shares over mobile data.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ProtocolCard(
                icon = Icons.Filled.Language,
                title = "Web browser (HTTP)",
                blurb = "Browse, stream, upload and download from any browser — PC, Mac, TV, tablet or phone.",
                scheme = "http",
                running = st.httpRunning,
                wanted = st.wantHttp,
                waiting = st.waitingForNetwork,
                error = st.httpError,
                port = settings.httpPort,
                otherPort = settings.ftpPort,
                addresses = addresses,
                runningPort = st.httpPort,
                loginHint = if (settings.anonymous) "No password needed" else "Sign in as ${settings.user} · ${settings.password}",
                onPortChange = { commit(settings.copy(httpPort = it)) },
                onStart = { ServerService.setRunning(context, http = true) },
                onStop = { ServerService.setRunning(context, http = false) },
            )
            ProtocolCard(
                icon = Icons.Filled.Dns,
                title = "FTP",
                blurb = "For FileZilla, Windows Explorer, macOS Finder (read-only) and file manager apps.",
                scheme = "ftp",
                running = st.ftpRunning,
                wanted = st.wantFtp,
                waiting = st.waitingForNetwork,
                error = st.ftpError,
                port = settings.ftpPort,
                otherPort = settings.httpPort,
                addresses = addresses,
                runningPort = st.ftpPort,
                loginHint = if (settings.anonymous) "Anonymous login" else "User ${settings.user} · password ${settings.password}",
                extraProblem = if (ServerSettings.parsePassive(settings.passivePorts) == null) "Fix the passive port range under Options" else null,
                onPortChange = { commit(settings.copy(ftpPort = it)) },
                onStart = { ServerService.setRunning(context, ftp = true) },
                onStop = { ServerService.setRunning(context, ftp = false) },
            )
            if (st.anyRunning) ActivityCard(st.activity)
            SharesCard(settings, ::commit)
            AccessCard(settings, ::commit)
            OptionsCard(settings, st, ::commit)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Protocol card: big Start / Stop, address + QR ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolCard(
    icon: ImageVector,
    title: String,
    blurb: String,
    scheme: String,
    running: Boolean,
    wanted: Boolean,
    waiting: Boolean,
    error: String?,
    port: Int,
    otherPort: Int,
    addresses: List<LanAddresses.LanAddress>,
    runningPort: Int,
    loginHint: String,
    extraProblem: String? = null,
    onPortChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var portText by rememberSaveable(port) { mutableStateOf(port.toString()) }
    var portError by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val starting = wanted && !running && !waiting && error == null

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        running -> "Running"
                        waiting && wanted -> "Paused — waiting for Wi-Fi"
                        starting -> "Starting…"
                        else -> "Off"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (starting) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(6.dp))

        if (running && addresses.isNotEmpty()) {
            val pick = selected.coerceIn(0, addresses.lastIndex)
            val url = "$scheme://${addresses[pick].ip}:$runningPort/"
            Text("On the other device, open:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    url,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(url))
                    AppSnackbar.show(context, "Address copied")
                }) { Icon(Icons.Filled.ContentCopy, "Copy address") }
            }
            if (addresses.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    addresses.forEachIndexed { i, a ->
                        FilterChip(selected = i == pick, onClick = { selected = i }, label = { Text("${a.kind.label} ${a.ip}") })
                    }
                }
            }
            Text(loginHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCodeImage(url) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Filled.Stop, null)
                Spacer(Modifier.width(8.dp))
                Text("Stop", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Text(blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            addresses.firstOrNull()?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Will be at $scheme://${it.ip}:${portText.ifBlank { "?" }}/",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { v -> portText = v.filter { it.isDigit() }.take(5); portError = null },
                    label = { Text("Port") },
                    singleLine = true,
                    enabled = !wanted,
                    isError = portError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(12.dp))
                if (wanted) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
                } else {
                    Button(
                        onClick = {
                            val p = portText.toIntOrNull()
                            val problem = ServerSettings.portProblem(p)
                                ?: if (p == otherPort) "HTTP and FTP need different ports" else null
                            portError = problem ?: extraProblem
                            if (portError == null && p != null) {
                                if (p != port) onPortChange(p)
                                onStart()
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            portError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Who's connected ──

@Composable
private fun ActivityCard(a: ServerActivity.Snapshot) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Devices, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val n = a.devices.size
                Text(
                    if (n == 0) "No devices connected yet" else "$n ${if (n == 1) "device" else "devices"} connected",
                    style = MaterialTheme.typography.titleSmall,
                )
                val extra = buildList {
                    if (a.devices.isNotEmpty()) add(a.devices.joinToString(", "))
                    if (a.transfers > 0) add("${a.transfers} ${if (a.transfers == 1) "transfer" else "transfers"} running")
                }
                if (extra.isNotEmpty()) {
                    Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (a.recent.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
            a.recent.take(6).forEach { e ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text(
                        fmt.format(Date(e.time)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        "${e.text}  ·  ${e.protocol} ${e.ip}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── What's shared ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharesCard(settings: ServerSettings, commit: (ServerSettings) -> Unit) {
    val context = LocalContext.current
    val volumes = remember { runCatching { StorageRoots.list(context) }.getOrDefault(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val path = DirPicker.resolvePath(result.data) ?: run {
            AppSnackbar.show(context, "That folder can't be shared (pick one on internal storage or an SD card).", long = true)
            return@rememberLauncherForActivityResult
        }
        if (settings.folders.any { it.path == path }) return@rememberLauncherForActivityResult
        val label = volumes.firstOrNull { it.dir.absolutePath == path }?.label ?: File(path).name.ifEmpty { path }
        commit(settings.copy(wholeStorage = false, folders = settings.folders + SharedFolder(path, readOnly = false, label = label)))
    }

    SectionCard {
        Text("What's shared", style = sectionTitle())
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.wholeStorage,
                onClick = { commit(settings.copy(wholeStorage = true)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Whole storage") }
            SegmentedButton(
                selected = !settings.wholeStorage,
                onClick = { commit(settings.copy(wholeStorage = false)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Chosen folders") }
        }
        Spacer(Modifier.height(10.dp))
        if (settings.wholeStorage) {
            FolderRow(
                icon = Icons.Filled.Storage,
                name = ServerSettings.STORAGE_LABEL,
                path = ServerSettings.storageRoot().absolutePath,
                readOnly = settings.wholeStorageReadOnly,
                onReadOnly = { commit(settings.copy(wholeStorageReadOnly = it)) },
                onRemove = null,
            )
            Text(
                "SD cards and USB drives: switch to Chosen folders and add them there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (settings.folders.isEmpty()) {
                Text(
                    "Nothing chosen yet. Add folders or whole drives — each shows up as its own folder on the other device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            settings.folders.forEachIndexed { i, f ->
                FolderRow(
                    icon = if (volumes.any { it.removable && it.dir.absolutePath == f.path }) Icons.Filled.SdStorage else Icons.Filled.Folder,
                    name = f.label,
                    path = f.path,
                    readOnly = f.readOnly,
                    onReadOnly = { ro -> commit(settings.copy(folders = settings.folders.toMutableList().also { it[i] = f.copy(readOnly = ro) })) },
                    onRemove = { commit(settings.copy(folders = settings.folders.filterIndexed { j, _ -> j != i })) },
                )
                if (i < settings.folders.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { picker.launch(DirPicker.buildDirIntent()) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add folder…")
                }
                volumes.filter { v -> v.readable && settings.folders.none { it.path == v.dir.absolutePath } }.forEach { v ->
                    AssistChip(
                        onClick = {
                            commit(settings.copy(folders = settings.folders + SharedFolder(v.dir.absolutePath, readOnly = false, label = v.label)))
                        },
                        label = { Text("Add ${v.label}") },
                        leadingIcon = { Icon(if (v.removable) Icons.Filled.SdStorage else Icons.Filled.Storage, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderRow(
    icon: ImageVector,
    name: String,
    path: String,
    readOnly: Boolean,
    onReadOnly: (Boolean) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Stop sharing $name") }
            }
        }
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = readOnly, onClick = { onReadOnly(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Read-only")
            }
            SegmentedButton(selected = !readOnly, onClick = { onReadOnly(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("Read & write")
            }
        }
    }
}

// ── Who can connect ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessCard(settings: ServerSettings, commit: (ServerSettings) -> Unit) {
    var user by rememberSaveable { mutableStateOf(settings.user) }
    var password by rememberSaveable { mutableStateOf(settings.password) }

    SectionCard {
        Text("Who can connect", style = sectionTitle())
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !settings.anonymous,
                onClick = { commit(settings.copy(anonymous = false)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Password") }
            SegmentedButton(
                selected = settings.anonymous,
                onClick = { commit(settings.copy(anonymous = true)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("No password") }
        }
        Spacer(Modifier.height(10.dp))
        if (!settings.anonymous) {
            OutlinedTextField(
                value = user,
                onValueChange = { v ->
                    user = v.trim()
                    if (user.isNotEmpty()) commit(settings.copy(user = user))
                },
                label = { Text("User name") },
                singleLine = true,
                isError = user.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { v ->
                    password = v
                    if (password.isNotEmpty()) commit(settings.copy(password = password))
                },
                label = { Text("Password") },
                singleLine = true,
                isError = password.isEmpty(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = {
                    IconButton(onClick = {
                        password = ServerPrefs.generatePassword()
                        commit(settings.copy(password = password))
                    }) { Icon(Icons.Filled.Refresh, "New random password") }
                },
                supportingText = {
                    Text(if (password.isEmpty()) "Enter a password" else "Used by both the browser and FTP. Shown here so you can type it on the other device.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (settings.anyWritable) {
            WarnCard {
                Text("Anyone on this network can change files", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Without a password, every device on this Wi-Fi can upload, rename and delete in folders set to " +
                        "Read & write. Use a password, or make the shared folders read-only.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text(
                "Anyone on this network can browse and download what's shared. Nothing can be changed (all read-only).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Options ──

@Composable
private fun OptionsCard(settings: ServerSettings, st: ServerState, commit: (ServerSettings) -> Unit) {
    var passive by rememberSaveable { mutableStateOf(settings.passivePorts) }
    val passiveOk = ServerSettings.parsePassive(passive) != null

    SectionCard {
        Text("Options", style = sectionTitle())
        SwitchRow(
            "Keep running with the screen off",
            "Holds a wake lock so transfers don't stall when the screen sleeps. Uses more battery.",
            settings.keepAwake,
        ) { commit(settings.copy(keepAwake = it)) }
        SwitchRow("Start the web server when BFE opens", null, settings.startHttpOnLaunch) {
            commit(settings.copy(startHttpOnLaunch = it))
        }
        SwitchRow("Start the FTP server when BFE opens", null, settings.startFtpOnLaunch) {
            commit(settings.copy(startFtpOnLaunch = it))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = passive,
            onValueChange = { v ->
                passive = v.filter { it.isDigit() || it == '-' }.take(11)
                if (ServerSettings.parsePassive(passive) != null) commit(settings.copy(passivePorts = passive))
            },
            label = { Text("FTP passive ports") },
            singleLine = true,
            enabled = !st.wantFtp,
            isError = !passiveOk,
            supportingText = {
                Text(
                    if (passiveOk) "FTP data connections use these ports (default ${ServerSettings.DEFAULT_PASSIVE_PORTS})."
                    else "Use a range like 50000-50050 (1024 or higher, at most 1000 ports).",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ── Shared bits (same card look as the Unpack screen) ──

@Composable
private fun sectionTitle() = MaterialTheme.typography.labelLarge.copy(
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold,
)

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable
private fun WarnCard(icon: ImageVector = Icons.Filled.Warning, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), content = content)
        }
    }
}
