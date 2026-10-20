package com.the412banner.bfe.ui.hex

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * The file under the hex editor. It is read in 64 KB pages through a positional file channel, so a
 * file of any size (many GB) opens at once and only the pages on screen are kept in memory. Edits are
 * overwrites only — the file never grows or shrinks — kept in a sparse map (offset → new byte) until
 * [save] writes just those bytes back in place. Every edit can be undone until then.
 */
internal class HexDoc private constructor(
    private val reader: FileChannel,
    private val writer: FileChannel?,
    private val closeables: List<Closeable>,
    /** The file's length in bytes. */
    val length: Long,
) : Closeable {

    /** False when the file can only be read (no write access, or a cache copy). */
    val writable: Boolean get() = writer != null

    /** Changed bytes not saved yet: offset → new value. Observed by the screen. */
    val edits = mutableStateMapOf<Long, Byte>()

    /** How many edits can be undone (observed, for the Undo button). */
    var undoDepth by mutableIntStateOf(0)
        private set

    // Each undo entry is (offset, the edit that was there before: null = the file's own byte).
    private val undo = ArrayDeque<Pair<Long, Byte?>>()

    private val pages = object : LinkedHashMap<Long, ByteArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = this.size > MAX_PAGES
    }

    /** The file's own byte at [offset] (ignoring edits), or -1 past the end / when it can't be read. */
    @Synchronized
    fun original(offset: Long): Int {
        if (offset < 0 || offset >= length) return -1
        val page = pages.getOrPut(offset / PAGE) { readPage(offset / PAGE) }
        val i = (offset % PAGE).toInt()
        return if (i < page.size) page[i].toInt() and 0xFF else -1
    }

    /** The byte shown at [offset]: the edit when there is one, else the file's own. -1 past the end. */
    fun byteAt(offset: Long): Int = edits[offset]?.let { it.toInt() and 0xFF } ?: original(offset)

    /** Overwrites the byte at [offset] with [value] (an edit back to the file's own byte drops the edit). */
    fun set(offset: Long, value: Int) {
        if (!writable || offset < 0 || offset >= length) return
        val v = value and 0xFF
        if (byteAt(offset) == v) return
        undo.addLast(offset to edits[offset])
        if (undo.size > MAX_UNDO) undo.removeFirst()
        undoDepth = undo.size
        if (original(offset) == v) edits.remove(offset) else edits[offset] = v.toByte()
    }

    /** Takes back the last edit; returns its offset (to show it), or null when there was nothing to undo. */
    fun undoLast(): Long? {
        val (offset, before) = undo.removeLastOrNull() ?: return null
        undoDepth = undo.size
        if (before == null) edits.remove(offset) else edits[offset] = before
        return offset
    }

    /**
     * Writes the changed bytes into the file in place (runs of neighbouring bytes in one write), then
     * flushes. Blocking: call off the main thread. Returns how many bytes were written. The edit map
     * is cleared by [afterSave] on the main thread.
     */
    fun save(changes: Map<Long, Byte>): Int {
        val w = writer ?: throw IOException("The file is read-only")
        val keys = changes.keys.sorted()
        var i = 0
        while (i < keys.size) {
            var j = i
            while (j + 1 < keys.size && keys[j + 1] == keys[j] + 1 && j - i < 65535) j++
            val buf = ByteBuffer.allocate(j - i + 1)
            for (k in i..j) buf.put(changes.getValue(keys[k]))
            buf.flip()
            var pos = keys[i]
            while (buf.hasRemaining()) pos += w.write(buf, pos)
            i = j + 1
        }
        w.force(false)
        return keys.size
    }

    /** After a successful [save]: the edits are part of the file now. */
    @Synchronized
    fun afterSave() {
        pages.clear()
        edits.clear()
        undo.clear()
        undoDepth = 0
    }

    /**
     * Searches for [pattern] from [from] (inclusive) towards the end ([forward]) or the start, reading
     * 1 MB chunks straight from the file with the unsaved edits laid over them. [ignoreCase] folds ASCII
     * letters. Returns the offset of the match, or -1. Blocking; checks [active] between chunks so a
     * cancelled search stops quickly.
     */
    fun find(pattern: ByteArray, from: Long, forward: Boolean, ignoreCase: Boolean, active: () -> Boolean): Long {
        val m = pattern.size
        if (m == 0 || m > length) return -1
        val pat = if (ignoreCase) ByteArray(m) { fold(pattern[it]) } else pattern
        val overlay = edits.toMap()
        if (forward) {
            var pos = from.coerceAtLeast(0)
            while (pos + m <= length && active()) {
                val chunk = readRange(pos, (CHUNK + m - 1).toLong().coerceAtMost(length - pos).toInt(), overlay, ignoreCase)
                val hit = indexIn(chunk, pat, 0, chunk.size - m, 1)
                if (hit >= 0) return pos + hit
                pos += CHUNK
            }
        } else {
            var end = (from + m).coerceAtMost(length) // exclusive end of the region still to search
            while (end >= m && active()) {
                val start = (end - CHUNK - m + 1).coerceAtLeast(0)
                val chunk = readRange(start, (end - start).toInt(), overlay, ignoreCase)
                val hit = indexIn(chunk, pat, chunk.size - m, 0, -1)
                if (hit >= 0) return start + hit
                if (start == 0L) break
                end = start + m - 1
            }
        }
        return -1
    }

    override fun close() {
        closeables.forEach { runCatching { it.close() } }
    }

    private fun readPage(index: Long): ByteArray {
        val start = index * PAGE
        val len = (length - start).coerceIn(0, PAGE.toLong()).toInt()
        return try { readFully(start, len) } catch (_: IOException) { ByteArray(0) }
    }

    private fun readFully(start: Long, len: Int): ByteArray {
        val buf = ByteBuffer.allocate(len)
        var pos = start
        while (buf.hasRemaining()) {
            val n = reader.read(buf, pos)
            if (n <= 0) break
            pos += n
        }
        return if (buf.position() == len) buf.array() else buf.array().copyOf(buf.position())
    }

    private fun readRange(start: Long, len: Int, overlay: Map<Long, Byte>, ignoreCase: Boolean): ByteArray {
        val a = try { readFully(start, len) } catch (_: IOException) { ByteArray(0) }
        if (overlay.isNotEmpty()) {
            val end = start + a.size
            // Few edits: walk them; many: look up each byte of the chunk instead.
            if (overlay.size < a.size) {
                for ((k, v) in overlay) if (k in start until end) a[(k - start).toInt()] = v
            } else {
                for (i in a.indices) overlay[start + i]?.let { a[i] = it }
            }
        }
        if (ignoreCase) for (i in a.indices) a[i] = fold(a[i])
        return a
    }

    companion object {
        private const val PAGE = 64 * 1024
        private const val MAX_PAGES = 48
        private const val CHUNK = 1024 * 1024
        private const val MAX_UNDO = 10_000

        private fun fold(b: Byte): Byte = b.toInt().let { if (it in 65..90) (it + 32).toByte() else b }

        // First (step 1) or last (step -1) index i between [from] and [to] where [pat] starts in [a].
        private fun indexIn(a: ByteArray, pat: ByteArray, from: Int, to: Int, step: Int): Int {
            if (a.size < pat.size) return -1
            var i = from
            while (if (step > 0) i <= to else i >= to) {
                if (a[i] == pat[0]) {
                    var k = 1
                    while (k < pat.size && a[i + k] == pat[k]) k++
                    if (k == pat.size) return i
                }
                i += step
            }
            return -1
        }

        /**
         * Opens [path] (a real file, preferred) or [uri]. With [allowWrite] the file is opened for
         * writing when the system lets BFE; otherwise, or when that fails, read-only. Blocking.
         */
        fun open(context: Context, path: String?, uri: Uri?, allowWrite: Boolean): HexDoc {
            val f = path?.let { File(it) }?.takeIf { it.isFile && it.canRead() }
            if (f != null) {
                val raf = if (allowWrite && f.canWrite()) runCatching { RandomAccessFile(f, "rw") }.getOrNull() else null
                if (raf != null) return HexDoc(raf.channel, raf.channel, listOf(raf), raf.length())
                val ro = RandomAccessFile(f, "r")
                return HexDoc(ro.channel, null, listOf(ro), ro.length())
            }
            requireNotNull(uri) { "The file couldn't be opened." }
            val cr = context.contentResolver
            // "rw" never truncates (that is "rwt"), so a write grant edits the bytes in place.
            val rw = if (allowWrite) runCatching { cr.openFileDescriptor(uri, "rw") }.getOrNull() else null
            val pfd: ParcelFileDescriptor = rw ?: cr.openFileDescriptor(uri, "r") ?: throw IOException("The file couldn't be opened.")
            val size = pfd.statSize
            if (size < 0) { pfd.close(); throw IOException("This file can only be read from start to end, not opened in the hex editor.") }
            val input = FileInputStream(pfd.fileDescriptor)
            val output = if (rw != null) FileOutputStream(pfd.fileDescriptor) else null
            return HexDoc(input.channel, output?.channel, listOfNotNull(input, output, pfd), size)
        }
    }
}
