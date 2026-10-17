package com.the412banner.bfe

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.io.File
import com.the412banner.bfe.ui.icons.BfeIcon
import com.the412banner.bfe.ui.icons.FileIcons
import com.the412banner.bfe.ui.ApkJobPill
import com.the412banner.bfe.ui.ConvertProgressPill
import com.the412banner.bfe.ui.PackProgressPill
import com.the412banner.bfe.ui.UnpackProgressPill
import com.the412banner.bfe.ui.screens.FileManagerScreen
import com.the412banner.bfe.ui.screens.SettingsScreen
import com.the412banner.bfe.ui.screens.apps.AppsScreen
import com.the412banner.bfe.ui.screens.categories.CategoryScreen
import com.the412banner.bfe.ui.screens.categories.MediaCategory
import com.the412banner.bfe.ui.screens.cloud.CloudScreen
import com.the412banner.bfe.ui.screens.cloud.RemoteScreen
import com.the412banner.bfe.ui.screens.home.AppRoute
import com.the412banner.bfe.ui.screens.home.BackScope
import com.the412banner.bfe.ui.screens.home.FmLocationRequest
import com.the412banner.bfe.ui.screens.home.HomePrefs
import com.the412banner.bfe.ui.screens.home.HomeScreen
import com.the412banner.bfe.ui.screens.server.ServerScreen
import com.the412banner.bfe.ui.update.UpdateHost
import com.the412banner.bfe.server.ServerService
import com.the412banner.bfe.update.UpdateManager
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.ui.theme.BfeTheme
import com.the412banner.bfe.ui.theme.AuroraBackdrop
import com.the412banner.bfe.ui.theme.LookTransitionOverlay
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.ui.AuroraRouteHost
import com.the412banner.bfe.ui.screens.home.AuroraWelcome
import com.the412banner.bfe.ui.SettingsHost
import com.the412banner.bfe.ui.AuroraSnackbar
import com.the412banner.bfe.ui.auroraScreenLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

/**
 * Root of BFE. Hosts the app content with the app-wide [UnpackProgressPill] pinned at the bottom so a
 * running extraction floats over every screen (mirrors how the source app hosts it below the content).
 *
 * On first run it asks for All-Files-Access (MANAGE_EXTERNAL_STORAGE): the bundled native extractors
 * write directly to the filesystem and cannot go through SAF, so extraction simply does not work
 * without it. The gate re-checks on resume, so returning from Settings unlocks the app immediately.
 *
 * Launched (or re-delivered via onNewIntent) with [EXTRA_OPEN_PATH] naming a readable folder, it skips
 * Home and opens the file manager there — e.g. an "Open folder" button on a finished-extraction
 * notification.
 */
class MainActivity : ComponentActivity() {

    /** A folder asked for by an incoming intent; each one is a new request object. */
    private val openPathRequest = mutableStateOf<FmLocationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Silent, throttled update check (no-op when turned off in Settings or checked recently).
        if (savedInstanceState == null) {
            UpdateManager.checkOnLaunch(this)
            openPathRequest.value = folderRequestFrom(intent)
            // "Access from network": start the servers the user chose to start with BFE (no-op otherwise).
            ServerService.startOnLaunch(this)
        }
        setContent {
            BfeTheme(edgeToEdge = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(openPathRequest.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        folderRequestFrom(intent)?.let { openPathRequest.value = it }
    }

    companion object {
        /** String extra: an absolute path of a folder to open in the file manager instead of Home. */
        const val EXTRA_OPEN_PATH = "com.the412banner.bfe.extra.OPEN_PATH"

        private fun folderRequestFrom(intent: Intent?): FmLocationRequest? {
            val path = intent?.getStringExtra(EXTRA_OPEN_PATH) ?: return null
            val dir = File(path)
            return if (dir.isDirectory && dir.canRead()) FmLocationRequest.folder(dir) else null
        }
    }
}

@Composable
private fun AppRoot(externalRequest: FmLocationRequest?) {
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Top-level screen. BFE opens on Home unless Settings → Start on says "Last folder" (or an
    // intent asked for a folder); the file manager itself is always composed underneath.
    val startOnLast = remember { HomePrefs.startOn(context) == HomePrefs.START_LAST_FOLDER }
    var route by rememberSaveable { mutableStateOf(if (externalRequest != null || startOnLast) AppRoute.FILES else AppRoute.HOME) }
    var category by rememberSaveable { mutableStateOf(MediaCategory.IMAGES) }
    // Where Back from "Access from network" returns: Home, or the file manager when opened from its
    // Aurora "Sharing" chip.
    var serverBack by rememberSaveable { mutableStateOf(AppRoute.HOME) }
    var fmRequest by remember {
        mutableStateOf(if (startOnLast) HomePrefs.lastFolder(context)?.let { FmLocationRequest.folder(it) } else null)
    }
    val goHome: () -> Unit = { route = AppRoute.HOME }
    val openFiles: (FmLocationRequest?) -> Unit = { req -> if (req != null) fmRequest = req; route = AppRoute.FILES }
    LaunchedEffect(externalRequest) {
        if (externalRequest != null) { showSettings = false; openFiles(externalRequest) }
    }
    val snackbarHost = remember { SnackbarHostState() }
    // App-wide messages (AppSnackbar) land here while the screen is visible; when BFE is in the
    // background nothing collects, so messages fall back to a Toast instead of an unseen snackbar.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            AppSnackbar.messages.collect { m ->
                snackbarHost.currentSnackbarData?.dismiss()
                launch {
                    snackbarHost.showSnackbar(
                        m.text,
                        withDismissAction = m.long,
                        duration = if (m.long) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    // The screen for a non-file-manager route; both looks show the same screens.
    @Composable
    fun RouteContent(r: AppRoute) {
        when (r) {
            AppRoute.HOME -> HomeScreen(
                onOpenFolder = { openFiles(it) },
                onOpenFiles = { openFiles(null) },
                onOpenCategory = { category = it; route = AppRoute.CATEGORY },
                onOpenApps = { route = AppRoute.APPS },
                onOpenCloud = { route = AppRoute.CLOUD },
                onOpenRemote = { route = AppRoute.REMOTE },
                onOpenServer = { serverBack = AppRoute.HOME; route = AppRoute.SERVER },
                onOpenSettings = { showSettings = true },
            )
            AppRoute.CATEGORY -> CategoryScreen(
                category = category,
                onBack = goHome,
                onShowInFolder = { dir -> openFiles(FmLocationRequest.folder(dir)) },
            )
            AppRoute.APPS -> AppsScreen(onBack = goHome, onBrowseData = { openFiles(it) })
            AppRoute.CLOUD -> CloudScreen(onBack = goHome)
            AppRoute.REMOTE -> RemoteScreen(onBack = goHome)
            AppRoute.SERVER -> ServerScreen(onBack = { route = serverBack })
            AppRoute.FILES -> Unit
        }
    }

    // Aurora draws glass over a living backdrop, edge-to-edge (the backdrop runs under the system bars;
    // the content is padded for them and the keyboard). Classic is laid out exactly as before.
    val aurora = isAurora
    Box(modifier = Modifier.fillMaxSize()) {
        if (aurora) AuroraBackdrop(Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize().then(if (aurora) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)) {
            // Aurora: running jobs as glass capsules under the status bar, clear of the thumb zone.
            if (aurora) JobPills()
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PermissionGate {
                    // The file manager stays composed under Home and Settings so both panes keep their
                    // place; while another screen covers it, its Back handling is switched off.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .then(if (aurora) Modifier.auroraScreenLayer(visible = route == AppRoute.FILES && !showSettings) else Modifier),
                        ) {
                            BackScope(active = route == AppRoute.FILES) {
                                FileManagerScreen(
                                    onOpenSettings = { showSettings = true },
                                    openRequest = fmRequest,
                                    onHome = goHome,
                                    onBackAtRoot = goHome,
                                    onOpenServer = { serverBack = AppRoute.FILES; route = AppRoute.SERVER },
                                )
                            }
                        }
                        if (aurora) {
                            AuroraRouteHost(route = route, hidden = showSettings) { r -> RouteContent(r) }
                        } else if (route != AppRoute.FILES) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                RouteContent(route)
                            }
                        }
                        // One host for both looks, so switching the look from Settings keeps its page.
                        SettingsHost(visible = showSettings) { SettingsScreen(onBack = { showSettings = false }) }
                    }
                }
            }
            // Pinned app-wide extraction + compression pills (each renders nothing when idle).
            if (!aurora) JobPills()
        }
        if (aurora) {
            // Glass snackbar, lifted above the floating pill on the screens that have one.
            val overPill = !showSettings && (route == AppRoute.FILES || route == AppRoute.HOME)
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = if (overPill) 80.dp else 12.dp),
                snackbar = { AuroraSnackbar(it) },
            )
        } else {
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
        // The crossfade when the look / palette / theme changes (draws nothing otherwise).
        LookTransitionOverlay()
    }
    UpdateHost()
}

/** The app-wide extraction / compression / APK / convert pills (each renders nothing when idle). */
@Composable
private fun JobPills() {
    UnpackProgressPill()
    PackProgressPill()
    ApkJobPill()
    ConvertProgressPill()
}

/** Shows [content] when All-Files-Access is granted; otherwise a short explanation + grant button. */
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val hasAllFiles = remember(resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    if (hasAllFiles) {
        content()
        return
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }.onFailure {
                // Some ROMs lack the per-app screen; fall back to the global list.
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        }
    }

    if (isAurora) {
        AuroraWelcome(onAllow = ::requestAllFilesAccess)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BfeIcon(FileIcons.FOLDER_OPEN, size = 80.dp)
        Spacer(Modifier.height(16.dp))
        Text("Welcome to BFE", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            "Browse and manage your files, and extract archives, disc images and GOG / Inno Setup " +
                "installers with the built-in 7-Zip, innoextract and unarc engines.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The extraction engines write straight to storage, so BFE needs access to all files. " +
                "Nothing leaves your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { requestAllFilesAccess() }) { Text("Allow access to all files") }
    }
}
