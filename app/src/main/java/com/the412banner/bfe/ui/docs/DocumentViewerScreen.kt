package com.the412banner.bfe.ui.docs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.the412banner.bfe.DocumentViewer
import com.the412banner.bfe.docs.DirAssets
import com.the412banner.bfe.docs.DocConverter
import com.the412banner.bfe.docs.DocException
import com.the412banner.bfe.docs.DocFormats
import com.the412banner.bfe.docs.DocKind
import com.the412banner.bfe.docs.DocResult
import com.the412banner.bfe.docs.EpubConverter
import com.the412banner.bfe.docs.ReadingStyle
import com.the412banner.bfe.docs.TextDoc
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.components.GlassChip
import com.the412banner.bfe.ui.screens.OpenWithSheet
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.screens.guessMime
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.IncomingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** The file being viewed: a real path (opened in place) or BFE's cache copy of a content:// Uri. */
private class DocSource(val file: File, val name: String, val originalUri: Uri?) {
    /** Names this file's converted pages and its remembered page. */
    val key: String = sha1("${file.absolutePath}|${file.length()}|${file.lastModified()}|$name").take(20)
}

private sealed interface DocState {
    data class Loading(val label: String) : DocState
    data class Failed(val title: String, val message: String, val textFallback: Boolean) : DocState
    data object Legacy : DocState
    class Pdf(val doc: PdfDoc) : DocState
    class Text(val doc: TextDoc) : DocState
    class Web(val result: DocResult, val dir: File) : DocState
}

/**
 * The document viewer: resolves the file (a real path when there is one, else a cache copy), picks the
 * kind (name, MIME type, then content), and shows it — PDF pages, the text view, or converted pages in
 * a WebView — with find, text size, Open with, Share and Print, in either look.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun DocumentViewerScreen(uri: Uri?, explicitPath: String?, nameHint: String?, mime: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val theme = rememberReadingTheme()
    var source by remember { mutableStateOf<DocSource?>(null) }
    var kind by remember { mutableStateOf<DocKind?>(null) }
    var forceText by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf<DocState>(DocState.Loading("Opening…")) }

    var textZoom by remember { mutableIntStateOf(DocPrefs.textZoom(context)) }
    var wrap by remember { mutableStateOf(DocPrefs.wrap(context)) }
    var lineNumbers by remember { mutableStateOf(DocPrefs.lineNumbers(context)) }
    var night by remember { mutableStateOf(DocPrefs.pdfNight(context)) }

    var finding by remember { mutableStateOf(false) }
    var webQuery by remember { mutableStateOf("") }
    var webMatches by remember { mutableStateOf(0 to 0) }
    val textFind = remember { TextFind() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var request by remember { mutableStateOf(PageRequest(0)) }
    var written by remember { mutableStateOf<String?>(null) }

    var menu by remember { mutableStateOf(false) }
    var openWith by remember { mutableStateOf(false) }
    var gotoPage by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }

    // ── Find the file ──
    LaunchedEffect(uri, explicitPath) {
        val resolved = withContext(Dispatchers.IO) { runCatching { resolveSource(context, uri, explicitPath, nameHint) } }
        resolved.onSuccess { s ->
            // Kind first, then the source, with no suspension between: the load below sees both at once.
            val k = DocFormats.kindOf(s.name, mime) ?: withContext(Dispatchers.IO) { DocFormats.sniff(s.file) }
            kind = k
            source = s
        }.onFailure {
            state = DocState.Failed("Couldn't open the file", it.message ?: "The file couldn't be read.", false)
        }
    }

    // ── Load it as its kind (or as text) ──
    LaunchedEffect(source, kind, forceText) {
        val s = source ?: return@LaunchedEffect
        val k = kind
        written = null
        state = DocState.Loading(if (k == DocKind.PDF || k == null || forceText) "Opening…" else "Converting…")
        state = withContext(Dispatchers.IO) { load(context, s, k, forceText) }
        if (state is DocState.Web) {
            val r = (state as DocState.Web).result
            request = PageRequest(if (r.kind == DocKind.EPUB || r.pages.size > 1) DocPrefs.lastPage(context, s.key).coerceIn(0, r.pages.size - 1) else 0)
        }
    }
    val pdf = (state as? DocState.Pdf)?.doc
    DisposableEffect(pdf) { onDispose { pdf?.close() } }
    val pdfState = remember(pdf) {
        pdf?.let { d -> PdfViewState(d.sizes).also { st -> source?.key?.let { k -> st.pendingPage = DocPrefs.lastPage(context, k).coerceIn(0, d.pageCount - 1) } } }
    }
    if (pdfState != null) {
        LaunchedEffect(pdfState) {
            snapshotFlow { pdfState.currentPage }.distinctUntilChanged().debounce(600).collect { p -> source?.key?.let { DocPrefs.setLastPage(context, it, p) } }
        }
    }

    // ── Converted pages: written for the current theme, rewritten when it changes ──
    val web = state as? DocState.Web
    val pageFiles = remember(web) { web?.let { w -> w.result.pages.indices.map { File(w.dir, EpubConverter.pageFile(it)) } } ?: emptyList() }
    LaunchedEffect(web, theme.key) {
        val w = web ?: return@LaunchedEffect
        val wide = w.result.kind in setOf(DocKind.XLSX, DocKind.ODS, DocKind.CSV, DocKind.TSV)
        withContext(Dispatchers.IO) {
            w.result.pages.forEachIndexed { i, p ->
                File(w.dir, EpubConverter.pageFile(i)).writeText(ReadingStyle.wrap(p, theme, if (i == 0) w.result.notes else emptyList(), wide))
            }
        }
        written = theme.key
    }
    LaunchedEffect(web, request.page) {
        val w = web ?: return@LaunchedEffect
        if (w.result.pages.size > 1) source?.key?.let { DocPrefs.setLastPage(context, it, request.page) }
    }

    fun closeFind() {
        finding = false
        webQuery = ""
        webView?.clearMatches()
        textFind.query = ""
    }

    BackHandler {
        when {
            finding -> closeFind()
            webView?.canGoBack() == true -> webView?.goBack()
            else -> onClose()
        }
    }

    val s = source
    val name = s?.name ?: nameHint ?: "Document"
    val shareUri = remember(s) { s?.let { shareUriFor(context, it) } }
    val docMime = remember(name) { guessMime(name, mime) }

    Column(Modifier.fillMaxSize()) {
        DocTopBar(
            title = name,
            subtitle = subtitleOf(state, kind),
            onBack = { if (finding) closeFind() else onClose() },
        ) {
            val canFind = state is DocState.Text || (state is DocState.Web && written != null)
            if (canFind) IconButton(onClick = { if (finding) closeFind() else finding = true }) { Icon(Icons.Filled.Search, "Find") }
            if (state is DocState.Text || state is DocState.Web) {
                TextSizeButton(textZoom) { textZoom = it; DocPrefs.setTextZoom(context, it) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val st = state
                    if (st is DocState.Web && st.result.kind == DocKind.EPUB) {
                        MenuRow("Contents", Icons.Filled.Toc) { menu = false; contents = true }
                    }
                    if (st is DocState.Pdf) {
                        MenuRow("Go to page…", Icons.AutoMirrored.Filled.ArrowForward) { menu = false; gotoPage = true }
                        MenuRow("Night mode", Icons.Filled.DarkMode, checked = night) { menu = false; night = !night; DocPrefs.setPdfNight(context, night) }
                    }
                    if (st is DocState.Text) {
                        MenuRow("Wrap lines", Icons.Filled.WrapText, checked = wrap) { menu = false; wrap = !wrap; DocPrefs.setWrap(context, wrap) }
                        MenuRow("Line numbers", Icons.Filled.FormatListNumbered, checked = lineNumbers) { menu = false; lineNumbers = !lineNumbers; DocPrefs.setLineNumbers(context, lineNumbers) }
                    }
                    val k = kind
                    if (k != null && k.hasSource) {
                        val label = when {
                            !forceText -> if (k == DocKind.CSV || k == DocKind.TSV) "Open as text" else "View source"
                            k == DocKind.CSV || k == DocKind.TSV -> "View as table"
                            else -> "View document"
                        }
                        MenuRow(label, if (forceText) Icons.Filled.Description else Icons.Filled.TextSnippet) { menu = false; closeFind(); forceText = !forceText }
                    }
                    if (st is DocState.Pdf || (st is DocState.Web && written != null)) {
                        MenuRow("Print…", Icons.Filled.Print) {
                            menu = false
                            printDocument(context, name, st, webView, s?.file)
                        }
                    }
                    if (shareUri != null) {
                        MenuRow("Share", Icons.Filled.Share) { menu = false; share(context, shareUri, docMime, name) }
                        MenuRow("Open with…", Icons.AutoMirrored.Filled.OpenInNew) { menu = false; openWith = true }
                    }
                }
            }
        }

        FindBar(
            visible = finding,
            query = if (state is DocState.Text) textFind.query else webQuery,
            onQuery = { q ->
                if (state is DocState.Text) {
                    textFind.query = q
                } else {
                    webQuery = q
                    if (q.isEmpty()) webView?.clearMatches() else webView?.findAllAsync(q)
                }
            },
            status = if (state is DocState.Text) textFind.status else if (webQuery.isEmpty()) "" else "${webMatches.first}/${webMatches.second}",
            onPrev = { if (state is DocState.Text) textFind.step(-1) else webView?.findNext(false) },
            onNext = { if (state is DocState.Text) textFind.step(1) else webView?.findNext(true) },
            onClose = { closeFind() },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val st = state) {
                is DocState.Loading -> DocNotice(Icons.Filled.Description, st.label, null)
                is DocState.Failed -> DocNotice(
                    Icons.Filled.ErrorOutline, st.title, st.message,
                    primary = if (shareUri != null) "Open with…" to { openWith = true } else null,
                    secondary = if (st.textFallback) "Open as text" to { forceText = true } else null,
                )
                DocState.Legacy -> {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    DocNotice(
                        Icons.Filled.Description,
                        "This older Office format can't be shown in BFE",
                        "${if (ext.isNotEmpty()) ".$ext" else "This"} is the binary format of Office 97–2003, which BFE's viewer doesn't read. " +
                            "Open it with another app, or save it as ${modernOf(ext)} to view it here.",
                        primary = if (shareUri != null) "Open with…" to { openWith = true } else null,
                    )
                }
                is DocState.Pdf -> {
                    val ps = pdfState
                    if (ps != null) {
                        Box(Modifier.fillMaxSize().then(if (isAurora) Modifier else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))) {
                            PdfPane(st.doc, ps, night)
                            PageChip(
                                text = "${ps.currentPage + 1} / ${st.doc.pageCount}",
                                onClick = { gotoPage = true },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                            )
                        }
                    }
                }
                is DocState.Text -> DocSurface {
                    val listState = rememberLazyListState()
                    TextPane(st.doc, textZoom, wrap, lineNumbers, textFind, listState)
                }
                is DocState.Web -> DocSurface {
                    val key = written
                    if (key == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        WebDocPane(
                            pages = pageFiles,
                            request = request,
                            reloadKey = key,
                            textZoom = textZoom,
                            background = if (isAurora) Color.Transparent else MaterialTheme.colorScheme.background,
                            onPageShown = { request = request.copy(page = it, anchor = null) },
                            onWebView = { webView = it },
                            onFindResult = { a, t -> webMatches = a to t },
                            onLocalFile = { f -> openLocal(context, f) },
                        )
                    }
                }
            }
        }

        // Sheets (tabs) and e-book chapters (previous / contents / next).
        val w = state as? DocState.Web
        if (w != null && w.result.pages.size > 1) {
            if (w.result.kind == DocKind.EPUB) {
                val pages = w.result.pages
                DocBottomBar {
                    DocBarButton(null, Icons.AutoMirrored.Filled.ArrowBack, onClick = { closeFind(); request = PageRequest(request.page - 1, null, request.nonce + 1) }, enabled = request.page > 0, contentDescription = "Previous chapter")
                    DocBarButton("${request.page + 1} / ${pages.size} · ${pages[request.page.coerceIn(0, pages.size - 1)].title}", Icons.Filled.Toc, onClick = { contents = true })
                    DocBarButton(null, Icons.AutoMirrored.Filled.ArrowForward, onClick = { closeFind(); request = PageRequest(request.page + 1, null, request.nonce + 1) }, enabled = request.page < pages.size - 1, contentDescription = "Next chapter")
                }
            } else {
                SheetTabs(w.result.pages.map { it.title }, request.page) { i -> closeFind(); request = PageRequest(i, null, request.nonce + 1) }
            }
        }
    }

    // ── Sheets and dialogs ──
    if (openWith && s != null && shareUri != null) {
        OpenWithSheet(
            file = Loc.FileLoc(s.file),
            uri = shareUri,
            detectedMime = docMime,
            actions = emptyList(),
            allowWrite = false,
            onDismiss = { openWith = false },
        )
    }
    if (contents) {
        val wr = (state as? DocState.Web)?.result
        if (wr == null) contents = false else ContentsSheet(wr, request.page, onDismiss = { contents = false }) { page, anchor ->
            contents = false
            closeFind()
            request = PageRequest(page, anchor, request.nonce + 1)
        }
    }
    if (gotoPage) {
        val p = pdfState
        val count = pdf?.pageCount ?: 0
        if (p == null || count == 0) gotoPage = false else GoToPageDialog(count, p.currentPage + 1, onDismiss = { gotoPage = false }) { n ->
            gotoPage = false
            p.goTo(n - 1)
        }
    }
}

/** The menu's rows: icon, label, a check for toggles. */
@Composable
private fun MenuRow(label: String, icon: ImageVector, checked: Boolean? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        trailingIcon = if (checked == true) ({ Icon(Icons.Filled.Check, "On", tint = MaterialTheme.colorScheme.primary) }) else null,
        onClick = onClick,
    )
}

/** "3 / 41" over the PDF; tapping it asks for a page. */
@Composable
private fun PageChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .then(
                if (isAurora) Modifier.glass(Glass.Pill, GlassLevel.Floating)
                else Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A workbook's sheets as tabs along the bottom. */
@Composable
private fun SheetTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val row: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(scroll).padding(horizontal = 4.dp),
        ) {
            titles.forEachIndexed { i, t ->
                if (isAurora) GlassChip(t, onClick = { onSelect(i) }, selected = i == selected)
                else FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(t, maxLines = 1) })
            }
        }
    }
    DocBottomBar { Box(Modifier.widthIn(max = 640.dp)) { row() } }
}

/** An e-book's table of contents (or its chapter list when it has none). */
@Composable
private fun ContentsSheet(result: DocResult, current: Int, onDismiss: () -> Unit, onPick: (Int, String?) -> Unit) {
    DocSheet(onDismiss) {
        Text("Contents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        val entries = result.toc.ifEmpty { result.pages.mapIndexed { i, p -> com.the412banner.bfe.docs.TocEntry(p.title, i, null, 0) } }
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            itemsIndexed(entries) { _, e ->
                SheetRow(e.title, e.depth.coerceAtMost(4), selected = e.page == current && (e.anchor == null || e.depth == 0)) { onPick(e.page, e.anchor) }
            }
        }
    }
}

@Composable
private fun GoToPageDialog(count: Int, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val n = text.trim().toIntOrNull()
    val ok = n != null && n in 1..count
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    label = { Text("Page (1–$count)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { n?.let(onGo) }, enabled = ok) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun subtitleOf(state: DocState, kind: DocKind?): String? = when (state) {
    is DocState.Pdf -> "PDF · ${state.doc.pageCount} page${if (state.doc.pageCount == 1) "" else "s"}"
    is DocState.Text -> buildString {
        append(state.doc.encoding.label).append(" · ").append("%,d".format(state.doc.lineCount)).append(if (state.doc.lineCount == 1) " line" else " lines")
        if (state.doc.truncated) append(" · start of the file")
        if (state.doc.binary) append(" · looks binary")
    }
    is DocState.Web -> {
        val r = state.result
        val n = r.pages.size
        when (r.kind) {
            DocKind.EPUB -> "EPUB · $n chapter${if (n == 1) "" else "s"}"
            DocKind.XLSX, DocKind.ODS -> "${r.kind.label} · $n sheet${if (n == 1) "" else "s"}"
            else -> r.kind.label
        }
    }
    DocState.Legacy -> DocKind.LEGACY_OFFICE.label
    else -> kind?.label
}

private fun modernOf(ext: String): String = when (ext) {
    "xls", "xlt" -> ".xlsx or .ods"
    "ppt", "pps", "pot" -> ".pptx or .odp"
    else -> ".docx or .odt"
}

// ── Loading ──

private fun sha1(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

private fun cacheRoot(ctx: Context) = File(ctx.cacheDir, "docview")

/** The real file behind the request, or a copy of the content:// Uri in BFE's cache. */
private fun resolveSource(ctx: Context, uri: Uri?, explicitPath: String?, nameHint: String?): DocSource {
    val path = explicitPath ?: uri?.let { runCatching { IncomingFile.resolvePath(ctx, it) }.getOrNull() }
    path?.let { File(it) }?.takeIf { it.isFile && it.canRead() }?.let { f -> return DocSource(f, nameHint ?: f.name, uri) }
    val u = uri ?: throw DocException("The file couldn't be found.")
    val meta = runCatching { IncomingFile.meta(ctx, u) }.getOrNull()
    val name = (nameHint ?: meta?.name ?: "document").replace('/', '_')
    val dir = File(cacheRoot(ctx), "incoming/" + sha1(u.toString()).take(16)).apply { mkdirs() }
    val out = File(dir, name)
    val input = ctx.contentResolver.openInputStream(u) ?: throw DocException("The app that shared the file didn't provide it.")
    input.use { i -> out.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
    return DocSource(out, name, u)
}

/** Drops converted documents not opened for two days, keeping at most the 12 most recent. */
private fun pruneCache(root: File, keep: File) {
    val dirs = root.listFiles()?.filter { it.isDirectory && it.name != "incoming" && it != keep }?.sortedByDescending { it.lastModified() } ?: return
    val old = System.currentTimeMillis() - 2L * 24 * 3600 * 1000
    dirs.forEachIndexed { i, d -> if (i >= 11 || d.lastModified() < old) d.deleteRecursively() }
    File(root, "incoming").listFiles()?.filter { it.lastModified() < old }?.forEach { it.deleteRecursively() }
}

private fun load(ctx: Context, s: DocSource, kind: DocKind?, asText: Boolean): DocState = try {
    when {
        asText || kind == DocKind.TEXT -> DocState.Text(TextDoc.open(s.file))
        kind == DocKind.PDF -> DocState.Pdf(PdfDoc.open(s.file))
        kind == DocKind.LEGACY_OFFICE -> DocState.Legacy
        kind == null -> {
            val texty = runCatching { DocFormats.sniff(s.file) == DocKind.TEXT }.getOrDefault(false)
            DocState.Failed("BFE can't show this kind of file", "Open it with another app.", texty)
        }
        else -> {
            val root = cacheRoot(ctx)
            val dir = File(root, s.key).apply { mkdirs(); setLastModified(System.currentTimeMillis()) }
            runCatching { pruneCache(root, dir) }
            DocState.Web(DocConverter.convert(s.file, kind, s.name, DirAssets(dir)), dir)
        }
    }
} catch (e: DocException) {
    DocState.Failed("Couldn't show this document", e.message ?: "The document couldn't be read.", kind?.hasSource == true)
} catch (e: OutOfMemoryError) {
    DocState.Failed("Couldn't show this document", "It's too big for BFE's viewer.", kind?.hasSource == true)
} catch (e: Exception) {
    DocState.Failed("Couldn't show this document", e.message ?: e.javaClass.simpleName, kind?.hasSource == true)
}

private fun shareUriFor(ctx: Context, s: DocSource): Uri? =
    s.originalUri?.takeIf { it.scheme == "content" }
        ?: runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", s.file) }.getOrNull()

private fun share(ctx: Context, uri: Uri, mime: String, name: String) {
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share $name"))
    }.onFailure { AppSnackbar.show(ctx, "Couldn't share $name") }
}

/** A link to another local file: BFE's viewer when it reads it, else whatever app views it. */
private fun openLocal(ctx: Context, f: File) {
    if (!f.isFile) { AppSnackbar.show(ctx, "${f.name} isn't there"); return }
    if (DocFormats.isViewable(f.name)) { DocumentViewer.open(ctx, f.absolutePath, null, f.name); return }
    val uri = runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f) }.getOrNull() ?: return
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, guessMime(f.name, null)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open ${f.name}"))
    }.onFailure { AppSnackbar.show(ctx, "No app opens ${f.name}") }
}

private fun printDocument(ctx: Context, name: String, state: DocState, webView: WebView?, file: File?) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = when {
        state is DocState.Pdf && file != null -> PdfPrintAdapter(file, name)
        webView != null -> webView.createPrintDocumentAdapter(name)
        else -> return
    }
    runCatching { pm.print(name, adapter, PrintAttributes.Builder().build()) }
        .onFailure { AppSnackbar.show(ctx, "Printing isn't available") }
}

/** Hands the PDF itself to the print system (it paginates and previews it). */
private class PdfPrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
    }

    override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
        try {
            file.inputStream().use { i -> FileOutputStream(destination.fileDescriptor).use { o -> i.copyTo(o) } }
            if (cancel?.isCanceled == true) callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
