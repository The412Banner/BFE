package com.the412banner.bfe.ui.internals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.the412banner.bfe.docs.TextDoc
import com.the412banner.bfe.docs.TextEncoding
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.docs.CodeLang
import com.the412banner.bfe.ui.docs.DocPrefs
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.docs.EditorState
import com.the412banner.bfe.ui.docs.FindBar
import com.the412banner.bfe.ui.docs.TextEditorPane
import com.the412banner.bfe.ui.docs.TextFind
import com.the412banner.bfe.ui.docs.TextPane
import com.the412banner.bfe.ui.docs.TextSizeButton
import com.the412banner.bfe.ui.docs.pinchTextZoom
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import kotlinx.coroutines.launch

/**
 * Decoded code (XML, smali, a javap listing) in BFE's text viewer — find, text size, wrap, line numbers,
 * colouring by [lang] — and, when [onSave] is given, Edit in the text editor. [onSave] gets the edited
 * text, does the tool's own save (compile, assemble, write into the APK) and returns true when it was
 * taken; the editor then counts it as saved. [bottom] sits under the text (a save-progress strip).
 */
@Composable
internal fun CodeScreen(
    title: String,
    subtitle: String?,
    text: String,
    lang: CodeLang,
    onBack: () -> Unit,
    onSave: (suspend (String) -> Boolean)? = null,
    saveIcon: ImageVector = Icons.Filled.Save,
    saveLabel: String = "Save",
    startEditing: Boolean = false,
    menuExtras: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
    bottom: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shown by remember(text) { mutableStateOf(text) }
    val doc = remember(shown) { TextDoc.of(shown.toByteArray(Charsets.UTF_8)) }
    var textZoom by remember { mutableIntStateOf(DocPrefs.textZoom(context)) }
    var wrap by remember { mutableStateOf(DocPrefs.wrap(context)) }
    var lineNumbers by remember { mutableStateOf(DocPrefs.lineNumbers(context)) }
    var finding by remember { mutableStateOf(false) }
    val find = remember { TextFind() }
    var menu by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorState?>(if (startEditing && onSave != null) EditorState(text, TextEncoding.UTF8, false) else null) }
    var saving by remember { mutableStateOf(false) }
    var leaveEditor by remember { mutableStateOf<(() -> Unit)?>(null) }
    val onZoom: (Int) -> Unit = { textZoom = it; DocPrefs.setTextZoom(context, it) }

    fun closeFind() { finding = false; find.query = "" }

    fun save(then: () -> Unit = {}) {
        val ed = editor ?: return
        val save = onSave ?: return
        if (saving) return
        saving = true
        val t = ed.value.text
        scope.launch {
            val ok = runCatching { save(t) }.getOrDefault(false)
            saving = false
            if (ok) { ed.markSaved(); shown = t; then() }
        }
    }

    fun closeEditor(after: () -> Unit = {}) {
        val ed = editor ?: return after()
        val done = { editor = null; after() }
        if (ed.dirty) leaveEditor = done else done()
    }

    BackHandler {
        when {
            editor != null -> closeEditor()
            finding -> closeFind()
            else -> onBack()
        }
    }

    Column(Modifier.fillMaxSize()) {
        val ed = editor
        DocTopBar(
            title = title,
            subtitle = ed?.let { e -> val (ln, col) = e.cursor(); "Ln $ln, Col $col${if (e.dirty) " · edited" else ""}" } ?: subtitle,
            onBack = { when { editor != null -> closeEditor(onBack); finding -> closeFind(); else -> onBack() } },
        ) {
            if (ed != null) {
                IconButton(onClick = { ed.undo() }, enabled = ed.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                IconButton(onClick = { ed.redo() }, enabled = ed.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                IconButton(onClick = { save() }, enabled = ed.dirty && !saving) { Icon(saveIcon, saveLabel) }
                IconButton(onClick = { closeEditor() }) { Icon(Icons.Filled.Check, "Done editing") }
                return@DocTopBar
            }
            if (onSave != null) IconButton(onClick = { closeFind(); editor = EditorState(shown, TextEncoding.UTF8, false) }) { Icon(Icons.Filled.Edit, "Edit") }
            IconButton(onClick = { if (finding) closeFind() else finding = true }) { Icon(Icons.Filled.Search, "Find") }
            TextSizeButton(textZoom, onZoom)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Wrap lines") }, leadingIcon = { Icon(Icons.Filled.WrapText, null) },
                        trailingIcon = if (wrap) ({ Icon(Icons.Filled.Check, "On", tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { menu = false; wrap = !wrap; DocPrefs.setWrap(context, wrap) },
                    )
                    DropdownMenuItem(
                        text = { Text("Line numbers") }, leadingIcon = { Icon(Icons.Filled.FormatListNumbered, null) },
                        trailingIcon = if (lineNumbers) ({ Icon(Icons.Filled.Check, "On", tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { menu = false; lineNumbers = !lineNumbers; DocPrefs.setLineNumbers(context, lineNumbers) },
                    )
                    menuExtras { menu = false }
                }
            }
        }
        FindBar(
            visible = finding && editor == null,
            query = find.query,
            onQuery = { find.query = it },
            status = find.status,
            onPrev = { find.step(-1) },
            onNext = { find.step(1) },
            onClose = { closeFind() },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DocSurface {
                val e = editor
                if (e != null) {
                    TextEditorPane(e, textZoom, onZoom, wrap, lineNumbers, lang = lang)
                } else {
                    val listState = rememberLazyListState()
                    TextPane(doc, textZoom, wrap, lineNumbers, find, listState, Modifier.pinchTextZoom(textZoom, onZoom), lang)
                }
            }
        }
        bottom()
    }

    leaveEditor?.let { leave ->
        OutlinedAlertDialog(
            onDismissRequest = { leaveEditor = null },
            title = { Text("Keep your changes?") },
            text = { Text("$title has changes that aren't saved yet.") },
            confirmButton = { TextButton(onClick = { leaveEditor = null; save(leave) }) { Text(saveLabel) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { leaveEditor = null; leave() }) { Text("Discard") }
                    TextButton(onClick = { leaveEditor = null }) { Text("Keep editing") }
                }
            },
        )
    }
}
