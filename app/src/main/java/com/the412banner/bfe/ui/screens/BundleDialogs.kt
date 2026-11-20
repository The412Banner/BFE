package com.the412banner.bfe.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.the412banner.bfe.apk.KeyRef
import com.the412banner.bfe.apk.SignSchemes
import com.the412banner.bfe.apk.SigningKeys
import com.the412banner.bfe.apk.bundle.BundleInstallManager
import com.the412banner.bfe.apk.bundle.BundleInstallService
import com.the412banner.bfe.apk.bundle.BundleStage
import com.the412banner.bfe.apk.bundle.Bundles
import java.io.File
import java.util.zip.ZipFile

/** A read bundle plus where it came from; [staged] = BFE's cache copy of a non-local source. */
data class BundleTarget(
    val info: Bundles.Info,
    val name: String,
    val icon: Bitmap?,
    val staged: File?,
    val autoSelected: Set<String>,
)

internal const val CONVERT_CAVEATS =
    "The APK is signed with the key you chose, not the original one, so it can't update a copy " +
        "installed from a store (uninstall that first). Apps that check their own signature or rely " +
        "on Play services may refuse to run."

/** Reads [file] (a bundle or a folder of APKs) for the dialog. Blocking — call off the main thread. */
internal fun loadBundle(context: Context, file: File, displayName: String, staged: File?): BundleTarget {
    var info = Bundles.refineWithManifests(Bundles.read(file))
    var icon = info.iconEntry?.let { e ->
        runCatching { ZipFile(file).use { z -> z.getInputStream(z.getEntry(e)).use { BitmapFactory.decodeStream(it) } } }.getOrNull()
    }
    // No name or icon in the bundle's own metadata (bundletool .apks, a folder of splits, an XAPK
    // without icon.png): ask Android's parser, which resolves the label resource and adaptive icons.
    if (info.label == null || icon == null) {
        baseLook(context, info)?.let { (label, bmp) ->
            if (info.label == null && label != null) info = info.copy(label = label)
            if (icon == null) icon = bmp
        }
    }
    val dm = context.resources.displayMetrics
    val locales = context.resources.configuration.locales
    val langs = (0 until locales.size()).map { locales[it].language }
    val auto = Bundles.autoSelect(info.parts, Build.SUPPORTED_ABIS.toList(), dm.densityDpi, langs)
    return BundleTarget(info, displayName, icon, staged, auto)
}

/** A base APK bigger than this isn't copied out of a bundle just for its name and icon. */
private const val LOOK_MAX_BYTES = 512L shl 20

/**
 * The base APK's label and launcher icon via `PackageManager.getPackageArchiveInfo`, like
 * [com.the412banner.bfe.core.ApkIconLoader]. The parser needs a path: a folder's base APK is used in
 * place, a zip's is copied to the cache for the moment it takes and deleted again.
 */
private fun baseLook(context: Context, info: Bundles.Info): Pair<String?, Bitmap?>? = runCatching {
    val base = info.base ?: return null
    var tmp: File? = null
    try {
        val path = if (info.isFolder) File(info.source, base.entry).absolutePath else {
            if (base.size > LOOK_MAX_BYTES) return null
            val t = File(context.cacheDir, "bundle-look-${System.nanoTime()}.apk")
            tmp = t
            ZipFile(info.source).use { z -> Bundles.open(info, base.entry, z).use { i -> t.outputStream().use { o -> i.copyTo(o, 1 shl 20) } } }
            t.absolutePath
        }
        val pm = context.packageManager
        val ai = pm.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return null
        ai.sourceDir = path
        ai.publicSourceDir = path
        val label = pm.getApplicationLabel(ai).toString().takeIf { it.isNotBlank() && it != ai.packageName }
        val px = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bmp = runCatching { ai.loadIcon(pm).toBitmap(px, px) }.getOrNull()
        label to bmp
    } finally {
        tmp?.delete()
    }
}.getOrNull()

private fun fmt(context: Context, b: Long): String = android.text.format.Formatter.formatShortFileSize(context, b)

/**
 * The bundle sheet: app name and version, every part with a tick (the parts this device needs are
 * ticked), XAPK game data, and Install / Convert to APK. While an install runs it shows its progress
 * and the result in place.
 */
@Composable
internal fun BundleDialog(
    target: BundleTarget,
    onDismiss: () -> Unit,
    onInstall: (parts: List<Bundles.Part>, obbs: List<Bundles.Obb>) -> Unit,
    onConvert: (parts: List<Bundles.Part>) -> Unit,
    onBrowse: (() -> Unit)?,
) {
    val context = LocalContext.current
    val info = target.info
    var selected by remember(target) { mutableStateOf(target.autoSelected) }
    var obbOn by remember(target) { mutableStateOf(info.obbs.isNotEmpty()) }
    var showAll by remember(target) { mutableStateOf(false) }
    val install by BundleInstallManager.state.collectAsState()
    var startedHere by remember(target) { mutableStateOf(false) }
    val running = startedHere && install.isRunning
    val finished = startedHere && !install.isRunning && install.stage != BundleStage.IDLE
    val chosen = info.parts.filter { it.entry in selected }
    val baseOn = info.base?.entry in selected
    val missingAbi = Bundles.missingAbi(info.parts, selected)
    val title = info.label ?: target.name.substringBeforeLast('.')

    OutlinedAlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        icon = {
            if (target.icon != null) Image(target.icon.asImageBitmap(), null, Modifier.size(48.dp))
            else Icon(Icons.Filled.Android, null, Modifier.size(40.dp))
        },
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                val sub = listOfNotNull(
                    info.packageName,
                    info.versionName?.let { "v$it" },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${chosen.size} of ${info.parts.size} parts · ${fmt(context, chosen.sumOf { it.size } + if (obbOn) info.obbs.sumOf { it.size } else 0L)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (running || finished) {
                    Text(
                        if (install.stage == BundleStage.DONE) "Installed" else install.stage.label,
                        fontWeight = FontWeight.SemiBold,
                        color = if (install.stage == BundleStage.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    if (install.stage == BundleStage.OBB || install.stage == BundleStage.WRITING) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { install.fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${fmt(context, install.done)} of ${fmt(context, install.total)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    install.message?.takeIf { install.stage != BundleStage.CANCELLED || it != "Cancelled" }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, fontSize = 12.sp, color = if (install.stage == BundleStage.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }
                // Parts this device doesn't need stay folded away unless asked for.
                val visible = if (showAll) info.parts else info.parts.filter { it.entry in target.autoSelected || it.entry in selected }
                visible.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (p.entry in selected) selected - p.entry else selected + p.entry },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = p.entry in selected, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                        Text(Bundles.describe(p), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt(context, p.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val hidden = info.parts.size - visible.size
                if (hidden > 0 || showAll) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Hide parts this device doesn't need" else "Show all parts ($hidden more)", fontSize = 12.sp)
                    }
                }
                if (info.obbs.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth().clickable { obbOn = !obbOn }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = obbOn, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Game data (${info.obbs.size} file${if (info.obbs.size == 1) "" else "s"})", fontSize = 13.sp)
                            Text(info.obbs.first().target.substringBeforeLast('/'), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(fmt(context, info.obbs.sumOf { it.size }), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (info.obbs.any { it.target.startsWith("Android/obb//") }) {
                        Text("Package unknown: game data can't be placed", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (!baseOn) Text("The main app part is needed", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                else if (missingAbi) Text("No part for this device's processor is ticked", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
                Row {
                    TextButton(enabled = baseOn, onClick = { onConvert(chosen) }) { Text("Convert to APK…") }
                    if (onBrowse != null) TextButton(onClick = onBrowse) { Text("Open as folder") }
                }
            }
        },
        confirmButton = {
            when {
                running -> TextButton(onClick = { BundleInstallService.cancel(context) }, enabled = install.stage != BundleStage.INSTALLING && install.stage != BundleStage.CONFIRM) { Text("Cancel") }
                finished -> TextButton(onClick = { BundleInstallManager.clearIfTerminal(); onDismiss() }) { Text("Close") }
                else -> TextButton(
                    enabled = baseOn && !install.isRunning && info.obbs.none { obbOn && it.target.startsWith("Android/obb//") },
                    onClick = {
                        startedHere = true
                        onInstall(chosen, if (obbOn) info.obbs else emptyList())
                    },
                ) { Text("Install") }
            }
        },
        dismissButton = { if (!running && !finished) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Convert: output name, signing key + schemes, Convert / Convert & install. */
@Composable
internal fun ConvertBundleDialog(
    sourceName: String,
    partCount: Int,
    defaultOutputName: String,
    onDismiss: () -> Unit,
    onManageKeys: () -> Unit,
    onConfirm: (outputName: String, key: KeyRef, schemes: SignSchemes, installAfter: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val keys = remember { SigningKeys.list(context) }
    var key by remember { mutableStateOf(SigningKeys.lastUsed(context)) }
    var schemes by remember { mutableStateOf(SigningKeys.lastSchemes(context)) }
    var outName by remember { mutableStateOf(defaultOutputName) }
    var installAfter by remember { mutableStateOf(false) }
    val ok = outName.isNotBlank() && !outName.contains('/') && (schemes.v1 || schemes.v2 || schemes.v3)
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert to APK") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("$sourceName · $partCount parts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = outName, onValueChange = { outName = it }, label = { Text("Output file") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                KeyAndSchemesRows(keys, key, { key = it }, schemes, { schemes = it }, onManageKeys)
                Row(Modifier.fillMaxWidth().clickable { installAfter = !installAfter }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Install when done", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = installAfter, onCheckedChange = { installAfter = it })
                }
                Spacer(Modifier.width(1.dp))
                Text(CONVERT_CAVEATS, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                SigningKeys.setLastUsed(context, key); SigningKeys.setLastSchemes(context, schemes)
                onConfirm(outName.trim().let { if (it.endsWith(".apk", true)) it else "$it.apk" }, key, schemes, installAfter)
            }) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
