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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.the412banner.bfe.ui.ApkJobPill
import com.the412banner.bfe.ui.PackProgressPill
import com.the412banner.bfe.ui.UnpackProgressPill
import com.the412banner.bfe.ui.screens.FileManagerScreen
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
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PermissionGate {
                FileManagerScreen()
            }
        }
        // Pinned app-wide extraction + compression pills (each renders nothing when idle).
        UnpackProgressPill()
        PackProgressPill()
        ApkJobPill()
    }
}

/** Shows [content] when All-Files-Access is granted; otherwise a rationale + grant button. */
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BFE", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Browse your files and extract archives, disc images and GOG/InnoSetup installers with a " +
                "bundled 7-Zip / innoextract / unarc engine.",
            color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "The extractors write directly to storage (they can't use SAF), so BFE needs All-Files-Access.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }
        }) { Text("Grant All-Files-Access") }
    }
}
