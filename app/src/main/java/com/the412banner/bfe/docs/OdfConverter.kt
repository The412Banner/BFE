package com.the412banner.bfe.docs

import java.io.File
import java.util.Base64

/**
 * OpenDocument (.odt / .ods / .odp, their templates, and the flat-XML .fodt / .fods / .fodp) → HTML.
 *
 * Text: headings (text:h, or paragraph styles with an outline level / "Heading N" / Title / Subtitle),
 * bold / italic / underline / strike / super- and subscript, colour, highlight and size from the
 * automatic and common styles (with their parent chains), lists by their list style (bullets, 1 / a /
 * A / i / I numbering, nesting), tables with spanned cells, links, footnotes and endnotes, pictures
 * (Pictures/… or inline base64), text boxes, page breaks, sections and tables of contents.
 * Spreadsheets: one page per sheet, the cells' displayed text (as the app formatted it), spans, with
 * LibreOffice's million repeated empty rows / columns trimmed. Presentations: one card per slide with
 * its title, text, pictures, tables and speaker notes.
 */
class OdfConverter private constructor(
    private val zip: DocZip?,
    private val assets: DocAssets,
) {
    private class TextProps(
        val bold: Boolean? = null, val italic: Boolean? = null, val underline: Boolean? = null, val strike: Boolean? = null,
        val sup: Boolean? = null, val sub: Boolean? = null, val color: String? = null, val bg: String? = null, val sizePt: Float? = null,
        val mono: Boolean? = null,
    )

    private class OStyle(
        val parent: String?, val display: String?, val outline: Int?, val text: TextProps,
        val align: Char?, val breakBefore: Boolean, val breakAfter: Boolean,
    )

    private class ListLevel(val ordered: Boolean, val format: String?, val start: Int?)

    private val styles = HashMap<String, OStyle>()      // "family:name"
    private val listStyles = HashMap<String, Map<Int, ListLevel>>()
    private val notes = ArrayList<String>()
    private var firstHeading: String? = null

    companion object {
        fun convert(file: File, kind: DocKind, fileName: String, assets: DocAssets): DocResult {
            val head = runCatching { file.inputStream().use { s -> ByteArray(2).also { s.read(it) } } }.getOrNull()
            if (head != null && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                return DocZip(file).use { z -> OdfConverter(z, assets).fromZip(z, kind, fileName) }
            }
            // Flat XML (.fodt / .fods / .fodp): one file holds styles, body and base64 pictures.
            val root = try { file.inputStream().use { Xml.parse(CappedStream(it, DocZip.MAX_PART)) } } catch (e: DocException) {
                throw e
            } catch (e: Exception) {
                throw DocException("The document is damaged or isn't an OpenDocument file.", e)
            }
            return OdfConverter(null, assets).fromRoots(root, root, root.child(Ns.OFFICE, "meta"), kind, fileName)
        }

        private val HEADING_STYLE = Regex("(?i)^heading[ _]?(?:_20_)?(\\d)$")

        /** "2.5cm" / "1in" / "12pt" / "40px" → CSS pixels. */
        internal fun lengthPx(v: String?): Float? {
            val m = Regex("^\\s*([0-9.]+)\\s*(cm|mm|in|pt|pc|px)?\\s*$").find(v ?: return null) ?: return null
            val n = m.groupValues[1].toFloatOrNull() ?: return null
            return when (m.groupValues[2]) {
                "cm" -> n * 37.795f; "mm" -> n * 3.7795f; "in" -> n * 96f; "pt" -> n * 4f / 3f; "pc" -> n * 16f
                else -> n
            }
        }
    }

    private fun fromZip(z: DocZip, kind: DocKind, fileName: String): DocResult {
        val content = z.xml("content.xml", keep = ::keepSheetRows) ?: throw DocException("The document has no content (content.xml is missing).")
        val stylesXml = runCatching { z.xml("styles.xml") }.getOrNull()
        val meta = runCatching { z.xml("meta.xml") }.getOrNull()
        return fromRoots(content, stylesXml, meta?.child(Ns.OFFICE, "meta"), kind, fileName)
    }

    /** While parsing a spreadsheet: drop rows past the display cap (each sheet keeps its first ones). */
    private fun keepSheetRows(parent: XEl, ns: String, name: String): Boolean {
        if (ns != Ns.TABLE || name != "table-row") return true
        return parent.children.size < MAX_SHEET_ROWS
    }

    private fun fromRoots(content: XEl, stylesRoot: XEl?, meta: XEl?, kind: DocKind, fileName: String): DocResult {
        stylesRoot?.let { s ->
            s.child(Ns.OFFICE, "styles")?.let(::readStyles)
            s.child(Ns.OFFICE, "automatic-styles")?.let(::readStyles)
        }
        if (content !== stylesRoot) {
            content.child(Ns.OFFICE, "styles")?.let(::readStyles)
            content.child(Ns.OFFICE, "automatic-styles")?.let(::readStyles)
        }
        val bodyEl = content.child(Ns.OFFICE, "body") ?: throw DocException("The document has no body.")
        val metaTitle = meta?.child(Ns.DC, "title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        bodyEl.child(Ns.OFFICE, "spreadsheet")?.let { sheet ->
            val pages = sheet.children(Ns.TABLE, "table").map { t -> DocPage(t.attr(Ns.TABLE, "name") ?: "Sheet", sheetHtml(t)) }.toList()
            if (pages.isEmpty()) throw DocException("The spreadsheet has no sheets.")
            return DocResult(DocKind.ODS, metaTitle ?: fileName, pages)
        }
        bodyEl.child(Ns.OFFICE, "presentation")?.let { pres ->
            val sb = StringBuilder()
            pres.children(Ns.DRAW, "page").forEachIndexed { i, page -> slide(page, i + 1, sb) }
            if (sb.isEmpty()) throw DocException("The presentation has no slides.")
            val title = metaTitle ?: fileName
            return DocResult(DocKind.ODP, title, listOf(DocPage(title, sb.toString())))
        }
        val text = bodyEl.child(Ns.OFFICE, "text") ?: bodyEl.elements.firstOrNull() ?: throw DocException("The document has no text.")
        val sb = StringBuilder()
        blocks(text, sb)
        appendNotes(sb)
        val title = metaTitle ?: firstHeading ?: fileName
        return DocResult(if (kind == DocKind.ODS || kind == DocKind.ODP) DocKind.ODT else kind, title, listOf(DocPage(title, sb.toString())))
    }

    // ── Styles ──

    private fun readStyles(container: XEl) {
        for (e in container.elements) {
            when {
                e.isA(Ns.STYLE, "style") -> {
                    val name = e.attr(Ns.STYLE, "name") ?: continue
                    val family = e.attr(Ns.STYLE, "family") ?: "paragraph"
                    val tp = e.child(Ns.STYLE, "text-properties")
                    val pp = e.child(Ns.STYLE, "paragraph-properties")
                    styles["$family:$name"] = OStyle(
                        parent = e.attr(Ns.STYLE, "parent-style-name"),
                        display = e.attr(Ns.STYLE, "display-name"),
                        outline = e.attr(Ns.STYLE, "default-outline-level")?.toIntOrNull(),
                        text = tp?.let(::textProps) ?: TextProps(),
                        align = when (pp?.attr(Ns.FO, "text-align")) { "center" -> 'c'; "end", "right" -> 'r'; "justify" -> 'j'; "start", "left" -> 'l'; else -> null },
                        breakBefore = pp?.attr(Ns.FO, "break-before") == "page",
                        breakAfter = pp?.attr(Ns.FO, "break-after") == "page",
                    )
                    // A table cell's list style lives on the paragraph; lists inherit through style:list-style-name too.
                }
                e.isA(Ns.TEXT, "list-style") -> {
                    val name = e.attr(Ns.STYLE, "name") ?: continue
                    val levels = HashMap<Int, ListLevel>()
                    for (lvl in e.elements) {
                        val n = lvl.attr(Ns.TEXT, "level")?.toIntOrNull() ?: continue
                        levels[n] = when (lvl.name) {
                            "list-level-style-number" -> ListLevel(
                                ordered = !lvl.attr(Ns.STYLE, "num-format").isNullOrEmpty(),
                                format = lvl.attr(Ns.STYLE, "num-format"),
                                start = lvl.attr(Ns.TEXT, "start-value")?.toIntOrNull(),
                            )
                            else -> ListLevel(false, null, null)
                        }
                    }
                    listStyles[name] = levels
                }
            }
        }
    }

    private fun textProps(tp: XEl): TextProps {
        fun on(v: String?) = v != null && v != "none"
        val weight = tp.attr(Ns.FO, "font-weight")
        val style = tp.attr(Ns.FO, "font-style")
        val pos = tp.attr(Ns.STYLE, "text-position")?.trim()
        val size = tp.attr(Ns.FO, "font-size")?.let { s -> if (s.endsWith("pt")) s.removeSuffix("pt").toFloatOrNull() else null }
        val fontName = (tp.attr(Ns.STYLE, "font-name") ?: tp.attr(Ns.FO, "font-family")).orEmpty()
        return TextProps(
            bold = weight?.let { it == "bold" || (it.toIntOrNull() ?: 400) >= 600 },
            italic = style?.let { it == "italic" || it == "oblique" },
            underline = tp.attr(Ns.STYLE, "text-underline-style")?.let(::on),
            strike = tp.attr(Ns.STYLE, "text-line-through-style")?.let(::on),
            sup = pos?.let { it.startsWith("super") || (it.substringBefore(' ').removeSuffix("%").toFloatOrNull() ?: 0f) > 0f },
            sub = pos?.let { it.startsWith("sub") || (it.substringBefore(' ').removeSuffix("%").toFloatOrNull() ?: 0f) < 0f },
            color = Colors.hex(tp.attr(Ns.FO, "color")),
            bg = tp.attr(Ns.FO, "background-color")?.takeIf { it != "transparent" }?.let(Colors::hex),
            sizePt = size,
            mono = if (fontName.contains("Mono", true) || fontName.contains("Courier", true) || fontName.contains("Source_20_Text") || fontName == "Source Text") true else null,
        )
    }

    private fun style(family: String, name: String?): OStyle? = name?.let { styles["$family:$it"] }

    /** The style chain of [name] (the style first, then its parents). */
    private fun chain(family: String, name: String?): List<OStyle> {
        val out = ArrayList<OStyle>()
        var n = name
        var guard = 0
        while (n != null && guard++ < 20) {
            val s = style(family, n) ?: break
            out.add(s)
            n = s.parent
        }
        return out
    }

    private fun names(family: String, name: String?): List<String> {
        val out = ArrayList<String>()
        var n = name
        var guard = 0
        while (n != null && guard++ < 20) {
            out.add(n)
            n = style(family, n)?.parent
        }
        return out
    }

    /** Merges [chain]'s text properties onto [base], nearest style winning. */
    private fun fmtFrom(base: RunWriter.Fmt, chain: List<OStyle>, includeSize: Boolean): RunWriter.Fmt {
        var f = base
        for (s in chain.asReversed()) {
            val t = s.text
            f = f.copy(
                bold = t.bold ?: f.bold, italic = t.italic ?: f.italic, underline = t.underline ?: f.underline,
                strike = t.strike ?: f.strike, sup = t.sup ?: f.sup, sub = t.sub ?: f.sub,
                color = t.color ?: f.color, bg = t.bg ?: f.bg,
                sizePt = if (includeSize) t.sizePt ?: f.sizePt else f.sizePt,
                code = t.mono ?: f.code,
            )
        }
        return f
    }

    /** Heading level of a paragraph style (outline level, or a "Heading N" name), Title = 0. */
    private fun headingOf(styleName: String?): Int? {
        for (n in names("paragraph", styleName)) {
            val s = style("paragraph", n)
            s?.outline?.let { if (it in 1..10) return it.coerceAtMost(6) }
            val label = s?.display ?: n.replace("_20_", " ")
            HEADING_STYLE.find(label.replace(" ", "_"))?.let { return it.groupValues[1].toInt().coerceIn(1, 6) }
            HEADING_STYLE.find(n)?.let { return it.groupValues[1].toInt().coerceIn(1, 6) }
            if (label.equals("Title", true)) return 0
            if (label.equals("Subtitle", true)) return -1
        }
        return null
    }

    // ── Blocks ──

    private fun blocks(parent: XEl, sb: StringBuilder) {
        for (e in parent.elements) block(e, sb)
    }

    private fun block(e: XEl, sb: StringBuilder) {
        when {
            e.isA(Ns.TEXT, "h") -> heading(e, sb)
            e.isA(Ns.TEXT, "p") -> paragraph(e, sb)
            e.isA(Ns.TEXT, "list") -> list(e, sb, e.attr(Ns.TEXT, "style-name"), 1)
            e.isA(Ns.TABLE, "table") -> table(e, sb)
            e.isA(Ns.TEXT, "section") || e.isA(Ns.TEXT, "index-body") || e.isA(Ns.TEXT, "index-title") ||
                e.name.endsWith("-index") || e.isA(Ns.TEXT, "table-of-content") || e.isA(Ns.TEXT, "bibliography") -> blocks(e, sb)
            e.isA(Ns.DRAW, "frame") -> frame(e, sb, block = true)
            e.isA(Ns.DRAW, "a") -> blocks(e, sb)
            else -> Unit  // sequence / variable declarations, tracked changes, forms, soft page breaks
        }
    }

    private fun heading(e: XEl, sb: StringBuilder) {
        val level = (e.attr(Ns.TEXT, "outline-level")?.toIntOrNull() ?: headingOf(e.attr(Ns.TEXT, "style-name")) ?: 1).coerceIn(1, 6)
        val (html, plain, after) = inline(e)
        val s = style("paragraph", e.attr(Ns.TEXT, "style-name"))
        if (s?.breakBefore == true) sb.append("<hr class=\"pb\">")
        if (plain.isBlank() && !html.contains("<img")) { sb.append(after); return }
        if (firstHeading == null) firstHeading = plain.trim()
        sb.append("<h$level${alignAttr(e)}>").append(html).append("</h$level>").append(after)
        if (s?.breakAfter == true) sb.append("<hr class=\"pb\">")
    }

    private fun alignAttr(e: XEl): String {
        val a = chain("paragraph", e.attr(Ns.TEXT, "style-name")).firstNotNullOfOrNull { it.align }
        return when (a) { 'c' -> " class=\"c\""; 'r' -> " class=\"r\""; 'j' -> " class=\"j\""; else -> "" }
    }

    private fun paragraph(e: XEl, sb: StringBuilder, inList: Boolean = false) {
        val styleName = e.attr(Ns.TEXT, "style-name")
        val chain = chain("paragraph", styleName)
        if (chain.firstOrNull()?.breakBefore == true) sb.append("<hr class=\"pb\">")
        val level = headingOf(styleName)
        val (html, plain, after) = inline(e)
        when {
            plain.isBlank() && !html.contains("<img") && !html.contains("<br") -> if (!inList) sb.append("<p class=\"e\"></p>")
            level == 0 -> { if (firstHeading == null) firstHeading = plain.trim(); sb.append("<p class=\"title\">").append(html).append("</p>") }
            level == -1 -> sb.append("<p class=\"subtitle\">").append(html).append("</p>")
            level != null -> { if (firstHeading == null) firstHeading = plain.trim(); sb.append("<h$level${alignAttr(e)}>").append(html).append("</h$level>") }
            inList -> sb.append(html)
            else -> sb.append("<p${alignAttr(e)}>").append(html).append("</p>")
        }
        sb.append(after)
        if (chain.firstOrNull()?.breakAfter == true) sb.append("<hr class=\"pb\">")
    }

    private fun list(e: XEl, sb: StringBuilder, inheritedStyle: String?, depth: Int) {
        val styleName = e.attr(Ns.TEXT, "style-name") ?: inheritedStyle
        val level = styleName?.let { listStyles[it]?.get(depth) }
        val ordered = level?.ordered == true
        val tag = if (ordered) "ol" else "ul"
        val type = when (level?.format) { "a" -> " type=\"a\""; "A" -> " type=\"A\""; "i" -> " type=\"i\""; "I" -> " type=\"I\""; else -> "" }
        val start = level?.start?.takeIf { it != 1 }?.let { " start=\"$it\"" } ?: ""
        sb.append('<').append(tag).append(if (ordered) type + start else "").append('>')
        for (item in e.elements) {
            val header = item.isA(Ns.TEXT, "list-header")
            if (!header && !item.isA(Ns.TEXT, "list-item")) continue
            sb.append(if (header) "<li style=\"list-style:none\">" else "<li>")
            val kids = item.elements.toList()
            // One paragraph (maybe with a nested list) sits in the item as plain text; several stay paragraphs.
            val onePara = kids.count { it.isA(Ns.TEXT, "p") } == 1
            for (k in kids) {
                when {
                    k.isA(Ns.TEXT, "list") -> list(k, sb, styleName, depth + 1)
                    k.isA(Ns.TEXT, "p") && onePara -> paragraph(k, sb, inList = true)
                    else -> block(k, sb)
                }
            }
            sb.append("</li>")
        }
        sb.append("</").append(tag).append('>')
    }

    // ── Inline content ──

    private data class Inline(val html: String, val plain: String, val after: String)

    private fun inline(p: XEl): Inline {
        val sb = StringBuilder()
        val runs = RunWriter(sb)
        val plain = StringBuilder()
        val after = StringBuilder()
        val base = fmtFrom(RunWriter.PLAIN, chain("paragraph", p.attr(Ns.TEXT, "style-name")), includeSize = false)
            .let { if (headingOf(p.attr(Ns.TEXT, "style-name")) != null || p.isA(Ns.TEXT, "h")) it.copy(bold = false, sizePt = null) else it }
        inlineChildren(p, base, runs, plain, after, atStart = true)
        runs.close()
        return Inline(sb.toString(), plain.toString(), after.toString())
    }

    private fun inlineChildren(e: XEl, f: RunWriter.Fmt, runs: RunWriter, plain: StringBuilder, after: StringBuilder, atStart: Boolean) {
        var first = atStart
        for (c in e.children) {
            if (c is String) {
                var t = c.replace(WS, " ")
                if (first && plain.isEmpty()) t = t.trimStart()
                if (t.isNotEmpty()) { runs.text(t, f); plain.append(t) }
                first = false
                continue
            }
            val el = c as XEl
            when {
                el.isA(Ns.TEXT, "span") -> inlineChildren(el, fmtFrom(f, chain("text", el.attr(Ns.TEXT, "style-name")), includeSize = true), runs, plain, after, false)
                el.isA(Ns.TEXT, "s") -> { val n = el.attr(Ns.TEXT, "c")?.toIntOrNull() ?: 1; val s = "\u00A0".repeat(n.coerceIn(1, 200)); runs.text(s, f); plain.append(' ') }
                el.isA(Ns.TEXT, "tab") -> { runs.text("\u2003", f); plain.append(' ') }
                el.isA(Ns.TEXT, "line-break") -> runs.raw("<br>", f)
                el.isA(Ns.TEXT, "a") -> {
                    val href = el.attr(Ns.XLINK, "href")?.let(::safeHref)
                    if (href != null) runs.raw("<a href=\"${esc(href)}\">", RunWriter.PLAIN)
                    inlineChildren(el, f.copy(underline = false), runs, plain, after, false)
                    if (href != null) runs.raw("</a>", RunWriter.PLAIN)
                }
                el.isA(Ns.TEXT, "note") -> footnote(el, runs)
                el.isA(Ns.TEXT, "bookmark") || el.isA(Ns.TEXT, "bookmark-start") ->
                    el.attr(Ns.TEXT, "name")?.let { runs.raw("<a id=\"${esc(it)}\"></a>", RunWriter.PLAIN) }
                el.isA(Ns.DRAW, "frame") -> runs.raw(frameInline(el, after), RunWriter.PLAIN)
                el.isA(Ns.DRAW, "a") -> el.child(Ns.DRAW, "frame")?.let { runs.raw(frameInline(it, after), RunWriter.PLAIN) }
                el.isA(Ns.OFFICE, "annotation") || el.isA(Ns.OFFICE, "annotation-end") -> Unit
                el.isA(Ns.TEXT, "soft-page-break") || el.isA(Ns.TEXT, "bookmark-end") || el.isA(Ns.TEXT, "reference-mark-start") -> Unit
                el.isA(Ns.TEXT, "deletion") || el.isA(Ns.TEXT, "change") || el.isA(Ns.TEXT, "change-start") || el.isA(Ns.TEXT, "change-end") -> Unit
                el.isA(Ns.TEXT, "ruby") -> el.child(Ns.TEXT, "ruby-base")?.let { inlineChildren(it, f, runs, plain, after, false) }
                // Fields (page numbers, dates, sequences, references…) show their current text.
                else -> inlineChildren(el, f, runs, plain, after, false)
            }
            first = false
        }
    }

    private fun footnote(el: XEl, runs: RunWriter) {
        val n = notes.size + 1
        val mark = el.child(Ns.TEXT, "note-citation")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: n.toString()
        val body = StringBuilder()
        el.child(Ns.TEXT, "note-body")?.let { blocks(it, body) }
        notes.add(body.toString().replaceFirst(Regex("</p>\\s*$"), " <a href=\"#fnref-$n\">↩</a></p>"))
        runs.raw("<sup class=\"fn\"><a id=\"fnref-$n\" href=\"#fn-$n\">${esc(mark)}</a></sup>", RunWriter.PLAIN)
    }

    private fun appendNotes(sb: StringBuilder) {
        if (notes.isEmpty()) return
        sb.append("<section class=\"footnotes\"><hr><ol>")
        notes.forEachIndexed { i, n -> sb.append("<li id=\"fn-${i + 1}\">").append(n).append("</li>") }
        sb.append("</ol></section>")
    }

    /** A frame inside a paragraph: its picture inline; a text box's paragraphs go after the paragraph. */
    private fun frameInline(frame: XEl, after: StringBuilder): String {
        frame.child(Ns.DRAW, "text-box")?.let { tb -> blocks(tb, after); return "" }
        frame.child(Ns.TABLE, "table")?.let { t -> table(t, after); return "" }
        return image(frame) ?: ""
    }

    private fun frame(frame: XEl, sb: StringBuilder, block: Boolean) {
        frame.child(Ns.DRAW, "text-box")?.let { blocks(it, sb); return }
        frame.child(Ns.TABLE, "table")?.let { table(it, sb); return }
        image(frame)?.let { sb.append(if (block) "<p>$it</p>" else it) }
    }

    private fun image(frame: XEl): String? {
        val img = frame.child(Ns.DRAW, "image") ?: return null
        val w = lengthPx(frame.attr(Ns.SVG, "width"))
        val size = if (w != null && w > 0) " style=\"width:${w.toInt()}px\"" else ""
        val alt = frame.child(Ns.SVG, "title")?.text() ?: frame.child(Ns.SVG, "desc")?.text() ?: ""
        val href = img.attr(Ns.XLINK, "href")
        val bin = img.child(Ns.OFFICE, "binary-data")?.text()
        val z = zip
        val url = when {
            bin != null -> runCatching { Base64.getMimeDecoder().decode(bin.trim()) }.getOrNull()?.let { bytes -> assets.put("image." + sniffImageExt(bytes), bytes) }
            href != null && z != null && !href.contains("://") -> {
                val path = resolveZipPath("", href.removePrefix("./"))
                val bytes = runCatching { z.bytes(path) }.getOrNull() ?: return placeholder(path)
                val name = if (isWebImage(path)) path else "$path.${sniffImageExt(bytes)}"
                assets.put(name, bytes) ?: return placeholder(path)
            }
            else -> null
        } ?: return placeholder(href ?: "picture")
        return "<img src=\"${esc(url)}\"$size alt=\"${esc(alt)}\">"
    }

    private fun placeholder(name: String): String {
        val ext = name.substringAfterLast('/').substringAfterLast('.', "")
        return if (ext.isEmpty() || ext.length > 5) "<span class=\"noimg\">Embedded object</span>"
        else "<span class=\"noimg\">Picture (${esc(ext.uppercase())})</span>"
    }

    // ── Tables ──

    private class Cell(val html: String, val colspan: Int, val rowspan: Int, val numeric: Boolean, val header: Boolean) {
        val empty: Boolean get() = html.isEmpty()
    }

    /** Rows of a table (header rows, groups and repeats expanded, capped), each a list of cells / gaps. */
    private fun tableRows(t: XEl, sheet: Boolean): List<List<Cell?>> {
        val rows = ArrayList<List<Cell?>>()
        var pendingEmpty = 0
        fun addRow(row: XEl, header: Boolean) {
            val cells = ArrayList<Cell?>()
            var pendingCells = 0
            for (c in row.elements) {
                val covered = c.isA(Ns.TABLE, "covered-table-cell")
                if (!covered && !c.isA(Ns.TABLE, "table-cell")) continue
                val rep = (c.attr(Ns.TABLE, "number-columns-repeated")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                if (covered) {
                    repeat(minOf(rep, MAX_COLS)) { cells.add(null) }
                    continue
                }
                val sb = StringBuilder()
                if (sheet) {
                    // A cell's displayed text: its paragraphs, one per line (or the bare value when the
                    // file stores none).
                    c.children(Ns.TEXT, "p").forEachIndexed { i, p -> if (i > 0) sb.append("<br>"); sb.append(inline(p).html) }
                    if (sb.isEmpty()) {
                        (c.attr(Ns.OFFICE, "value") ?: c.attr(Ns.OFFICE, "date-value") ?: c.attr(Ns.OFFICE, "time-value")
                            ?: c.attr(Ns.OFFICE, "boolean-value") ?: c.attr(Ns.OFFICE, "string-value"))?.let { sb.append(esc(it)) }
                    }
                } else {
                    blocks(c, sb)
                }
                val type = c.attr(Ns.OFFICE, "value-type")
                val cell = Cell(
                    html = sb.toString().let { if (!sheet && it.trim() == "<p class=\"e\"></p>") "" else it },
                    colspan = (c.attr(Ns.TABLE, "number-columns-spanned")?.toIntOrNull() ?: 1).coerceIn(1, MAX_COLS),
                    rowspan = (c.attr(Ns.TABLE, "number-rows-spanned")?.toIntOrNull() ?: 1).coerceIn(1, MAX_SHEET_ROWS),
                    numeric = type == "float" || type == "percentage" || type == "currency",
                    header = header,
                )
                // Spreadsheets pad rows with runs of empty cells: keep them only when a value follows.
                if (sheet && cell.empty && cell.colspan == 1) { pendingCells += rep; continue }
                if (cells.size >= MAX_COLS) continue
                repeat(minOf(pendingCells, MAX_COLS - cells.size)) { cells.add(Cell("", 1, 1, false, header)) }
                pendingCells = 0
                repeat(minOf(rep, MAX_COLS - cells.size)) { cells.add(cell) }
            }
            val rep = (row.attr(Ns.TABLE, "number-rows-repeated")?.toIntOrNull() ?: 1).coerceAtLeast(1)
            if (cells.isEmpty() && sheet) { pendingEmpty += rep; return }
            if (rows.size >= MAX_SHEET_ROWS) return
            repeat(minOf(pendingEmpty, MAX_SHEET_ROWS - rows.size)) { rows.add(emptyList()) }
            pendingEmpty = 0
            repeat(minOf(rep, MAX_SHEET_ROWS - rows.size)) { rows.add(cells) }
        }
        fun walk(container: XEl, header: Boolean) {
            for (e in container.elements) {
                when {
                    e.isA(Ns.TABLE, "table-row") -> addRow(e, header)
                    e.isA(Ns.TABLE, "table-header-rows") -> walk(e, true)
                    e.isA(Ns.TABLE, "table-rows") || e.isA(Ns.TABLE, "table-row-group") -> walk(e, header)
                }
            }
        }
        walk(t, false)
        return rows
    }

    private fun table(t: XEl, sb: StringBuilder) {
        val rows = tableRows(t, sheet = false)
        if (rows.isEmpty()) return
        sb.append("<div class=\"tw\"><table>")
        for (row in rows) {
            sb.append("<tr>")
            for (c in row) {
                if (c == null) continue
                val tag = if (c.header) "th" else "td"
                sb.append('<').append(tag)
                if (c.colspan > 1) sb.append(" colspan=\"").append(c.colspan).append('"')
                if (c.rowspan > 1) sb.append(" rowspan=\"").append(c.rowspan).append('"')
                sb.append('>').append(unwrapSingleParagraph(c.html)).append("</").append(tag).append('>')
            }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
    }

    private fun sheetHtml(t: XEl): String {
        val rows = tableRows(t, sheet = true)
        val width = rows.maxOfOrNull { r -> r.indices.lastOrNull { i -> r[i]?.empty == false }?.let { it + 1 } ?: 0 } ?: 0
        if (rows.isEmpty() || width == 0) return "<p class=\"subtitle\">This sheet is empty.</p>"
        val lastRow = rows.indexOfLast { r -> r.any { it?.empty == false } }
        val sb = StringBuilder()
        sb.append("<div class=\"tw\"><table class=\"sheet\"><thead><tr><th class=\"ch\"></th>")
        for (c in 0 until width) sb.append("<th class=\"ch\">").append(columnName(c)).append("</th>")
        sb.append("</tr></thead><tbody>")
        for (r in 0..lastRow) {
            val row = rows[r]
            sb.append("<tr><th class=\"rh\">").append(r + 1).append("</th>")
            var c = 0
            while (c < width) {
                if (c < row.size && row[c] == null) { c++; continue }  // covered by a span
                val cell = row.getOrNull(c)
                if (cell == null) { sb.append("<td></td>"); c++; continue }
                sb.append(if (cell.numeric) "<td class=\"n\"" else "<td")
                if (cell.colspan > 1) sb.append(" colspan=\"").append(cell.colspan).append('"')
                if (cell.rowspan > 1) sb.append(" rowspan=\"").append(cell.rowspan).append('"')
                sb.append('>').append(cell.html).append("</td>")
                c += 1
            }
            sb.append("</tr>")
        }
        sb.append("</tbody></table></div>")
        if (rows.size >= MAX_SHEET_ROWS || t.children.size >= MAX_SHEET_ROWS) {
            sb.append("<p class=\"subtitle\">Only the first ${MAX_SHEET_ROWS} rows are shown.</p>")
        }
        return sb.toString()
    }

    // ── Presentations ──

    private fun slide(page: XEl, num: Int, sb: StringBuilder) {
        sb.append("<section class=\"slide\"><div class=\"num\">Slide ").append(num)
        page.attr(Ns.DRAW, "name")?.takeIf { it.isNotBlank() && !it.matches(Regex("(?i)page\\s*\\d+|slide\\s*\\d+")) }?.let { sb.append(" · ").append(esc(it)) }
        sb.append("</div>")
        val frames = page.elements.filter { it.isA(Ns.DRAW, "frame") || it.isA(Ns.DRAW, "custom-shape") || it.isA(Ns.DRAW, "rect") || it.isA(Ns.DRAW, "g") }.toList()
        val (titles, rest) = frames.partition { it.attr(Ns.PRESENTATION, "class") == "title" }
        for (t in titles) {
            val (html, plain, _) = t.child(Ns.DRAW, "text-box")?.elements?.firstOrNull()?.let { inline(it) } ?: Inline("", "", "")
            if (plain.isNotBlank()) sb.append("<h2>").append(html).append("</h2>")
        }
        val ordered = rest.sortedWith(compareBy({ lengthPx(it.attr(Ns.SVG, "y")) ?: 0f }, { lengthPx(it.attr(Ns.SVG, "x")) ?: 0f }))
        for (f in ordered) shape(f, sb)
        page.child(Ns.PRESENTATION, "notes")?.let { notesEl ->
            val n = StringBuilder()
            notesEl.descendants(Ns.DRAW, "text-box").forEach { blocks(it, n) }
            if (n.isNotBlank() && n.toString() != "<p class=\"e\"></p>") sb.append("<details class=\"notes\"><summary>Notes</summary>").append(n).append("</details>")
        }
        sb.append("</section>")
    }

    private fun shape(f: XEl, sb: StringBuilder) {
        when {
            f.isA(Ns.DRAW, "g") -> f.elements.forEach { shape(it, sb) }
            f.isA(Ns.DRAW, "frame") -> frame(f, sb, block = true)
            else -> blocks(f, sb)  // custom shapes / rectangles carry their text directly
        }
    }

    private fun sniffImageExt(b: ByteArray): String = when {
        b.size > 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpg"
        b.size > 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() -> "gif"
        b.size > 2 && b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
        b.size > 12 && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() -> "webp"
        b.size > 5 && String(b, 0, minOf(b.size, 256), Charsets.ISO_8859_1).contains("<svg") -> "svg"
        else -> "bin"
    }
}

private val WS = Regex("[ \t\r\n]+")

/** Most rows a sheet shows; most columns any table keeps. */
internal const val MAX_SHEET_ROWS = 5000
internal const val MAX_COLS = 200

/** A, B, …, Z, AA, AB… for column index [i] (0-based). */
internal fun columnName(i: Int): String {
    var n = i + 1
    val sb = StringBuilder()
    while (n > 0) { val r = (n - 1) % 26; sb.append('A' + r); n = (n - 1) / 26 }
    return sb.reverse().toString()
}
