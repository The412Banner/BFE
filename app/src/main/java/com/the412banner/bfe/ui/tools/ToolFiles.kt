package com.the412banner.bfe.ui.tools

import android.content.Context
import android.net.Uri
import com.the412banner.bfe.util.IncomingFile
import java.io.File
import java.io.IOException

/**
 * Files for the "Open in BFE as" tools (font, SVG, script): the real file when BFE can read it, else
 * a copy of the Uri in BFE's cache (`cache/open_as/<tool>/`, one file per tool, replaced each time).
 */
internal object ToolFiles {
    /** Blocking. [path] when it is a readable file, else [uri] copied into the [tool]'s cache folder as [name]. */
    fun readable(ctx: Context, path: String?, uri: Uri?, name: String, tool: String): File {
        val p = path ?: uri?.let { runCatching { IncomingFile.resolvePath(ctx, it) }.getOrNull() }
        p?.let { File(it) }?.takeIf { it.isFile && it.canRead() }?.let { return it }
        val u = uri ?: throw IOException("The file couldn't be found.")
        val dir = File(ctx.cacheDir, "open_as/$tool").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, name.replace('/', '_').ifBlank { "file" })
        val input = ctx.contentResolver.openInputStream(u) ?: throw IOException("The file couldn't be opened.")
        input.use { i -> out.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
        return out
    }
}
