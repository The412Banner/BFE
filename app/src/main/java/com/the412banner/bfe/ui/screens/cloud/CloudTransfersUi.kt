@file:OptIn(ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.cloud

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * The running transfer, pinned under a Cloud/Remote screen (renders nothing when idle). Tap opens the
 * transfers sheet; ✕ cancels everything. Self-contained so the app root can host it next to the other
 * progress pills too.
 */
@Composable
fun CloudTransferPill(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val items by CloudTransfers.items.collectAsState()
    val cur = items.firstOrNull { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PREPARING }
        ?: items.firstOrNull { it.status == TransferStatus.QUEUED }
        ?: return
    val queued = items.count { it.status == TransferStatus.QUEUED && it.id != cur.id }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (cur.kind == TransferKind.DOWNLOAD) Icons.Filled.CloudDownload else Icons.Filled.CloudUpload, null,
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

/** Every transfer this session: progress, cancel per item, clear the finished ones. */
@Composable
internal fun TransfersSheet(onDismiss: () -> Unit) {
    val items by CloudTransfers.items.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            ) {
                Text("Transfers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                items(items.reversed(), key = { it.id }) { t -> TransferRow(t) }
            }
        }
    }
}

@Composable
private fun TransferRow(t: TransferItem) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        val (icon, tint) = when (t.status) {
            TransferStatus.DONE -> Icons.Filled.CheckCircle to cs.primary
            TransferStatus.FAILED -> Icons.Filled.ErrorOutline to cs.error
            else -> (if (t.kind == TransferKind.DOWNLOAD) Icons.Filled.CloudDownload else Icons.Filled.CloudUpload) to cs.onSurfaceVariant
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                (if (t.kind == TransferKind.DOWNLOAD) "From ${t.remote}" else "To ${t.remote}") + "  •  " + transferLine(t),
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
