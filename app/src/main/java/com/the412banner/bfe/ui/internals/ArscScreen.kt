package com.the412banner.bfe.ui.internals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.apk.internals.ArscDoc
import com.the412banner.bfe.apk.internals.ArscHit
import com.the412banner.bfe.apk.internals.ArscKind
import com.the412banner.bfe.apk.internals.ArscResource
import com.the412banner.bfe.apk.internals.ArscType
import com.the412banner.bfe.apk.internals.ArscValue
import com.the412banner.bfe.apk.internals.InternalsSource
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

private sealed interface ArscState {
    data object Loading : ArscState
    data class Failed(val message: String) : ArscState
    class Ready(val doc: ArscDoc) : ArscState
}

/**
 * The resources browser for a resources.arsc (inside an APK or on its own): package → type (string,
 * drawable, layout, …) → resource → its value in every configuration (language, density, night …).
 * Search by name or value across everything. Strings and simple values (bool, integer, color,
 * dimension) can be edited; Save writes the table back — into the APK as a new copy or over it, signed
 * again in the background — or into the file itself.
 */
@Composable
internal fun ArscScreen(source: InternalsSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ArscState>(ArscState.Loading) }
    val runner = rememberApkSaveRunner()
    var ask by remember { mutableStateOf<CompletableDeferred<ApkSaveChoice?>?>(null) }

    var pkgIndex by remember { mutableIntStateOf(-1) }
    var type by remember { mutableStateOf<String?>(null) }
    var resource by remember { mutableStateOf<ArscResource?>(null) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ArscHit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ArscValue?>(null) }
    // Bumped on every change so the lists and the Save button recompose.
    var editTick by remember { mutableIntStateOf(0) }
    var savedTick by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var leaveAsk by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        state = withContext(Dispatchers.IO) {
            runCatching<ArscState> { ArscState.Ready(ArscDoc.open(source.readBytes())) }
                .getOrElse { ArscState.Failed(it.message ?: it.javaClass.simpleName) }
        }
        (state as? ArscState.Ready)?.let { if (it.doc.packages.size == 1) pkgIndex = 0 }
    }
    val doc = (state as? ArscState.Ready)?.doc
    val dirty = editTick != savedTick

    // Search: debounced, off the main thread, a new query cancels the last.
    LaunchedEffect(doc, query) {
        val d = doc ?: return@LaunchedEffect
        if (query.isBlank()) { hits = null; searching = false; return@LaunchedEffect }
        delay(250)
        searching = true
        val job = coroutineContext[Job]
        hits = withContext(Dispatchers.Default) { d.search(query, cancelled = { job?.isActive == false }) }
        searching = false
    }

    fun save() {
        val d = doc ?: return
        if (saving) return
        saving = true
        val tick = editTick
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { runCatching { d.bytes() } }.getOrElse {
                saving = false
                AppSnackbar.show(context, "Couldn't write the resources: ${it.message ?: it.javaClass.simpleName}", long = true); return@launch
            }
            val entry = source.entry
            if (entry == null) {
                withContext(Dispatchers.IO) { runCatching { saveStandalone(context, source.file, bytes) } }
                    .onSuccess { savedTick = tick; AppSnackbar.show(context, if (it == source.file) "Saved" else "Saved as ${it.absolutePath}", long = it != source.file) }
                    .onFailure { AppSnackbar.show(context, "Couldn't save: ${it.message ?: it.javaClass.simpleName}", long = true) }
                saving = false
                return@launch
            }
            val ch = CompletableDeferred<ApkSaveChoice?>()
            ask = ch
            val choice = ch.await()
            if (choice != null) {
                val staged = withContext(Dispatchers.IO) { File(stagingDir(context), "resources.arsc").apply { writeBytes(bytes) } }
                if (runner.start(source.file, mapOf(entry to staged), choice)) savedTick = tick
            }
            saving = false
        }
    }

    fun back() {
        when {
            resource != null -> resource = null
            query.isNotEmpty() -> query = ""
            type != null -> type = null
            pkgIndex >= 0 && (doc?.packages?.size ?: 0) > 1 -> pkgIndex = -1
            dirty -> leaveAsk = true
            else -> onClose()
        }
    }
    BackHandler { back() }

    Column(Modifier.fillMaxSize()) {
        val pkg = doc?.packages?.getOrNull(pkgIndex)
        DocTopBar(
            title = resource?.let { "@${it.type}/${it.name}" } ?: type?.let { "@$it" } ?: source.name,
            subtitle = when {
                resource != null -> resource!!.hexId + " · " + resource!!.values.size + " value" + if (resource!!.values.size == 1) "" else "s"
                pkg != null && doc != null -> doc.packageLabel(pkg) + (source.entry?.let { " · in ${source.file.name}" } ?: "")
                else -> source.entry?.let { "in ${source.file.name}" }
            },
            onBack = { back() },
        ) {
            if (dirty) IconButton(onClick = { save() }, enabled = !saving && !runner.running) { Icon(Icons.Filled.Save, "Save") }
        }
        if (doc != null && resource == null) {
            InternalsSearchField(query, { query = it }, "Search names and values")
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val st = state) {
                ArscState.Loading -> DocNotice(Icons.Filled.Inventory2, "Reading ${source.name}…", null)
                is ArscState.Failed -> DocNotice(Icons.Filled.ErrorOutline, "Couldn't read ${source.name}", st.message)
                is ArscState.Ready -> DocSurface {
                    val d = st.doc
                    val r = resource
                    val h = hits
                    when {
                        r != null -> ValuesList(r, editTick) { v -> if (v.editable) editing = v else AppSnackbar.show(context, "This kind of value can't be edited here") }
                        query.isNotBlank() -> HitsList(h, searching, editTick) { resource = it }
                        pkg == null -> LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(d.packages) { i, p -> InternalsRow(Icons.Filled.Folder, p.name, "Package 0x%02x".format(p.id), { pkgIndex = i }) }
                        }
                        type == null -> TypesList(d, pkg) { type = it }
                        else -> ResourcesList(d, pkg, type!!, editTick) { resource = it }
                    }
                }
            }
        }
        runner.Panel()
    }

    editing?.let { v ->
        val r = resource
        if (r == null || doc == null) { editing = null } else EditValueDialog(r, v, onDismiss = { editing = null }) { input ->
            // Returns an error to show, or null when the value was set.
            runCatching { doc.setValue(r, v, input) }.fold(
                onSuccess = { editTick++; editing = null; null },
                onFailure = { it.message ?: "That value can't be used" },
            )
        }
    }
    ask?.let { d ->
        SaveIntoApkDialog(apk = source.file, what = source.entry ?: source.name, onDismiss = { ask = null; d.complete(null) }, onConfirm = { c -> ask = null; d.complete(c) })
    }
    if (leaveAsk) {
        OutlinedAlertDialog(
            onDismissRequest = { leaveAsk = false },
            title = { Text("Leave without saving?") },
            text = { Text("Your changes to ${source.name} haven't been saved.") },
            confirmButton = { TextButton(onClick = { leaveAsk = false; save() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { leaveAsk = false; onClose() }) { Text("Discard") } },
        )
    }
}

@Composable
private fun TypesList(doc: ArscDoc, pkg: com.reandroid.arsc.chunk.PackageBlock, onPick: (String) -> Unit) {
    var types by remember(pkg) { mutableStateOf<List<ArscType>?>(null) }
    LaunchedEffect(pkg) { types = withContext(Dispatchers.IO) { doc.types(pkg) } }
    val t = types
    if (t == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(t, key = { it.name }) { ty -> InternalsRow(Icons.Filled.Folder, ty.name, null, { onPick(ty.name) }, end = "%,d".format(ty.count)) }
    }
}

@Composable
private fun ResourcesList(doc: ArscDoc, pkg: com.reandroid.arsc.chunk.PackageBlock, type: String, tick: Int, onPick: (ArscResource) -> Unit) {
    var list by remember(pkg, type) { mutableStateOf<List<ArscResource>?>(null) }
    LaunchedEffect(pkg, type) { list = withContext(Dispatchers.IO) { doc.resources(pkg, type) } }
    val l = list
    if (l == null) { Loading(); return }
    if (l.isEmpty()) { DocNotice(Icons.Filled.Inventory2, "No resources of this type", null); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(l, key = { it.id }) { r ->
            val first = remember(r, tick) { r.values.firstOrNull { it.config.isEmpty() } ?: r.values.firstOrNull() }
            InternalsRow(null, r.name, first?.text?.lineSequence()?.firstOrNull()?.take(160), { onPick(r) },
                end = if (r.values.size > 1) "${r.values.size}×" else null)
        }
    }
}

@Composable
private fun HitsList(hits: List<ArscHit>?, searching: Boolean, tick: Int, onPick: (ArscResource) -> Unit) {
    if (hits == null || (searching && hits.isEmpty())) { Loading(); return }
    if (hits.isEmpty()) { DocNotice(Icons.Filled.Inventory2, "Nothing found", null); return }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(hits) { _, h ->
            val v = h.value
            val detail = remember(h, tick) { if (v == null) h.resource.values.firstOrNull()?.text?.take(160) else "[${v.configLabel}] ${v.text.take(160)}" }
            InternalsRow(null, "@${h.resource.type}/${h.resource.name}", detail, { onPick(h.resource) })
        }
        if (hits.size >= 2000) item { InternalsHeader("Showing the first 2,000 results") }
    }
}

@Composable
private fun ValuesList(r: ArscResource, tick: Int, onPick: (ArscValue) -> Unit) {
    val values = remember(r, tick) { r.values.toList() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(values) { v ->
            InternalsRow(
                icon = null,
                title = v.configLabel,
                detail = v.text.ifEmpty { "(empty)" },
                onClick = { onPick(v) },
                end = if (v.editable) v.kind.label else null,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Changes one value: a text field prefilled with it and a hint for its kind; [onApply] returns an error or null. */
@Composable
private fun EditValueDialog(r: ArscResource, v: ArscValue, onDismiss: () -> Unit, onApply: (String) -> String?) {
    var text by remember(v) { mutableStateOf(v.text) }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@${r.type}/${r.name}") },
        text = {
            Column {
                Text("${v.kind.label} · ${v.configLabel}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = v.kind != ArscKind.STRING,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: when (v.kind) {
                            ArscKind.BOOL -> "true or false"
                            ArscKind.INT -> "A whole number, or 0x… hex"
                            ArscKind.COLOR -> "#RGB, #ARGB, #RRGGBB or #AARRGGBB"
                            ArscKind.DIMEN -> "e.g. 16dp, 14sp, 2px, 1.5, 50%"
                            else -> "The new text"
                        }, fontSize = 11.sp)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (v.kind == ArscKind.STRING) FontFamily.Default else FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { error = onApply(text) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
