package com.the412banner.bfe.docs

import java.io.File
import java.util.zip.ZipException

/** Turns a document file into pages of HTML, whichever format it is. */
object DocConverter {
    /** Biggest RTF / Markdown / CSV read whole. */
    private const val MAX_TEXT = 64L * 1024 * 1024

    /** Converts [file] as [kind] (named [name]). Blocking; run it off the main thread. */
    fun convert(file: File, kind: DocKind, name: String, assets: DocAssets): DocResult = try {
        when (kind) {
            DocKind.ODT, DocKind.ODS, DocKind.ODP -> OdfConverter.convert(file, kind, name, assets)
            DocKind.DOCX -> DocxConverter.convert(file, name, assets)
            DocKind.XLSX -> XlsxConverter.convert(file, name)
            DocKind.PPTX -> PptxConverter.convert(file, name, assets)
            DocKind.EPUB -> EpubConverter.convert(file, name, assets)
            DocKind.RTF -> RtfConverter.convert(file.inputStream().use { readCapped(it, MAX_TEXT) }, name, assets)
            DocKind.MARKDOWN -> MarkdownConverter.convert(text(file), name, file.parentFile)
            DocKind.CSV, DocKind.TSV -> table(file, kind, name)
            DocKind.PDF, DocKind.TEXT, DocKind.LEGACY_OFFICE -> throw DocException("${kind.label} isn't converted to a page.")
        }
    } catch (e: DocException) {
        throw e
    } catch (e: ZipException) {
        throw DocException("The file is damaged or isn't really a ${kind.label} file.", e)
    } catch (e: OutOfMemoryError) {
        throw DocException("This document is too big for BFE's viewer.")
    } catch (e: org.xml.sax.SAXException) {
        throw DocException("The document is damaged (its XML doesn't read).", e)
    } catch (e: Exception) {
        throw DocException("The document couldn't be read: ${e.message ?: e.javaClass.simpleName}", e)
    }

    private fun text(file: File): String {
        if (file.length() > MAX_TEXT) throw DocException("This file is too big to render; open it as text instead.")
        return TextDoc.open(file).fullText()
    }

    private fun table(file: File, kind: DocKind, name: String): DocResult {
        val text = text(file)
        val delimiter = if (kind == DocKind.TSV) '\t' else Csv.detectDelimiter(text.take(64 * 1024))
        val t = Csv.parse(text, delimiter)
        val notes = if (t.truncated) listOf("Only the first ${t.rows.size} rows are shown as a table. Open as text to see them all.") else emptyList()
        return DocResult(kind, name, listOf(DocPage(name, Csv.toHtml(t))), notes = notes)
    }
}
