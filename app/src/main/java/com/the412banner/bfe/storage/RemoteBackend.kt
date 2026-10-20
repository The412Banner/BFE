package com.the412banner.bfe.storage

import android.content.Context
import com.the412banner.bfe.cloud.RcEntry
import com.the412banner.bfe.cloud.RcloneApi
import com.the412banner.bfe.cloud.RcloneDaemon
import com.the412banner.bfe.cloud.awaitJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A remote (SMB share, SFTP / FTP / WebDAV server, cloud account) as a file-manager location, through
 * BFE's rclone engine ([RcloneDaemon]). Every op is one rclone call, so a folder is listed with ONE
 * `operations/list` (names, sizes and dates in one answer, never a call per entry).
 *
 * The quick operations (list, new folder, rename, delete) run here directly. Copies and moves that
 * involve a remote do NOT come through [openInputStream] / [openOutputStream]: the file manager queues
 * them as rclone jobs in [com.the412banner.bfe.cloud.CloudTransferService] (background, retries,
 * progress notification). The streams are the fallback for the few paths that need bytes in-app, e.g.
 * "New file" or a copy between a remote and an app-storage (SAF) or root folder.
 *
 * Unlike the other backends, [listChildren] throws when the remote can't be reached, so the pane can
 * show rclone's reason ("Couldn't reach the server…") rather than an empty folder.
 */
object RemoteBackend : StorageBackend {

    /** The start of a remote in the file manager: [path] is where it opens ("" = the remote's top). */
    fun rootLoc(remote: String, path: String = ""): Loc.RemoteLoc =
        Loc.RemoteLoc(remote, path.trim('/'), if (path.isBlank()) remote else path.trim('/').substringAfterLast('/'), true, 0L, 0L)

    /**
     * The pane stack for [path] opened below [startPath]: the start folder, then one entry per folder
     * down to [path], so the breadcrumb and Back walk up through them. A [path] outside [startPath]
     * starts from the remote's top instead.
     */
    fun stackFor(remote: String, startPath: String, path: String): List<Loc> {
        val start = startPath.trim('/')
        val target = path.trim('/')
        val base = if (start.isNotEmpty() && (target == start || target.startsWith("$start/"))) start else ""
        val out = mutableListOf<Loc>(rootLoc(remote, base))
        var acc = base
        target.removePrefix(base).trim('/').split('/').filter { it.isNotEmpty() }.forEach { seg ->
            acc = RcloneApi.join(acc, seg)
            out += Loc.RemoteLoc(remote, acc, seg, true, 0L, 0L)
        }
        return out
    }

    fun toLoc(remote: String, e: RcEntry): Loc.RemoteLoc =
        Loc.RemoteLoc(remote, e.path, e.name.ifBlank { e.path.substringAfterLast('/') }, e.isDir, e.size.coerceAtLeast(0L), e.modTime)

    private fun api(context: Context): RcloneApi = runBlocking { RcloneDaemon.api(context) }

    private fun r(loc: Loc) = loc as Loc.RemoteLoc

    override fun listChildren(context: Context, dir: Loc): List<Loc> {
        val d = r(dir)
        return api(context).list(d.remote, d.path).map { toLoc(d.remote, it) }
    }

    override fun openInputStream(context: Context, loc: Loc): InputStream? {
        val l = r(loc)
        return api(context).rc.openRead(RcloneApi.fsOf(l.remote), l.path)
    }

    /**
     * rclone's remote control has no "write a stream" call, so the bytes go to a temp file in BFE's
     * cache and are copied up as one file when the stream is closed (close() blocks until it's there).
     */
    override fun openOutputStream(context: Context, loc: Loc): OutputStream? {
        val l = r(loc)
        val tmp = File(context.cacheDir, "remote-write/${System.nanoTime()}/${l.name}").apply { parentFile?.mkdirs() }
        return object : FilterOutputStream(FileOutputStream(tmp)) {
            private var closed = false
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() {
                if (closed) return
                closed = true
                try {
                    super.close()
                    val api = api(context)
                    val job = api.startCopyFile(RcloneApi.LOCAL_FS, RcloneApi.localPath(tmp.absolutePath), RcloneApi.fsOf(l.remote), l.path)
                    runBlocking { api.rc.awaitJob(job) }
                    api.rc.deleteStats("job/$job")
                } catch (e: Exception) {
                    throw e as? IOException ?: IOException(e.message, e)
                } finally {
                    tmp.parentFile?.deleteRecursively()
                }
            }
        }
    }

    override fun childNamed(context: Context, parent: Loc, name: String): Loc? {
        val p = r(parent)
        return runCatching { api(context).stat(p.remote, RcloneApi.join(p.path, name)) }.getOrNull()?.let { toLoc(p.remote, it) }
    }

    override fun createFolder(context: Context, parent: Loc, name: String): Loc? = runCatching {
        val p = r(parent)
        val path = RcloneApi.join(p.path, name)
        api(context).mkdir(p.remote, path)
        Loc.RemoteLoc(p.remote, path, name, true, 0L, System.currentTimeMillis())
    }.getOrNull()

    /** Nothing is created yet: the file appears when [openOutputStream]'s stream is closed. */
    override fun createFile(context: Context, parent: Loc, name: String, mime: String): Loc? {
        val p = r(parent)
        return Loc.RemoteLoc(p.remote, RcloneApi.join(p.path, name), name, false, 0L, System.currentTimeMillis())
    }

    /** A file is renamed in one call; a folder is moved as a job (server-side when the backend can). */
    override fun rename(context: Context, loc: Loc, newName: String): Loc? = runCatching {
        val l = r(loc)
        val target = RcloneApi.join(RcloneApi.parentOf(l.path), newName)
        val api = api(context)
        val fs = RcloneApi.fsOf(l.remote)
        if (l.isDir) {
            val job = api.startMoveDir(RcloneApi.fsOf(l.remote, l.path), RcloneApi.fsOf(l.remote, target))
            runBlocking { api.rc.awaitJob(job) }
            api.rc.deleteStats("job/$job")
        } else {
            api.moveFile(fs, l.path, fs, target)
        }
        Loc.RemoteLoc(l.remote, target, newName, l.isDir, l.size, l.lastModified)
    }.getOrNull()

    override fun delete(context: Context, loc: Loc): Boolean = runCatching {
        val l = r(loc)
        if (l.isDir) api(context).purge(l.remote, l.path) else api(context).deleteFile(l.remote, l.path)
    }.isSuccess
}
