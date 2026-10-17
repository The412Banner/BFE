package com.the412banner.bfe.docs

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * One screen of a converted document: a spreadsheet's sheet, an e-book's chapter, or the whole text.
 * [css] is extra (already filtered) style the page brings, e.g. an e-book's own layout rules.
 */
data class DocPage(val title: String, val body: String, val css: String = "")

/** A heading in an e-book's table of contents, pointing at a [DocPage] (and an anchor in it). */
data class TocEntry(val title: String, val page: Int, val anchor: String?, val depth: Int)

/**
 * A document converted to HTML: one or more [pages] of body markup (wrapped for display by
 * [ReadingStyle.wrap]), any pictures it uses already written through the [DocAssets] it was given,
 * and [notes] about what couldn't be shown (shown under the title, never instead of the text).
 */
data class DocResult(
    val kind: DocKind,
    val title: String?,
    val pages: List<DocPage>,
    val toc: List<TocEntry> = emptyList(),
    val notes: List<String> = emptyList(),
)

/**
 * Where a converter puts the pictures it pulls out of a document. [put] stores the bytes under a name
 * derived from [name] and answers the URL the page should use, or null when the picture is in a format
 * a WebView can't draw (EMF / WMF / TIFF…) — the converter then shows a placeholder.
 */
interface DocAssets {
    fun put(name: String, bytes: ByteArray): String?
}

/** Formats a WebView shows in an `<img>`. */
internal fun isWebImage(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "jpe", "gif", "bmp", "webp", "svg", "ico", "avif")

/** Pictures saved as files next to the pages (`res/<name>`), in [dir]. */
class DirAssets(private val dir: File) : DocAssets {
    private val used = HashSet<String>()

    override fun put(name: String, bytes: ByteArray): String? {
        val base = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "image" }
        if (!isWebImage(base)) return null
        var n = base
        var i = 1
        while (!used.add(n)) n = "${base.substringBeforeLast('.')}-${i++}.${base.substringAfterLast('.')}"
        val f = File(dir, "res/$n")
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        return "res/$n"
    }
}

/** Keeps pictures in memory (tests; nothing is written). */
class MemoryAssets : DocAssets {
    val files = LinkedHashMap<String, ByteArray>()

    override fun put(name: String, bytes: ByteArray): String? {
        val base = name.substringAfterLast('/')
        if (!isWebImage(base)) return null
        var n = base
        var i = 1
        while (files.containsKey(n)) n = "${i++}-$base"
        files[n] = bytes
        return "res/$n"
    }
}

/**
 * A zip-based document (OpenDocument, Office Open XML, EPUB) opened for reading, with size caps so a
 * zip bomb or a 2 GB "document" fails cleanly instead of exhausting memory.
 */
class DocZip(file: File) : AutoCloseable {
    private val zip = try { ZipFile(file) } catch (e: Exception) {
        throw DocException("The file is damaged or isn't really this kind of document.", e)
    }

    /** Entry names, as stored. */
    val names: List<String> by lazy { zip.entries().asSequence().map { it.name }.toList() }

    fun has(name: String): Boolean = entry(name) != null

    /** [name] exactly, or case-insensitively (some tools write `Word/Document.xml`). */
    fun entry(name: String): ZipEntry? {
        val n = name.removePrefix("/")
        return zip.getEntry(n) ?: names.firstOrNull { it.equals(n, ignoreCase = true) }?.let { zip.getEntry(it) }
    }

    fun bytes(name: String, limit: Long = MAX_PART): ByteArray? {
        val e = entry(name) ?: return null
        return zip.getInputStream(e).use { readCapped(it, limit) }
    }

    fun text(name: String): String? = bytes(name)?.toString(Charsets.UTF_8)

    fun xml(name: String, keep: ((XEl, String, String) -> Boolean)? = null): XEl? {
        val e = entry(name) ?: return null
        return try {
            zip.getInputStream(e).use { Xml.parse(CappedStream(it, MAX_PART), keep) }
        } catch (x: DocException) {
            throw x
        } catch (x: Exception) {
            throw DocException("Part of the document ($name) is damaged.", x)
        }
    }

    fun <T> stream(name: String, read: (InputStream) -> T): T? {
        val e = entry(name) ?: return null
        return zip.getInputStream(e).use { read(CappedStream(it, MAX_PART)) }
    }

    override fun close() { runCatching { zip.close() } }

    companion object {
        /** Biggest single part read (XML or picture), uncompressed. */
        const val MAX_PART = 96L * 1024 * 1024
    }
}

internal fun readCapped(input: InputStream, limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > limit) throw DocException("Part of the document is too big to show.")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** Stops reading (with a [DocException]) once more than [limit] bytes came through. */
internal class CappedStream(private val inner: InputStream, private val limit: Long) : InputStream() {
    private var total = 0L
    private fun count(n: Int): Int {
        if (n > 0) { total += n; if (total > limit) throw DocException("Part of the document is too big to show.") }
        return n
    }
    override fun read(): Int = inner.read().also { if (it >= 0) count(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = count(inner.read(b, off, len))
    override fun close() = inner.close()
}

/** Resolves [target] (an OOXML / EPUB relative reference) against the folder of [from], zip-style. */
internal fun resolveZipPath(from: String, target: String): String {
    val t = target.substringBefore('#').let { runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it) }
    if (t.startsWith("/")) return normalizeZipPath(t.removePrefix("/"))
    val dir = from.substringBeforeLast('/', "")
    return normalizeZipPath(if (dir.isEmpty()) t else "$dir/$t")
}

internal fun normalizeZipPath(p: String): String {
    val out = ArrayList<String>()
    for (seg in p.split('/')) {
        when (seg) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            else -> out.add(seg)
        }
    }
    return out.joinToString("/")
}
