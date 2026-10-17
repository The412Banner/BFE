package com.the412banner.bfe

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.the412banner.bfe.docs.DocFormats
import com.the412banner.bfe.docs.DocKind
import com.the412banner.bfe.ui.components.BfePage
import com.the412banner.bfe.ui.docs.DocumentViewerScreen
import com.the412banner.bfe.ui.theme.BfeTheme
import com.the412banner.bfe.util.AppSnackbar

/**
 * BFE's built-in document viewer: PDF (PdfRenderer pages with zoom), OpenDocument and Office Open XML
 * text / spreadsheets / presentations, RTF, Markdown, EPUB (converted to HTML on the device, offline,
 * and shown in a WebView with BFE's reading style), CSV / TSV tables, and plain text / logs / code
 * (a lazy text view that copes with very large files). Older binary .doc / .xls / .ppt are recognised
 * and explained, not rendered. Opened from BFE's Open with sheet (View document) and offered to other
 * apps as "BFE Documents".
 */
class DocumentViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val path = intent.getStringExtra(EXTRA_PATH)
        if (uri == null && path == null) { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME)
        val mime = intent.type
        setContent {
            BfeTheme {
                BfePage {
                    DocumentViewerScreen(uri = uri, explicitPath = path, nameHint = name, mime = mime, onClose = { finish() })
                }
            }
        }
    }

    internal companion object {
        const val EXTRA_PATH = "com.the412banner.bfe.extra.DOC_PATH"
        const val EXTRA_NAME = "com.the412banner.bfe.extra.DOC_NAME"
    }
}

/** Opening BFE's document viewer from anywhere in the app. */
object DocumentViewer {
    /**
     * True when BFE's viewer opens [name] (by extension, else [mime]): PDF, OpenDocument, Office Open
     * XML, RTF, Markdown, EPUB, CSV / TSV, plain text / logs / configs / code — and the older binary
     * Office formats, for which the viewer explains why they can't be shown and offers other apps.
     */
    fun isDocumentName(name: String, mime: String? = null): Boolean = DocFormats.isViewable(name, mime)

    /** True for the plain-text kinds ("View text" rather than "View document"). */
    fun isTextName(name: String, mime: String? = null): Boolean = DocFormats.kindOf(name, mime) == DocKind.TEXT

    /**
     * The viewer for [path] (a real, readable file — preferred: no copy, and Markdown's relative
     * pictures load) or [uri] (a content:// Uri, read-granted to the viewer). [name] is what the file
     * is called (its kind comes from it), [mime] a hint when the name has no extension.
     */
    fun intent(context: Context, path: String?, uri: Uri?, name: String, mime: String? = null): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            component = component(context)
            putExtra(DocumentViewerActivity.EXTRA_NAME, name)
            path?.let { putExtra(DocumentViewerActivity.EXTRA_PATH, it) }
            when {
                uri != null -> { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                mime != null -> type = mime
            }
        }

    fun open(context: Context, path: String?, uri: Uri?, name: String, mime: String? = null) {
        runCatching { context.startActivity(intent(context, path, uri, name, mime)) }
            .onFailure { AppSnackbar.show(context, "Couldn't open $name") }
    }

    fun component(context: Context): ComponentName = ComponentName(context, DocumentViewerActivity::class.java)

    fun label(context: Context): String = context.getString(R.string.documents_label)
}
