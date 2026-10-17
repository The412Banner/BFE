package com.the412banner.bfe.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.the412banner.bfe.ui.components.ShimmerProgressBar
import com.the412banner.bfe.ui.theme.isAurora
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.the412banner.bfe.BuildConfig
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.ui.components.BRAND_NAME
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.update.UpdateManager
import com.the412banner.bfe.update.UpdateManager.DownloadState

/**
 * Hosts every piece of updater UI at the app root: the "update available" dialog, the download
 * progress dialog (cancelable), the install-permission explainer and failures. Also nudges a parked
 * install forward when the user comes back from the "install unknown apps" screen.
 */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val offer by UpdateManager.offer.collectAsState()
    val download by UpdateManager.download.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) UpdateManager.resumeIfPermitted(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    offer?.let { info ->
        OutlinedAlertDialog(
            onDismissRequest = { UpdateManager.later() },
            icon = { Icon(Icons.Filled.SystemUpdate, null) },
            title = { Text("$BRAND_NAME ${info.versionName} is available") },
            text = {
                Column {
                    Text(
                        buildString {
                            append("You have ${BuildConfig.VERSION_NAME}.")
                            if (info.size > 0) append(" Download size ${StringUtils.formatBytes(info.size)}.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What's new", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                            Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!info.canUpdateInApp) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This version is too old to update in place. Download the new version from GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (info.canUpdateInApp) UpdateManager.startDownload(context, info)
                    else { UpdateManager.later(); UpdateManager.openUrl(context, UpdateManager.RELEASES_PAGE) }
                }) { Text(if (info.canUpdateInApp) "Update" else "Open GitHub") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { UpdateManager.skip(context, info) }) { Text("Skip this version") }
                    TextButton(onClick = { UpdateManager.later() }) { Text("Later") }
                }
            },
        )
    }

    when (val d = download) {
        is DownloadState.Downloading, is DownloadState.Verifying -> {
            val info = if (d is DownloadState.Downloading) d.info else (d as DownloadState.Verifying).info
            OutlinedAlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text("Downloading $BRAND_NAME ${info.versionName}") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (d is DownloadState.Downloading && d.total > 0) {
                            if (isAurora) ShimmerProgressBar((d.bytes.toFloat() / d.total).coerceIn(0f, 1f))
                            else LinearProgressIndicator(
                                progress = { (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${StringUtils.formatBytes(d.bytes)} of ${StringUtils.formatBytes(d.total)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            if (isAurora) ShimmerProgressBar(null) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (d is DownloadState.Verifying) "Checking the download…" else "Starting…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    if (d is DownloadState.Downloading) TextButton(onClick = { UpdateManager.cancel() }) { Text("Cancel") }
                },
            )
        }
        is DownloadState.NeedsPermission -> OutlinedAlertDialog(
            onDismissRequest = { UpdateManager.dismiss() },
            title = { Text("Allow BFE to install updates") },
            text = {
                Text(
                    "$BRAND_NAME ${d.info.versionName} is downloaded and verified. Android needs your OK before " +
                        "BFE can install it: turn on “Allow from this source”, then come back here.",
                )
            },
            confirmButton = {
                Button(onClick = { UpdateManager.openInstallPermissionSettings(context) }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Not now") } },
        )
        is DownloadState.Failed -> OutlinedAlertDialog(
            onDismissRequest = { UpdateManager.dismiss() },
            title = { Text("Update failed") },
            text = { Text(d.reason) },
            confirmButton = {
                Button(onClick = { UpdateManager.startDownload(context, d.info) }) { Text("Try again") }
            },
            dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Close") } },
        )
        // Installing: the system installer is on screen; nothing to draw. Idle: nothing either.
        else -> Unit
    }
}
