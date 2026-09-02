package com.the412banner.bfe.pack

import android.system.Os
import android.util.Log
import com.github.luben.zstd.ZstdOutputStreamNoFinalizer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

/**
 * tar + zstd in Java — `.tzst` and the Winlator `.wcp` (which is exactly a tar.zst with `profile.json`
 * at the archive root). Needed because the bundled 7-Zip 24.08 DECODES zstd but cannot encode it
 * (`7zz a -tzstd` → E_NOTIMPL), so this is the one archive path that doesn't go through `7zz`.
 *
 * Fully streamed: each file is copied straight from disk into the tar → zstd → output chain in
 * 256 KB chunks, never buffered whole, so an 80 GB game folder needs no extra space. Symlinks are
 * stored as links (as `tar` does, and as Bannerlator's own `TarCompressorUtils.compress` does);
 * POSIX long-name / big-number extensions cover >100-char paths and >8 GB files. Cancellation is a
 * flag checked between chunks; the caller deletes the partial output.
 */
object TarZstPack {
    private const val TAG = "TarZstPack"
    private const val CHUNK = 256 * 1024

    /** Cancel token — the service flips it; the copy loop bails at the next chunk boundary. */
    class Job {
        @Volatile var cancelled = false
    }

    class Cancelled : IOException("cancelled")

    /** One thing to put in the tar: [file] on disk stored under archive path [entryName]. */
    data class Input(val file: File, val entryName: String)

    interface Listener {
        /** Cumulative input bytes read so far, plus the entry currently being written. */
        fun onProgress(bytesDone: Long, currentEntry: String?)
        fun onFile(name: String)
    }

    /**
     * Expands the user's selection into archive entries.
     *
     * - tzst: every selected item keeps its name (a folder becomes `folder/…`) — same layout 7zz gives.
     * - wcp: a SINGLE selected folder contributes its CONTENTS at the archive root (the component
     *   files sit beside profile.json, as the installer expects); anything else is stored by name.
     */
    fun planInputs(selection: List<File>, wcpContents: Boolean): List<Input> {
        if (wcpContents && selection.size == 1 && selection.first().isDirectory) {
            return (selection.first().listFiles()?.toList() ?: emptyList())
                .sortedBy { it.name.lowercase() }
                .map { Input(it, it.name) }
        }
        return selection.map { Input(it, it.name) }
    }

    /** Every regular file under [inputs], archive-relative — feeds [WcpProfile.deriveFiles]. */
    fun listRelativeFiles(inputs: List<Input>): List<String> {
        val out = ArrayList<String>()
        fun walk(f: File, name: String) {
            if (Files.isSymbolicLink(f.toPath())) return
            if (f.isDirectory) f.listFiles()?.forEach { walk(it, "$name/${it.name}") }
            else if (f.isFile) out.add(name)
        }
        for (i in inputs) walk(i.file, i.entryName)
        return out
    }

    /** Total regular-file bytes (the progress denominator) and file count under [inputs]. */
    fun measure(inputs: List<Input>): Pair<Long, Int> {
        var bytes = 0L
        var count = 0
        fun walk(f: File) {
            if (Files.isSymbolicLink(f.toPath())) { count++; return }
            if (f.isDirectory) f.listFiles()?.forEach { walk(it) }
            else if (f.isFile) { bytes += f.length(); count++ }
        }
        inputs.forEach { walk(it.file) }
        return bytes to count
    }

    /**
     * Writes [inputs] (plus, for a .wcp, [profileJson] as the root `profile.json`) to [out] as
     * tar.zst at zstd [level] using [workers] compression threads.
     *
     * @return null on success, else a short error message. A cancel returns "cancelled".
     */
    fun create(
        out: File,
        inputs: List<Input>,
        level: Int,
        workers: Int,
        profileJson: String?,
        listener: Listener,
        job: Job,
    ): String? {
        out.parentFile?.mkdirs()
        try {
            FileOutputStream(out).use { fos ->
                val zstd = ZstdOutputStreamNoFinalizer(BufferedOutputStream(fos, CHUNK), level)
                // Multithreaded zstd is a big win on a phone at the higher levels; older builds without
                // ZSTD_MULTITHREAD reject it, and single-threaded is still correct.
                runCatching { zstd.setWorkers(workers.coerceAtLeast(1)) }
                    .onFailure { Log.w(TAG, "zstd workers unsupported, single-threaded: ${it.message}") }
                TarArchiveOutputStream(zstd).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                    if (profileJson != null) {
                        val bytes = profileJson.toByteArray(Charsets.UTF_8)
                        val e = TarArchiveEntry("profile.json")
                        e.size = bytes.size.toLong()
                        e.mode = TarArchiveEntry.DEFAULT_FILE_MODE
                        e.modTime = java.util.Date()
                        tar.putArchiveEntry(e)
                        tar.write(bytes)
                        tar.closeArchiveEntry()
                        listener.onFile("profile.json")
                    }

                    val w = Writer(tar, listener, job)
                    for (i in inputs) w.addAny(i.file, i.entryName)
                    tar.finish()
                }
            }
            return null
        } catch (c: Cancelled) {
            return "cancelled"
        } catch (t: Throwable) {
            Log.e(TAG, "tar.zst creation failed", t)
            return t.message ?: t.javaClass.simpleName
        }
    }

    /** Recursive entry writer (a class rather than local funs: dir ↔ any is mutually recursive). */
    private class Writer(
        private val tar: TarArchiveOutputStream,
        private val listener: Listener,
        private val job: Job,
    ) {
        private val buf = ByteArray(TarZstPack.CHUNK)
        private var done = 0L

        fun addAny(f: File, name: String) {
            when {
                Files.isSymbolicLink(f.toPath()) -> addLink(f, name)
                f.isDirectory -> addDir(f, name)
                f.isFile -> addFile(f, name)
            }
        }

        private fun addFile(f: File, name: String) {
            if (job.cancelled) throw Cancelled()
            val e = TarArchiveEntry(f, name)
            e.mode = TarZstPack.modeOf(f, TarArchiveEntry.DEFAULT_FILE_MODE)
            tar.putArchiveEntry(e)
            BufferedInputStream(FileInputStream(f), TarZstPack.CHUNK).use { i ->
                var remaining = e.size
                while (remaining > 0) {
                    if (job.cancelled) throw Cancelled()
                    val n = i.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    tar.write(buf, 0, n)
                    remaining -= n
                    done += n
                    listener.onProgress(done, name)
                }
                // A file that shrank while we read it: pad so the tar stays consistent.
                while (remaining > 0) {
                    val n = minOf(buf.size.toLong(), remaining).toInt()
                    java.util.Arrays.fill(buf, 0, n, 0)
                    tar.write(buf, 0, n)
                    remaining -= n
                }
            }
            tar.closeArchiveEntry()
            listener.onFile(name)
        }

        private fun addLink(f: File, name: String) {
            val target = runCatching { Os.readlink(f.absolutePath) }.getOrNull() ?: return
            val e = TarArchiveEntry(name, TarConstants.LF_SYMLINK)
            e.linkName = target
            e.modTime = java.util.Date(f.lastModified())
            tar.putArchiveEntry(e)
            tar.closeArchiveEntry()
            listener.onFile(name)
        }

        private fun addDir(d: File, name: String) {
            if (job.cancelled) throw Cancelled()
            val e = TarArchiveEntry(d, "$name/")
            e.mode = TarZstPack.modeOf(d, TarArchiveEntry.DEFAULT_DIR_MODE)
            tar.putArchiveEntry(e)
            tar.closeArchiveEntry()
            listener.onProgress(done, name)
            val children = d.listFiles()?.sortedBy { it.name.lowercase() } ?: return
            for (c in children) addAny(c, "$name/${c.name}")
        }
    }

    /** The file's real permission bits (so a `bin/wine` stays executable for extractors that honour modes). */
    private fun modeOf(f: File, fallback: Int): Int =
        runCatching { Os.stat(f.absolutePath).st_mode and 0b111_111_111_111 }.getOrDefault(fallback)
}
