package com.the412banner.bfe.storage

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Cross-backend copy / move by streams. The File Manager keeps using the fast, unchanged
 * FileUtils path for File→File; this handles anything that involves a SAF ([Loc.SafLoc]) side —
 * copying files or whole folders INTO or OUT OF a SAF tree, recursively.
 */
object StorageTransfer {

    /**
     * Copy [src] into the directory [destDir] as a child named [targetName] (which the caller has
     * already resolved for conflicts — same name = overwrite/merge, a uniquified name = keep-both).
     * Directories merge into an existing same-named child. [onBytes] is fed the running byte count.
     */
    fun copyInto(
        context: Context,
        src: Loc,
        destDir: Loc,
        targetName: String,
        onBytes: (Long) -> Unit = {},
    ): Boolean = copyRec(context, src, destDir, targetName, longArrayOf(0L), onBytes)

    private fun copyRec(
        context: Context,
        src: Loc,
        destDir: Loc,
        targetName: String,
        acc: LongArray,
        onBytes: (Long) -> Unit,
    ): Boolean {
        val srcBackend = Storage.backend(src)
        val dstBackend = Storage.backend(destDir)
        return if (src.isDir) {
            val existing = dstBackend.childNamed(context, destDir, targetName)?.takeIf { it.isDir }
            val newDir = existing ?: dstBackend.createFolder(context, destDir, targetName) ?: return false
            var ok = true
            for (child in srcBackend.listChildren(context, src)) {
                ok = copyRec(context, child, newDir, child.name, acc, onBytes) && ok
            }
            ok
        } else {
            val existing = dstBackend.childNamed(context, destDir, targetName)
            val target = existing ?: dstBackend.createFile(context, destDir, mimeFor(targetName), targetName) ?: return false
            val input = srcBackend.openInputStream(context, src) ?: return false
            val output = dstBackend.openOutputStream(context, target) ?: run { input.close(); return false }
            try {
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    acc[0] += n
                    onBytes(acc[0])
                }
                output.flush()
                true
            } catch (e: Exception) {
                false
            } finally {
                runCatching { input.close() }
                runCatching { output.close() }
            }
        }
    }

    /** Move = copy then delete the source (used whenever a SAF side is involved). */
    fun moveInto(
        context: Context,
        src: Loc,
        destDir: Loc,
        targetName: String,
        onBytes: (Long) -> Unit = {},
    ): Boolean {
        if (!copyInto(context, src, destDir, targetName, onBytes)) return false
        return Storage.backend(src).delete(context, src)
    }

    private fun mimeFor(name: String): String {
        val ext = File(name).extension.lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
