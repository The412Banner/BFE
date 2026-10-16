package com.the412banner.bfe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.BuildConfig
import com.the412banner.bfe.ui.components.BRAND_NAME
import com.the412banner.bfe.ui.screens.home.HomePrefs
import com.the412banner.bfe.ui.screens.server.ServerScreen
import com.the412banner.bfe.ui.theme.ThemeSettings
import com.the412banner.bfe.update.UpdateManager
import com.the412banner.bfe.update.UpdateManager.CheckState
import com.the412banner.bfe.util.DefaultApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** One viewable license/notice bundled under assets (sourced from the repo's licenses/ folder). */
private data class LicenseDoc(val title: String, val subtitle: String, val asset: String)

private val LICENSE_DOCS = listOf(
    LicenseDoc(BRAND_NAME, "GNU General Public License v3.0", "LICENSE_BFE_GPL-3.0.txt"),
    LicenseDoc("7-Zip", "GNU LGPL v2.1+ with unRAR restriction", "NOTICE_7ZIP.txt"),
    LicenseDoc("7-Zip license", "Full license text", "License_7zip.txt"),
    LicenseDoc("innoextract", "zlib License, with Boost / liblzma / libiconv", "NOTICE_INNOEXTRACT.txt"),
    LicenseDoc("unarc (FreeArc)", "License and source notice", "NOTICE_UNARC.txt"),
    LicenseDoc("FFmpeg + x264", "GNU GPL v2 or later", "NOTICE_FFMPEG.txt"),
    LicenseDoc("lunzip (lzip)", "GNU GPL v2 or later", "NOTICE_LUNZIP.txt"),
    LicenseDoc("lzop + LZO", "GNU GPL v2 or later", "NOTICE_LZOP.txt"),
    LicenseDoc("lz4", "GNU GPL v2 or later; library BSD 2-Clause", "NOTICE_LZ4.txt"),
    LicenseDoc("unecm (ecm-tools)", "GNU GPL v3 or later", "NOTICE_UNECM.txt"),
    LicenseDoc("unalz", "zlib License, with bzip2 / libiconv (LGPL v2.1+)", "NOTICE_UNALZ.txt"),
    LicenseDoc("unace 1.2b", "GNU GPL v2 or later", "NOTICE_UNACE.txt"),
    LicenseDoc("lzx (amiga-lzx)", "WTFPL, with MIT / Apache-2.0 crates", "NOTICE_LZX.txt"),
    LicenseDoc("EGG / AZO", "EggDotNet (MIT), xunazo (zlib)", "NOTICE_EGG.txt"),
    LicenseDoc("OTA payload", "AOSP update_engine format (Apache 2.0)", "NOTICE_OTA_PAYLOAD.txt"),
    LicenseDoc("NanoHTTPD", "BSD 3-Clause License", "NOTICE_NANOHTTPD.txt"),
    LicenseDoc("Apache FtpServer + MINA", "Apache License 2.0", "NOTICE_APACHE_FTPSERVER.txt"),
    LicenseDoc("rclone", "MIT; downloaded on first use of Cloud / Remote", "NOTICE_RCLONE.txt"),
    LicenseDoc("Libraries", "AndroidX, Coil, libsu, Commons Compress, XZ for Java, zstd-jni, ARSCLib, apksig, Bouncy Castle, SLF4J, QR Code generator", "THIRD_PARTY_LIBRARIES.txt"),
)

/**
 * Settings + About, a full-screen page over the file manager: updates, appearance, default apps,
 * and the about block (version, GitHub, licenses). Sub-pages (default apps, a license text) are
 * simple in-screen states; system Back walks back out of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("main") }
    var licenseAsset by rememberSaveable { mutableStateOf<String?>(null) }

    // "Access from network" lives here until the Home dashboard links to it; it has its own app bar.
    if (page == "server") {
        ServerScreen(onBack = { page = "main" })
        return
    }

    BackHandler {
        when {
            licenseAsset != null -> licenseAsset = null
            page != "main" -> page = "main"
            else -> onBack()
        }
    }

    val title = when {
        licenseAsset != null -> LICENSE_DOCS.firstOrNull { it.asset == licenseAsset }?.title ?: "License"
        page == "licenses" -> "Open-source licenses"
        page == "defaults" -> "Default apps"
        else -> "Settings"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = {
                    when {
                        licenseAsset != null -> licenseAsset = null
                        page != "main" -> page = "main"
                        else -> onBack()
                    }
                }) { Icon(Icons.Filled.ArrowBack, "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                licenseAsset != null -> LicenseText(licenseAsset!!)
                page == "licenses" -> LicenseList { licenseAsset = it }
                page == "defaults" -> DefaultAppsPage()
                else -> MainSettings(
                    onOpenLicenses = { page = "licenses" },
                    onOpenDefaults = { page = "defaults" },
                    onOpenLicense = { licenseAsset = it },
                    onOpenServer = { page = "server" },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettings(
    onOpenLicenses: () -> Unit,
    onOpenDefaults: () -> Unit,
    onOpenLicense: (String) -> Unit,
    onOpenServer: () -> Unit,
) {
    val context = LocalContext.current
    val check by UpdateManager.check.collectAsState()
    var auto by remember { mutableStateOf(UpdateManager.isAutoCheck(context)) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ── Updates ──
        SectionHeader("Updates")
        SettingRow(
            icon = Icons.Filled.SystemUpdate,
            title = "Check for updates automatically",
            subtitle = "Looks for a new stable release when BFE opens, at most every 6 hours.",
            onClick = { auto = !auto; UpdateManager.setAutoCheck(context, auto) },
            trailing = { Switch(checked = auto, onCheckedChange = { auto = it; UpdateManager.setAutoCheck(context, it) }) },
        )
        val last = UpdateManager.lastCheck(context)
        val status = when (val c = check) {
            CheckState.Checking -> "Checking…"
            is CheckState.UpToDate -> "You're up to date."
            is CheckState.Available -> "$BRAND_NAME ${c.info.versionName} is available."
            CheckState.Failed -> "Couldn't reach GitHub. Check your connection."
            CheckState.Idle -> if (last > 0) "Last checked ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))}." else "Not checked yet."
        }
        SettingRow(
            icon = Icons.Filled.NewReleases,
            title = "Version ${BuildConfig.VERSION_NAME}",
            subtitle = status,
            trailing = {
                when (val c = check) {
                    CheckState.Checking -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    is CheckState.Available -> Button(onClick = { UpdateManager.showOffer(c.info) }) { Text("Update") }
                    else -> OutlinedButton(onClick = { UpdateManager.checkNow(context) }) { Text("Check now") }
                }
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // ── Appearance ──
        SectionHeader("Appearance")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            val modes = ThemeSettings.Mode.values()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = ThemeSettings.mode == m,
                        onClick = { ThemeSettings.setMode(context, m) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) {
                        Text(when (m) { ThemeSettings.Mode.SYSTEM -> "System"; ThemeSettings.Mode.LIGHT -> "Light"; ThemeSettings.Mode.DARK -> "Dark" })
                    }
                }
            }
        }
        if (ThemeSettings.dynamicSupported) {
            SettingRow(
                icon = Icons.Filled.Palette,
                title = "Dynamic color",
                subtitle = "Use colors from your wallpaper instead of BFE blue.",
                onClick = { ThemeSettings.setDynamic(context, !ThemeSettings.dynamicColor) },
                trailing = { Switch(checked = ThemeSettings.dynamicColor, onCheckedChange = { ThemeSettings.setDynamic(context, it) }) },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // ── Files ──
        SectionHeader("Files")
        // Which screen BFE opens on: the Home dashboard, or straight into the last folder you had open.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Home, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("Start on", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            var startOn by remember { mutableStateOf(HomePrefs.startOn(context)) }
            val choices = listOf(HomePrefs.START_HOME to "Home", HomePrefs.START_LAST_FOLDER to "Last folder")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                choices.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = startOn == value,
                        onClick = { startOn = value; HomePrefs.setStartOn(context, value) },
                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                    ) { Text(label) }
                }
            }
        }
        SettingRow(
            icon = Icons.Filled.Apps,
            title = "Default apps",
            subtitle = "Apps chosen with “Always use” in Open with.",
            onClick = onOpenDefaults,
            trailing = { Icon(Icons.Filled.ChevronRight, null) },
        )
        SettingRow(
            icon = Icons.Filled.Wifi,
            title = "Access from network",
            subtitle = "Share files with other devices on your Wi-Fi through a browser or FTP.",
            onClick = onOpenServer,
            trailing = { Icon(Icons.Filled.ChevronRight, null) },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // ── About ──
        SectionHeader("About")
        SettingRow(
            icon = Icons.Filled.Description,
            title = "$BRAND_NAME ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            subtitle = "A file manager and archive tool for Android. Free software under the GPL-3.0.",
        )
        SettingRow(
            icon = Icons.Filled.Code,
            title = "Source code on GitHub",
            subtitle = "github.com/The412Banner/BFE",
            onClick = { UpdateManager.openUrl(context, UpdateManager.REPO_PAGE) },
            trailing = { Icon(Icons.Filled.OpenInNew, null) },
        )
        SettingRow(
            icon = Icons.Filled.NewReleases,
            title = "Release notes",
            subtitle = "What changed in each version",
            onClick = { UpdateManager.openUrl(context, UpdateManager.REPO_PAGE + "/releases") },
            trailing = { Icon(Icons.Filled.OpenInNew, null) },
        )
        SettingRow(
            icon = Icons.Filled.Gavel,
            title = "License",
            subtitle = "GNU General Public License v3.0",
            onClick = { onOpenLicense("LICENSE_BFE_GPL-3.0.txt") },
            trailing = { Icon(Icons.Filled.ChevronRight, null) },
        )
        SettingRow(
            icon = Icons.Filled.Gavel,
            title = "Open-source licenses",
            subtitle = "7-Zip, innoextract, unarc, FFmpeg, x264, NanoHTTPD, Apache FtpServer and libraries",
            onClick = onOpenLicenses,
            trailing = { Icon(Icons.Filled.ChevronRight, null) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun LicenseList(onOpen: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(LICENSE_DOCS) { doc ->
            SettingRow(
                icon = Icons.Filled.Gavel,
                title = doc.title,
                subtitle = doc.subtitle,
                onClick = { onOpen(doc.asset) },
                trailing = { Icon(Icons.Filled.ChevronRight, null) },
            )
        }
    }
}

@Composable
private fun LicenseText(asset: String) {
    val context = LocalContext.current
    val text by produceState<String?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }
                .getOrElse { "This license text is missing from the build." }
        }
    }
    val t = text
    if (t == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        // Notices are pre-formatted plain text: monospace, scrollable both ways, selectable.
        SelectionContainer {
            Text(
                t,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun DefaultAppsPage() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val entries = remember(tick) { DefaultApps.all(context) }
    if (entries.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Apps, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("No default apps yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Turn on “Always use for .ext” in the Open with sheet to remember an app for a file type.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.ext }) { e ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(".${e.ext}", style = MaterialTheme.typography.bodyLarge)
                    Text(e.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { DefaultApps.remove(context, e.ext); tick++ }) {
                    Icon(Icons.Filled.Close, "Clear default for .${e.ext}")
                }
            }
        }
        item {
            TextButton(
                onClick = { DefaultApps.clear(context); tick++ },
                modifier = Modifier.padding(16.dp),
            ) { Text("Clear all defaults") }
        }
    }
}
