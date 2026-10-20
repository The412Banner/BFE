package com.the412banner.bfe.apk.internals

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry

/**
 * Writes a copy of an APK with some entries replaced — the edited AndroidManifest.xml, resources.arsc,
 * res XML or dex. Every other entry is copied as its raw compressed bytes (nothing is recompressed, so
 * a 100 MB APK copies at disk speed). A replaced entry keeps the original's compression method:
 * resources.arsc and anything else that was stored stays stored, which Android 11+ requires of
 * resources.arsc. Old signature files are dropped; the APK is signed afresh afterwards (ApkSigning
 * also aligns it).
 */
object ApkPatch {

    /** [replacements]: entry name → file with its new bytes. [isCancelled] is polled between entries. */
    fun write(input: File, replacements: Map<String, File>, out: File, isCancelled: () -> Boolean = { false }) {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()
        val pending = replacements.toMutableMap()
        ZipFile(input).use { zip ->
            ZipArchiveOutputStream(out).use { zos ->
                val entries = zip.entriesInPhysicalOrder
                while (entries.hasMoreElements()) {
                    if (isCancelled()) throw IOException("cancelled")
                    val e = entries.nextElement()
                    if (e.isDirectory || isOldSignature(e.name)) continue
                    val repl = pending.remove(e.name)
                    if (repl == null) {
                        zip.getRawInputStream(e).use { raw -> zos.addRawArchiveEntry(ZipArchiveEntry(e), raw) }
                    } else {
                        putFile(zos, e.name, repl, stored = e.method == ZipEntry.STORED, time = e.time)
                    }
                }
                // Entries that weren't in the APK yet (a new dex, say) go in at the end.
                pending.forEach { (name, file) -> putFile(zos, name, file, stored = name == "resources.arsc", time = DOS_EPOCH) }
            }
        }
    }

    private fun putFile(zos: ZipArchiveOutputStream, name: String, file: File, stored: Boolean, time: Long) {
        val entry = ZipArchiveEntry(name)
        if (stored) {
            val crc = CRC32()
            file.inputStream().use { i -> val buf = ByteArray(256 * 1024); while (true) { val n = i.read(buf); if (n < 0) break; crc.update(buf, 0, n) } }
            entry.method = ZipEntry.STORED
            entry.size = file.length()
            entry.compressedSize = file.length()
            entry.crc = crc.value
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        entry.time = if (time > 0) time else DOS_EPOCH
        zos.putArchiveEntry(entry)
        file.inputStream().use { it.copyTo(zos, 256 * 1024) }
        zos.closeArchiveEntry()
    }

    // 1980-01-01, the earliest time a zip entry can hold.
    private const val DOS_EPOCH = 315532800000L

    // META-INF/*.SF, *.RSA, *.DSA, *.EC and the MANIFEST.MF that lists the old digests.
    private fun isOldSignature(name: String): Boolean {
        if (!name.startsWith("META-INF/") || name.count { it == '/' } != 1) return false
        val n = name.substringAfter('/').uppercase()
        return n == "MANIFEST.MF" || n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")
    }
}
