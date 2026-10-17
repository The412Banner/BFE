package com.the412banner.bfe.docs

import java.nio.charset.Charset

/**
 * RTF → HTML: paragraphs (alignment, indents, outline-level / "heading N" styles as headings), bold /
 * italic / underline / strike / super- and subscript, font size and colour, hyperlink fields, tables
 * (one level), footnotes, PNG / JPEG pictures, page breaks, and text in the document's code page or
 * a font's charset (`\'hh`) and Unicode (`\uN`). Headers, footers, comments, embedded objects and the
 * metadata groups are skipped.
 */
class RtfConverter private constructor(private val src: ByteArray, private val assets: DocAssets) {

    private enum class Dest { BODY, SKIP, FONTTBL, COLORTBL, STYLESHEET, PICT, FLDINST, FLDRSLT }

    private class State(
        var dest: Dest = Dest.BODY,
        var bold: Boolean = false, var italic: Boolean = false, var underline: Boolean = false, var strike: Boolean = false,
        var sup: Boolean = false, var sub: Boolean = false, var hidden: Boolean = false,
        var fontSize: Int = 24, var color: Int = 0, var bg: Int = 0, var font: Int = -1,
        var uc: Int = 1,
        // Paragraph properties (reset by \pard).
        var align: Char = 'l', var leftIndent: Int = 0, var firstIndent: Int = 0, var outline: Int = -1, var style: Int = -1,
        var inTable: Boolean = false,
        /** Inside a footnote's text. */
        var footnote: Boolean = false,
        /** The group just opened and nothing was read in it yet (its first word may name a destination). */
        var fresh: Boolean = true,
        var starred: Boolean = false,
        /** This group opened a hyperlink that closes with it. */
        var closesLink: Boolean = false,
    ) {
        fun copy() = State(
            dest, bold, italic, underline, strike, sup, sub, hidden, fontSize, color, bg, font, uc,
            align, leftIndent, firstIndent, outline, style, inTable, footnote, true, false, false,
        )

        fun resetChar() { bold = false; italic = false; underline = false; strike = false; sup = false; sub = false; hidden = false; fontSize = 24; color = 0; bg = 0 }
        fun resetPara() { align = 'l'; leftIndent = 0; firstIndent = 0; outline = -1; style = -1; inTable = false }
    }

    private var p = 0
    private var st = State()
    private val stack = ArrayList<State>()

    private var ansi: Charset = Charset.forName("windows-1252")
    private val fontCharsets = HashMap<Int, Charset>()
    private var curFont = -1
    private val colors = ArrayList<String?>()
    private var r = -1; private var g = -1; private var b = -1
    private val styleNames = HashMap<Int, String>()
    private var curStyle = -1
    private val styleName = StringBuilder()

    // Output
    private val body = StringBuilder()
    private val notes = ArrayList<String>()
    private var noteOut: StringBuilder? = null
    private var para = StringBuilder()
    private var runs = RunWriter(para)
    private var paraHasContent = false
    private val rowCells = ArrayList<String>()
    private val tableRows = StringBuilder()
    private var cell = StringBuilder()
    private var firstTitle: String? = null

    // Fields, pictures, pending code-page bytes
    private val fieldInst = StringBuilder()
    private var pictHex = StringBuilder()
    private var pictType: String? = null
    private var pictW = 0; private var pictH = 0
    private val pending = java.io.ByteArrayOutputStream()
    private var skipChars = 0

    companion object {
        fun convert(bytes: ByteArray, fileName: String, assets: DocAssets): DocResult {
            if (!String(bytes, 0, minOf(bytes.size, 16), Charsets.ISO_8859_1).trimStart().startsWith("{\\rtf")) {
                throw DocException("This isn't an RTF document.")
            }
            val c = RtfConverter(bytes, assets)
            c.run()
            val title = c.firstTitle ?: fileName
            return DocResult(DocKind.RTF, title, listOf(DocPage(title, c.html())))
        }

        private val SKIP_DESTS = setOf(
            "fonttbl", "colortbl", "stylesheet", "info", "header", "headerl", "headerr", "headerf", "footer", "footerl",
            "footerr", "footerf", "object", "objdata", "themedata", "colorschememapping", "latentstyles", "datastore",
            "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator", "pgdsctbl", "mmathPr", "filetbl",
            "revtbl", "annotation", "atnid", "atnauthor", "atrfstart", "atrfend", "bkmkstart", "bkmkend", "nonshppict",
            "pntext", "pntxta", "pntxtb", "comment", "doccomm", "author", "operator", "title", "subject", "keywords",
            "template", "userprops", "xe", "tc", "txe", "fldtype", "docvar", "wgrffmtfilter", "passwordhash",
        )

        private fun charsetFor(code: Int): Charset? = runCatching {
            Charset.forName(
                when (code) {
                    128 -> "Shift_JIS"; 129 -> "x-windows-949"; 134 -> "GBK"; 136 -> "Big5"; 161 -> "windows-1253"
                    162 -> "windows-1254"; 163 -> "windows-1258"; 177 -> "windows-1255"; 178 -> "windows-1256"
                    186 -> "windows-1257"; 204 -> "windows-1251"; 222 -> "x-windows-874"; 238 -> "windows-1250"
                    77 -> "x-MacRoman"
                    else -> return null
                },
            )
        }.getOrNull()
    }

    private fun run() {
        val n = src.size
        while (p < n) {
            val c = src[p].toInt() and 0xFF
            when (c) {
                '{'.code -> { flushBytes(); p++; stack.add(st); st = st.copy() }
                '}'.code -> { flushBytes(); p++; endGroup() }
                '\\'.code -> controlWord()
                '\r'.code, '\n'.code -> p++
                else -> { p++; byteChar(c) }
            }
        }
        flushBytes()
        endParagraph(force = false)
        closeTable()
    }

    private fun endGroup() {
        val closed = st
        if (stack.isEmpty()) return
        st = stack.removeAt(stack.size - 1)
        when {
            closed.dest == Dest.PICT && st.dest != Dest.PICT -> finishPicture()
            closed.dest == Dest.FONTTBL && st.dest != Dest.FONTTBL -> curFont = -1
            closed.dest == Dest.STYLESHEET && st.dest == Dest.STYLESHEET -> commitStyle()
        }
        if (closed.closesLink) runs.raw("</a>", RunWriter.PLAIN)
        if (closed.footnote && !st.footnote) finishFootnote()
    }

    /** A control word or symbol at [p] (on the backslash). */
    private fun controlWord() {
        val n = src.size
        p++
        if (p >= n) return
        val c = src[p].toInt() and 0xFF
        if (!isLetter(c)) {
            p++
            when (c) {
                '\''.code -> {
                    if (p + 2 <= n) {
                        val v = hexVal(src[p].toInt()) * 16 + hexVal(src[p + 1].toInt())
                        p += 2
                        if (skipChars > 0) { skipChars--; return }
                        if (st.dest == Dest.BODY || st.dest == Dest.FLDRSLT || st.dest == Dest.FLDINST || st.dest == Dest.STYLESHEET) pending.write(v)
                    }
                }
                '*'.code -> st.starred = true
                '~'.code -> emitText("\u00A0")
                '-'.code -> Unit
                '_'.code -> emitText("\u2011")
                '{'.code, '}'.code, '\\'.code -> emitText(c.toChar().toString())
                '\n'.code, '\r'.code -> paragraph()
                '|'.code, ':'.code -> Unit
                else -> Unit
            }
            return
        }
        val start = p
        while (p < n && isLetter(src[p].toInt() and 0xFF)) p++
        val word = String(src, start, p - start, Charsets.US_ASCII)
        var param: Int? = null
        if (p < n && (src[p] == '-'.code.toByte() || isDigit(src[p].toInt()))) {
            val ps = p
            p++
            while (p < n && isDigit(src[p].toInt())) p++
            param = String(src, ps, p - ps, Charsets.US_ASCII).toIntOrNull()
        }
        if (p < n && src[p] == ' '.code.toByte()) p++
        if (word != "u" && word != "bin") flushBytes()
        handle(word, param)
    }

    private fun handle(word: String, param: Int?) {
        val fresh = st.fresh
        st.fresh = false
        // Destinations: the first word in a group.
        if (fresh) {
            when (word) {
                "fonttbl" -> { st.dest = Dest.FONTTBL; return }
                "colortbl" -> { st.dest = Dest.COLORTBL; return }
                "stylesheet" -> { st.dest = Dest.STYLESHEET; return }
                "pict" -> { st.dest = Dest.PICT; pictHex = StringBuilder(); pictType = null; pictW = 0; pictH = 0; return }
                "fldinst" -> { st.dest = Dest.FLDINST; fieldInst.setLength(0); return }
                "fldrslt" -> {
                    if (st.dest == Dest.SKIP) return
                    st.dest = Dest.FLDRSLT
                    hyperlinkTarget()?.let { url ->
                        runs.raw("<a href=\"${esc(url)}\">", RunWriter.PLAIN)
                        st.closesLink = true
                    }
                    return
                }
                "footnote" -> {
                    if (st.dest == Dest.SKIP) return
                    startFootnote()
                    return
                }
                "shppict" -> return  // Word's picture wrapper: read the \pict inside.
                "field" -> return
            }
            if (st.starred || word in SKIP_DESTS) { st.dest = Dest.SKIP; return }
        }
        when (st.dest) {
            Dest.SKIP -> return
            Dest.FONTTBL -> {
                when (word) {
                    "f" -> curFont = param ?: -1
                    "fcharset" -> if (curFont >= 0) param?.let { cs -> charsetFor(cs)?.let { fontCharsets[curFont] = it } }
                }
                return
            }
            Dest.COLORTBL -> {
                when (word) { "red" -> r = param ?: 0; "green" -> g = param ?: 0; "blue" -> b = param ?: 0 }
                return
            }
            Dest.PICT -> {
                when (word) {
                    "pngblip" -> pictType = "png"
                    "jpegblip" -> pictType = "jpg"
                    "emfblip" -> pictType = "emf"
                    "wmetafile" -> pictType = "wmf"
                    "picwgoal" -> pictW = param ?: 0
                    "pichgoal" -> pictH = param ?: 0
                    "bin" -> skipBinary(param ?: 0)
                }
                return
            }
            Dest.STYLESHEET -> {
                when (word) { "s" -> curStyle = param ?: -1 }
                return
            }
            else -> Unit
        }
        when (word) {
            "ansicpg" -> param?.let { cp -> runCatching { Charset.forName("windows-$cp") }.getOrNull() ?: runCatching { Charset.forName("cp$cp") }.getOrNull() }?.let { ansi = it }
            "mac" -> runCatching { Charset.forName("x-MacRoman") }.getOrNull()?.let { ansi = it }
            "pc" -> runCatching { Charset.forName("IBM437") }.getOrNull()?.let { ansi = it }
            "pca" -> runCatching { Charset.forName("IBM850") }.getOrNull()?.let { ansi = it }
            "par" -> paragraph()
            "sect" -> paragraph()
            "line" -> emitRaw("<br>")
            "tab" -> emitText("\u2003")
            "page" -> { endParagraph(force = false); closeTable(); out().append("<hr class=\"pb\">") }
            "pard" -> st.resetPara()
            "plain" -> st.resetChar()
            "b" -> st.bold = param != 0
            "i" -> st.italic = param != 0
            "ul", "uld", "uldb", "ulw", "ulwave", "uldash", "ulth" -> st.underline = param != 0
            "ulnone" -> st.underline = false
            "strike", "striked" -> st.strike = param != 0
            "super" -> { st.sup = true; st.sub = false }
            "sub" -> { st.sub = true; st.sup = false }
            "nosupersub" -> { st.sup = false; st.sub = false }
            "v" -> st.hidden = param != 0
            "fs" -> st.fontSize = param ?: 24
            "cf" -> st.color = param ?: 0
            "highlight", "cb", "chcbpat" -> st.bg = param ?: 0
            "f" -> st.font = param ?: -1
            "uc" -> st.uc = param ?: 1
            "u" -> {
                var v = param ?: 0
                if (v < 0) v += 65536
                flushBytes()
                emitText(v.toChar().toString())
                skipChars = st.uc
                return
            }
            "qc" -> st.align = 'c'
            "qr" -> st.align = 'r'
            "qj" -> st.align = 'j'
            "ql" -> st.align = 'l'
            "li" -> st.leftIndent = param ?: 0
            "fi" -> st.firstIndent = param ?: 0
            "outlinelevel" -> st.outline = param ?: -1
            "s" -> st.style = param ?: -1
            "intbl" -> st.inTable = true
            "cell" -> endCell()
            "row" -> endRow()
            "trowd" -> Unit
            "emdash" -> emitText("—")
            "endash" -> emitText("–")
            "bullet" -> emitText("•")
            "lquote" -> emitText("‘")
            "rquote" -> emitText("’")
            "ldblquote" -> emitText("“")
            "rdblquote" -> emitText("”")
            "emspace" -> emitText("\u2003")
            "enspace" -> emitText("\u2002")
            "chftn" -> Unit  // the footnote mark; BFE numbers footnotes itself
            "bin" -> skipBinary(param ?: 0)
        }
        if (skipChars > 0) skipChars--
    }

    private fun skipBinary(len: Int) { p = (p + len).coerceAtMost(src.size) }

    private fun byteChar(c: Int) {
        when (st.dest) {
            Dest.FONTTBL, Dest.SKIP -> return
            Dest.COLORTBL -> {
                if (c == ';'.code) {
                    colors.add(if (r < 0 && g < 0 && b < 0) null else "#%02x%02x%02x".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)))
                    r = -1; g = -1; b = -1
                }
                return
            }
            Dest.PICT -> { if (hexVal(c) >= 0) pictHex.append(c.toChar()); return }
            Dest.STYLESHEET -> {
                if (c == ';'.code) commitStyle() else styleName.append(c.toChar())
                return
            }
            Dest.FLDINST -> { fieldInst.append(c.toChar()); return }
            else -> Unit
        }
        if (skipChars > 0) { skipChars--; return }
        if (c >= 0x80) { pending.write(c); return }
        flushBytes()
        emitText(c.toChar().toString())
    }

    /** Decodes code-page bytes collected from `\'hh` / 8-bit text, with the current font's charset. */
    private fun flushBytes() {
        if (pending.size() == 0) return
        val bytes = pending.toByteArray()
        pending.reset()
        val cs = fontCharsets[st.font] ?: ansi
        val s = String(bytes, cs)
        when (st.dest) {
            Dest.FLDINST -> fieldInst.append(s)
            Dest.STYLESHEET -> styleName.append(s)
            Dest.BODY, Dest.FLDRSLT -> emitText(s)
            else -> Unit
        }
    }

    private fun commitStyle() {
        if (curStyle >= 0) styleNames[curStyle] = styleName.toString().trim()
        styleName.setLength(0)
        curStyle = -1
    }

    private fun hyperlinkTarget(): String? {
        val inst = fieldInst.toString().trim()
        if (!inst.startsWith("HYPERLINK", ignoreCase = true)) return null
        val q = Regex("\"([^\"]+)\"").find(inst)?.groupValues?.get(1) ?: inst.substringAfter(' ').trim().substringBefore(' ')
        return safeHref(q)
    }

    private fun fmt(): RunWriter.Fmt = RunWriter.Fmt(
        bold = st.bold, italic = st.italic, underline = st.underline && !st.closesLinkInScope(), strike = st.strike,
        sup = st.sup, sub = st.sub,
        color = colors.getOrNull(st.color),
        bg = if (st.bg > 0) colors.getOrNull(st.bg) else null,
        // Headings get their size from the reading style.
        sizePt = if (headingLevel() != null) null else st.fontSize / 2f,
    )

    /** 1–6 for an outline-level or "heading N"-styled paragraph, else null. */
    private fun headingLevel(): Int? = when {
        st.outline in 0..5 -> st.outline + 1
        else -> styleNames[st.style]?.let { Regex("(?i)^heading\\s*(\\d)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    }?.coerceIn(1, 6)

    // A link's own underline comes from CSS.
    private fun State.closesLinkInScope(): Boolean = closesLink || stack.any { it.closesLink }

    private fun emitText(s: String) {
        if (st.dest != Dest.BODY && st.dest != Dest.FLDRSLT) return
        if (st.hidden) return
        runs.text(s, fmt())
        if (s.isNotBlank()) paraHasContent = true
    }

    private fun emitRaw(s: String) {
        if (st.dest != Dest.BODY && st.dest != Dest.FLDRSLT) return
        runs.raw(s, fmt())
        paraHasContent = true
    }

    /** Where finished blocks go: the open footnote, the open table cell, or the body. */
    private fun out(): StringBuilder = noteOut ?: body

    private fun paragraph() {
        if (st.dest != Dest.BODY && st.dest != Dest.FLDRSLT) return
        endParagraph(force = true)
    }

    private fun endParagraph(force: Boolean) {
        runs.close()
        val content = para.toString()
        para = StringBuilder(); runs = RunWriter(para)
        val had = paraHasContent
        paraHasContent = false
        if (!force && content.isBlank()) return
        val level = headingLevel()
        val cls = when (st.align) { 'c' -> "c"; 'r' -> "r"; 'j' -> "j"; else -> null }
        val style = buildString {
            if (st.leftIndent > 0) append("margin-left:%.1fem;".format(java.util.Locale.US, st.leftIndent / 240f))
            if (st.firstIndent != 0 && level == null) append("text-indent:%.1fem;".format(java.util.Locale.US, st.firstIndent / 240f))
        }
        val attrs = buildString {
            if (cls != null) append(" class=\"$cls\"")
            if (style.isNotEmpty()) append(" style=\"$style\"")
        }
        val html = when {
            !had && content.isBlank() -> "<p class=\"e\"$attrs></p>"
            level != null -> "<h$level$attrs>$content</h$level>"
            else -> "<p$attrs>$content</p>"
        }
        if (level != null && firstTitle == null && noteOut == null) firstTitle = Regex("<[^>]+>").replace(content, "").trim().takeIf { it.isNotEmpty() }
        if (st.inTable && noteOut == null) {
            cell.append(html)
        } else {
            if (noteOut == null) closeTable()
            out().append(html)
        }
    }

    private fun endCell() {
        if (paraHasContent || para.isNotEmpty()) { val keep = st.inTable; st.inTable = true; endParagraph(force = false); st.inTable = keep }
        rowCells.add(cell.toString())
        cell = StringBuilder()
    }

    private fun endRow() {
        if (paraHasContent) endCell()
        if (rowCells.isEmpty()) return
        if (tableRows.isEmpty()) tableRows.append("<div class=\"tw\"><table>")
        tableRows.append("<tr>")
        rowCells.forEach { tableRows.append("<td>").append(unwrapSingleParagraph(it)).append("</td>") }
        tableRows.append("</tr>")
        rowCells.clear()
    }

    private fun closeTable() {
        if (rowCells.isNotEmpty()) endRow()
        if (tableRows.isNotEmpty()) {
            body.append(tableRows).append("</table></div>")
            tableRows.setLength(0)
        }
    }

    private fun startFootnote() {
        val n = notes.size + 1
        runs.raw("<sup class=\"fn\"><a id=\"fnref-$n\" href=\"#fn-$n\">$n</a></sup>", RunWriter.PLAIN)
        // The footnote's own paragraphs collect apart from the one it sits in.
        savedPara = para; savedRuns = runs; savedHas = paraHasContent
        para = StringBuilder(); runs = RunWriter(para); paraHasContent = false
        noteOut = StringBuilder()
        st.footnote = true
        st.dest = Dest.BODY
        st.resetPara()
    }

    private var savedPara: StringBuilder? = null
    private var savedRuns: RunWriter? = null
    private var savedHas = false

    private fun finishFootnote() {
        endParagraph(force = false)
        val text = noteOut?.toString().orEmpty()
        val n = notes.size + 1
        notes.add(text.replaceFirst(Regex("</p>\\s*$"), " <a href=\"#fnref-$n\">↩</a></p>"))
        noteOut = null
        para = savedPara ?: StringBuilder(); runs = savedRuns ?: RunWriter(para); paraHasContent = savedHas
        savedPara = null; savedRuns = null
    }

    private fun finishPicture() {
        val type = pictType
        val hex = pictHex.toString()
        pictHex = StringBuilder()
        if (st.dest != Dest.BODY && st.dest != Dest.FLDRSLT) return
        if (type == null || hex.length < 16) return
        val bytes = ByteArray(hex.length / 2) { i -> ((hexVal(hex[i * 2].code) shl 4) or hexVal(hex[i * 2 + 1].code)).toByte() }
        val url = assets.put("picture.$type", bytes)
        val w = if (pictW > 0) " width=\"${pictW / 15}\"" else ""
        emitRaw(if (url != null) "<img src=\"${esc(url)}\"$w alt=\"\">" else "<span class=\"noimg\">Picture (${type.uppercase()})</span>")
    }

    private fun html(): String {
        val sb = StringBuilder(body)
        if (notes.isNotEmpty()) {
            sb.append("<section class=\"footnotes\"><hr><ol>")
            notes.forEachIndexed { i, n -> sb.append("<li id=\"fn-${i + 1}\">").append(n).append("</li>") }
            sb.append("</ol></section>")
        }
        return sb.toString()
    }

    private fun isLetter(c: Int) = c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code
    private fun isDigit(c: Int) = c in '0'.code..'9'.code
    private fun hexVal(c: Int): Int = when (c) {
        in '0'.code..'9'.code -> c - '0'.code
        in 'a'.code..'f'.code -> c - 'a'.code + 10
        in 'A'.code..'F'.code -> c - 'A'.code + 10
        else -> -1
    }
}
