package com.the412banner.bfe.ui.font

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.tools.ToolFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface FontState {
    data object Loading : FontState
    data class Failed(val title: String, val message: String) : FontState
    class Ready(val info: FontInfo, val family: FontFamily) : FontState
}

private val SIZES = listOf(12, 16, 20, 28, 40, 56, 72)
private const val CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789\n!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\nÀÉÎÕÜßæøåçñ €£¥©®™ «»…"

/**
 * The font viewer: the font's name, format and glyph count, a line of sample text you can change,
 * that line at several sizes, and the basic character set. A file that isn't a font (or that Android
 * can't load, like a compressed web font) gets a notice instead. Both looks.
 */
@Composable
internal fun FontViewerScreen(path: String?, uri: Uri?, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<FontState>(FontState.Loading) }
    var sample by remember { mutableStateOf("The quick brown fox jumps over the lazy dog") }

    LaunchedEffect(path, uri) {
        state = withContext<FontState>(Dispatchers.IO) {
            runCatching<FontState> {
                val file = ToolFiles.readable(context, path, uri, name, "font")
                val info = FontInfoReader.read(file)
                    ?: return@runCatching FontState.Failed("This isn't a font file", "It doesn't start like a TrueType or OpenType font.")
                // Android hands back the default typeface instead of failing on some versions.
                val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
                if (tf == null || tf == Typeface.DEFAULT) {
                    FontState.Failed("Android can't load this font", "${info.format} — it can't be previewed on this device.")
                } else {
                    FontState.Ready(info, FontFamily(tf))
                }
            }.getOrElse { FontState.Failed("Couldn't open the file", it.message ?: "The file couldn't be read.") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val ready = state as? FontState.Ready
        DocTopBar(
            title = ready?.info?.fullName ?: ready?.info?.family ?: name,
            subtitle = ready?.info?.let { i ->
                listOfNotNull(i.format, i.glyphs?.let { "$it glyphs" }, i.faces.takeIf { it > 1 }?.let { "$it fonts" }).joinToString(" · ")
            } ?: name,
            onBack = onClose,
        ) {}
        DocSurface(Modifier.weight(1f)) {
            when (val st = state) {
                FontState.Loading -> DocNotice(Icons.Filled.FontDownload, "Opening…", null)
                is FontState.Failed -> DocNotice(Icons.Filled.ErrorOutline, st.title, st.message)
                is FontState.Ready -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    item {
                        val i = st.info
                        Column {
                            Text(i.family ?: name, fontFamily = st.family, fontSize = 30.sp, lineHeight = 38.sp)
                            Spacer(Modifier.height(4.dp))
                            listOfNotNull(
                                i.style?.let { "Style: $it" },
                                i.fullName?.let { "Full name: $it" },
                                "File: $name",
                            ).forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = sample,
                                onValueChange = { sample = it },
                                label = { Text("Sample text") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                    items(SIZES) { size ->
                        Column(Modifier.padding(bottom = 14.dp)) {
                            Text("$size sp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sample.ifEmpty { " " }, fontFamily = st.family, fontSize = size.sp, lineHeight = (size * 1.25f).sp)
                        }
                    }
                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 12.dp))
                        Text(CHARSET, fontFamily = st.family, fontSize = 22.sp, lineHeight = 32.sp)
                    }
                }
            }
        }
    }
}
