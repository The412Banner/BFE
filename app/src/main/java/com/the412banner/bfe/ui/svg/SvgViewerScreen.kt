package com.the412banner.bfe.ui.svg

import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.ui.docs.DocNotice
import com.the412banner.bfe.ui.docs.DocSurface
import com.the412banner.bfe.ui.docs.DocTopBar
import com.the412banner.bfe.ui.tools.ToolFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

private sealed interface SvgState {
    data object Loading : SvgState
    data class Failed(val title: String, val message: String) : SvgState
    class Ready(val base64: String, val bytes: Long) : SvgState
}

/** The page behind the drawing. */
private enum class SvgBackground { LIGHT, DARK, CHECKER }

private const val MAX_SVG = 32L * 1024 * 1024

/**
 * The SVG viewer: the file's SVG markup (gzipped .svgz too) drawn as an image in a WebView, fitted to
 * the screen, with pinch zoom and a light / dark / checkerboard background (the button cycles them).
 * Scripts inside the drawing never run (it is shown as an image). A file without SVG markup gets a
 * notice. Both looks.
 */
@Composable
internal fun SvgViewerScreen(path: String?, uri: Uri?, name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var state by remember { mutableStateOf<SvgState>(SvgState.Loading) }
    var bg by rememberSaveable { mutableStateOf(if (dark) SvgBackground.DARK else SvgBackground.LIGHT) }

    LaunchedEffect(path, uri) {
        state = withContext(Dispatchers.IO) {
            runCatching {
                val file = ToolFiles.readable(context, path, uri, name, "svg")
                if (file.length() > MAX_SVG) return@runCatching SvgState.Failed("Too big to show", "SVG files up to 32 MB can be shown.")
                val bytes = readSvg(file)
                val head = String(bytes, 0, minOf(bytes.size, 1 shl 20), Charsets.UTF_8)
                if (!head.contains("<svg", ignoreCase = true)) {
                    SvgState.Failed("This isn't an SVG drawing", "The file has no <svg> markup.")
                } else {
                    SvgState.Ready(Base64.encodeToString(bytes, Base64.NO_WRAP), file.length())
                }
            }.getOrElse { SvgState.Failed("Couldn't open the file", it.message ?: "The file couldn't be read.") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        DocTopBar(
            title = name,
            subtitle = (state as? SvgState.Ready)?.let { "SVG · ${StringUtils.formatBytes(it.bytes)}" },
            onBack = onClose,
        ) {
            if (state is SvgState.Ready) {
                IconButton(onClick = { bg = SvgBackground.entries[(bg.ordinal + 1) % SvgBackground.entries.size] }) {
                    when (bg) {
                        SvgBackground.LIGHT -> Icon(Icons.Filled.LightMode, "Background: light")
                        SvgBackground.DARK -> Icon(Icons.Filled.DarkMode, "Background: dark")
                        SvgBackground.CHECKER -> Icon(Icons.Filled.GridOn, "Background: checkerboard")
                    }
                }
            }
        }
        DocSurface(Modifier.weight(1f)) {
            when (val st = state) {
                SvgState.Loading -> DocNotice(Icons.Filled.Brush, "Opening…", null)
                is SvgState.Failed -> DocNotice(Icons.Filled.ErrorOutline, st.title, st.message)
                is SvgState.Ready -> {
                    var web by remember { mutableStateOf<WebView?>(null) }
                    DisposableEffect(Unit) { onDispose { web?.destroy() } }
                    val html = remember(st, bg) { pageFor(st.base64, bg) }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = false
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.setSupportZoom(true)
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                web = this
                            }
                        },
                        update = { v ->
                            if (v.tag != html) {
                                v.tag = html
                                v.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The file's bytes, un-gzipped when it is a .svgz (gzip magic 1F 8B). */
private fun readSvg(file: File): ByteArray {
    val gz = file.inputStream().use { val a = it.read(); val b = it.read(); a == 0x1F && b == 0x8B }
    return if (gz) GZIPInputStream(file.inputStream()).use { it.readBytes() } else file.readBytes()
}

/** A page with the drawing as an image filling the screen (kept to its proportions) on [bg]. */
private fun pageFor(base64: String, bg: SvgBackground): String {
    val ground = when (bg) {
        SvgBackground.LIGHT -> "background:#ffffff;"
        SvgBackground.DARK -> "background:#121212;"
        SvgBackground.CHECKER -> "background-color:#ffffff;" +
            "background-image:linear-gradient(45deg,#cfcfcf 25%,transparent 25%),linear-gradient(-45deg,#cfcfcf 25%,transparent 25%)," +
            "linear-gradient(45deg,transparent 75%,#cfcfcf 75%),linear-gradient(-45deg,transparent 75%,#cfcfcf 75%);" +
            "background-size:20px 20px;background-position:0 0,0 10px,10px -10px,-10px 0;"
    }
    return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=10,user-scalable=yes">
<style>html,body{margin:0;height:100%;$ground}body{display:flex;align-items:center;justify-content:center}
img{width:96vw;height:96vh;object-fit:contain}</style></head>
<body><img alt="" src="data:image/svg+xml;base64,$base64"></body></html>"""
}
