package com.the412banner.bfe.storage

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Root acquisition. Root is requested LAZILY on the first root action (Magisk then prompts the user
 * to grant BFE). The app keeps working fully without root — callers check [ensure] and degrade.
 */
object RootAccess {
    @Volatile private var configured = false
    /** null = not asked yet; true/false = last answer. */
    @Volatile var available: Boolean? = null
        private set

    /** Blocking — call off the main thread. Prompts Magisk the first time. */
    fun ensure(): Boolean {
        if (!configured) {
            runCatching {
                Shell.setDefaultBuilder(
                    Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).setTimeout(20),
                )
            }
            configured = true
        }
        val ok = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        available = ok
        return ok
    }
}

/**
 * Root-backed filesystem backend (libsu). Lists a directory with ONE `find … -exec stat {} +` batch
 * (no per-file shell exec), uses [SuFile] for exists/mkdir/rename/delete, and libsu `io` streams so
 * [StorageTransfer]'s cross-backend stream copy works into and out of root locations.
 *
 * OWNERSHIP SAFETY: anything written INTO an app sandbox (`/data/data/<pkg>` or `/data/user/N/<pkg>`)
 * as root is chown'd to that app's uid:gid (resolved via PackageManager), given sane perms, and
 * `restorecon`'d — root-owned leftovers inside an app's data dir would break that app (EACCES).
 * Reads/copies OUT need nothing.
 */
object RootBackend : StorageBackend {
    private const val TAG = "RootBackend"

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** `stat -L -c '%F|%s|%Y|%n'` line → RootLoc (type|size|mtime|path). */
    private fun parseStat(line: String): Loc.RootLoc? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        val path = parts[3]
        val isDir = parts[0].trim() == "directory"
        val size = parts[1].trim().toLongOrNull() ?: 0L
        val mtime = (parts[2].trim().toLongOrNull() ?: 0L) * 1000L
        return Loc.RootLoc(path, path.substringAfterLast('/').ifBlank { path }, isDir, size, mtime)
    }

    /** Stat a single path (one exec), or null if it doesn't exist. */
    fun stat(path: String): Loc.RootLoc? {
        val r = Shell.cmd("stat -L -c '%F|%s|%Y|%n' ${q(path)}").exec()
        return if (r.isSuccess) r.out.firstOrNull()?.let(::parseStat) else null
    }

    override fun listChildren(context: Context, dir: Loc): List<Loc> {
        val p = (dir as Loc.RootLoc).path
        val r = Shell.cmd("find ${q(p)} -maxdepth 1 -mindepth 1 -exec stat -L -c '%F|%s|%Y|%n' {} +").exec()
        if (!r.isSuccess && r.out.isEmpty()) {
            Log.w(TAG, "listChildren failed for $p: ${r.err.joinToString(" ")}")
        }
        return r.out.mapNotNull(::parseStat)
    }

    override fun openInputStream(context: Context, loc: Loc): InputStream? = try {
        SuFileInputStream.open(SuFile((loc as Loc.RootLoc).path))
    } catch (e: Exception) {
        Log.e(TAG, "openInputStream failed", e); null
    }

    override fun openOutputStream(context: Context, loc: Loc): OutputStream? = try {
        val path = (loc as Loc.RootLoc).path
        val raw = SuFileOutputStream.open(SuFile(path))
        // Chown/chmod/restorecon the written file the moment it's closed.
        object : FilterOutputStream(raw) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() {
                super.close()
                fixOwnership(context, path)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "openOutputStream failed", e); null
    }

    override fun childNamed(context: Context, parent: Loc, name: String): Loc? {
        val path = (parent as Loc.RootLoc).path.trimEnd('/') + "/" + name
        return if (SuFile(path).exists()) stat(path) else null
    }

    override fun createFolder(context: Context, parent: Loc, name: String): Loc? {
        val path = (parent as Loc.RootLoc).path.trimEnd('/') + "/" + name
        val f = SuFile(path)
        if (!(f.isDirectory || f.mkdirs())) return null
        fixOwnership(context, path)
        return stat(path) ?: Loc.RootLoc(path, name, true, 0L, System.currentTimeMillis())
    }

    override fun createFile(context: Context, parent: Loc, name: String, mime: String): Loc? {
        val path = (parent as Loc.RootLoc).path.trimEnd('/') + "/" + name
        // The stream will create/truncate + chown on close; just describe the target.
        return Loc.RootLoc(path, name, false, 0L, System.currentTimeMillis())
    }

    override fun rename(context: Context, loc: Loc, newName: String): Loc? {
        val src = (loc as Loc.RootLoc).path
        val dst = File(src).parent?.let { "$it/$newName" } ?: return null
        if (!SuFile(src).renameTo(SuFile(dst))) return null
        fixOwnership(context, dst)
        return stat(dst) ?: Loc.RootLoc(dst, newName, loc.isDir, loc.size, System.currentTimeMillis())
    }

    override fun delete(context: Context, loc: Loc): Boolean {
        val f = SuFile((loc as Loc.RootLoc).path)
        return if (f.isDirectory) f.deleteRecursive() else f.delete()
    }

    /** The app package that owns [path] when it lies inside an app sandbox, else null. */
    fun sandboxPackageOf(path: String): String? {
        val m = Regex("^/data/(?:data|user(?:_de)?/\\d+)/([^/]+)").find(path) ?: return null
        return m.groupValues[1]
    }

    /**
     * Make a path written as root belong to the sandbox app again: chown -R uid:uid, dirs 0770 /
     * files 0660 (the app owns them, so it can always read+write its own files), then restorecon.
     * No-op outside an app sandbox or when the package can't be resolved.
     */
    fun fixOwnership(context: Context, path: String) {
        val pkg = sandboxPackageOf(path) ?: return
        val uid = runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return
        val p = q(path)
        Shell.cmd(
            "chown -R $uid:$uid $p",
            "find $p -type d -exec chmod 770 {} +",
            "find $p -type f -exec chmod 660 {} +",
            "restorecon -R $p 2>/dev/null || true",
        ).exec()
    }
}
