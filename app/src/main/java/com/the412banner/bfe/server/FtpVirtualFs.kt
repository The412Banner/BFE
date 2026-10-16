package com.the412banner.bfe.server

import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * FTP's view of the shared folders, backed by [VirtualFs] — the same namespace and the same escape
 * checks as HTTP. Pure JVM.
 *
 * FTP clients legitimately send relative paths and "..", so paths are first normalised against the
 * working directory (".." clamps at "/", backslashes become slashes); only then are they resolved,
 * so a client can never name anything above a share. Read-only shares answer false to isWritable /
 * isRemovable — which FtpServer checks before STOR, APPE, STOU, MKD, DELE, RMD and RNTO — and the
 * file operations themselves refuse writes again as a second line.
 */
internal class BfeFileSystemFactory(private val host: ServerHost) : FileSystemFactory {
    override fun createFileSystemView(user: User?): FileSystemView = BfeFileSystemView(host)
}

internal class BfeFileSystemView(private val host: ServerHost) : FileSystemView {

    private var cwd = "/"

    override fun getHomeDirectory(): FtpFile = fileAt("/")
    override fun getWorkingDirectory(): FtpFile = fileAt(cwd)

    override fun changeWorkingDirectory(dir: String?): Boolean {
        val p = normalize(dir ?: "/")
        if (!fileAt(p).isDirectory) return false
        cwd = p
        return true
    }

    override fun getFile(file: String?): FtpFile = fileAt(normalize(file ?: ""))
    override fun isRandomAccessible(): Boolean = true
    override fun dispose() {}

    /** Absolute, clean virtual path for [input] relative to the working directory. */
    fun normalize(input: String): String {
        val s = input.replace('\\', '/')
        val parts = ArrayList<String>()
        if (!s.startsWith("/")) cwd.split('/').filterTo(parts) { it.isNotEmpty() }
        for (seg in s.split('/')) when (seg) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            "~" -> parts.clear()
            else -> parts += seg
        }
        return "/" + parts.joinToString("/")
    }

    private fun fileAt(path: String): FtpFile {
        val fs = host.fs
        return BfeFtpFile(fs, path, fs.resolve(path))
    }
}

internal class BfeFtpFile(
    private val fs: VirtualFs,
    private val vpath: String,
    /** Null when the path isn't shared or escapes its share: then nothing exists and nothing is allowed. */
    private val node: VirtualFs.Node?,
) : FtpFile {

    private val real: VirtualFs.Real? get() = node as? VirtualFs.Real
    private val top: Boolean get() = node is VirtualFs.Top

    override fun getAbsolutePath(): String = vpath
    override fun getName(): String = if (vpath == "/") "/" else vpath.substringAfterLast('/')
    override fun isHidden(): Boolean = vpath != "/" && getName().startsWith(".")
    override fun isDirectory(): Boolean = top || real?.file?.isDirectory == true
    override fun isFile(): Boolean = real?.file?.isFile == true
    override fun doesExist(): Boolean = top || real?.file?.exists() == true
    override fun isReadable(): Boolean = top || real?.file?.canRead() == true

    override fun isWritable(): Boolean {
        val r = real ?: return false
        if (r.readOnly) return false
        val f = r.file
        return if (f.exists()) f.canWrite() else f.parentFile?.isDirectory == true
    }

    override fun isRemovable(): Boolean {
        val r = real ?: return false
        return !r.readOnly && !r.isMountRoot && r.file.exists()
    }

    override fun getOwnerName(): String = "user"
    override fun getGroupName(): String = "user"
    override fun getLinkCount(): Int = if (isDirectory) 3 else 1

    override fun getLastModified(): Long = when {
        top -> fs.mounts.maxOfOrNull { it.dir.lastModified() } ?: 0L
        else -> real?.file?.lastModified() ?: 0L
    }

    override fun setLastModified(time: Long): Boolean {
        val r = real ?: return false
        return !r.readOnly && r.file.setLastModified(time)
    }

    override fun getSize(): Long = if (isFile) real!!.file.length() else 0L
    override fun getPhysicalFile(): Any? = real?.file

    override fun mkdir(): Boolean {
        val r = real ?: return false
        return !r.readOnly && r.file.mkdir()
    }

    override fun delete(): Boolean {
        if (!isRemovable()) return false
        return real!!.file.delete()
    }

    override fun move(destination: FtpFile?): Boolean {
        val d = destination as? BfeFtpFile ?: return false
        val from = real ?: return false
        val to = d.real ?: return false
        if (!isRemovable() || !d.isWritable()) return false
        return from.file.renameTo(to.file)
    }

    override fun listFiles(): List<FtpFile> {
        val n = node ?: return emptyList()
        return fs.list(n).orEmpty().map { BfeFtpFile(fs, it.node.path, it.node) }
    }

    override fun createOutputStream(offset: Long): OutputStream {
        val r = real ?: throw IOException("No such file")
        if (r.readOnly) throw IOException("This folder is read-only")
        if (r.isMountRoot || r.file.isDirectory) throw IOException("Not a file")
        val raf = RandomAccessFile(r.file, "rw")
        try {
            raf.setLength(offset)
            raf.seek(offset)
        } catch (e: IOException) {
            raf.close()
            throw e
        }
        return object : FileOutputStream(raf.fd) {
            override fun close() {
                try { super.close() } finally { raf.close() }
            }
        }
    }

    override fun createInputStream(offset: Long): InputStream {
        val f = real?.file ?: throw IOException("No such file")
        if (!f.isFile) throw IOException("Not a file")
        val raf = RandomAccessFile(f, "r")
        try {
            raf.seek(offset)
        } catch (e: IOException) {
            raf.close()
            throw e
        }
        return object : FileInputStream(raf.fd) {
            override fun close() {
                try { super.close() } finally { raf.close() }
            }
        }
    }

    override fun equals(other: Any?): Boolean = other is BfeFtpFile && other.vpath == vpath
    override fun hashCode(): Int = vpath.hashCode()
    override fun toString(): String = vpath
}
