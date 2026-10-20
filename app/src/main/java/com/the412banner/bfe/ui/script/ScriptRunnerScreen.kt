package com.the412banner.bfe.ui.script

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.tools.ToolFiles
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** Where a run is. */
private sealed interface RunPhase {
    data object Idle : RunPhase
    data class Running(val runner: ScriptRunner) : RunPhase
    data class Done(val exitCode: Int, val stopped: Boolean) : RunPhase
    data class Failed(val message: String) : RunPhase
}

private const val MAX_LINES = 20_000
private const val MAX_LINE_CHARS = 4_000

/**
 * The script runner: asks first ("Run <name>? Scripts can change or delete files.", with a Run as root
 * choice), then shows the script's output live as it runs, scrolling along unless you scroll up, and
 * the exit code when it ends. Stop ends it (and whatever it started); Copy puts the output on the
 * clipboard; Run again asks again. [rootOnly] = the file is somewhere only root can read, so it can
 * only run as root. Both looks.
 */
@Composable
internal fun ScriptRunnerScreen(path: String?, uri: Uri?, name: String, rootOnly: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var script by remember { mutableStateOf<File?>(null) }
    var shebang by remember { mutableStateOf<Shebang?>(null) }
    var copied by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(true) }
    var everRan by remember { mutableStateOf(false) }
    var asRoot by remember { mutableStateOf(rootOnly) }
    var phase by remember { mutableStateOf<RunPhase>(RunPhase.Idle) }
    val lines = remember { mutableStateListOf<String>() }
    var dropped by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }

    // ── Find the file (a cache copy when it has no readable path) and its #! line ──
    LaunchedEffect(path, uri) {
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val real = path?.let { File(it) }
                val f = if (rootOnly && real != null) real else ToolFiles.readable(context, path, uri, name, "script")
                // Root-only files: the #! line is read from the cache copy BFE was given.
                val first = runCatching {
                    if (f.canRead()) f.bufferedReader().use { it.readLine() }
                    else uri?.let { u -> context.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readLine() } }
                }.getOrNull()
                f to Shebang.parse(first)
            }
        }
        found.onSuccess { (f, sb) ->
            copied = f.absolutePath != path
            shebang = sb
            script = f
        }.onFailure { loadError = it.message ?: "The file couldn't be read." }
    }

    fun start() {
        val f = script ?: return
        val runner = ScriptRunner(f, shebang, asRoot)
        lines.clear()
        dropped = 0
        follow = true
        everRan = true
        phase = RunPhase.Running(runner)
        val queue = ConcurrentLinkedQueue<String>()
        scope.launch {
            // Lines arrive on a background thread; they are moved into the list ten times a second.
            val flusher = launch {
                while (isActive) {
                    delay(100)
                    flush(queue, lines) { dropped += it }
                }
            }
            val result = withContext(Dispatchers.IO) { runCatching { runner.run { queue.add(it) } } }
            flusher.cancel()
            flush(queue, lines) { dropped += it }
            phase = result.fold(
                onSuccess = { RunPhase.Done(it, runner.stopped) },
                onFailure = { RunPhase.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun stop() {
        val r = (phase as? RunPhase.Running)?.runner ?: return
        scope.launch(Dispatchers.IO) { r.stop() }
    }

    BackHandler {
        if (phase is RunPhase.Running) AppSnackbar.show(context, "Stop the script first") else onClose()
    }

    // Scroll along with new output while the bottom is in view; scrolling up stops following.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            val info = listState.layoutInfo
            val atBottom = info.visibleItemsInfo.lastOrNull()?.index?.let { it >= info.totalItemsCount - 2 } ?: true
            if (!scrolling) follow = atBottom
        }
    }
    LaunchedEffect(lines.size, follow) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        val p = phase
        DocTopBar(
            title = name,
            subtitle = when (p) {
                RunPhase.Idle -> if (asRoot) "Ready · as root" else "Ready"
                is RunPhase.Running -> if (asRoot) "Running as root…" else "Running…"
                is RunPhase.Done -> if (p.stopped) "Stopped · exit code ${p.exitCode}" else "Finished · exit code ${p.exitCode}"
                is RunPhase.Failed -> "Couldn't run"
            },
            onBack = { if (p is RunPhase.Running) AppSnackbar.show(context, "Stop the script first") else onClose() },
        ) {
            if (p is RunPhase.Running) {
                IconButton(onClick = { stop() }) { Icon(Icons.Filled.Stop, "Stop") }
            } else if (script != null) {
                IconButton(onClick = { asking = true }) { Icon(Icons.Filled.Replay, "Run again") }
            }
            if (lines.isNotEmpty()) {
                IconButton(onClick = { copyOutput(context, lines) }) { Icon(Icons.Filled.ContentCopy, "Copy output") }
            }
        }
        DocSurface(Modifier.weight(1f)) {
            val err = loadError
            when {
                err != null -> DocNotice(Icons.Filled.Terminal, "Couldn't open the script", err)
                p is RunPhase.Failed && lines.isEmpty() -> DocNotice(Icons.Filled.Terminal, "Couldn't run the script", p.message)
                p is RunPhase.Idle -> DocNotice(Icons.Filled.Terminal, "Nothing has run yet", "Tap Run again to start $name.")
                else -> SelectionContainer {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        if (dropped > 0) item { OutputLine("… $dropped earlier lines not kept …", dim = true) }
                        itemsIndexed(lines) { _, l -> OutputLine(l) }
                        if (p is RunPhase.Done) item {
                            OutputLine(
                                if (p.stopped) "[stopped · exit code ${p.exitCode}]" else "[exit code ${p.exitCode}]",
                                dim = p.exitCode == 0, bold = true,
                            )
                        }
                        if (p is RunPhase.Failed) item { OutputLine("[couldn't run: ${p.message}]", bold = true) }
                    }
                }
            }
        }
    }

    if (asking && script != null) {
        OutlinedAlertDialog(
            onDismissRequest = { asking = false; if (!everRan) onClose() },
            icon = { Icon(Icons.Filled.Terminal, null) },
            title = { Text("Run $name?") },
            text = {
                Column {
                    Text("Scripts can change or delete files.")
                    Spacer(Modifier.height(8.dp))
                    val with = shebang?.let { "${it.label} (from its #! line, or sh when the device doesn't have it)" } ?: "sh"
                    Text(
                        "It runs with $with in ${script?.parentFile?.absolutePath ?: "its folder"}." +
                            if (copied) " The file isn't in a folder BFE reads directly, so a copy runs from BFE's cache." else "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(enabled = !rootOnly) { asRoot = !asRoot },
                    ) {
                        Checkbox(checked = asRoot, onCheckedChange = { if (!rootOnly) asRoot = it }, enabled = !rootOnly)
                        Text(if (rootOnly) "Run as root (only root can read this file)" else "Run as root")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { asking = false; start() }) { Text("Run") } },
            dismissButton = { TextButton(onClick = { asking = false; if (!everRan) onClose() }) { Text("Cancel") } },
        )
    }
}

/** One line of output in a monospaced font. */
@Composable
private fun OutputLine(text: String, dim: Boolean = false, bold: Boolean = false) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

// Moves the waiting lines into [lines], keeping at most MAX_LINES (the oldest go; [onDropped] counts them).
private fun flush(queue: ConcurrentLinkedQueue<String>, lines: SnapshotStateList<String>, onDropped: (Int) -> Unit) {
    if (queue.isEmpty()) return
    val batch = ArrayList<String>()
    while (true) {
        val l = queue.poll() ?: break
        batch.add(if (l.length > MAX_LINE_CHARS) l.take(MAX_LINE_CHARS) + " …" else l)
    }
    lines.addAll(batch)
    if (lines.size > MAX_LINES) {
        val cut = lines.size - MAX_LINES + MAX_LINES / 10
        lines.removeRange(0, cut)
        onDropped(cut)
    }
}

/** Puts the output on the clipboard (the last ~200 000 characters: the clipboard has a size limit). */
private fun copyOutput(context: Context, lines: List<String>) {
    val all = lines.joinToString("\n")
    val text = if (all.length > 200_000) all.takeLast(200_000) else all
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Script output", text))
    }.onSuccess {
        AppSnackbar.show(context, if (text.length < all.length) "Copied the last part of the output" else "Output copied")
    }.onFailure { AppSnackbar.show(context, "Couldn't copy the output") }
}
