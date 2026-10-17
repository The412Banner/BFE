package com.the412banner.bfe.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.DefaultApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A BFE action offered at the top of the Open with sheet (Extract, Install, Convert, Properties). */
internal data class SheetAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** One "Open as" choice: a chip label and the MIME type the app list is queried with. */
private data class OpenAs(val label: String, val mime: String)

private class ViewerApp(val component: ComponentName, val label: String, val icon: ImageBitmap?)

private val ARCHIVE_MIMES = setOf(
    "application/zip", "application/x-7z-compressed", "application/x-rar-compressed", "application/vnd.rar",
    "application/x-tar", "application/gzip", "application/x-gzip", "application/x-bzip2", "application/x-xz",
    "application/zstd", "application/x-iso9660-image",
)

/** Best MIME guess for [name] from its extension (the provider's answer wins when it has one). */
internal fun guessMime(name: String, providerType: String?): String {
    providerType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }?.let { return it }
    val ext = name.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: when (ext) {
            "log", "ini", "cfg", "conf", "json", "xml", "yml", "yaml", "md", "sh", "bat", "reg", "properties" -> "text/plain"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/x-rar-compressed"
            "tzst", "zst", "wcp" -> "application/zstd"
            else -> "*/*"
        }
}

private fun categoryOf(mime: String): String = when {
    mime.startsWith("text/") -> "Text"
    mime.startsWith("image/") -> "Image"
    mime.startsWith("video/") -> "Video"
    mime.startsWith("audio/") -> "Audio"
    mime in ARCHIVE_MIMES -> "Archive"
    else -> "Any"
}

/**
 * BFE's own "Open with" chooser: a modal bottom sheet with the file header, "Open as" type chips,
 * BFE's built-in actions for this file, then every app that can VIEW it (BFE excluded). Tapping an
 * app opens the file in exactly that app; with "Always use for .ext" on, the choice is remembered and
 * later taps on such files skip the sheet (see [DefaultApps]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OpenWithSheet(
    file: Loc,
    uri: Uri,
    detectedMime: String,
    actions: List<SheetAction>,
    allowWrite: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val ext = remember(file.name) { DefaultApps.extOf(file.name) }

    // Chips: the five categories + Any. The detected type replaces its category's generic MIME so the
    // default query is exact; a type outside every category gets its own leading chip.
    val chips = remember(detectedMime) {
        val base = mutableListOf(
            OpenAs("Text", "text/plain"), OpenAs("Image", "image/*"), OpenAs("Video", "video/*"),
            OpenAs("Audio", "audio/*"), OpenAs("Archive", "application/zip"), OpenAs("Any", "*/*"),
        )
        val cat = categoryOf(detectedMime)
        if (detectedMime != "*/*") {
            if (cat == "Any") base.add(0, OpenAs(ext.uppercase().ifEmpty { "Detected" }, detectedMime))
            else base.replaceAll { if (it.label == cat) it.copy(mime = detectedMime) else it }
        }
        base.toList()
    }
    var selected by remember(detectedMime) { mutableStateOf(chips.first { it.mime == detectedMime || (detectedMime == "*/*" && it.label == "Any") }) }
    var apps by remember { mutableStateOf<List<ViewerApp>?>(null) }
    // The remembered app for this extension (if any) is listed first and marked, never auto-launched:
    // a tap on a file always lands here so it can't surprise anyone.
    val saved = remember(ext) { DefaultApps.get(context, ext) }
    val savedComponent = saved?.let { ComponentName(it.pkg, it.cls) }
    var always by remember { mutableStateOf(saved != null) }

    LaunchedEffect(selected) {
        apps = null
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val probe = Intent(Intent.ACTION_VIEW).setDataAndType(uri, selected.mime)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
                .map { ri ->
                    val icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    ViewerApp(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), ri.loadLabel(pm).toString(), icon)
                }
                .sortedWith(compareBy<ViewerApp>({ it.component != savedComponent }, { it.label.lowercase() }))
        }
    }

    fun launch(app: ViewerApp) {
        val intent = DefaultApps.viewIntent(uri, selected.mime, app.component, allowWrite)
        val ok = runCatching { context.startActivity(intent) }.isSuccess ||
            // Some viewers refuse a write grant on a read-only provider; retry read-only.
            runCatching { context.startActivity(DefaultApps.viewIntent(uri, selected.mime, app.component, false)) }.isSuccess
        if (!ok) {
            AppSnackbar.show(context, "${app.label} couldn't open ${file.name}")
            return
        }
        if (always && ext.isNotEmpty()) {
            if (app.component != savedComponent) AppSnackbar.show(context, "${app.label} is now the default for .$ext files")
            DefaultApps.set(context, DefaultApps.Entry(ext, app.component.packageName, app.component.className, app.label, selected.mime))
        } else if (!always && saved != null) {
            DefaultApps.remove(context, ext)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            // ── Header: icon · name · size ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    FileTypeIcon(file.name, isDir = false, size = 40.dp, mime = detectedMime)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            StringUtils.formatBytes(file.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            // ── BFE's own actions for this file ──
            if (actions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        actions.forEach { a ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onDismiss(); a.onClick() }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                            ) {
                                Icon(a.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            // ── Open as ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Open as", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { c ->
                            FilterChip(
                                selected = c == selected,
                                onClick = { selected = c },
                                label = { Text(c.label) },
                            )
                        }
                    }
                    if (ext.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { always = !always }.padding(vertical = 4.dp),
                        ) {
                            Text("Always use for .$ext", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = always, onCheckedChange = { always = it })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            // ── App list ──
            val list = apps
            when {
                list == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    ) {
                        Icon(Icons.Filled.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No installed app opens this as ${selected.label.lowercase()}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (selected.mime != "*/*") {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { selected = chips.first { it.mime == "*/*" } }) { Text("Open as Any") }
                        }
                    }
                }
                else -> items(list, key = { it.component.flattenToString() }) { app ->
                    val isDefault = app.component == savedComponent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDefault) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { launch(app) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        if (app.icon != null) Image(app.icon, null, modifier = Modifier.size(44.dp))
                        else Icon(Icons.Filled.Android, null, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isDefault) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (isDefault) {
                            Text("Default", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** Built-in action icons, kept here so the call site reads as a plain list. */
internal object SheetIcons {
    val Browse = Icons.Filled.FolderOpen
    val ExtractHere = Icons.Filled.Bolt
    val ExtractTo = Icons.Filled.Unarchive
    val Install = Icons.Filled.Android
    val Convert = Icons.Filled.Movie
    val Properties = Icons.Filled.Info
    val Web = Icons.Filled.Language
}
