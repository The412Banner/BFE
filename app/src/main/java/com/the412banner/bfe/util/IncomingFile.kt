package com.the412banner.bfe.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a file another app hands BFE (a VIEW/SEND Uri from its "Open with" or Share sheet) into a real
 * path the native extractors can open. A path matters: 7-Zip/innoextract/unarc read by path, and an
 * InnoSetup or FreeArc repack needs its sibling Setup-*.bin volumes next to the .exe. So the real file
 * is located wherever the Uri allows it; streaming a copy in is only the last resort.
 */
object IncomingFile {
    /** What the sending app reports about the file; [size] is -1 when it didn't say. */
    data class Meta(val name: String, val size: Long)

    fun meta(ctx: Context, uri: Uri): Meta {
        var name: String? = null
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
        }
        val fromUri = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        val safeName = (name?.takeIf { it.isNotBlank() } ?: fromUri?.takeIf { it.isNotBlank() } ?: "archive")
            .replace('/', '_')
        return Meta(safeName, size)
    }

    /** The real path behind [uri], or null when it can't be located on storage. */
    fun resolvePath(ctx: Context, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.takeIf { File(it).isFile }
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val meta = meta(ctx, uri)
        fun valid(path: String?) = path?.takeIf { File(it).let { f -> f.isFile && (meta.size < 0 || f.length() == meta.size) } }
        return valid(documentPath(ctx, uri)) ?: valid(dataColumn(ctx, uri)) ?: embeddedPath(uri, meta)
    }

    /** DocumentsProvider Uris: primary/SD-card storage ("primary:Games/x.iso") and raw Downloads ids. */
    private fun documentPath(ctx: Context, uri: Uri): String? {
        val docId = runCatching {
            if (DocumentsContract.isDocumentUri(ctx, uri)) DocumentsContract.getDocumentId(uri)
            else DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull() ?: return null
        return when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val volume = docId.substringBefore(':')
                val rel = docId.substringAfter(':', "")
                if (volume.equals("primary", ignoreCase = true)) File(Environment.getExternalStorageDirectory(), rel).path
                else File("/storage/$volume", rel).path
            }
            "com.android.providers.downloads.documents" -> docId.takeIf { it.startsWith("raw:") }?.removePrefix("raw:")
            else -> null
        }
    }

    /** MediaStore and some older providers still answer the `_data` column with the file's path. */
    private fun dataColumn(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
            val i = c.getColumnIndex("_data")
            if (i >= 0 && c.moveToFirst() && !c.isNull(i)) c.getString(i) else null
        }
    }.getOrNull()

    /**
     * Most file managers share through a FileProvider whose Uri path still carries the real one, e.g.
     * `content://<app>.fileprovider/root/storage/emulated/0/Games/x.iso` or `.../external/Games/x.iso`.
     * Each suffix of the path is tried as an absolute path, then relative to primary storage. A relative
     * guess only counts when the sender reported a size and it matches, so a same-named file elsewhere
     * is never picked by mistake.
     */
    private fun embeddedPath(uri: Uri, meta: Meta): String? {
        val segments = uri.pathSegments
        val storage = Environment.getExternalStorageDirectory()
        fun sizeOk(f: File) = meta.size < 0 || f.length() == meta.size
        for (i in segments.indices) {
            val tail = segments.subList(i, segments.size).joinToString("/")
            File("/$tail").takeIf { it.isFile && sizeOk(it) }?.let { return it.path }
            if (meta.size >= 0) File(storage, tail).takeIf { it.isFile && it.length() == meta.size }?.let { return it.path }
        }
        return null
    }

    /** Where streamed-in files land: app-owned space on shared storage, the same volume as the user's files. */
    fun incomingDir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "incoming")

    fun freeBytes(dir: File): Long = runCatching { dir.mkdirs(); StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /** Copies [uri] into [incomingDir] as [Meta.name], reporting bytes copied. Cancellable; a partial copy is removed. */
    suspend fun copyIn(ctx: Context, uri: Uri, meta: Meta, onProgress: (Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = incomingDir(ctx).apply { mkdirs() }
        val out = File(dir, meta.name)
        val part = File(dir, meta.name + ".part")
        try {
            val input = ctx.contentResolver.openInputStream(uri) ?: error("The sending app didn't provide the file")
            input.use { src ->
                part.outputStream().use { dst ->
                    val buf = ByteArray(1 shl 20)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val n = src.read(buf)
                        if (n < 0) break
                        dst.write(buf, 0, n)
                        total += n
                        onProgress(total)
                    }
                }
            }
            out.delete()
            if (!part.renameTo(out)) error("Couldn't finish the copy")
            out
        } catch (t: Throwable) {
            part.delete()
            throw t
        }
    }

    /** Removes earlier streamed-in copies, keeping [keep] (the archive an extraction is still reading). */
    fun clean(ctx: Context, keep: String?) {
        incomingDir(ctx).listFiles()?.forEach { if (it.absolutePath != keep) it.deleteRecursively() }
    }
}
