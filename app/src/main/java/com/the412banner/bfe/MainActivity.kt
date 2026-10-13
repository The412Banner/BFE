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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
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
import com.the412banner.bfe.ui.ApkJobPill
import com.the412banner.bfe.ui.ConvertProgressPill
import com.the412banner.bfe.ui.PackProgressPill
import com.the412banner.bfe.ui.UnpackProgressPill
import com.the412banner.bfe.ui.screens.FileManagerScreen
import com.the412banner.bfe.ui.screens.SettingsScreen
import com.the412banner.bfe.ui.update.UpdateHost
import com.the412banner.bfe.update.UpdateManager
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.ui.theme.BfeTheme

/**
 * Root of BFE. Hosts the app content with the app-wide [UnpackProgressPill] pinned at the bottom so a
 * running extraction floats over every screen (mirrors how the source app hosts it below the content).
 *
 * On first run it asks for All-Files-Access (MANAGE_EXTERNAL_STORAGE): the bundled native extractors
 * write directly to the filesystem and cannot go through SAF, so extraction simply does not work
 * without it. The gate re-checks on resume, so returning from Settings unlocks the app immediately.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Silent, throttled update check (no-op when turned off in Settings or checked recently).
        if (savedInstanceState == null) UpdateManager.checkOnLaunch(this)
        setContent {
            BfeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PermissionGate {
                    // The file manager stays composed under Settings so both panes keep their place.
                    Box(modifier = Modifier.fillMaxSize()) {
                        FileManagerScreen(onOpenSettings = { showSettings = true })
                        if (showSettings) {
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                SettingsScreen(onBack = { showSettings = false })
                            }
                        }
                    }
                }
            }
            // Pinned app-wide extraction + compression pills (each renders nothing when idle).
            UnpackProgressPill()
            PackProgressPill()
            ApkJobPill()
            ConvertProgressPill()
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
    UpdateHost()
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

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.FolderOpen, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
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
        Button(onClick = {
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
        }) { Text("Allow access to all files") }
    }
}
