// SegmentedButton / FilterChip are still experimental in Material3 — file-level opt-in.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.the412banner.bfe.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.the412banner.bfe.apk.ApkEdits
import com.the412banner.bfe.apk.ApkJobKind
import com.the412banner.bfe.apk.ApkJobReport
import com.the412banner.bfe.apk.ApkMeta
import com.the412banner.bfe.apk.KeyRef
import com.the412banner.bfe.apk.PackageNames
import com.the412banner.bfe.apk.SignSchemes
import com.the412banner.bfe.apk.SigningKeys
import com.the412banner.bfe.storage.InstalledApp
import com.the412banner.bfe.storage.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the editor hands back. */
data class ApkEditRequest(
    val edits: ApkEdits,
    val outputName: String,
    val key: KeyRef,
    val schemes: SignSchemes,
    val installAfter: Boolean,
)

/** Copies a picked content:// Uri into the cache so the engines get a real path. */
internal fun stageUri(context: Context, uri: Uri, name: String): File? = runCatching {
    val dir = File(context.cacheDir, "picked").apply { mkdirs() }
    val f = File(dir, name)
    context.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return null
    f
}.getOrNull()

/** Display name of a content Uri (best effort), else [fallback]. */
internal fun uriDisplayName(context: Context, uri: Uri, fallback: String): String = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: fallback

/** The honest-caveats text, shown once as a card and always in the result. */
internal const val CLONE_CAVEATS =
    "A clone is the same code under a new package name. Apps that verify their own signature, " +
        "Play-licensed apps, apps bound to Firebase / Google services (push, sign-in, maps) and " +
        "split/bundle installs may refuse to run or lose those features — that's inherent to " +
        "cloning, not a BFE bug. FileProviders whose authority is hard-coded in the app's code " +
        "may also break (sharing files out of the clone)."

// ── Key picker row (shared by the editor + sign dialog) ──

@Composable
private fun KeyAndSchemesRows(
    keys: List<KeyRef>,
    key: KeyRef,
    onKey: (KeyRef) -> Unit,
    schemes: SignSchemes,
    onSchemes: (SignSchemes) -> Unit,
    onManageKeys: () -> Unit,
) {
    var keyMenu by remember { mutableStateOf(false) }
    Text("Signing", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { keyMenu = true }, modifier = Modifier.weight(1f)) {
            Text(key.displayName, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }, modifier = Modifier.outlinedMenuCard()) {
            keys.forEach { k ->
                DropdownMenuItem(text = { Text(k.displayName + if (k.isBuiltIn) "" else "  (${k.alias})") }, onClick = { onKey(k); keyMenu = false })
            }
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Manage keys…") }, onClick = { keyMenu = false; onManageKeys() })
        }
    }
    // Chips wrap on a narrow dialog rather than clipping.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("v1" to schemes.v1, "v2" to schemes.v2, "v3" to schemes.v3).forEach { (name, on) ->
            FilterChip(
                selected = on,
                onClick = {
                    onSchemes(
                        when (name) {
                            "v1" -> schemes.copy(v1 = !on)
                            "v2" -> schemes.copy(v2 = !on)
                            else -> schemes.copy(v3 = !on)
                        }
                    )
                },
                label = { Text(name, fontSize = 12.sp) },
            )
        }
        Text("signature schemes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

// ── Clone / Edit editor ──

/**
 * The APK editor: package name (validated), label, versionCode/Name, icon, an Advanced section of
 * manifest scalars, the output file name, signing key + schemes, and Clone / Clone & Install.
 * Everything is prefilled from [meta]; a field left equal to the source is sent as "no change".
 */
@Composable
internal fun ApkEditorDialog(
    meta: ApkMeta,
    kind: ApkJobKind,
    defaultOutputName: String,
    onDismiss: () -> Unit,
    onManageKeys: () -> Unit,
    onConfirm: (ApkEditRequest) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var caveatsSeen by remember { mutableStateOf(prefs.getBoolean("apkCloneCaveatsSeen", false)) }
    val keys = remember { SigningKeys.list(context) }
    var key by remember { mutableStateOf(SigningKeys.lastUsed(context)) }
    var schemes by remember { mutableStateOf(SigningKeys.lastSchemes(context)) }

    var pkg by remember { mutableStateOf(if (kind == ApkJobKind.CLONE) meta.packageName + ".clone" else meta.packageName) }
    var label by remember { mutableStateOf(meta.label) }
    var versionCode by remember { mutableStateOf(meta.versionCode.toString()) }
    var versionName by remember { mutableStateOf(meta.versionName) }
    var iconPath by remember { mutableStateOf<String?>(null) }
    var outName by remember { mutableStateOf(defaultOutputName) }
    var advanced by remember { mutableStateOf(false) }
    var minSdk by remember { mutableStateOf(meta.minSdk?.toString() ?: "") }
    var targetSdk by remember { mutableStateOf(meta.targetSdk?.toString() ?: "") }
    var debuggable by remember { mutableStateOf(meta.debuggable) }
    var allowBackup by remember { mutableStateOf(meta.allowBackup) }
    var extractNative by remember { mutableStateOf(meta.extractNativeLibs) }

    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) iconPath = stageUri(context, uri, "icon-${System.currentTimeMillis()}")?.absolutePath
    }
    val iconPreview = remember(iconPath) {
        iconPath?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
    }
    val sourceIcon = remember(meta.sourcePath) {
        runCatching {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(meta.sourcePath, 0)?.applicationInfo ?: return@runCatching null
            info.sourceDir = meta.sourcePath; info.publicSourceDir = meta.sourcePath
            info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }

    val pkgProblem = PackageNames.problem(pkg.trim())
    val vcOk = versionCode.trim().toIntOrNull() != null
    val minOk = minSdk.isBlank() || minSdk.trim().toIntOrNull() != null
    val tgtOk = targetSdk.isBlank() || targetSdk.trim().toIntOrNull() != null
    val outOk = outName.isNotBlank() && !outName.contains('/')
    val canConfirm = pkgProblem == null && vcOk && minOk && tgtOk && outOk && (schemes.v1 || schemes.v2 || schemes.v3)

    fun request(install: Boolean): ApkEditRequest {
        val p = pkg.trim()
        val edits = ApkEdits(
            packageName = p.takeIf { it != meta.packageName },
            label = label.takeIf { it != meta.label && it.isNotBlank() },
            versionCode = versionCode.trim().toIntOrNull()?.takeIf { it != meta.versionCode },
            versionName = versionName.takeIf { it != meta.versionName },
            minSdk = minSdk.trim().toIntOrNull()?.takeIf { it != meta.minSdk },
            targetSdk = targetSdk.trim().toIntOrNull()?.takeIf { it != meta.targetSdk },
            debuggable = debuggable?.takeIf { it != meta.debuggable },
            allowBackup = allowBackup?.takeIf { it != meta.allowBackup },
            extractNativeLibs = extractNative?.takeIf { it != meta.extractNativeLibs },
            iconImagePath = iconPath,
        )
        SigningKeys.setLastUsed(context, key); SigningKeys.setLastSchemes(context, schemes)
        return ApkEditRequest(edits, outName.trim().let { if (it.endsWith(".apk", true)) it else "$it.apk" }, key, schemes, install)
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == ApkJobKind.CLONE) "Clone APK" else "Edit APK") },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                if (!caveatsSeen) {
                    Text(CLONE_CAVEATS, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { caveatsSeen = true; prefs.edit().putBoolean("apkCloneCaveatsSeen", true).apply() }) { Text("Got it", fontSize = 12.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val shown = iconPreview ?: sourceIcon
                    if (shown != null) Image(bitmap = shown, contentDescription = null, modifier = Modifier.size(48.dp).clickable { pickIcon.launch("image/*") })
                    else Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp).clickable { pickIcon.launch("image/*") })
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(meta.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${meta.versionName} (${meta.versionCode})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (meta.splitNames.isNotEmpty()) Text("Split install: ${meta.splitNames.size} split APK(s) will be merged", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { pickIcon.launch("image/*") }) { Text(if (iconPath == null) "Icon…" else "Icon ✓", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = pkg, onValueChange = { pkg = it }, label = { Text("Package name") }, singleLine = true,
                    isError = pkgProblem != null, supportingText = { if (pkgProblem != null) Text(pkgProblem, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("App label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(
                        value = versionCode, onValueChange = { versionCode = it }, label = { Text("versionCode") }, singleLine = true, isError = !vcOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = versionName, onValueChange = { versionName = it }, label = { Text("versionName") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = outName, onValueChange = { outName = it }, label = { Text("Output file") }, singleLine = true, isError = !outOk,
                    suffix = { if (!outName.endsWith(".apk", true)) Text(".apk", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Advanced ▴" else "Advanced ▾", fontSize = 12.sp) }
                if (advanced) {
                    Row {
                        OutlinedTextField(
                            value = minSdk, onValueChange = { minSdk = it }, label = { Text("minSdk") }, singleLine = true, isError = !minOk,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = targetSdk, onValueChange = { targetSdk = it }, label = { Text("targetSdk") }, singleLine = true, isError = !tgtOk,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        )
                    }
                    @Composable
                    fun TriRow(name: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(if (value == null) "unset" else value.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = value == true, onCheckedChange = { onChange(it) })
                        }
                    }
                    TriRow("debuggable", debuggable) { debuggable = it }
                    TriRow("allowBackup", allowBackup) { allowBackup = it }
                    TriRow("extractNativeLibs", extractNative) { extractNative = it }
                    if (meta.sharedUserId != null) Text("sharedUserId \"${meta.sharedUserId}\" will be dropped on rename", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meta.providerAuthorities.isNotEmpty()) Text("${meta.providerAuthorities.size} provider authorit${if (meta.providerAuthorities.size == 1) "y" else "ies"} will be re-prefixed on rename", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (meta.declaredPermissions.isNotEmpty()) Text("${meta.declaredPermissions.size} declared permission(s); package-scoped ones follow the rename", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                KeyAndSchemesRows(keys, key, { key = it }, schemes, { schemes = it }, onManageKeys)
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = canConfirm, onClick = { onConfirm(request(install = false)) }) { Text(if (kind == ApkJobKind.CLONE) "Clone" else "Save") }
                TextButton(enabled = canConfirm, onClick = { onConfirm(request(install = true)) }) { Text(if (kind == ApkJobKind.CLONE) "Clone & Install" else "Save & Install") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Sign ──

@Composable
internal fun SignApkDialog(
    fileName: String,
    defaultOutputName: String,
    onDismiss: () -> Unit,
    onManageKeys: () -> Unit,
    onConfirm: (outputName: String, key: KeyRef, schemes: SignSchemes) -> Unit,
) {
    val context = LocalContext.current
    val keys = remember { SigningKeys.list(context) }
    var key by remember { mutableStateOf(SigningKeys.lastUsed(context)) }
    var schemes by remember { mutableStateOf(SigningKeys.lastSchemes(context)) }
    var outName by remember { mutableStateOf(defaultOutputName) }
    val ok = outName.isNotBlank() && !outName.contains('/') && (schemes.v1 || schemes.v2 || schemes.v3)
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign APK") },
        text = {
            Column {
                Text(fileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Any existing signature is stripped; the output is zip-aligned and re-signed.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = outName, onValueChange = { outName = it }, label = { Text("Output file") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                KeyAndSchemesRows(keys, key, { key = it }, schemes, { schemes = it }, onManageKeys)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                SigningKeys.setLastUsed(context, key); SigningKeys.setLastSchemes(context, schemes)
                onConfirm(outName.trim().let { if (it.endsWith(".apk", true)) it else "$it.apk" }, key, schemes)
            }) { Text("Sign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Key manager ──

@Composable
internal fun KeyManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val keys = remember(tick) { SigningKeys.list(context) }
    var lastUsed by remember(tick) { mutableStateOf(SigningKeys.lastUsed(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<File?>(null) }
    var importName by remember { mutableStateOf("") }
    var infoFor by remember { mutableStateOf<KeyRef?>(null) }

    val pickStore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uriDisplayName(context, uri, "keystore")
            importing = stageUri(context, uri, name)
            importName = name
            if (importing == null) error = "Couldn't read the picked file"
        }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signing keys") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "The built-in BFE Test Key is generated once and kept in app-private storage, so every clone shares one " +
                        "signature and can be updated in place. Import a PKCS12 (.p12/.pfx) or BKS keystore, or generate a new key.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                keys.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SigningKeys.setLastUsed(context, k); lastUsed = k }) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(k.displayName + if (k.displayName == lastUsed.displayName) "  •  default" else "", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${k.storeType} · alias ${k.alias}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { infoFor = k }) { Text("Info", fontSize = 11.sp) }
                        if (!k.isBuiltIn) IconButton(onClick = { SigningKeys.remove(context, k); tick++ }) {
                            Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { pickStore.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Import keystore…", fontSize = 12.sp) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { generating = true }, modifier = Modifier.weight(1f)) { Text("Generate new…", fontSize = 12.sp) }
                }
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    infoFor?.let { k ->
        var subject by remember(k) { mutableStateOf<String?>(null) }
        LaunchedEffect(k) { subject = withContext(Dispatchers.IO) { runCatching { SigningKeys.load(k).subject }.getOrElse { "Can't open: ${it.message}" } } }
        OutlinedAlertDialog(
            onDismissRequest = { infoFor = null },
            title = { Text(k.displayName) },
            text = { Column { Text(subject ?: "Opening…", fontSize = 12.sp); Text(k.storePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            confirmButton = { TextButton(onClick = { infoFor = null }) { Text("Close") } },
        )
    }

    if (generating) {
        var name by remember { mutableStateOf("My Key") }
        var cn by remember { mutableStateOf("") }
        var org by remember { mutableStateOf("") }
        var years by remember { mutableStateOf("25") }
        var pass by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        OutlinedAlertDialog(
            onDismissRequest = { if (!busy) generating = false },
            title = { Text("Generate key") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (shown in pickers)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cn, onValueChange = { cn = it }, label = { Text("CN (your name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = org, onValueChange = { org = it }, label = { Text("Organisation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = years, onValueChange = { years = it }, label = { Text("Validity (years)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    if (busy) Text("Generating RSA-2048…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                TextButton(enabled = !busy && name.isNotBlank() && pass.isNotEmpty() && years.toIntOrNull() != null, onClick = {
                    busy = true
                    scope.launch {
                        val dn = buildString {
                            append("CN=").append((cn.ifBlank { name }).replace(",", "\\,"))
                            if (org.isNotBlank()) append(", O=").append(org.replace(",", "\\,"))
                        }
                        val r = withContext(Dispatchers.IO) { runCatching { SigningKeys.generate(context, name.trim(), dn, years.toInt(), pass) } }
                        busy = false
                        r.onSuccess { generating = false; tick++ }.onFailure { err = it.message ?: "Generation failed" }
                    }
                }) { Text("Generate") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { generating = false }) { Text("Cancel") } },
        )
    }

    importing?.let { file ->
        var storeType by remember(file) { mutableStateOf(if (file.name.endsWith(".bks", true)) "BKS" else if (file.name.endsWith(".jks", true) || file.name.endsWith(".keystore", true)) "JKS" else "PKCS12") }
        var storePass by remember(file) { mutableStateOf("") }
        var keyPass by remember(file) { mutableStateOf("") }
        var aliases by remember(file) { mutableStateOf<List<String>>(emptyList()) }
        var alias by remember(file) { mutableStateOf("") }
        var err by remember(file) { mutableStateOf<String?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        OutlinedAlertDialog(
            onDismissRequest = { importing = null },
            title = { Text("Import keystore") },
            text = {
                Column {
                    Text(importName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SigningKeys.STORE_TYPES.forEach { t ->
                            FilterChip(selected = storeType == t, onClick = { storeType = t; aliases = emptyList() }, label = { Text(t, fontSize = 12.sp) })
                        }
                    }
                    OutlinedTextField(value = storePass, onValueChange = { storePass = it }, label = { Text("Store password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { SigningKeys.aliases(SigningKeys.openStore(storeType, file, storePass)) } }
                            r.onSuccess { aliases = it; alias = it.firstOrNull() ?: ""; err = if (it.isEmpty()) "No private-key entries in this keystore" else null }
                                .onFailure { err = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open & list aliases", fontSize = 12.sp) }
                    if (aliases.isNotEmpty()) {
                        Text("Alias", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            aliases.forEach { a ->
                                FilterChip(selected = alias == a, onClick = { alias = a }, label = { Text(a, fontSize = 12.sp) })
                            }
                        }
                        OutlinedTextField(value = keyPass, onValueChange = { keyPass = it }, label = { Text("Key password (blank = same as store)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    }
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = alias.isNotBlank(), onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { SigningKeys.import(context, file, storeType, alias, storePass, keyPass.ifEmpty { storePass }, importName) }
                        }
                        r.onSuccess { importing = null; tick++ }.onFailure { err = it.message ?: "Import failed" }
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importing = null }) { Text("Cancel") } },
        )
    }
}

// ── Installed app picker ──

@Composable
internal fun InstalledAppPickerDialog(onDismiss: () -> Unit, onPick: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var filter by remember { mutableStateOf("") }
    val all = remember { InstalledApps.list(context) }
    val shown = remember(all, filter) { if (filter.isBlank()) all else all.filter { it.label.contains(filter, true) || it.packageName.contains(filter, true) } }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone installed app") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp)) {
                OutlinedTextField(value = filter, onValueChange = { filter = it }, label = { Text("Filter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                LazyColumn {
                    items(shown, key = { it.packageName }) { app ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 6.dp)) {
                            val bmp = remember(app.packageName) { runCatching { pm.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap() }.getOrNull() }
                            if (bmp != null) Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(28.dp))
                            else Icon(Icons.Filled.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Result ──

@Composable
internal fun ApkResultDialog(report: ApkJobReport, onInstall: () -> Unit, onDismiss: () -> Unit) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("APK ready") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(File(report.outputPath).name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(report.packageName, report.label?.let { "\"$it\"" }, report.versionName?.let { "$it (${report.versionCode})" }).joinToString("  ·  "),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Signature: " + (if (report.verified) "verified " else "NOT verified ") +
                        listOfNotNull("v1".takeIf { report.v1 }, "v2".takeIf { report.v2 }, "v3".takeIf { report.v3 }).joinToString("+").ifBlank { "none" } +
                        (report.signerDn?.let { "\n$it" } ?: ""),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("Applied", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                report.fixups.forEach { Text("• $it", fontSize = 12.sp) }
                if (report.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Warnings", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    report.warnings.forEach { Text("• $it", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Text(CLONE_CAVEATS, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onInstall) { Text("Install") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
