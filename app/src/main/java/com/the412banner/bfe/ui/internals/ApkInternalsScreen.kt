package com.the412banner.bfe.ui.internals

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.the412banner.bfe.ApkInternalsActivity
import com.the412banner.bfe.apk.ApkMeta
import com.the412banner.bfe.apk.ApkRewriter
import com.the412banner.bfe.apk.internals.ApkEntries
import com.the412banner.bfe.apk.internals.InternalsSource
import com.the412banner.bfe.apk.internals.InternalsTool
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.screens.stageUri
import com.the412banner.bfe.util.IncomingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** What the opened tool works on, once the file is resolved. */
private sealed interface Resolved {
    data object Loading : Resolved
    data class Failed(val message: String) : Resolved
    class Ready(val file: File, val isZip: Boolean) : Resolved
}

/**
 * Picks the tool's screen for the request: resolves the file (a content Uri is copied into the cache),
 * then opens [tool] on [entry] inside it, on the file itself, or — for an APK / jar opened as a whole —
 * on the entry the tool is about (the manifest, resources.arsc, every dex, every class).
 */
@Composable
internal fun ApkInternalsScreen(tool: InternalsTool, path: String?, uri: Uri?, entry: String?, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var resolved by remember { mutableStateOf<Resolved>(Resolved.Loading) }
    LaunchedEffect(path, uri) {
        resolved = withContext(Dispatchers.IO) {
            runCatching {
                val real = path ?: uri?.let { runCatching { IncomingFile.resolvePath(context, it) }.getOrNull() }
                val f = real?.let { File(it) }?.takeIf { it.isFile && it.canRead() }
                    ?: uri?.let { stageUri(context, it, name.replace('/', '_')) }
                    ?: throw IOException("The file couldn't be read.")
                Resolved.Ready(f, ApkEntries.isZip(f))
            }.getOrElse { Resolved.Failed(it.message ?: "The file couldn't be read.") }
        }
    }
    when (val r = resolved) {
        Resolved.Loading -> Column(Modifier.fillMaxSize()) {
            DocTopBar(name, null, onBack = onClose) {}
            DocNotice(Icons.Filled.Android, "Opening…", null)
        }
        is Resolved.Failed -> Column(Modifier.fillMaxSize()) {
            BackHandler { onClose() }
            DocTopBar(name, null, onBack = onClose) {}
            DocNotice(Icons.Filled.ErrorOutline, "Couldn't open $name", r.message)
        }
        is Resolved.Ready -> {
            val f = r.file
            when (tool) {
                InternalsTool.HUB ->
                    if (r.isZip) ApkHubScreen(f, onClose) else NotThisKind(name, "This isn't an APK.", onClose)
                InternalsTool.AXML -> {
                    val src = when {
                        entry != null -> InternalsSource(f, entry)
                        r.isZip -> InternalsSource(f, "AndroidManifest.xml")
                        else -> InternalsSource(f, null)
                    }
                    AxmlScreen(src, onClose)
                }
                InternalsTool.ARSC -> {
                    val src = when {
                        entry != null -> InternalsSource(f, entry)
                        r.isZip -> InternalsSource(f, "resources.arsc")
                        else -> InternalsSource(f, null)
                    }
                    ArscScreen(src, onClose)
                }
                InternalsTool.DEX -> DexScreen(f, inApk = r.isZip, startEntry = entry, onClose = onClose)
                InternalsTool.JAVA -> when {
                    entry != null -> JavaClassScreen(InternalsSource(f, entry), onClose)
                    r.isZip -> JarScreen(f, onClose)
                    else -> JavaClassScreen(InternalsSource(f, null), onClose)
                }
            }
        }
    }
}

@Composable
private fun NotThisKind(name: String, message: String, onClose: () -> Unit) {
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize()) {
        DocTopBar(name, null, onBack = onClose) {}
        DocNotice(Icons.Filled.ErrorOutline, "Couldn't open $name", message)
    }
}

/** What the hub lists for one APK. */
private class HubInfo(val meta: ApkMeta?, val hasManifest: Boolean, val hasArsc: Boolean, val dex: List<Pair<String, Long>>, val classes: Int)

/**
 * The APK internals hub: the package at a glance, then Manifest, Resources and each dex file — every
 * row opens its tool on this APK.
 */
@Composable
internal fun ApkHubScreen(apk: File, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onClose() }
    var info by remember { mutableStateOf<HubInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(apk) {
        withContext(Dispatchers.IO) {
            runCatching {
                val meta = runCatching { ApkRewriter.inspect(apk) }.getOrNull()
                ZipFile(apk).use { z ->
                    val names = z.entries().asSequence().map { it.name to it.size }.toList()
                    val dex = ApkEntries.dexEntries(apk).map { n -> n to (names.firstOrNull { it.first == n }?.second ?: 0L) }
                    HubInfo(meta, z.getEntry("AndroidManifest.xml") != null, z.getEntry("resources.arsc") != null, dex,
                        names.count { it.first.endsWith(".class") })
                }
            }
        }.onSuccess { info = it }.onFailure { error = it.message ?: "The APK couldn't be read." }
    }
    fun open(tool: InternalsTool, entry: String?) {
        context.startActivity(ApkInternalsActivity.intent(context, tool, apk.absolutePath, null, entry?.substringAfterLast('/') ?: apk.name, entry))
    }
    Column(Modifier.fillMaxSize()) {
        val m = info?.meta
        DocTopBar(
            title = m?.label?.ifBlank { null } ?: apk.name,
            subtitle = m?.let { listOf(it.packageName, "${it.versionName} (${it.versionCode})").joinToString(" · ") } ?: "APK internals",
            onBack = onClose,
        ) {}
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val i = info
            when {
                error != null -> DocNotice(Icons.Filled.ErrorOutline, "Couldn't read ${apk.name}", error)
                i == null -> DocNotice(Icons.Filled.Android, "Reading ${apk.name}…", null)
                else -> DocSurface {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { InternalsHeader("APK internals") }
                        if (i.hasManifest) item {
                            InternalsRow(Icons.Filled.Code, "AndroidManifest.xml", "Binary XML · view, edit, save into the APK", { open(InternalsTool.AXML, "AndroidManifest.xml") })
                        }
                        if (i.hasArsc) item {
                            InternalsRow(Icons.Filled.Inventory2, "Resources", "resources.arsc · strings, colours, sizes per language and screen", { open(InternalsTool.ARSC, "resources.arsc") })
                        }
                        if (i.dex.isNotEmpty()) {
                            item { InternalsHeader("Dex files (${i.dex.size})") }
                            if (i.dex.size > 1) item {
                                InternalsRow(Icons.Filled.Memory, "All dex files", "Browse every dex of this APK, one at a time", { open(InternalsTool.DEX, null) })
                            }
                            items(i.dex, key = { it.first }) { (n, size) ->
                                InternalsRow(Icons.Filled.Memory, n, "Classes, search, smali", { open(InternalsTool.DEX, n) }, end = StringUtils.formatBytes(size))
                            }
                        }
                        if (i.classes > 0) item {
                            InternalsHeader("Java classes")
                            InternalsRow(Icons.Filled.Code, "${i.classes} .class files", "javap-style view", { open(InternalsTool.JAVA, null) })
                        }
                        val meta = i.meta
                        if (meta != null) item {
                            InternalsHeader("Package")
                            InternalsRow(null, meta.packageName, listOfNotNull(
                                "version ${meta.versionName} (${meta.versionCode})",
                                meta.minSdk?.let { "min SDK $it" }, meta.targetSdk?.let { "target SDK $it" },
                            ).joinToString(" · "), null, divider = false)
                        }
                    }
                }
            }
        }
    }
}
