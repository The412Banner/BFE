package com.the412banner.bfe.ui.docs

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.io.File

/** A request to show page [page] (and scroll to [anchor]); [nonce] makes a repeat request count. */
internal data class PageRequest(val page: Int, val anchor: String? = null, val nonce: Int = 0)

/**
 * The converted pages ([pages], files written next to their pictures) in a WebView: no JavaScript, no
 * network, file access only for the pages and the pictures they reference. Links between pages (an
 * e-book's chapters) stay here and report the page through [onPageShown]; web, mail and phone links
 * go to their apps; links to other local files go to [onLocalFile]. [reloadKey] changes when the
 * pages were rewritten (a theme change) and reloads the one shown.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebDocPane(
    pages: List<File>,
    request: PageRequest,
    reloadKey: String,
    textZoom: Int,
    background: Color,
    onPageShown: (Int) -> Unit,
    onWebView: (WebView?) -> Unit,
    onFindResult: (active: Int, total: Int) -> Unit,
    onLocalFile: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by rememberUpdatedState(onPageShown)
    val local by rememberUpdatedState(onLocalFile)
    val findResult by rememberUpdatedState(onFindResult)
    val currentPages by rememberUpdatedState(pages)
    val holder = remember { LoadHolder() }

    fun indexOf(url: String?): Int {
        val path = url?.let { Uri.parse(it).path } ?: return -1
        return currentPages.indexOfFirst { it.absolutePath == path }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = true          // the pages and their pictures, in BFE's cache
                    allowContentAccess = false
                    blockNetworkLoads = true        // documents never phone home
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    useWideViewPort = true
                    loadWithOverviewMode = false
                    defaultTextEncodingName = "utf-8"
                }
                // The pages carry BFE's own light / dark colours; nothing is recoloured algorithmically.
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    @Suppress("DEPRECATION")
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                        val target = req.url
                        when (target.scheme) {
                            "file" -> {
                                if (indexOf(target.toString()) >= 0) return false
                                target.path?.let { local(File(it)) }
                                return true
                            }
                            "about", "data" -> return false
                            "http", "https", "mailto", "tel", "geo" -> {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                return true
                            }
                            else -> return true
                        }
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        val i = indexOf(url)
                        if (i >= 0 && i != holder.page) {
                            // A link moved to another page: remember it so the next update doesn't reload.
                            holder.page = i
                            shown(i)
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (holder.clearHistory) { holder.clearHistory = false; view.clearHistory() }
                    }
                }
                setFindListener { active, total, done -> if (done) findResult(if (total == 0) 0 else active + 1, total) }
                holder.view = this
                onWebView(this)
            }
        },
        update = { wv ->
            wv.setBackgroundColor(background.toArgb())
            if (wv.settings.textZoom != textZoom) wv.settings.textZoom = textZoom
            val file = pages.getOrNull(request.page) ?: return@AndroidView
            val key = "${request.page}|${request.anchor}|${request.nonce}|$reloadKey"
            if (holder.loaded != key) {
                val sameDoc = holder.page == request.page && holder.reloadKey == reloadKey && holder.loaded != null
                holder.loaded = key
                holder.page = request.page
                holder.reloadKey = reloadKey
                val url = Uri.fromFile(file).toString() + (request.anchor?.let { "#$it" } ?: "")
                if (sameDoc && request.anchor == null) return@AndroidView
                // Programmatic moves (tabs, Next, Contents) don't pile up Back history; link jumps do.
                holder.clearHistory = !sameDoc
                wv.loadUrl(url)
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            onWebView(null)
            holder.view?.destroy()
            holder.view = null
            holder.loaded = null
        }
    }
}

/** What the WebView last loaded (plain state, not observed). */
private class LoadHolder {
    var loaded: String? = null
    var page: Int = -1
    var reloadKey: String = ""
    var clearHistory = false
    var view: WebView? = null
}
