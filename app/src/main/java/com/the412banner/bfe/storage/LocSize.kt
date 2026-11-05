package com.the412banner.bfe.storage

import android.content.Context
import android.provider.DocumentsContract
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.BufferOverflow
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Size of a selection, for Properties and the delete confirmation: [bytes] of every file, counting
 * folders through their whole tree; [files] / [folders] found inside the selection (a selected folder
 * itself is not counted); [partial] when something could not be read, a backend can't size folders, or
 * the walk stopped at [LocSize.MAX_ENTRIES]; [done] once the count is final.
 */
data class SizeTotal(
    val bytes: Long = 0L,
    val files: Int = 0,
    val folders: Int = 0,
    val partial: Boolean = false,
    val done: Boolean = false,
) {
    operator fun plus(o: SizeTotal) =
        SizeTotal(bytes + o.bytes, files + o.files, folders + o.folders, partial || o.partial, done && o.done)
}

/**
 * Counts [SizeTotal] for a list of [Loc]s off the main thread, sending running totals while big
 * folders are walked and a last value with `done = true`. Cancelling the collector stops the walk.
 *
 * Per backend: File = `Files.walkFileTree` (one lstat per entry, symlinks are not followed, the same
 * walk as the home screen's folder stats); Root = one `du -sk` + two `find | wc -l` through the libsu
 * shell, only when root is already granted (`du` reports space on disk, so it reads as "about"); SAF =
 * one children query per folder (size + MIME columns only); Archive and Remote entries use their listed
 * size, and their folders mark the total partial (no recursive listing over the network or of an
 * archive index from here).
 */
object LocSize {
    const val MAX_ENTRIES = 2_000_000
    private const val PROGRESS_EVERY = 2_000

    fun measure(context: Context, locs: List<Loc>): Flow<SizeTotal> = channelFlow {
        var total = SizeTotal()
        for (loc in locs) {
            currentCoroutineContext().ensureActive()
            val base = total
            val one = when (loc) {
                is Loc.FileLoc -> walkFile(loc) { trySend(base + it) }
                is Loc.RootLoc -> root(loc)
                is Loc.SafLoc -> saf(context, loc) { trySend(base + it) }
                else -> if (loc.isDir) SizeTotal(partial = true) else SizeTotal(bytes = loc.size.coerceAtLeast(0L))
            }
            total += one
            trySend(total)
        }
        send(total.copy(done = true))
    }.buffer(1, BufferOverflow.DROP_OLDEST).flowOn(Dispatchers.IO)

    private suspend fun walkFile(loc: Loc.FileLoc, progress: (SizeTotal) -> Unit): SizeTotal {
        val f = loc.file
        if (!f.isDirectory) return SizeTotal(bytes = f.length(), files = if (f.isFile) 1 else 0)
        val ctx = currentCoroutineContext()
        val root = f.toPath()
        var bytes = 0L
        var files = 0
        var folders = 0
        var seen = 0
        var partial = false
        if (f.list() == null) return SizeTotal(partial = true)
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            fun step(): FileVisitResult {
                if (!ctx.isActive) return FileVisitResult.TERMINATE
                if (++seen >= MAX_ENTRIES) { partial = true; return FileVisitResult.TERMINATE }
                if (seen % PROGRESS_EVERY == 0) progress(SizeTotal(bytes, files, folders, partial))
                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root) folders++
                return step()
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) { bytes += attrs.size(); files++ }
                return step()
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                partial = true
                return FileVisitResult.CONTINUE
            }
        })
        return SizeTotal(bytes, files, folders, partial)
    }

    private fun root(loc: Loc.RootLoc): SizeTotal {
        if (!loc.isDir) return SizeTotal(bytes = loc.size.coerceAtLeast(0L), files = 1)
        if (Shell.isAppGrantedRoot() != true) return SizeTotal(partial = true)
        val p = "'" + loc.path.replace("'", "'\\''") + "'"
        val r = Shell.cmd(
            "du -sk $p 2>/dev/null | cut -f1; find $p -mindepth 1 -type f 2>/dev/null | wc -l; " +
                "find $p -mindepth 1 -type d 2>/dev/null | wc -l"
        ).exec()
        val n = r.out.map { it.trim().toLongOrNull() }
        if (n.size < 3 || n.any { it == null }) return SizeTotal(partial = true)
        return SizeTotal(n[0]!! * 1024L, n[1]!!.toInt(), n[2]!!.toInt(), partial = !r.isSuccess)
    }

    private suspend fun saf(context: Context, loc: Loc.SafLoc, progress: (SizeTotal) -> Unit): SizeTotal {
        if (!loc.isDir) return SizeTotal(bytes = loc.size.coerceAtLeast(0L), files = 1)
        val ctx = currentCoroutineContext()
        var bytes = 0L
        var files = 0
        var folders = 0
        var seen = 0
        var partial = false
        val pending = ArrayDeque<String>().apply { add(loc.documentId) }
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (pending.isNotEmpty() && ctx.isActive) {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(loc.treeUri, pending.removeFirst())
            val c = runCatching { context.contentResolver.query(uri, cols, null, null, null) }.getOrNull()
            if (c == null) { partial = true; continue }
            c.use {
                while (it.moveToNext()) {
                    if (it.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders++
                        pending.add(it.getString(0))
                    } else {
                        files++
                        if (!it.isNull(2)) bytes += it.getLong(2)
                    }
                    if (++seen >= MAX_ENTRIES) { partial = true; pending.clear(); break }
                    if (seen % PROGRESS_EVERY == 0) progress(SizeTotal(bytes, files, folders, partial))
                }
            }
        }
        return SizeTotal(bytes, files, folders, partial)
    }
}
