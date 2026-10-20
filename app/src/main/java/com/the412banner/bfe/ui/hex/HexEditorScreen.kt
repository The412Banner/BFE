package com.the412banner.bfe.ui.hex

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.docs.FindBar
import com.the412banner.bfe.ui.docs.TextSizeButton
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface HexState {
    data object Loading : HexState
    data class Failed(val message: String) : HexState
    class Ready(val doc: HexDoc) : HexState
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * The hex editor: offset | hex bytes | text columns over a file of any size, read lazily page by page.
 * Go to offset, find hex bytes or text (forward / back), position slider, pinch or "Aa" text size.
 * When the file can be written, tap a byte and type its new value on the hex keypad — overwrite only,
 * so the file keeps its size; changed bytes are marked until Save writes them in place. Undo, and a
 * question before leaving with unsaved changes. Both looks.
 */
@Composable
internal fun HexEditorScreen(path: String?, uri: Uri?, name: String, allowWrite: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HexState>(HexState.Loading) }

    LaunchedEffect(path, uri) {
        state = withContext(Dispatchers.IO) {
            runCatching { HexState.Ready(HexDoc.open(context, path, uri, allowWrite)) }
                .getOrElse { HexState.Failed(it.message ?: "The file couldn't be read.") }
        }
    }
    val doc = (state as? HexState.Ready)?.doc
    DisposableEffect(doc) { onDispose { doc?.close() } }

    var zoom by remember { mutableFloatStateOf(1f) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var pendingNibble by remember { mutableStateOf<Int?>(null) }
    var match by remember { mutableStateOf<LongRange?>(null) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var textMode by remember { mutableStateOf(false) }
    var matchCase by remember { mutableStateOf(false) }
    var findStatus by remember { mutableStateOf("") }
    var findJob by remember { mutableStateOf<Job?>(null) }
    var menu by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var leaveAsk by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var bytesPerRow by remember { mutableStateOf(16) }

    fun closeFind() { finding = false; findJob?.cancel(); match = null; findStatus = "" }

    fun save(then: () -> Unit = {}) {
        val d = doc ?: return
        if (d.edits.isEmpty() || saving) { then(); return }
        saving = true
        val changes = d.edits.toMap()
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { d.save(changes) } }
            saving = false
            r.onSuccess { n ->
                d.afterSave()
                AppSnackbar.show(context, if (n == 1) "Saved 1 changed byte" else "Saved $n changed bytes")
                then()
            }.onFailure { AppSnackbar.show(context, "Couldn't save: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun leave() {
        when {
            finding -> closeFind()
            selected != null -> { selected = null; pendingNibble = null }
            doc != null && doc.edits.isNotEmpty() -> leaveAsk = true
            else -> onClose()
        }
    }
    BackHandler { leave() }

    // Bring [offset]'s row on screen (a few rows below the top) unless it already is.
    fun reveal(offset: Long) {
        val row = (offset / bytesPerRow).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val visible = listState.layoutInfo.visibleItemsInfo
        val shown = visible.size > 2 && row > visible.first().index && row < visible.last().index
        if (!shown) scope.launch { listState.scrollToItem((row - 3).coerceAtLeast(0)) }
    }

    fun runFind(forward: Boolean) {
        val d = doc ?: return
        if (query.isEmpty()) return
        val pattern = if (textMode) query.toByteArray(Charsets.UTF_8) else parseHex(query)
        if (pattern == null) { findStatus = "Use hex pairs, e.g. 4F 2A"; return }
        val topOffset = listState.firstVisibleItemIndex.toLong() * bytesPerRow
        val here = match?.first ?: selected ?: (topOffset - 1)
        val from = if (forward) here + 1 else here - 1
        findJob?.cancel()
        findStatus = "Searching…"
        findJob = scope.launch {
            val ctx = coroutineContext
            val hit = withContext(Dispatchers.IO) { d.find(pattern, from, forward, textMode && !matchCase) { ctx.isActive } }
            if (!isActive) return@launch
            if (hit < 0) {
                findStatus = "Not found"
            } else {
                match = hit until hit + pattern.size
                selected = hit
                pendingNibble = null
                findStatus = "0x" + hit.toString(16).uppercase()
                reveal(hit)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val d = doc
        val changed = d?.edits?.size ?: 0
        DocTopBar(
            title = name,
            subtitle = when {
                d == null -> null
                changed > 0 -> "${StringUtils.formatBytes(d.length)} · $changed unsaved"
                !d.writable -> "${StringUtils.formatBytes(d.length)} · read-only"
                else -> StringUtils.formatBytes(d.length)
            },
            onBack = { leave() },
        ) {
            if (d != null) {
                if (d.writable && d.undoDepth > 0) {
                    IconButton(onClick = { d.undoLast()?.let { selected = it; pendingNibble = null; reveal(it) } }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                    }
                }
                if (changed > 0) IconButton(onClick = { save() }, enabled = !saving) { Icon(Icons.Filled.Save, "Save") }
                IconButton(onClick = { if (finding) closeFind() else finding = true }) { Icon(Icons.Filled.Search, "Find") }
                TextSizeButton((zoom * 100).toInt()) { zoom = it / 100f }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Go to offset…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                            onClick = { menu = false; gotoOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Go to start") },
                            leadingIcon = { Icon(Icons.Filled.VerticalAlignTop, null) },
                            onClick = { menu = false; scope.launch { listState.scrollToItem(0) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Go to end") },
                            leadingIcon = { Icon(Icons.Filled.VerticalAlignBottom, null) },
                            onClick = {
                                menu = false
                                scope.launch { listState.scrollToItem(rowCount(d.length, bytesPerRow) - 1) }
                            },
                        )
                    }
                }
            }
        }

        FindBar(
            visible = finding,
            query = query,
            onQuery = { query = it; match = null; findStatus = "" },
            status = findStatus,
            onPrev = { runFind(false) },
            onNext = { runFind(true) },
            onClose = { closeFind() },
        )
        if (finding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = !textMode, onClick = { textMode = false; findStatus = "" }, label = { Text("Hex") })
                FilterChip(selected = textMode, onClick = { textMode = true; findStatus = "" }, label = { Text("Text") })
                if (textMode) FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text("Match case") })
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val st = state) {
                HexState.Loading -> DocNotice(Icons.Filled.Tag, "Opening…", null)
                is HexState.Failed -> DocNotice(Icons.Filled.ErrorOutline, "Couldn't open the file", st.message)
                is HexState.Ready -> DocSurface {
                    HexGrid(
                        doc = st.doc,
                        zoom = zoom,
                        onZoom = { zoom = (zoom * it).coerceIn(0.5f, 2.5f) },
                        selected = selected,
                        pendingNibble = pendingNibble,
                        match = match,
                        listState = listState,
                        onBytesPerRow = { bytesPerRow = it },
                        onTap = { off -> selected = off; pendingNibble = null; match = null },
                    )
                }
            }
        }

        if (d != null) {
            BottomPanel {
                PositionSlider(d, bytesPerRow, listState)
                val sel = selected
                if (sel != null) {
                    ByteInfo(d, sel, pendingNibble)
                    if (d.writable) {
                        HexKeypad(
                            onDigit = { v ->
                                val p = pendingNibble
                                if (p == null) {
                                    pendingNibble = v
                                } else {
                                    d.set(sel, (p shl 4) or v)
                                    pendingNibble = null
                                    if (sel + 1 < d.length) { selected = sel + 1; reveal(sel + 1) }
                                }
                            },
                            onPrev = { if (sel > 0) { selected = sel - 1; pendingNibble = null; reveal(sel - 1) } },
                            onNext = { if (sel + 1 < d.length) { selected = sel + 1; pendingNibble = null; reveal(sel + 1) } },
                            onDone = { selected = null; pendingNibble = null },
                        )
                    }
                }
            }
        }
    }

    if (gotoOpen && doc != null) {
        GotoOffsetDialog(
            length = doc.length,
            onDismiss = { gotoOpen = false },
            onGo = { off ->
                gotoOpen = false
                selected = off
                pendingNibble = null
                scope.launch { listState.scrollToItem(((off / bytesPerRow) - 3).coerceAtLeast(0).toInt()) }
            },
        )
    }

    if (leaveAsk && doc != null) {
        OutlinedAlertDialog(
            onDismissRequest = { leaveAsk = false },
            title = { Text("Save changes?") },
            text = { Text("${doc.edits.size} changed byte(s) in $name aren't saved yet.") },
            confirmButton = { TextButton(onClick = { leaveAsk = false; save { onClose() } }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { leaveAsk = false; onClose() }) { Text("Discard") }
                    TextButton(onClick = { leaveAsk = false }) { Text("Cancel") }
                }
            },
        )
    }
}

/** Rows needed for [length] bytes at [perRow] a row (at least 1, at most what a list can hold). */
private fun rowCount(length: Long, perRow: Int): Int =
    ((length + perRow - 1) / perRow).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()

/** Hex digits with or without spaces ("4F2A", "4f 2a", "0x4F") to bytes; null when it isn't hex pairs. */
private fun parseHex(s: String): ByteArray? {
    val clean = s.replace("0x", "", ignoreCase = true).filterNot { it.isWhitespace() || it == ',' }
    if (clean.isEmpty() || clean.length % 2 != 0) return null
    if (clean.any { HEX_DIGITS.indexOf(it.uppercaseChar()) < 0 }) return null
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/**
 * The byte rows. Each row is one monospaced line — offset, the bytes in hex, the same bytes as text —
 * and a tap is mapped from its x position to the byte under it (in either column). As many bytes a
 * row (4, 8, 16 or 32) as fit the width at the current text size. Changed bytes are drawn in the
 * accent colour, the selected byte and a find match get a tinted background. Two fingers zoom the text.
 */
@Composable
private fun HexGrid(
    doc: HexDoc,
    zoom: Float,
    onZoom: (Float) -> Unit,
    selected: Long?,
    pendingNibble: Int?,
    match: LongRange?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBytesPerRow: (Int) -> Unit,
    onTap: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (13f * zoom).sp, lineHeight = (19f * zoom).sp, color = cs.onSurface)
    val measurer = rememberTextMeasurer()
    val charW = remember(style) { measurer.measure(AnnotatedString("0"), style).size.width.toFloat().coerceAtLeast(1f) }
    val digits = remember(doc.length) { maxOf(8, (doc.length - 1).coerceAtLeast(0).toString(16).length) }
    val padPx = with(LocalDensity.current) { 8.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Pinch anywhere over the rows: caught before the list so it doesn't scroll meanwhile.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (ev.changes.count { it.pressed } >= 2) {
                            val z = ev.calculateZoom()
                            if (z != 1f) onZoom(z)
                            ev.changes.forEach { it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
    ) {
        val widthPx = constraints.maxWidth - 2 * padPx
        // A row is: offset, 2 spaces, n × "XX ", 1 space, n text characters.
        val perRow = listOf(32, 16, 8, 4).firstOrNull { n -> (digits + 2 + 4 * n + 1) * charW <= widthPx } ?: 4
        LaunchedEffect(perRow) { onBytesPerRow(perRow) }
        val rows = rowCount(doc.length, perRow)
        val hexStart = digits + 2
        val textStart = hexStart + perRow * 3 + 1
        val accent = SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold)
        val selBg = SpanStyle(background = cs.primary.copy(alpha = 0.30f))
        val matchBg = SpanStyle(background = cs.tertiary.copy(alpha = 0.30f))
        val dim = SpanStyle(color = cs.onSurfaceVariant)
        val edits = doc.edits // read here so rows recompose on every edit

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows) { r ->
                val start = r.toLong() * perRow
                val line = buildAnnotatedString {
                    withStyle(dim) { append(start.toString(16).uppercase().padStart(digits, '0')) }
                    append("  ")
                    val values = IntArray(perRow) { i -> if (start + i < doc.length) doc.byteAt(start + i) else -1 }
                    for (i in 0 until perRow) {
                        val off = start + i
                        val v = values[i]
                        val st = when {
                            v < 0 -> null
                            off == selected -> selBg
                            match != null && off in match -> matchBg
                            else -> null
                        }
                        val text = when {
                            v < 0 -> "  "
                            off == selected && pendingNibble != null -> "${HEX_DIGITS[pendingNibble]}_"
                            else -> "${HEX_DIGITS[v shr 4]}${HEX_DIGITS[v and 15]}"
                        }
                        val edited = v >= 0 && edits.containsKey(off)
                        when {
                            st != null && edited -> withStyle(st) { withStyle(accent) { append(text) } }
                            st != null -> withStyle(st) { append(text) }
                            edited -> withStyle(accent) { append(text) }
                            else -> append(text)
                        }
                        append(' ')
                    }
                    append(' ')
                    for (i in 0 until perRow) {
                        val off = start + i
                        val v = values[i]
                        if (v < 0) break
                        val c = if (v in 0x20..0x7E) v.toChar() else '.'
                        val st = when {
                            off == selected -> selBg
                            match != null && off in match -> matchBg
                            else -> null
                        }
                        val edited = edits.containsKey(off)
                        when {
                            st != null -> withStyle(st) { if (edited) withStyle(accent) { append(c) } else append(c) }
                            edited -> withStyle(accent) { append(c) }
                            else -> append(c)
                        }
                    }
                }
                Text(
                    line,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(start, perRow, charW) {
                            detectTapGestures { pos ->
                                val col = ((pos.x - padPx) / charW).toInt()
                                val i = when {
                                    col in hexStart until hexStart + perRow * 3 -> (col - hexStart) / 3
                                    col in textStart until textStart + perRow -> col - textStart
                                    else -> -1
                                }
                                if (i >= 0 && start + i < doc.length) onTap(start + i)
                            }
                        }
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Under the rows: the slider panel. Classic: a bar in the toolbar colour. Aurora: a glass card. */
@Composable
private fun BottomPanel(content: @Composable () -> Unit) {
    val m = if (isAurora) {
        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp).glass(Glass.Card, GlassLevel.Raised)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().then(m).padding(horizontal = 12.dp, vertical = 6.dp)) { content() }
}

/** Where in the file the top row is, and a slider to jump anywhere in it (the list alone can't fling 4 GB). */
@Composable
private fun PositionSlider(doc: HexDoc, perRow: Int, listState: androidx.compose.foundation.lazy.LazyListState) {
    val scope = rememberCoroutineScope()
    val rows = rowCount(doc.length, perRow)
    val top by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "0x" + (top.toLong() * perRow).toString(16).uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Slider(
            value = if (rows <= 1) 0f else top.toFloat() / (rows - 1),
            onValueChange = { v -> scope.launch { listState.scrollToItem((v * (rows - 1)).toInt().coerceIn(0, rows - 1)) } },
            enabled = rows > 1,
            modifier = Modifier.weight(1f).height(32.dp),
        )
    }
}

/** The selected byte: its offset (hex and decimal) and value (hex, decimal, binary, character). */
@Composable
private fun ByteInfo(doc: HexDoc, offset: Long, pendingNibble: Int?) {
    val v = doc.byteAt(offset)
    val orig = doc.original(offset)
    val ch = if (v in 0x20..0x7E) "'${v.toChar()}'" else "·"
    val value = if (v < 0) "—" else "0x%02X · %d · %s · %s".format(v, v, v.toString(2).padStart(8, '0'), ch)
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            "Offset 0x${offset.toString(16).uppercase()} ($offset)",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value + (if (orig >= 0 && orig != v) "   (was 0x%02X)".format(orig) else "") +
                (if (pendingNibble != null) "   typing ${HEX_DIGITS[pendingNibble]}_" else ""),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        if (!doc.writable) {
            Text("Read-only: BFE can't write to this file.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The hex keypad for overwriting the selected byte: two taps make a byte (high digit, then low) and
 * move to the next one. ◀ / ▶ step between bytes, Done hides the pad.
 */
@Composable
private fun HexKeypad(onDigit: (Int) -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until 8) {
                    val v = row * 8 + k
                    PadKey(HEX_DIGITS[v].toString(), Modifier.weight(1f)) { onDigit(v) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PadKey(null, Modifier.weight(1f), icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous byte") }, onClick = onPrev)
            PadKey(null, Modifier.weight(1f), icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next byte") }, onClick = onNext)
            PadKey("Done", Modifier.weight(2f), onClick = onDone)
        }
    }
}

/** One keypad key: a rounded outlined cell in either look. */
@Composable
private fun PadKey(label: String?, modifier: Modifier, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val fill = if (isAurora) LocalGlass.current.panel else cs.surfaceContainerHighest
    val edge = if (isAurora) LocalGlass.current.border else cs.outlineVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .clickable(onClick = onClick),
    ) {
        if (icon != null) icon()
        else Text(label.orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
    }
}

/** "Go to offset": a hex or decimal number, kept inside the file. */
@Composable
private fun GotoOffsetDialog(length: Long, onDismiss: () -> Unit, onGo: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf(true) }
    val parsed = remember(text, hex) {
        val t = text.trim().removePrefix("0x").removePrefix("0X")
        if (t.isEmpty()) null else t.toLongOrNull(if (hex) 16 else 10)
    }
    val valid = parsed != null && parsed in 0 until length
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to offset") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = hex, onClick = { hex = true }, label = { Text("Hex") })
                    FilterChip(selected = !hex, onClick = { hex = false }, label = { Text("Decimal") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(if (hex) "e.g. 1F40" else "e.g. 8000") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (hex) KeyboardType.Ascii else KeyboardType.Number),
                    isError = text.isNotBlank() && !valid,
                    supportingText = {
                        Text("0 – " + if (hex) "0x" + (length - 1).coerceAtLeast(0).toString(16).uppercase() else "${(length - 1).coerceAtLeast(0)}")
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onGo) }, enabled = valid) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
