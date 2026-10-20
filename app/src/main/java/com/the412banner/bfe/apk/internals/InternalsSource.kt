package com.the412banner.bfe.apk.internals

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * What an APK-internals tool is looking at: a file on its own ([entry] null), or one entry inside an
 * APK / zip / jar ([file] is the container, [entry] the path inside it). Reads go straight to the zip,
 * so nothing is extracted first.
 */
class InternalsSource(val file: File, val entry: String?) {
    val inContainer: Boolean get() = entry != null

    /** The name shown in the title bar: the entry's own name, else the file's. */
    val name: String get() = entry?.substringAfterLast('/') ?: file.name

    fun readBytes(): ByteArray = if (entry == null) file.readBytes() else ApkEntries.read(file, entry)
}

/** Small helpers over an APK's zip entries (java.util.zip: random access, no extraction). */
object ApkEntries {
    /** Everything in [apk], in zip order. */
    fun list(apk: File): List<String> = ZipFile(apk).use { z -> z.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList() }

    /** The bytes of [name] inside [apk]. */
    fun read(apk: File, name: String): ByteArray = ZipFile(apk).use { z ->
        val e = z.getEntry(name) ?: throw IOException("$name isn't in ${apk.name}")
        z.getInputStream(e).use { it.readBytes() }
    }

    fun has(apk: File, name: String): Boolean = runCatching { ZipFile(apk).use { it.getEntry(name) != null } }.getOrDefault(false)

    /** classes.dex, classes2.dex, … in the order Android loads them. */
    fun dexEntries(apk: File): List<String> = list(apk).filter { DEX_NAME.matches(it) }.sortedBy { dexIndex(it) }

    /** True for a zip (APK, jar, AAR): the "PK" local-header magic. */
    fun isZip(file: File): Boolean = runCatching {
        file.inputStream().use { val b = ByteArray(4); it.read(b) == 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2].toInt() == 3 && b[3].toInt() == 4 }
    }.getOrDefault(false)

    private val DEX_NAME = Regex("^classes(\\d*)\\.dex$")

    private fun dexIndex(name: String): Int = DEX_NAME.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 1
}

/** Which APK-internals tool an entry (or file) opens in, by its name; null when none fits. */
enum class InternalsTool { AXML, ARSC, DEX, JAVA, HUB;

    companion object {
        /**
         * The tool for the entry at [path] inside an APK / zip: AndroidManifest.xml and compiled
         * res XML, resources.arsc, classes*.dex, and .class files.
         */
        fun forEntry(path: String): InternalsTool? {
            val p = path.trimStart('/')
            val lower = p.lowercase()
            return when {
                p == "AndroidManifest.xml" || p.endsWith("/AndroidManifest.xml") -> AXML
                lower.startsWith("res/") && lower.endsWith(".xml") -> AXML
                p == "resources.arsc" || lower.endsWith(".arsc") -> ARSC
                lower.endsWith(".dex") -> DEX
                lower.endsWith(".class") -> JAVA
                else -> null
            }
        }
    }
}
