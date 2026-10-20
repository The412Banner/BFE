package com.the412banner.bfe.ui.internals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.reandroid.arsc.chunk.TableBlock
import com.the412banner.bfe.apk.internals.Axml
import com.the412banner.bfe.apk.internals.InternalsSource
import com.the412banner.bfe.ui.docs.CodeLang
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.util.AppSnackbar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface AxmlState {
    data object Loading : AxmlState
    data class Failed(val message: String) : AxmlState
    /** [binary] false = the entry was plain text XML already (saved back as text). */
    class Ready(val text: String, val binary: Boolean, val table: TableBlock) : AxmlState
}

/**
 * Binary XML (AXML) — AndroidManifest.xml or a compiled res XML, on its own or inside an APK: decoded to
 * readable XML (resource ids named from the APK's resources.arsc) in the text viewer, editable in the
 * text editor. Save compiles it back to binary XML; inside an APK it then asks "Save to a new APK" /
 * "Overwrite" and rebuilds + re-signs the APK in the background. A file on its own is written in place
 * (or beside it when it can't be).
 */
@Composable
internal fun AxmlScreen(source: InternalsSource, onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<AxmlState>(AxmlState.Loading) }
    val runner = rememberApkSaveRunner()
    var ask by remember { mutableStateOf<CompletableDeferred<ApkSaveChoice?>?>(null) }

    LaunchedEffect(source) {
        state = withContext(Dispatchers.IO) {
            runCatching<AxmlState> {
                val bytes = source.readBytes()
                // Names come from the APK this entry is in; a file on its own still gets android: names.
                val table = Axml.tableFor(if (source.inContainer) source.file else null)
                if (Axml.isBinary(bytes)) AxmlState.Ready(Axml.decode(bytes, table), true, table)
                else AxmlState.Ready(String(bytes, Charsets.UTF_8), false, table)
            }.getOrElse { AxmlState.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    // Compile, then write where it belongs. True = taken (the editor counts it as saved).
    suspend fun save(st: AxmlState.Ready, text: String): Boolean {
        val bytes = withContext(Dispatchers.Default) {
            runCatching { if (st.binary) Axml.encode(text, st.table) else text.toByteArray(Charsets.UTF_8) }
        }.getOrElse {
            AppSnackbar.show(context, "Couldn't compile the XML: ${it.message ?: it.javaClass.simpleName}", long = true)
            return false
        }
        val entry = source.entry
        if (entry == null) {
            return withContext(Dispatchers.IO) { runCatching { saveStandalone(context, source.file, bytes) } }
                .onSuccess { AppSnackbar.show(context, if (it == source.file) "Saved" else "Saved as ${it.absolutePath}", long = it != source.file) }
                .onFailure { AppSnackbar.show(context, "Couldn't save: ${it.message ?: it.javaClass.simpleName}", long = true) }
                .isSuccess
        }
        val d = CompletableDeferred<ApkSaveChoice?>()
        ask = d
        val choice = d.await() ?: return false
        val staged = withContext(Dispatchers.IO) { File(stagingDir(context), entry.substringAfterLast('/')).apply { writeBytes(bytes) } }
        return runner.start(source.file, mapOf(entry to staged), choice)
    }

    when (val st = state) {
        AxmlState.Loading -> Column(Modifier.fillMaxSize()) {
            BackHandler { onClose() }
            DocTopBar(source.name, null, onBack = onClose) {}
            DocNotice(Icons.Filled.Code, "Decoding ${source.name}…", null)
        }
        is AxmlState.Failed -> Column(Modifier.fillMaxSize()) {
            BackHandler { onClose() }
            DocTopBar(source.name, null, onBack = onClose) {}
            DocNotice(Icons.Filled.ErrorOutline, "Couldn't decode ${source.name}", st.message)
        }
        is AxmlState.Ready -> CodeScreen(
            title = source.name,
            subtitle = listOfNotNull(
                if (st.binary) "Binary XML" else "Text XML",
                source.entry?.let { "in ${source.file.name}" },
            ).joinToString(" · "),
            text = st.text,
            lang = CodeLang.XML,
            onBack = onClose,
            onSave = { t -> save(st, t) },
            bottom = { runner.Panel() },
        )
    }

    ask?.let { d ->
        SaveIntoApkDialog(
            apk = source.file,
            what = source.entry ?: source.name,
            onDismiss = { ask = null; d.complete(null) },
            onConfirm = { c -> ask = null; d.complete(c) },
        )
    }
}
