package com.the412banner.bfe.util

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * A tiny, self-contained "choose a folder" helper built on the system's ACTION_OPEN_DOCUMENT_TREE
 * picker. The native extractors write through java.io.File (not SAF), so we only use the picker to
 * NAME a destination and then resolve the chosen tree Uri back to a real filesystem path — the app
 * holds MANAGE_EXTERNAL_STORAGE, so that path is directly writable.
 *
 * (BFE's minimal stand-in for the source app's in-app FilePickerActivity, so the Unpack screen has a
 * working dest chooser without depending on the full File Manager.)
 */
object DirPicker {

    fun buildDirIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    /** Resolve an OK result's tree Uri to an absolute filesystem path, or null if it can't be mapped. */
    fun resolvePath(data: Intent?): String? {
        val treeUri = data?.data ?: return null
        return runCatching { treeUriToPath(treeUri) }.getOrNull()
    }

    /**
     * Map a Storage Access Framework tree Uri to a real path. Handles the externalstorage provider's
     * "primary:Sub/Dir" and "<volumeUuid>:Sub/Dir" document ids — the only ones a native write can use.
     */
    private fun treeUriToPath(uri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
        val parts = docId.split(":", limit = 2)
        val volume = parts.getOrNull(0) ?: return null
        val relative = parts.getOrNull(1).orEmpty()
        val base: File = when {
            volume.equals("primary", ignoreCase = true) ->
                @Suppress("DEPRECATION") Environment.getExternalStorageDirectory()
            else -> File("/storage/$volume")
        }
        val target = if (relative.isEmpty()) base else File(base, relative)
        return target.absolutePath
    }
}
