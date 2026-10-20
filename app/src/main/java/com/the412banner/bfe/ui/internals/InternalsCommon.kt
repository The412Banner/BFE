package com.the412banner.bfe.ui.internals

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.the412banner.bfe.apk.ApkJobKind
import com.the412banner.bfe.apk.ApkJobManager
import com.the412banner.bfe.apk.ApkJobReport
import com.the412banner.bfe.apk.ApkJobRequest
import com.the412banner.bfe.apk.ApkJobService
import com.the412banner.bfe.apk.ApkJobStage
import com.the412banner.bfe.apk.KeyRef
import com.the412banner.bfe.apk.SignSchemes
import com.the412banner.bfe.apk.SigningKeys
import com.the412banner.bfe.ui.screens.ApkResultDialog
import com.the412banner.bfe.ui.screens.KeyAndSchemesRows
import com.the412banner.bfe.ui.screens.KeyManagerDialog
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.util.AppSnackbar
import java.io.File

// ── Where edits are written ──

/** True when [f] is inside BFE's own cache / files (a cache copy, an extracted archive entry). */
internal fun isAppPrivate(ctx: Context, f: File): Boolean {
    val p = f.absolutePath
    return listOfNotNull(ctx.cacheDir, ctx.filesDir, ctx.externalCacheDir).any { p.startsWith(it.absolutePath + "/") }
}

/** True when BFE may write [f] itself in place: a real, writable file that isn't one of its own copies. */
internal fun canWriteInPlace(ctx: Context, f: File): Boolean = f.isFile && f.canWrite() && !isAppPrivate(ctx, f)

/**
 * The folder a new file made from [source] goes to: beside it when that folder can be written and isn't
 * BFE's own cache, else Download.
 */
internal fun outputDirFor(ctx: Context, source: File): File {
    val parent = source.absoluteFile.parentFile
    return if (parent != null && parent.canWrite() && !isAppPrivate(ctx, source)) parent
    else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
}

/** [name] in [dir], with " (1)", " (2)", … before the extension when it's taken. */
internal fun uniqueFile(dir: File, name: String): File {
    var f = File(dir, name)
    if (!f.exists()) return f
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
    var i = 1
    while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
    return f
}

/** A cache folder for the entries one save stages before the APK job picks them up (it deletes them). */
internal fun stagingDir(ctx: Context): File = File(ctx.cacheDir, "apk-internals/save-${System.currentTimeMillis()}").apply { mkdirs() }

/** Hands [apk] to the system installer. */
internal fun installApkFile(ctx: Context, apk: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure { AppSnackbar.show(ctx, "Couldn't start the installer for ${apk.name}") }
}

/**
 * For a file opened on its own (not inside an APK): writes [bytes] back to it when BFE can, else to a
 * new "-edited" copy beside it or in Download. Returns the file written.
 */
internal fun saveStandalone(ctx: Context, source: File, bytes: ByteArray): File {
    val target = if (canWriteInPlace(ctx, source)) source else {
        val ext = source.name.substringAfterLast('.', "")
        uniqueFile(outputDirFor(ctx, source), source.name.substringBeforeLast('.', source.name) + "-edited" + if (ext.isNotEmpty() && ext != source.name) ".$ext" else "")
    }
    val tmp = File(target.parentFile, ".${target.name}.bfe-saving")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) { tmp.delete(); target.writeBytes(bytes) }
    return target
}

// ── Saving into an APK ──

/** Where the edited APK goes: a new file (the default) or over the source APK. */
internal class ApkSaveChoice(val output: File, val key: KeyRef, val schemes: SignSchemes)

/**
 * "Save into APK": a new APK (default, name editable) or Overwrite the source when BFE can write it,
 * then the signing key and schemes — the same key picker as Sign APK. [what] names the edited entries.
 */
@Composable
internal fun SaveIntoApkDialog(apk: File, what: String, onDismiss: () -> Unit, onConfirm: (ApkSaveChoice) -> Unit) {
    val context = LocalContext.current
    val canOverwrite = remember(apk) { canWriteInPlace(context, apk) }
    val dir = remember(apk) { outputDirFor(context, apk) }
    var overwrite by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(apk.nameWithoutExtension + "-edited.apk") }
    var keyTick by remember { mutableStateOf(0) }
    val keys = remember(keyTick) { SigningKeys.list(context) }
    var key by remember { mutableStateOf(SigningKeys.lastUsed(context)) }
    var schemes by remember { mutableStateOf(SigningKeys.lastSchemes(context)) }
    var manageKeys by remember { mutableStateOf(false) }
    val ok = (overwrite || (name.isNotBlank() && !name.contains('/'))) && (schemes.v1 || schemes.v2 || schemes.v3)
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save into APK") },
        text = {
            Column {
                Text("$what → ${apk.name}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("The APK is rebuilt with the change, zip-aligned and signed again, in the background.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChoiceRow("Save to a new APK", selected = !overwrite) { overwrite = false }
                if (!overwrite) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("File name") },
                        supportingText = { Text("In ${dir.absolutePath}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                    )
                }
                ChoiceRow(if (canOverwrite) "Overwrite ${apk.name}" else "Overwrite (this copy can't be written)", selected = overwrite, enabled = canOverwrite) { overwrite = true }
                Spacer(Modifier.height(8.dp))
                KeyAndSchemesRows(keys, key, { key = it }, schemes, { schemes = it }, { manageKeys = true })
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                SigningKeys.setLastUsed(context, key); SigningKeys.setLastSchemes(context, schemes)
                val out = if (overwrite) apk else uniqueFile(dir, name.trim().let { if (it.endsWith(".apk", true)) it else "$it.apk" })
                onConfirm(ApkSaveChoice(out, key, schemes))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (manageKeys) KeyManagerDialog(onDismiss = { manageKeys = false; keyTick++ })
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = onClick).padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label, fontSize = 14.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Runs "save into APK" jobs for one screen: [start] hands the staged entries to the APK job service
 * (background, notification, Cancel), [Panel] shows its progress while it runs and the result dialog
 * (Install / Close) when it's done.
 */
internal class ApkSaveRunner(private val context: Context) {
    var jobId by mutableLongStateOf(0L)
    var report by mutableStateOf<ApkJobReport?>(null)

    /** Starts the job; false (with a message) when another APK job is still running. */
    fun start(source: File, replacements: Map<String, File>, choice: ApkSaveChoice): Boolean {
        ApkJobService.busyReason()?.let { AppSnackbar.show(context, it); return false }
        jobId = ApkJobService.start(
            context,
            ApkJobRequest(
                kind = ApkJobKind.PATCH, input = source.absolutePath, splits = emptyList(), output = choice.output.absolutePath,
                edits = null, key = choice.key, schemes = choice.schemes, installAfter = false,
                replacements = replacements.mapValues { it.value.absolutePath },
            ),
        )
        return true
    }

    val running: Boolean get() = jobId != 0L && ApkJobManager.current.jobId == jobId && ApkJobManager.current.isRunning

    /** The progress strip while this screen's job runs, then the result. [onDone] gets the finished APK. */
    @Composable
    fun Panel(onDone: (File) -> Unit = {}) {
        val st by ApkJobManager.state.collectAsState()
        val mine = jobId != 0L && st.jobId == jobId
        LaunchedEffect(st.stage, st.jobId) {
            if (!mine) return@LaunchedEffect
            when (st.stage) {
                ApkJobStage.DONE -> { report = st.report; jobId = 0L; st.report?.let { onDone(File(it.outputPath)) } }
                ApkJobStage.ERROR -> { AppSnackbar.show(context, "Couldn't save the APK: ${st.error ?: "unknown error"}", long = true); jobId = 0L }
                ApkJobStage.CANCELLED -> { AppSnackbar.show(context, "Save cancelled"); jobId = 0L }
                else -> Unit
            }
        }
        if (mine && st.isRunning) {
            Column(
                Modifier.fillMaxWidth()
                    .then(if (isAurora) Modifier.padding(horizontal = 12.dp, vertical = 6.dp).glass(Glass.Card, GlassLevel.Raised) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saving ${st.sourceName} · ${st.stage.label}…", fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { ApkJobService.cancel(context) }) { Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.error) }
                }
                LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp))
            }
        }
        report?.let { r ->
            ApkResultDialog(
                report = r,
                onInstall = { report = null; installApkFile(context, File(r.outputPath)) },
                onDismiss = { report = null },
                caveats = "The APK is signed with your key now, so Android installs it only over a copy signed with the same key: uninstall the original first.",
            )
        }
    }
}

@Composable
internal fun rememberApkSaveRunner(): ApkSaveRunner {
    val context = LocalContext.current
    return remember { ApkSaveRunner(context.applicationContext) }
}

// ── Lists ──

/** A filter / search field over a list, in either look. */
@Composable
internal fun InternalsSearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
            .then(if (isAurora) Modifier.padding(horizontal = 12.dp, vertical = 4.dp).glass(Glass.Card, GlassLevel.Raised) else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Clear") } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** One row of a browser list: icon, a (monospace when [code]) title over a detail line, an optional end label. */
@Composable
internal fun InternalsRow(
    icon: ImageVector?,
    title: String,
    detail: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    end: String? = null,
    code: Boolean = false,
    divider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                    fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = if (code) FontWeight.Normal else FontWeight.Medium,
                )
                if (!detail.isNullOrEmpty()) {
                    Text(detail, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (end != null) Text(end, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isAurora) 0.35f else 0.6f), modifier = Modifier.padding(start = if (icon != null) 50.dp else 16.dp))
    }
}

/** A small section label above a group of rows. */
@Composable
internal fun InternalsHeader(text: String) {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** A thin "working on it" line with a label, for long background steps (a dex rebuild). */
@Composable
internal fun BusyStrip(label: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
    }
}
