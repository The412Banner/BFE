package com.the412banner.bfe.server

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * The namespace the network sees, shared by the HTTP and FTP servers. Pure JVM (no android.*).
 *
 * Either ONE folder mounted at "/" (whole storage), or several folders, each appearing as a
 * top-level virtual folder under its (de-duplicated) name. Every client path goes through
 * [segments] + [resolve], and nothing can reach above a share's own folder:
 *  - a ".." segment is refused outright (HTTP clients normalise it away before sending; FTP paths
 *    are normalised against the working directory first, clamped at "/"),
 *  - anything whose canonical path (symlinks resolved) lands outside the share's canonical folder is
 *    treated as not existing, and listings leave such entries out.
 */
class VirtualFs private constructor(val mounts: List<Mount>, val single: Boolean) {

    class Mount internal constructor(val name: String, val dir: File, val readOnly: Boolean) {
        val canonical: String = canonicalOf(dir) ?: dir.absolutePath
        private val prefix = if (canonical.endsWith("/")) canonical else "$canonical/"

        /** True when [f], with every symlink resolved, is this share's folder or lies inside it. */
        fun contains(f: File): Boolean {
            val c = canonicalOf(f) ?: return false
            return c == canonical || c.startsWith(prefix)
        }
    }

    sealed class Node {
        /** Virtual path: "/" or "/a/b" (never a trailing slash). */
        abstract val path: String
    }

    /** The synthetic top level that lists every share (multi-folder mode only). Never writable. */
    object Top : Node() {
        override val path = "/"
    }

    /** A real file or folder (which may not exist yet, e.g. an upload target) inside [mount]. */
    class Real internal constructor(
        val mount: Mount,
        val file: File,
        override val path: String,
        /** This is the shared folder itself: it can be written into but never renamed or deleted. */
        val isMountRoot: Boolean,
    ) : Node() {
        val name: String get() = if (isMountRoot) mount.name else file.name
        val readOnly: Boolean get() = mount.readOnly
    }

    class Entry(val name: String, val isDir: Boolean, val size: Long, val mtime: Long, val node: Real)

    private val byName: Map<String, Mount> = mounts.associateBy { it.name }

    /** Clean path segments of a client path, or null when it has a ".." (or NUL) segment. */
    fun segments(path: String): List<String>? {
        val out = ArrayList<String>()
        for (s in path.split('/')) {
            if (s.isEmpty() || s == ".") continue
            if (s == ".." || s.indexOf('\u0000') >= 0) return null
            out += s
        }
        return out
    }

    fun resolve(path: String): Node? = segments(path)?.let { resolve(it) }

    /** Maps clean [segs] to a node; null when the name isn't shared or the target escapes its share. */
    fun resolve(segs: List<String>): Node? {
        val mount: Mount
        val rest: List<String>
        if (single) {
            mount = mounts.firstOrNull() ?: return null
            rest = segs
        } else {
            if (segs.isEmpty()) return Top
            mount = byName[segs[0]] ?: return null
            rest = segs.subList(1, segs.size)
        }
        if (rest.any { it == ".." || it.indexOf('\u0000') >= 0 }) return null
        val file = if (rest.isEmpty()) mount.dir else File(mount.dir, rest.joinToString("/"))
        if (rest.isNotEmpty() && !mount.contains(file)) return null
        val vpath = if (segs.isEmpty()) "/" else "/" + segs.joinToString("/")
        return Real(mount, file, vpath, rest.isEmpty())
    }

    /** True when [node] is a folder (the top level, or a real directory). */
    fun isDir(node: Node): Boolean = node is Top || (node is Real && node.file.isDirectory)

    /** The children of a folder node, or null when it can't be listed. */
    fun list(node: Node): List<Entry>? = when (node) {
        is Top -> mounts.map { m ->
            Entry(m.name, true, 0L, m.dir.lastModified(), Real(m, m.dir, "/" + m.name, true))
        }
        is Real -> node.file.listFiles()?.mapNotNull { entryOf(node, it) }
    }

    /** The node for [name] inside folder [parent] (no lookup through the client path parser). */
    fun child(parent: Node, name: String): Real? {
        if (!isValidName(name)) return null
        return when (parent) {
            is Top -> byName[name]?.let { Real(it, it.dir, "/$name", true) }
            is Real -> {
                val f = File(parent.file, name)
                if (!parent.mount.contains(f)) null else Real(parent.mount, f, childPath(parent.path, name), false)
            }
        }
    }

    private fun entryOf(parent: Real, f: File): Entry? {
        val a = lstat(f) ?: return null
        var isDir = a.isDirectory
        var size = a.size()
        var mtime = a.lastModifiedTime().toMillis()
        if (a.isSymbolicLink) {
            // Links are followed only while they stay inside the share; otherwise they're invisible.
            if (!parent.mount.contains(f)) return null
            val t = stat(f) ?: return null
            isDir = t.isDirectory
            size = t.size()
            mtime = t.lastModifiedTime().toMillis()
        }
        return Entry(f.name, isDir, if (isDir) 0L else size, mtime, Real(parent.mount, f, childPath(parent.path, f.name), false))
    }

    companion object {
        /**
         * Builds the namespace. With [single] the first root is mounted at "/"; otherwise every root
         * becomes a top-level folder, and clashing names get " (2)", " (3)"... (compared without case,
         * since Windows and macOS clients treat names case-insensitively).
         */
        fun of(roots: List<ShareRoot>, single: Boolean): VirtualFs {
            if (single) {
                val r = roots.first()
                return VirtualFs(listOf(Mount(cleanName(r.name), r.dir, r.readOnly)), true)
            }
            val used = HashSet<String>()
            val mounts = roots.map { r ->
                val base = cleanName(r.name)
                var name = base
                var i = 2
                while (!used.add(name.lowercase())) name = "$base (${i++})"
                Mount(name, r.dir, r.readOnly)
            }
            return VirtualFs(mounts, false)
        }

        /** A usable single path segment for a new file or folder name from a client. */
        fun isValidName(name: String): Boolean =
            name.isNotEmpty() && name != "." && name != ".." && name.indexOf('/') < 0 &&
                name.indexOf('\u0000') < 0 && name.toByteArray(Charsets.UTF_8).size <= 255

        fun childPath(parent: String, name: String) = if (parent == "/") "/$name" else "$parent/$name"

        private fun cleanName(raw: String): String {
            val s = raw.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim()
            return when (s) { "", ".", ".." -> "Folder"; else -> s }
        }

        internal fun canonicalOf(f: File): String? = try {
            f.canonicalPath
        } catch (e: IOException) {
            null
        }

        internal fun lstat(f: File): BasicFileAttributes? = try {
            Files.readAttributes(f.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: Exception) {
            null
        }

        internal fun stat(f: File): BasicFileAttributes? = try {
            Files.readAttributes(f.toPath(), BasicFileAttributes::class.java)
        } catch (e: Exception) {
            null
        }

        /** Deletes [f] and, for a real folder, everything in it. Symlinks are removed, never followed. */
        fun deleteTree(f: File): Boolean {
            val a = lstat(f) ?: return !f.exists()
            if (a.isDirectory && !a.isSymbolicLink) {
                f.listFiles()?.forEach { deleteTree(it) }
            }
            return f.delete() || !f.exists()
        }
    }
}
