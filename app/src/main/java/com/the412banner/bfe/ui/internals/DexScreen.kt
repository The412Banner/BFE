package com.the412banner.bfe.ui.internals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.the412banner.bfe.apk.internals.DexFile
import com.the412banner.bfe.apk.internals.DexHit
import com.the412banner.bfe.apk.internals.DexSearchMode
import com.the412banner.bfe.apk.internals.DexSet
import com.the412banner.bfe.apk.internals.SmaliException
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.docs.CodeLang
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

private sealed interface DexLoad {
    data object Loading : DexLoad
    data class Failed(val message: String) : DexLoad
    class Ready(val dex: DexFile, val names: List<String>) : DexLoad
}

/** The class open in the smali view: its type, and its smali once decoded. */
private class OpenClass(val type: String, val smali: String?)

/**
 * The dex browser: one dex of an APK (switch between classes.dex, classes2.dex, … at the top) or a
 * .dex on its own. Classes are browsed by package like folders, or found by class name, method name
 * or a string the code uses. A class opens as smali; Edit assembles the edited smali back into the
 * dex and saves it — into a new APK (default) or over the original, re-signed in the background — or
 * in place for a .dex on its own. Every saved class is kept, so later saves include earlier edits.
 */
@Composable
internal fun DexScreen(file: File, inApk: Boolean, startEntry: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val set = remember(file) { if (inApk) DexSet.forApk(file) else DexSet.forFile(file) }
    var entry by remember { mutableStateOf(startEntry?.takeIf { it in set.entries } ?: set.entries.firstOrNull()) }
    var load by remember { mutableStateOf<DexLoad>(DexLoad.Loading) }
    var pkg by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DexSearchMode.CLASSES) }
    var hits by remember { mutableStateOf<List<DexHit>?>(null) }
    var picker by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<OpenClass?>(null) }
    // Classes saved so far, per dex entry, keyed by their original type.
    val edits = remember { HashMap<String, HashMap<String, ClassDef>>() }
    val runner = rememberApkSaveRunner()
    var ask by remember { mutableStateOf<CompletableDeferred<ApkSaveChoice?>?>(null) }

    LaunchedEffect(entry) {
        val e = entry ?: run { load = DexLoad.Failed("There's no dex file in ${file.name}."); return@LaunchedEffect }
        load = DexLoad.Loading
        pkg = ""
        hits = null
        load = withContext(Dispatchers.IO) {
            runCatching<DexLoad> {
                val d = set.load(e)
                DexLoad.Ready(d, d.classes.map { it.removePrefix("L").removeSuffix(";") })
            }.getOrElse {
                DexLoad.Failed(if (it is OutOfMemoryError) "$e is too big to open here." else it.message ?: it.javaClass.simpleName)
            }
        }
    }

    // Search off the main thread; typing again cancels the last search.
    LaunchedEffect(query, mode, load) {
        val d = (load as? DexLoad.Ready)?.dex ?: return@LaunchedEffect
        if (query.isBlank()) { hits = null; return@LaunchedEffect }
        delay(300)
        hits = null
        val job = coroutineContext[Job]
        hits = withContext(Dispatchers.Default) { d.search(query, mode, cancelled = { job?.isActive == false }) }
    }

    // Decode the opened class.
    LaunchedEffect(open?.type, load) {
        val o = open ?: return@LaunchedEffect
        if (o.smali != null) return@LaunchedEffect
        val d = (load as? DexLoad.Ready)?.dex ?: return@LaunchedEffect
        val e = entry ?: return@LaunchedEffect
        val text = withContext(Dispatchers.Default) {
            runCatching { edits[e]?.get(o.type)?.let { DexFile.smaliOf(it) } ?: d.smali(o.type) }
        }
        text.onSuccess { open = OpenClass(o.type, it) }
            .onFailure { AppSnackbar.show(context, "Couldn't decode ${DexFile.javaName(o.type)}: ${it.message ?: it.javaClass.simpleName}", long = true); open = null }
    }

    // Assemble, rebuild the dex with every saved class, then write it where it belongs.
    suspend fun save(type: String, smali: String): Boolean {
        val d = (load as? DexLoad.Ready)?.dex ?: return false
        val e = entry ?: return false
        val def = withContext(Dispatchers.Default) { runCatching { DexFile.assemble(smali) } }.getOrElse { err ->
            val msg = (err as? SmaliException)?.errors?.take(3)?.joinToString("\n") ?: err.message ?: err.javaClass.simpleName
            AppSnackbar.show(context, "The smali has errors:\n$msg", long = true)
            return false
        }
        val mine = HashMap(edits[e] ?: emptyMap()).apply { put(type, def) }
        if (!inApk) {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(stagingDir(context), file.name)
                    d.rebuild(mine, tmp)
                    saveStandalone(context, file, tmp.readBytes())
                }
            }.onSuccess { AppSnackbar.show(context, if (it == file) "Saved" else "Saved as ${it.absolutePath}", long = it != file) }
                .onFailure { AppSnackbar.show(context, "Couldn't save: ${it.message ?: it.javaClass.simpleName}", long = true) }
                .isSuccess
            if (ok) edits[e] = mine
            return ok
        }
        val deferred = CompletableDeferred<ApkSaveChoice?>()
        ask = deferred
        val choice = deferred.await() ?: return false
        val staged = withContext(Dispatchers.IO) {
            runCatching { File(stagingDir(context), e).also { d.rebuild(mine, it) } }
        }.getOrElse {
            AppSnackbar.show(context, "Couldn't rebuild $e: ${it.message ?: it.javaClass.simpleName}", long = true)
            return false
        }
        val started = runner.start(file, mapOf(e to staged), choice)
        if (started) edits[e] = mine
        return started
    }

    val o = open
    if (o != null) {
        val st = load as? DexLoad.Ready
        if (o.smali == null || st == null) {
            Column(Modifier.fillMaxSize()) {
                BackHandler { open = null }
                DocTopBar(DexFile.javaName(o.type).substringAfterLast('.'), entry, onBack = { open = null }) {}
                DocNotice(Icons.Filled.Code, "Decoding ${DexFile.javaName(o.type)}…", null)
            }
        } else {
            CodeScreen(
                title = DexFile.javaName(o.type).substringAfterLast('.'),
                subtitle = listOfNotNull(DexFile.javaName(o.type).substringBeforeLast('.', "").ifEmpty { null }, entry).joinToString(" · "),
                text = o.smali,
                lang = CodeLang.SMALI,
                onBack = { open = null },
                onSave = { t -> save(o.type, t) },
                bottom = { runner.Panel() },
            )
        }
    } else {
        BackHandler {
            when {
                searching -> { searching = false; query = "" }
                pkg.isNotEmpty() -> pkg = pkg.dropLast(1).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                else -> onClose()
            }
        }
        Column(Modifier.fillMaxSize()) {
            val ready = load as? DexLoad.Ready
            DocTopBar(
                title = if (pkg.isEmpty()) (entry ?: file.name) else pkg.dropLast(1).replace('/', '.'),
                subtitle = listOfNotNull(
                    ready?.let { "%,d classes".format(it.names.size) },
                    if (inApk) file.name else null,
                ).joinToString(" · ").ifEmpty { null },
                onBack = onClose,
            ) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, "Search") }
                if (set.entries.size > 1) {
                    Box {
                        TextButton(onClick = { picker = true }) {
                            Text(entry?.removeSuffix(".dex") ?: "dex")
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        BfeDropdownMenu(expanded = picker, onDismissRequest = { picker = false }) {
                            set.entries.forEach { e ->
                                DropdownMenuItem(
                                    text = { Text(e + (set.classCount(e)?.let { "  ·  %,d".format(it) } ?: "")) },
                                    leadingIcon = { Icon(Icons.Filled.Memory, null) },
                                    onClick = { picker = false; if (e != entry) entry = e },
                                )
                            }
                        }
                    }
                }
            }
            if (searching) {
                InternalsSearchField(query, { query = it }, "Find a ${mode.label.lowercase().removeSuffix("s")}…", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                    DexSearchMode.values().forEach { m -> FilterChip(selected = m == mode, onClick = { mode = m }, label = { Text(m.label) }) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val st = load) {
                    DexLoad.Loading -> DocNotice(Icons.Filled.Memory, "Reading ${entry ?: file.name}…", null)
                    is DexLoad.Failed -> DocNotice(Icons.Filled.ErrorOutline, "Couldn't open ${entry ?: file.name}", st.message)
                    is DexLoad.Ready -> DocSurface {
                        val h = hits
                        when {
                            searching && query.isNotBlank() && h == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            searching && h != null && h.isEmpty() -> DocNotice(Icons.Filled.SearchOff, "Nothing found", "No ${mode.label.lowercase()} match “${query.trim()}”.")
                            searching && h != null -> LazyColumn(Modifier.fillMaxSize()) {
                                items(h.size) { i ->
                                    val hit = h[i]
                                    InternalsRow(Icons.Filled.Code, DexFile.javaName(hit.classType), hit.detail, { open = OpenClass(hit.classType, null) }, code = true)
                                }
                            }
                            else -> PackageBrowser(
                                names = st.names,
                                pkg = pkg,
                                onPackage = { pkg = it },
                                onClass = { n -> open = OpenClass("L$n;", null) },
                            )
                        }
                    }
                }
            }
            runner.Panel()
        }
    }

    ask?.let { d ->
        SaveIntoApkDialog(
            apk = file,
            what = entry ?: "classes.dex",
            onDismiss = { ask = null; d.complete(null) },
            onConfirm = { c -> ask = null; d.complete(c) },
        )
    }
}
