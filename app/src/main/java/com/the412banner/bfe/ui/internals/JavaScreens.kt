package com.the412banner.bfe.ui.internals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.the412banner.bfe.apk.internals.InternalsSource
import com.the412banner.bfe.apk.internals.JavaClasses
import com.the412banner.bfe.ui.docs.CodeLang
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface JarLoad {
    data object Loading : JarLoad
    data class Failed(val message: String) : JarLoad
    class Ready(val entries: List<String>, val names: List<String>) : JarLoad
}

/**
 * The classes of a jar (or the .class files of any zip), browsed by package like folders or found by
 * name; a class opens in the javap-style view.
 */
@Composable
internal fun JarScreen(jar: File, onClose: () -> Unit) {
    var load by remember { mutableStateOf<JarLoad>(JarLoad.Loading) }
    var pkg by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(jar) {
        load = withContext(Dispatchers.IO) {
            runCatching<JarLoad> {
                val entries = JavaClasses.list(jar)
                JarLoad.Ready(entries, entries.map { it.removeSuffix(".class") })
            }.getOrElse { JarLoad.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    val o = open
    if (o != null) {
        JavaClassScreen(InternalsSource(jar, o), onClose = { open = null })
        return
    }
    BackHandler {
        when {
            searching -> { searching = false; query = "" }
            pkg.isNotEmpty() -> pkg = pkg.dropLast(1).substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            else -> onClose()
        }
    }
    Column(Modifier.fillMaxSize()) {
        val ready = load as? JarLoad.Ready
        DocTopBar(
            title = if (pkg.isEmpty()) jar.name else pkg.dropLast(1).replace('/', '.'),
            subtitle = ready?.let { "%,d classes".format(it.entries.size) },
            onBack = onClose,
        ) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, "Search") }
        }
        if (searching) InternalsSearchField(query, { query = it }, "Find a class…", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val st = load) {
                JarLoad.Loading -> DocNotice(Icons.Filled.Code, "Reading ${jar.name}…", null)
                is JarLoad.Failed -> DocNotice(Icons.Filled.ErrorOutline, "Couldn't read ${jar.name}", st.message)
                is JarLoad.Ready -> DocSurface {
                    if (st.entries.isEmpty()) {
                        DocNotice(Icons.Filled.SearchOff, "No Java classes", "${jar.name} has no .class files.")
                    } else if (searching && query.isNotBlank()) {
                        val q = query.trim()
                        val found = remember(st, q) { st.names.filter { it.replace('/', '.').contains(q, ignoreCase = true) }.take(2000) }
                        if (found.isEmpty()) DocNotice(Icons.Filled.SearchOff, "Nothing found", "No class matches “$q”.")
                        else LazyColumn(Modifier.fillMaxSize()) {
                            items(found, key = { it }) { n -> InternalsRow(Icons.Filled.Code, n.replace('/', '.'), null, { open = "$n.class" }, code = true) }
                        }
                    } else {
                        PackageBrowser(st.names, pkg, onPackage = { pkg = it }, onClass = { n -> open = "$n.class" })
                    }
                }
            }
        }
    }
}

private sealed interface ClassLoad {
    data object Loading : ClassLoad
    data class Failed(val message: String) : ClassLoad
    class Ready(val text: String) : ClassLoad
}

/** One Java class (a .class file, or an entry in a jar) as a javap-style listing, read-only. */
@Composable
internal fun JavaClassScreen(source: InternalsSource, onClose: () -> Unit) {
    var load by remember { mutableStateOf<ClassLoad>(ClassLoad.Loading) }
    LaunchedEffect(source.file, source.entry) {
        load = withContext(Dispatchers.IO) {
            runCatching<ClassLoad> { ClassLoad.Ready(JavaClasses.listing(source.readBytes())) }
                .getOrElse { ClassLoad.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }
    val title = source.name.removeSuffix(".class")
    when (val st = load) {
        ClassLoad.Loading -> Column(Modifier.fillMaxSize()) {
            BackHandler { onClose() }
            DocTopBar(title, null, onBack = onClose) {}
            DocNotice(Icons.Filled.Code, "Reading ${source.name}…", null)
        }
        is ClassLoad.Failed -> Column(Modifier.fillMaxSize()) {
            BackHandler { onClose() }
            DocTopBar(title, null, onBack = onClose) {}
            DocNotice(Icons.Filled.ErrorOutline, "Couldn't read ${source.name}", st.message)
        }
        is ClassLoad.Ready -> CodeScreen(
            title = title,
            subtitle = listOfNotNull(source.entry?.substringBeforeLast('/', "")?.replace('/', '.')?.ifEmpty { null }, source.entry?.let { source.file.name }).joinToString(" · ").ifEmpty { "Java class" },
            text = st.text,
            lang = CodeLang.CLIKE,
            onBack = onClose,
        )
    }
}
