package com.the412banner.bfe.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.the412banner.bfe.core.FileUtils
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * A browsable storage location, backed by EITHER a real filesystem [FileLoc] (direct java.io.File —
 * the app's own All-Files-Access storage) or a Storage-Access-Framework document [SafLoc] (another
 * app's DocumentsProvider, reached through a persisted tree Uri).
 *
 * The File Manager works in terms of [Loc]; each op is dispatched to the matching [StorageBackend] via
 * [Storage.backend]. The File path is byte-for-byte the old behaviour (FileBackend just calls the same
 * java.io.File / FileUtils code); SAF is the new capability.
 */
sealed class Loc {
    abstract val name: String
    abstract val isDir: Boolean
    abstract val size: Long
    abstract val lastModified: Long
    /** Stable identity for selection sets, list keys and favourites. */
    abstract val id: String

    // Structural equality by id so `showMenuFor == entry` matches across re-listings (entries are
    // rebuilt each listing, so identity equality would never match).
    override fun equals(other: Any?): Boolean = other is Loc && other.id == id
    override fun hashCode(): Int = id.hashCode()

    class FileLoc(val file: File) : Loc() {
        override val name get() = file.name
        override val isDir get() = file.isDirectory
        override val size get() = if (file.isDirectory) 0L else file.length()
        override val lastModified get() = file.lastModified()
        override val id get() = file.absolutePath
    }

    class SafLoc(
        val treeUri: Uri,
        val documentId: String,
        override val name: String,
        override val isDir: Boolean,
        override val size: Long,
        override val lastModified: Long,
    ) : Loc() {
        val docUri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        override val id get() = docUri.toString()
    }

    /** A root-backed filesystem path (any app's private storage on a rooted device). */
    class RootLoc(
        val path: String,
        override val name: String,
        override val isDir: Boolean,
        override val size: Long,
        override val lastModified: Long,
    ) : Loc() {
        override val id get() = "root:$path"
    }
}

/**
 * The operations the File Manager UI needs, abstracted over a backend. Every method is blocking IO —
 * callers wrap it in `withContext(Dispatchers.IO)`, exactly as the old File code did.
 */
interface StorageBackend {
    fun listChildren(context: Context, dir: Loc): List<Loc>
    fun openInputStream(context: Context, loc: Loc): InputStream?
    fun openOutputStream(context: Context, loc: Loc): OutputStream?
    /** Existing child of [parent] named [name], or null. Used for paste conflict detection. */
    fun childNamed(context: Context, parent: Loc, name: String): Loc?
    fun createFolder(context: Context, parent: Loc, name: String): Loc?
    /** Create (or truncate) a plain file child for a stream copy target. */
    fun createFile(context: Context, parent: Loc, name: String, mime: String): Loc?
    fun rename(context: Context, loc: Loc, newName: String): Loc?
    fun delete(context: Context, loc: Loc): Boolean
}

/** Resolves the backend for a [Loc]. */
object Storage {
    fun backend(loc: Loc): StorageBackend = when (loc) {
        is Loc.FileLoc -> FileBackend
        is Loc.SafLoc -> SafBackend
        is Loc.RootLoc -> RootBackend
    }
}

/** Direct java.io.File backend — the app's own All-Files-Access storage (unchanged behaviour). */
object FileBackend : StorageBackend {
    override fun listChildren(context: Context, dir: Loc): List<Loc> {
        val f = (dir as Loc.FileLoc).file
        return f.listFiles()?.map { Loc.FileLoc(it) } ?: emptyList()
    }

    override fun openInputStream(context: Context, loc: Loc): InputStream =
        (loc as Loc.FileLoc).file.inputStream()

    override fun openOutputStream(context: Context, loc: Loc): OutputStream =
        (loc as Loc.FileLoc).file.outputStream()

    override fun childNamed(context: Context, parent: Loc, name: String): Loc? {
        val c = File((parent as Loc.FileLoc).file, name)
        return if (c.exists()) Loc.FileLoc(c) else null
    }

    override fun createFolder(context: Context, parent: Loc, name: String): Loc? {
        val d = File((parent as Loc.FileLoc).file, name)
        return if (d.isDirectory || d.mkdirs()) Loc.FileLoc(d) else null
    }

    override fun createFile(context: Context, parent: Loc, name: String, mime: String): Loc? {
        val c = File((parent as Loc.FileLoc).file, name)
        c.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        return Loc.FileLoc(c)   // opened + truncated by openOutputStream
    }

    override fun rename(context: Context, loc: Loc, newName: String): Loc? {
        val f = (loc as Loc.FileLoc).file
        val target = File(f.parentFile, newName)
        return if (f.renameTo(target)) Loc.FileLoc(target) else null
    }

    override fun delete(context: Context, loc: Loc): Boolean =
        FileUtils.delete((loc as Loc.FileLoc).file)
}

/**
 * Storage-Access-Framework backend over a persisted tree Uri. Listing uses a single
 * ContentResolver.query on [DocumentsContract.buildChildDocumentsUriUsingTree] (NOT
 * DocumentFile.listFiles(), which is one IPC per child).
 */
object SafBackend : StorageBackend {
    private const val TAG = "SafBackend"

    /** The tree's own root document as a [Loc.SafLoc], or null if it can't be resolved. */
    fun rootLoc(context: Context, treeUri: Uri, fallbackName: String): Loc.SafLoc? = try {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        context.contentResolver.query(
            rootUri,
            arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: fallbackName
                val mime = c.getString(1)
                val lm = if (c.isNull(2)) 0L else c.getLong(2)
                Loc.SafLoc(treeUri, rootId, name, mime == Document.MIME_TYPE_DIR || mime == null, 0L, lm)
            } else null
        } ?: Loc.SafLoc(treeUri, rootId, fallbackName, true, 0L, 0L)
    } catch (e: Exception) {
        Log.e(TAG, "rootLoc failed for $treeUri", e)
        null
    }

    override fun listChildren(context: Context, dir: Loc): List<Loc> {
        val p = dir as Loc.SafLoc
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(p.treeUri, p.documentId)
        val out = ArrayList<Loc>()
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE,
                    Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                val ni = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val di = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val mi = c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val si = c.getColumnIndexOrThrow(Document.COLUMN_SIZE)
                val li = c.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val docId = c.getString(di) ?: continue
                    val name = c.getString(ni)?.takeIf { it.isNotBlank() } ?: docId.substringAfterLast('/')
                    val mime = c.getString(mi)
                    val isDir = mime == Document.MIME_TYPE_DIR
                    val size = if (c.isNull(si)) 0L else c.getLong(si)
                    val lm = if (c.isNull(li)) 0L else c.getLong(li)
                    out.add(Loc.SafLoc(p.treeUri, docId, name, isDir, size, lm))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listChildren failed for ${p.documentId}", e)
        }
        return out
    }

    override fun openInputStream(context: Context, loc: Loc): InputStream? =
        context.contentResolver.openInputStream((loc as Loc.SafLoc).docUri)

    override fun openOutputStream(context: Context, loc: Loc): OutputStream? =
        // "wt" = write + truncate, so re-copying onto an existing file overwrites it.
        context.contentResolver.openOutputStream((loc as Loc.SafLoc).docUri, "wt")

    override fun childNamed(context: Context, parent: Loc, name: String): Loc? =
        listChildren(context, parent).firstOrNull { it.name == name }

    override fun createFolder(context: Context, parent: Loc, name: String): Loc? =
        createChild(context, parent as Loc.SafLoc, Document.MIME_TYPE_DIR, name)

    override fun createFile(context: Context, parent: Loc, name: String, mime: String): Loc? =
        createChild(context, parent as Loc.SafLoc, mime.ifBlank { "application/octet-stream" }, name)

    private fun createChild(context: Context, parent: Loc.SafLoc, mime: String, name: String): Loc? = try {
        val created = DocumentsContract.createDocument(context.contentResolver, parent.docUri, mime, name)
        created?.let { uri ->
            val docId = DocumentsContract.getDocumentId(uri)
            Loc.SafLoc(parent.treeUri, docId, name, mime == Document.MIME_TYPE_DIR, 0L, System.currentTimeMillis())
        }
    } catch (e: Exception) {
        Log.e(TAG, "createChild($mime, $name) failed", e)
        null
    }

    override fun rename(context: Context, loc: Loc, newName: String): Loc? = try {
        val s = loc as Loc.SafLoc
        val newUri = DocumentsContract.renameDocument(context.contentResolver, s.docUri, newName)
        // renameDocument may return the same or a new doc Uri; re-resolve the id either way.
        val docId = newUri?.let { DocumentsContract.getDocumentId(it) } ?: s.documentId
        Loc.SafLoc(s.treeUri, docId, newName, s.isDir, s.size, System.currentTimeMillis())
    } catch (e: Exception) {
        Log.e(TAG, "rename failed", e)
        null
    }

    override fun delete(context: Context, loc: Loc): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, (loc as Loc.SafLoc).docUri)
    } catch (e: Exception) {
        Log.e(TAG, "delete failed", e)
        false
    }
}
