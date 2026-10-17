package com.the412banner.bfe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.the412banner.bfe.UnpackArchiveActivity
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.pack.PackManager
import com.the412banner.bfe.pack.PackPhase
import com.the412banner.bfe.pack.PackService
import com.the412banner.bfe.apk.ApkJobManager
import com.the412banner.bfe.video.ConvertManager
import com.the412banner.bfe.video.ConvertPhase
import com.the412banner.bfe.video.ConvertService
import androidx.compose.material.icons.filled.Movie
import com.the412banner.bfe.apk.ApkJobService
import com.the412banner.bfe.apk.ApkJobKind
import androidx.compose.material.icons.filled.Android
import com.the412banner.bfe.unpack.UnpackManager
import com.the412banner.bfe.unpack.UnpackPhase
import com.the412banner.bfe.unpack.UnpackService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.the412banner.bfe.ui.components.GlassIconButton
import com.the412banner.bfe.ui.components.ShimmerProgressBar
import com.the412banner.bfe.ui.theme.BfeMotion
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora

/**
 * App-wide minimized progress pill for a running unpack. Hosted in the root scaffold so it floats
 * over every screen, and observes the SAME [UnpackManager] StateFlow the foreground service updates
 * and the system notification renders — the three views (full [com.the412banner.bfe.ui.screens.UnpackArchiveScreen],
 * this pill, the notification) never diverge.
 *
 * Tap → reopen the full progress view; the pill's ⋯ Cancel kills the job. Renders nothing (and takes
 * no space) when no extraction is active, so it is safe to drop unconditionally into a layout.
 */
@Composable
fun UnpackProgressPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UnpackManager.state.collectAsState()
    if (isAurora) {
        AuroraPillSlot(state.isRunning, modifier) {
            val listing = state.phase == UnpackPhase.LISTING
            AuroraJobPill(
                icon = Icons.Filled.Unarchive,
                title = state.archiveName.ifBlank { "Unpacking" },
                detail = if (listing) "Reading archive…" else buildString {
                    append("${state.percent}%")
                    if (state.speedBps > 0) append("  •  ${StringUtils.formatBytes(state.speedBps)}/s")
                    if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                },
                progress = if (listing) null else state.percent / 100f,
                onClick = { context.startActivity(UnpackArchiveActivity.intent(context, state.archivePath)) },
                cancelLabel = "Cancel unpack",
                onCancel = { UnpackService.cancel(context) },
            )
        }
        return
    }
    if (!state.isRunning) return

    val listing = state.phase == UnpackPhase.LISTING
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { context.startActivity(UnpackArchiveActivity.intent(context, state.archivePath)) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Unarchive, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.archiveName.ifBlank { "Unpacking" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (listing) "Reading archive…"
                        else buildString {
                            append("${state.percent}%")
                            if (state.speedBps > 0) append("  •  ${StringUtils.formatBytes(state.speedBps)}/s")
                            if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { UnpackService.cancel(context) }) {
                    Icon(Icons.Filled.Close, "Cancel unpack", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (listing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}

/**
 * The creation-side twin of [UnpackProgressPill]: the app-wide pill for a running Compress… job,
 * fed by [PackManager] (which [PackService] and its notification also read). No full screen to
 * open — the ✕ cancels (kills the 7zz processes / trips the tar's cancel flag). Renders nothing
 * when idle, so it stacks harmlessly under the unpack pill in the root scaffold.
 */
@Composable
fun PackProgressPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by PackManager.state.collectAsState()
    if (isAurora) {
        AuroraPillSlot(state.isRunning, modifier) {
            val scanning = state.phase == PackPhase.SCANNING
            AuroraJobPill(
                icon = Icons.Filled.Archive,
                title = state.archiveName.ifBlank { "Compressing" },
                detail = if (scanning) "Scanning files…" else buildString {
                    append("${state.percent}%")
                    if (state.speedBps > 0) append("  •  ${StringUtils.formatBytes(state.speedBps)}/s")
                    if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                    state.currentFile?.let { append("  •  ").append(it.substringAfterLast('/')) }
                },
                progress = if (scanning) null else state.percent / 100f,
                onClick = null,
                cancelLabel = "Cancel compression",
                onCancel = { PackService.cancel(context) },
            )
        }
        return
    }
    if (!state.isRunning) return

    val scanning = state.phase == PackPhase.SCANNING
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Archive, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.archiveName.ifBlank { "Compressing" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (scanning) "Scanning files…"
                        else buildString {
                            append("${state.percent}%")
                            if (state.speedBps > 0) append("  •  ${StringUtils.formatBytes(state.speedBps)}/s")
                            if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                            state.currentFile?.let { append("  •  ").append(it.substringAfterLast('/')) }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { PackService.cancel(context) }) {
                    Icon(Icons.Filled.Close, "Cancel compression", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (scanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}

/** Pill for a running APK clone / edit / sign job ([ApkJobManager]); ✕ cancels between stages. */
@Composable
fun ApkJobPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by ApkJobManager.state.collectAsState()
    if (isAurora) {
        AuroraPillSlot(state.isRunning, modifier) {
            AuroraJobPill(
                icon = Icons.Filled.Android,
                title = when (state.kind) { ApkJobKind.CLONE -> "Cloning "; ApkJobKind.EDIT -> "Editing "; ApkJobKind.SIGN -> "Signing " } + state.sourceName,
                detail = state.stage.label + "…",
                progress = state.fraction,
                onClick = null,
                cancelLabel = "Cancel",
                onCancel = { ApkJobService.cancel(context) },
            )
        }
        return
    }
    if (!state.isRunning) return
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when (state.kind) { ApkJobKind.CLONE -> "Cloning "; ApkJobKind.EDIT -> "Editing "; ApkJobKind.SIGN -> "Signing " } + state.sourceName,
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(state.stage.label + "…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                }
                IconButton(onClick = { ApkJobService.cancel(context) }) {
                    Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

/** Pill for a running "Convert to MP4" job ([ConvertManager]); ✕ kills ffmpeg. */
@Composable
fun ConvertProgressPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by ConvertManager.state.collectAsState()
    if (isAurora) {
        AuroraPillSlot(state.isRunning, modifier) {
            val probing = state.phase == ConvertPhase.PROBING
            AuroraJobPill(
                icon = Icons.Filled.Movie,
                title = (if (state.batchTotal > 1) "Video ${state.batchIndex}/${state.batchTotal} — " else "Converting ") + state.currentName,
                detail = if (probing) "Reading…" else buildString {
                    append("${state.percent}%")
                    if (state.speed > 0f) append("  •  ${"%.1f".format(state.speed)}×")
                    if (state.outputBytes > 0) append("  •  ${StringUtils.formatBytes(state.outputBytes)}")
                    if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                },
                progress = if (probing) null else state.overallPercent / 100f,
                onClick = null,
                cancelLabel = "Cancel conversion",
                onCancel = { ConvertService.cancel(context) },
            )
        }
        return
    }
    if (!state.isRunning) return
    val probing = state.phase == ConvertPhase.PROBING
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (if (state.batchTotal > 1) "Video ${state.batchIndex}/${state.batchTotal} — " else "Converting ") + state.currentName,
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (probing) "Reading…"
                        else buildString {
                            append("${state.percent}%")
                            if (state.speed > 0f) append("  •  ${"%.1f".format(state.speed)}×")
                            if (state.outputBytes > 0) append("  •  ${StringUtils.formatBytes(state.outputBytes)}")
                            if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { ConvertService.cancel(context) }) {
                    Icon(Icons.Filled.Close, "Cancel conversion", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            if (probing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
            else LinearProgressIndicator(progress = { state.overallPercent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

/** Aurora Glass: a job pill grows in under the status bar while its job runs and folds away after. */
@Composable
private fun AuroraPillSlot(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else
            expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220, delayMillis = 60)),
        exit = if (reduced) ExitTransition.None else
            shrinkVertically(tween(260, easing = BfeMotion.EmphasizedAccelerate)) + fadeOut(tween(160)),
    ) { content() }
}

/**
 * One running job as a glass capsule: the job's icon, what it is, its live numbers, the shimmering
 * progress bar (indeterminate while listing / scanning) and ✕ to cancel. Same data as Classic's card.
 */
@Composable
internal fun AuroraJobPill(
    icon: ImageVector,
    title: String,
    detail: String,
    progress: Float?,
    onClick: (() -> Unit)?,
    cancelLabel: String,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val g = LocalGlass.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(18.dp), GlassLevel.Floating)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(CircleShape).background(g.raised),
        ) { Icon(icon, null, tint = cs.tertiary, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = cs.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            ShimmerProgressBar(progress, height = 3.dp)
        }
        GlassIconButton(Icons.Filled.Close, cancelLabel, onClick = onCancel, size = 38.dp, iconSize = 18.dp, tint = cs.error)
    }
}
