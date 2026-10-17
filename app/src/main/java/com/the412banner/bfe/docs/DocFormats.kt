package com.the412banner.bfe.docs

import java.io.File
import java.util.zip.ZipFile

/** What BFE's document viewer makes of a file. */
enum class DocKind(val label: String) {
    PDF("PDF"),
    ODT("OpenDocument text"),
    ODS("OpenDocument spreadsheet"),
    ODP("OpenDocument presentation"),
    DOCX("Word document"),
    XLSX("Excel workbook"),
    PPTX("PowerPoint presentation"),
    RTF("Rich text"),
    MARKDOWN("Markdown"),
    CSV("CSV table"),
    TSV("TSV table"),
    EPUB("EPUB book"),
    TEXT("Text"),
    /** Binary .doc / .xls / .ppt: recognised, not rendered. */
    LEGACY_OFFICE("Older Office format");

    /** Shown as text (the text viewer) rather than converted to a page. */
    val isText: Boolean get() = this == TEXT

    /** Has a meaningful "Open as text" / source view. */
    val hasSource: Boolean get() = this == MARKDOWN || this == CSV || this == TSV || this == RTF
}

/** Which files the document viewer opens, by name, MIME type or (last resort) content. */
object DocFormats {
    /** Plain-text files the text viewer opens: logs, configs, data and source code. */
    val TEXT_EXTS: Set<String> = setOf(
        "txt", "text", "log", "out", "ini", "cfg", "conf", "config", "inf", "env", "properties", "prop", "toml",
        "json", "jsonc", "json5", "xml", "plist", "yml", "yaml", "nfo", "diz", "cue", "lst", "m3u", "m3u8", "srt",
        "vtt", "ass", "ssa", "sh", "bash", "zsh", "bat", "cmd", "ps1", "reg", "lua", "py", "rb", "pl", "php",
        "js", "mjs", "kt", "kts", "java", "gradle", "groovy", "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "cs",
        "go", "rs", "swift", "dart", "sql", "css", "scss", "less", "diff", "patch", "gitignore", "gitattributes",
        "editorconfig", "mk", "cmake", "asm", "s", "glsl", "hlsl", "frag", "vert", "tex", "bib", "rst", "adoc",
        "org", "csproj", "sln", "vdf", "acf", "manifest", "pro", "desc",
    )

    private val BY_EXT: Map<String, DocKind> = buildMap {
        put("pdf", DocKind.PDF)
        listOf("odt", "ott", "fodt").forEach { put(it, DocKind.ODT) }
        listOf("ods", "ots", "fods").forEach { put(it, DocKind.ODS) }
        listOf("odp", "otp", "fodp").forEach { put(it, DocKind.ODP) }
        listOf("docx", "docm", "dotx", "dotm").forEach { put(it, DocKind.DOCX) }
        listOf("xlsx", "xlsm", "xltx", "xltm").forEach { put(it, DocKind.XLSX) }
        listOf("pptx", "pptm", "ppsx", "ppsm", "potx").forEach { put(it, DocKind.PPTX) }
        put("rtf", DocKind.RTF)
        listOf("md", "markdown", "mdown", "mkd", "mkdn").forEach { put(it, DocKind.MARKDOWN) }
        put("csv", DocKind.CSV)
        listOf("tsv", "tab").forEach { put(it, DocKind.TSV) }
        put("epub", DocKind.EPUB)
        listOf("doc", "dot", "xls", "xlt", "ppt", "pps", "pot").forEach { put(it, DocKind.LEGACY_OFFICE) }
        TEXT_EXTS.forEach { put(it, DocKind.TEXT) }
    }

    private val BY_MIME: Map<String, DocKind> = mapOf(
        "application/pdf" to DocKind.PDF,
        "application/vnd.oasis.opendocument.text" to DocKind.ODT,
        "application/vnd.oasis.opendocument.text-template" to DocKind.ODT,
        "application/vnd.oasis.opendocument.text-flat-xml" to DocKind.ODT,
        "application/vnd.oasis.opendocument.spreadsheet" to DocKind.ODS,
        "application/vnd.oasis.opendocument.spreadsheet-template" to DocKind.ODS,
        "application/vnd.oasis.opendocument.presentation" to DocKind.ODP,
        "application/vnd.oasis.opendocument.presentation-template" to DocKind.ODP,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DocKind.DOCX,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.template" to DocKind.DOCX,
        "application/vnd.ms-word.document.macroenabled.12" to DocKind.DOCX,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to DocKind.XLSX,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template" to DocKind.XLSX,
        "application/vnd.ms-excel.sheet.macroenabled.12" to DocKind.XLSX,
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to DocKind.PPTX,
        "application/vnd.openxmlformats-officedocument.presentationml.slideshow" to DocKind.PPTX,
        "application/vnd.ms-powerpoint.presentation.macroenabled.12" to DocKind.PPTX,
        "application/rtf" to DocKind.RTF,
        "text/rtf" to DocKind.RTF,
        "text/markdown" to DocKind.MARKDOWN,
        "text/x-markdown" to DocKind.MARKDOWN,
        "text/csv" to DocKind.CSV,
        "text/comma-separated-values" to DocKind.CSV,
        "text/tab-separated-values" to DocKind.TSV,
        "application/epub+zip" to DocKind.EPUB,
        "application/msword" to DocKind.LEGACY_OFFICE,
        "application/vnd.ms-excel" to DocKind.LEGACY_OFFICE,
        "application/vnd.ms-powerpoint" to DocKind.LEGACY_OFFICE,
        "application/json" to DocKind.TEXT,
        "application/xml" to DocKind.TEXT,
        "application/x-sh" to DocKind.TEXT,
        "application/javascript" to DocKind.TEXT,
    )

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    /** The kind for [name] by its extension, falling back to [mime]. Null = not a document BFE shows. */
    fun kindOf(name: String, mime: String? = null): DocKind? {
        BY_EXT[ext(name)]?.let { return it }
        val m = mime?.substringBefore(';')?.trim()?.lowercase() ?: return null
        BY_MIME[m]?.let { return it }
        // HTML has its own viewer; other text/* is plain text.
        if (m.startsWith("text/") && m != "text/html") return DocKind.TEXT
        return null
    }

    /** True when the file manager should offer "View document" / "View text" for [name]. */
    fun isViewable(name: String, mime: String? = null): Boolean = kindOf(name, mime) != null

    /**
     * Looks inside [file] when neither its name nor its MIME type says what it is (a file shared
     * without an extension): PDF and RTF signatures, the zip packages' marker entries, then text.
     */
    fun sniff(file: File): DocKind? {
        val head = runCatching { file.inputStream().use { s -> ByteArray(4096).let { b -> b.copyOf(s.read(b).coerceAtLeast(0)) } } }.getOrNull()
            ?: return null
        val ascii = String(head, 0, minOf(head.size, 64), Charsets.ISO_8859_1)
        when {
            ascii.startsWith("%PDF") -> return DocKind.PDF
            ascii.startsWith("{\\rtf") -> return DocKind.RTF
            head.size >= 8 && head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() && head[2] == 0x11.toByte() && head[3] == 0xE0.toByte() ->
                return DocKind.LEGACY_OFFICE
            ascii.startsWith("PK") -> return sniffZip(file)
        }
        if (ascii.contains("<office:document")) return DocKind.ODT
        return if (TextDoc.looksLikeText(head)) DocKind.TEXT else null
    }

    private fun sniffZip(file: File): DocKind? = runCatching {
        ZipFile(file).use { z ->
            z.getEntry("mimetype")?.let { e ->
                val m = z.getInputStream(e).use { String(readCapped(it, 256), Charsets.US_ASCII).trim() }
                BY_MIME[m]?.let { return@runCatching it }
            }
            when {
                z.getEntry("word/document.xml") != null -> DocKind.DOCX
                z.getEntry("xl/workbook.xml") != null -> DocKind.XLSX
                z.getEntry("ppt/presentation.xml") != null -> DocKind.PPTX
                z.getEntry("META-INF/container.xml") != null -> DocKind.EPUB
                else -> null
            }
        }
    }.getOrNull()
}
