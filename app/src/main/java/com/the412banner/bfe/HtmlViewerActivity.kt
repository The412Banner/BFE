package com.the412banner.bfe

import com.the412banner.bfe.ui.components.bfeTopBarColors
import com.the412banner.bfe.ui.components.BfePage
import androidx.compose.material3.TopAppBarDefaults
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.theme.BfeTheme
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.IncomingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BFE's built-in HTML viewer: an HTML / XHTML / SVG file rendered in a WebView, scripts on, with the
 * page's own images, styles and scripts next to it loading from storage (the file is opened as a real
 * file:// path whenever it has one). Pinch zoom, find in page, back/forward through local links,
 * reload, View source, Open in browser and Share. Web links leave for the browser.
 */
class HtmlViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val name = intent.getStringExtra(EXTRA_NAME)
        val explicitPath = intent.getStringExtra(EXTRA_PATH)
        val forceHtml = intent.getBooleanExtra(EXTRA_FORCE_HTML, false)
        if (uri == null && explicitPath == null) { finish(); return }
        setContent {
            BfeTheme {
                BfePage {
                    HtmlViewerScreen(uri, explicitPath, name, forceHtml, onClose = { finish() })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_FORCE_HTML = "force_html"
        private val HTML_EXTS = setOf("html", "htm", "xhtml", "xht", "svg")

        fun isHtmlName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in HTML_EXTS

        /**
         * Opens [path] (a real file, preferred: relative resources load) or [uri] in the viewer.
         * [forceHtml] renders it as a web page even when its name isn't .html (Open in BFE as → Web).
         */
        fun intent(context: Context, path: String?, uri: Uri?, name: String, forceHtml: Boolean = false): Intent =
            Intent(context, HtmlViewerActivity::class.java)
                .putExtra(EXTRA_NAME, name)
                .apply {
                    if (forceHtml) putExtra(EXTRA_FORCE_HTML, true)
                    path?.let { putExtra(EXTRA_PATH, it) }
                    uri?.let { data = it; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
    }
}

/** What the viewer loads: a real file (by URL, so relative links work) or inline markup from a stream. */
private sealed interface PageSource {
    data class Url(val url: String, val file: File) : PageSource
    data class Inline(val html: String, val baseUrl: String? = null) : PageSource
    data class Failed(val message: String) : PageSource
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HtmlViewerScreen(uri: Uri?, explicitPath: String?, name: String?, forceHtml: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var source by remember { mutableStateOf<PageSource?>(null) }
    var pageTitle by remember { mutableStateOf(name ?: "") }
    var progress by remember { mutableIntStateOf(0) }
    var showSource by remember { mutableStateOf(false) }
    var sourceText by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf(0 to 0) }  // (current 1-based, total)
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(uri, explicitPath) {
        source = withContext(Dispatchers.IO) {
            val path = explicitPath ?: uri?.let { IncomingFile.resolvePath(context, it) }
            val file = path?.let { File(it) }?.takeIf { it.isFile && it.canRead() }
            when {
                // Forced on a file whose name isn't .html: WebView would show it as text by its
                // extension, so the markup is loaded inline with the file's folder as the base.
                file != null && forceHtml && !HtmlViewerActivity.isHtmlName(file.name) -> runCatching {
                    PageSource.Inline(file.readText(), Uri.fromFile(file.parentFile ?: file).toString() + "/")
                }.getOrElse { PageSource.Failed(it.message ?: "The file couldn't be opened.") }
                file != null -> PageSource.Url(Uri.fromFile(file).toString(), file)
                uri != null -> runCatching {
                    context.contentResolver.openInputStream(uri)?.use { s ->
                        PageSource.Inline(s.readBytes().toString(Charsets.UTF_8))
                    } ?: PageSource.Failed("The file couldn't be opened.")
                }.getOrElse { PageSource.Failed(it.message ?: "The file couldn't be opened.") }
                else -> PageSource.Failed("The file couldn't be opened.")
            }
        }
    }
    LaunchedEffect(showSource, source) {
        if (showSource && sourceText == null) {
            sourceText = withContext(Dispatchers.IO) {
                when (val s = source) {
                    is PageSource.Url -> runCatching { s.file.readText().take(MAX_SOURCE_CHARS) }.getOrNull()
                    is PageSource.Inline -> s.html.take(MAX_SOURCE_CHARS)
                    else -> null
                }
            } ?: "Source unavailable."
        }
    }

    fun close() { if (finding) { finding = false; webView?.clearMatches() } else if (!showSource && webView?.canGoBack() == true) webView?.goBack() else onClose() }
    BackHandler { close() }

    fun openInBrowser() {
        val target = when (val s = source) {
            is PageSource.Url -> uri ?: Uri.parse(s.url)
            else -> uri
        } ?: return
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(target, "text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { context.startActivity(Intent.createChooser(i, "Open in browser")) } catch (_: ActivityNotFoundException) {
            AppSnackbar.show(context, "No browser can open this file")
        }
    }

    fun share() {
        val target = uri ?: return AppSnackbar.show(context, "Share it from the file list")
        val i = Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_STREAM, target)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(i, "Share")) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = bfeTopBarColors(TopAppBarDefaults.topAppBarColors()),
            navigationIcon = {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            title = {
                Column {
                    Text(pageTitle.ifBlank { name ?: "Page" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                    if (name != null && pageTitle.isNotBlank() && pageTitle != name) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            actions = {
                if (!showSource) IconButton(onClick = { finding = !finding; if (!finding) webView?.clearMatches() }) { Icon(Icons.Filled.Search, "Find in page") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    BfeDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!showSource) DropdownMenuItem(text = { Text("Reload") }, onClick = { menu = false; webView?.reload() })
                        DropdownMenuItem(
                            text = { Text(if (showSource) "View page" else "View source") },
                            onClick = { menu = false; finding = false; showSource = !showSource },
                        )
                        DropdownMenuItem(text = { Text("Open in browser") }, onClick = { menu = false; openInBrowser() })
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; share() })
                    }
                }
            },
        )
        AnimatedVisibility(visible = progress in 1..99 && !showSource) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        AnimatedVisibility(visible = finding) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; webView?.findAllAsync(it) },
                    singleLine = true,
                    placeholder = { Text("Find in page") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { webView?.findNext(true) }),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (query.isEmpty()) "" else "${matches.first}/${matches.second}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { webView?.findNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
                IconButton(onClick = { webView?.findNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
                IconButton(onClick = { finding = false; query = ""; webView?.clearMatches() }) { Icon(Icons.Filled.Close, "Close find") }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val s = source) {
                null -> Unit
                is PageSource.Failed -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true          // the page's own images / styles / scripts
                                allowContentAccess = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            // Pages with their own dark style (prefers-color-scheme) follow BFE's theme;
                            // nothing is algorithmically recoloured.
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                                @Suppress("DEPRECATION")
                                WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                            }
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                @Suppress("DEPRECATION")
                                WebSettingsCompat.setForceDark(settings, if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val target = request.url
                                    // Local pages and in-page anchors stay here; web, mail, tel… go to their apps.
                                    if (target.scheme == "file" || target.scheme == "about" || target.scheme == "data") return false
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                                    return true
                                }
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 1 }
                                override fun onPageFinished(view: WebView, url: String?) { progress = 100 }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                                override fun onReceivedTitle(view: WebView, title: String?) {
                                    if (!title.isNullOrBlank() && !title.startsWith("file:")) pageTitle = title
                                }
                            }
                            setFindListener { active, total, done -> if (done) matches = (if (total == 0) 0 else active + 1) to total }
                            when (s) {
                                is PageSource.Url -> loadUrl(s.url)
                                is PageSource.Inline -> loadDataWithBaseURL(s.baseUrl, s.html, "text/html", "utf-8", null)
                                else -> Unit
                            }
                            webView = this
                        }
                    },
                )
            }
            if (showSource) {
                BfePage {
                    SelectionContainer {
                        Text(
                            sourceText ?: "Loading…",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }
}

private const val MAX_SOURCE_CHARS = 2_000_000
