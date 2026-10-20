@file:OptIn(ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.cloud

import com.the412banner.bfe.ui.components.bfeCardColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.cloud.CloudTransfers
import com.the412banner.bfe.cloud.TransferItem
import com.the412banner.bfe.cloud.TransferKind
import com.the412banner.bfe.cloud.TransferStatus
import com.the412banner.bfe.core.StringUtils

/** "45%  •  12.3 MB/s  •  ETA 1m 5s" (or the file count for folders). */
internal fun transferLine(t: TransferItem): String = when (t.status) {
    TransferStatus.QUEUED -> "Waiting"
    TransferStatus.PREPARING -> "Preparing… " + StringUtils.formatBytes(t.bytes)
    TransferStatus.RUNNING -> buildString {
        if (t.attempt > 1) append("Retrying (try ${t.attempt})  •  ")
        if (t.totalBytes > 0) append("${t.percent}%  •  ${StringUtils.formatBytes(t.bytes)} of ${StringUtils.formatBytes(t.totalBytes)}")
        else append(StringUtils.formatBytes(t.bytes))
        if (t.isDir && t.totalFiles > 0) append("  •  ${t.files}/${t.totalFiles} files")
        if (t.speed > 0) append("  •  ${StringUtils.formatBytes(t.speed.toLong())}/s")
        if (t.etaSec >= 0) append("  •  ETA ${StringUtils.humanDuration(t.etaSec * 1000)}")
    }
    TransferStatus.DONE -> "Done" + (t.resultPath?.let { " → $it" } ?: "")
    TransferStatus.FAILED -> t.error ?: "Failed"
    TransferStatus.CANCELLED -> "Cancelled"
}

/** The kind's icon: cloud down / cloud up, or a copy for a file-manager paste. */
internal fun transferIcon(t: TransferItem): ImageVector = when (t.kind) {
    TransferKind.DOWNLOAD -> Icons.Filled.CloudDownload
    TransferKind.UPLOAD -> Icons.Filled.CloudUpload
    TransferKind.COPY -> Icons.Filled.FileCopy
}

/**
 * The file manager's view of the transfer queue: the same pill as the Cloud / Remote screens while
 * anything runs (its pastes to and from remotes run there), a "N failed" pill once a batch ends with
 * failures, and the transfers sheet (per-item Cancel / Retry) on tap. Renders nothing when idle.
 */
@Composable
fun FilesTransferPill(modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    CloudTransferPill(modifier = modifier, showFailed = true, onClick = { showSheet = true })
    if (showSheet) TransfersSheet(onDismiss = { showSheet = false })
}

/**
 * The running transfer, pinned under a Cloud/Remote screen (renders nothing when idle). Tap opens the
 * transfers sheet; ✕ cancels everything. Self-contained so the app root can host it next to the other
 * progress pills too.
 */
@Composable
fun CloudTransferPill(modifier: Modifier = Modifier, showFailed: Boolean = false, onClick: () -> Unit = {}) {
    val items by CloudTransfers.items.collectAsState()
    val cur = items.firstOrNull { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PREPARING }
        ?: items.firstOrNull { it.status == TransferStatus.QUEUED }
    if (cur == null) {
        // Nothing running: [showFailed] keeps a batch's failures visible until they're retried or cleared.
        val failed = items.count { it.status == TransferStatus.FAILED }
        if (showFailed && failed > 0) FailedTransfersPill(failed, modifier, onClick)
        return
    }
    val queued = items.count { it.status == TransferStatus.QUEUED && it.id != cur.id }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = bfeCardColors(CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    transferIcon(cur), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        cur.name + if (queued > 0) "  (+$queued)" else "",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        transferLine(cur),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { CloudTransfers.cancelAll() }) {
                    Icon(Icons.Filled.Close, "Cancel transfers", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            if (cur.status == TransferStatus.RUNNING && cur.totalBytes > 0) {
                LinearProgressIndicator(progress = { cur.percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
            }
        }
    }
}

/** "2 transfers failed": tap for the sheet (Retry there), ✕ clears the finished ones. */
@Composable
private fun FailedTransfersPill(failed: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = bfeCardColors(CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, bottom = 2.dp)) {
            Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (failed == 1) "1 transfer failed  •  tap to retry" else "$failed transfers failed  •  tap to retry",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { CloudTransfers.clearFinished() }) {
                Icon(Icons.Filled.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Top-bar button for the transfers sheet, with a count of active transfers. */
@Composable
internal fun TransfersButton(onClick: () -> Unit) {
    val items by CloudTransfers.items.collectAsState()
    if (items.isEmpty()) return
    val active = items.count { it.isActive }
    IconButton(onClick = onClick) {
        Box {
            Icon(Icons.Filled.SyncAlt, "Transfers")
            if (active > 0) {
                Badge(modifier = Modifier.align(Alignment.TopEnd)) { Text("$active", fontSize = 9.sp) }
            }
        }
    }
}

/** Every transfer this session: progress, cancel or retry per item, clear the finished ones. */
@Composable
internal fun TransfersSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items by CloudTransfers.items.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            ) {
                Text("Transfers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (items.count { it.canRetry } > 1) TextButton(onClick = { CloudTransfers.retryFailed(context) }) { Text("Retry failed") }
                if (items.any { it.isActive }) TextButton(onClick = { CloudTransfers.cancelAll() }) { Text("Cancel all") }
                if (items.any { !it.isActive }) TextButton(onClick = { CloudTransfers.clearFinished() }) { Text("Clear finished") }
            }
            if (items.isEmpty()) {
                Text(
                    "Nothing transferring.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items.reversed(), key = { it.id }) { t -> TransferRow(t, onRetry = { CloudTransfers.retry(context, t.id) }) }
            }
        }
    }
}

@Composable
private fun TransferRow(t: TransferItem, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        val (icon, tint) = when (t.status) {
            TransferStatus.DONE -> Icons.Filled.CheckCircle to cs.primary
            TransferStatus.FAILED -> Icons.Filled.ErrorOutline to cs.error
            else -> transferIcon(t) to cs.onSurfaceVariant
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                t.route + "  •  " + transferLine(t),
                color = if (t.status == TransferStatus.FAILED) cs.error else cs.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (t.status == TransferStatus.RUNNING || t.status == TransferStatus.PREPARING) {
                Spacer(Modifier.height(4.dp))
                if (t.totalBytes > 0 && t.status == TransferStatus.RUNNING) {
                    LinearProgressIndicator(progress = { t.percent / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                }
            }
        }
        if (t.isActive) {
            IconButton(onClick = { CloudTransfers.cancel(t.id) }) {
                Icon(Icons.Filled.Close, "Cancel", tint = cs.onSurfaceVariant)
            }
        } else if (t.canRetry) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, "Retry", tint = cs.primary)
            }
        }
    }
}

/** A round tinted badge with a service's icon. */
@Composable
internal fun ServiceAvatar(icon: ImageVector, color: Color, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.55f).dp))
    }
}
