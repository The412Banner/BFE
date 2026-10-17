package com.the412banner.bfe.docs

import java.io.File

/**
 * Word .docx / .docm / .dotx → HTML: headings (heading styles, outline levels, Title / Subtitle),
 * paragraphs with alignment, runs with bold / italic / underline / strike / super- and subscript /
 * colour / highlight / shading / size and code fonts (direct formatting over character and paragraph
 * styles), numbered and bulleted lists from numbering.xml (nesting, number formats, numbering that
 * continues across interruptions), tables with merged cells (gridSpan / vMerge) and shading,
 * hyperlinks (relationships and HYPERLINK fields), field results, footnotes and endnotes, pictures
 * (inline and floating; EMF / WMF get a placeholder), text boxes, page breaks, content controls and
 * tracked insertions (deletions are dropped).
 */
class DocxConverter private constructor(
    private val zip: DocZip,
    private val assets: DocAssets,
    private val ns: OoxmlNs,
) {
    private val w = ns.w

    private class WStyle(
        val name: String, val basedOn: String?, val outline: Int?, val rPr: XEl?, val numId: String?, val ilvl: Int?,
    )

    private class Lvl(val fmt: String?, val start: Int)

    private val styles = HashMap<String, WStyle>()
    private val defaultParaStyle: String? by lazy { styles.entries.firstOrNull { it.value.name.equals("Normal", true) }?.key }
    private val numToAbstract = HashMap<String, String>()
    private val numStartOverride = HashMap<String, MutableMap<Int, Int>>()
    private val abstractLevels = HashMap<String, Map<Int, Lvl>>()
    private val counters = HashMap<String, IntArray>()   // abstract id → per-level counters

    private var rels: Rels = Rels.EMPTY
    private val footnotes = HashMap<String, XEl>()
    private val endnotes = HashMap<String, XEl>()
    private var footRels: Rels = Rels.EMPTY
    private var endRels: Rels = Rels.EMPTY
    private val notes = ArrayList<String>()
    private var firstHeading: String? = null
    private val pendingAnchors = StringBuilder()

    // Complex fields span runs (and paragraphs): begin → instruction → separate → result → end.
    private var fieldDepth = 0
    private var inInstr = false
    private val instr = StringBuilder()
    private var fieldLinkOpen = false
    private var fieldLinkDepth = 0
    /** Inside a hyperlink: its runs drop Word's "Hyperlink" blue + underline (the page styles links). */
    private var linkDepth = 0

    companion object {
        fun convert(file: File, fileName: String, assets: DocAssets): DocResult = DocZip(file).use { z ->
            val docPart = Rels.of(z, "").ofType("/officeDocument").firstOrNull()?.target?.takeIf { z.has(it) }
                ?: "word/document.xml"
            val root = z.xml(docPart) ?: throw DocException("This isn't a Word document (word/document.xml is missing).")
            val c = DocxConverter(z, assets, OoxmlNs.of(root))
            c.load(docPart)
            val body = root.child(c.w, "body") ?: throw DocException("The document has no body.")
            val sb = StringBuilder()
            c.blocks(body, sb)
            c.appendNotes(sb)
            val title = coreTitle(z) ?: c.firstHeading ?: fileName
            DocResult(DocKind.DOCX, title, listOf(DocPage(title, sb.toString())))
        }
    }

    private fun load(docPart: String) {
        rels = Rels.of(zip, docPart)
        val stylesPart = rels.ofType("/styles").firstOrNull()?.target ?: "word/styles.xml"
        runCatching { zip.xml(stylesPart) }.getOrNull()?.let { root ->
            for (s in root.children(w, "style")) {
                val id = s.attr(w, "styleId") ?: continue
                val pPr = s.child(w, "pPr")
                val numPr = pPr?.child(w, "numPr")
                styles[id] = WStyle(
                    name = s.child(w, "name")?.attr(w, "val") ?: id,
                    basedOn = s.child(w, "basedOn")?.attr(w, "val"),
                    outline = pPr?.child(w, "outlineLvl")?.attr(w, "val")?.toIntOrNull(),
                    rPr = s.child(w, "rPr"),
                    numId = numPr?.child(w, "numId")?.attr(w, "val"),
                    ilvl = numPr?.child(w, "ilvl")?.attr(w, "val")?.toIntOrNull(),
                )
            }
        }
        val numPart = rels.ofType("/numbering").firstOrNull()?.target ?: "word/numbering.xml"
        runCatching { zip.xml(numPart) }.getOrNull()?.let { root ->
            for (a in root.children(w, "abstractNum")) {
                val id = a.attr(w, "abstractNumId") ?: continue
                abstractLevels[id] = a.children(w, "lvl").associate { l ->
                    (l.attr(w, "ilvl")?.toIntOrNull() ?: 0) to Lvl(
                        l.child(w, "numFmt")?.attr(w, "val"),
                        l.child(w, "start")?.attr(w, "val")?.toIntOrNull() ?: 1,
                    )
                }
            }
            for (n in root.children(w, "num")) {
                val id = n.attr(w, "numId") ?: continue
                numToAbstract[id] = n.child(w, "abstractNumId")?.attr(w, "val") ?: continue
                n.children(w, "lvlOverride").forEach { o ->
                    val lvl = o.attr(w, "ilvl")?.toIntOrNull() ?: return@forEach
                    o.child(w, "startOverride")?.attr(w, "val")?.toIntOrNull()?.let { numStartOverride.getOrPut(id) { HashMap() }[lvl] = it }
                }
            }
        }
        fun notesFrom(type: String, fallback: String, into: HashMap<String, XEl>): Rels {
            val part = rels.ofType(type).firstOrNull()?.target ?: fallback
            val root = runCatching { zip.xml(part) }.getOrNull() ?: return Rels.EMPTY
            root.elements.forEach { n ->
                val t = n.attr(w, "type")
                if (t == null || t == "normal") n.attr(w, "id")?.let { into[it] = n }
            }
            return Rels.of(zip, part)
        }
        footRels = notesFrom("/footnotes", "word/footnotes.xml", footnotes)
        endRels = notesFrom("/endnotes", "word/endnotes.xml", endnotes)
    }

    // ── Styles ──

    private fun styleChain(id: String?): List<WStyle> {
        val out = ArrayList<WStyle>()
        var cur = id
        var guard = 0
        while (cur != null && guard++ < 20) {
            val s = styles[cur] ?: break
            out.add(s)
            cur = s.basedOn
        }
        return out
    }

    /** 1–6 heading, 0 Title, -1 Subtitle, null otherwise. */
    private fun headingLevel(pPr: XEl?, styleId: String?): Int? {
        pPr?.child(w, "outlineLvl")?.attr(w, "val")?.toIntOrNull()?.let { if (it in 0..5) return it + 1 }
        for (s in styleChain(styleId)) {
            val n = s.name.lowercase()
            Regex("^heading\\s*(\\d)$").find(n)?.let { return it.groupValues[1].toInt().coerceIn(1, 6) }
            if (n == "title") return 0
            if (n == "subtitle") return -1
            s.outline?.let { if (it in 0..5) return it + 1 }
        }
        return null
    }

    private fun on(e: XEl?): Boolean? {
        e ?: return null
        val v = e.attr(w, "val") ?: return true
        return !(v == "0" || v.equals("false", true) || v.equals("off", true) || v == "none")
    }

    /** [rPr] applied over [base]. */
    private fun applyRPr(base: RunWriter.Fmt, rPr: XEl?, withSize: Boolean = true): RunWriter.Fmt {
        rPr ?: return base
        var f = base
        on(rPr.child(w, "b"))?.let { f = f.copy(bold = it) }
        on(rPr.child(w, "i"))?.let { f = f.copy(italic = it) }
        rPr.child(w, "u")?.let { u -> f = f.copy(underline = u.attr(w, "val").let { it == null || it != "none" }) }
        (on(rPr.child(w, "strike")) ?: on(rPr.child(w, "dstrike")))?.let { f = f.copy(strike = it) }
        rPr.child(w, "vertAlign")?.attr(w, "val")?.let { v -> f = f.copy(sup = v == "superscript", sub = v == "subscript") }
        rPr.child(w, "color")?.attr(w, "val")?.let { v -> f = f.copy(color = Colors.hex(v)) }
        val hl = Colors.highlight(rPr.child(w, "highlight")?.attr(w, "val"))
            ?: Colors.hex(rPr.child(w, "shd")?.attr(w, "fill"))?.takeIf { it != "#ffffff" }
        if (hl != null) f = f.copy(bg = hl)
        if (withSize) rPr.child(w, "sz")?.attr(w, "val")?.toFloatOrNull()?.let { f = f.copy(sizePt = it / 2f) }
        rPr.child(w, "rFonts")?.let { rf ->
            val font = rf.attr(w, "ascii") ?: rf.attr(w, "hAnsi")
            if (font != null) f = f.copy(code = isMono(font))
        }
        return f
    }

    private fun isMono(font: String) = Regex("(?i)courier|consolas|mono|menlo|lucida console|source code").containsMatchIn(font)

    private fun isHidden(rPr: XEl?) = on(rPr?.child(w, "vanish")) == true || on(rPr?.child(w, "webHidden")) == true

    // ── Blocks ──

    private fun blocks(container: XEl, sb: StringBuilder) {
        val lists = ListStack(sb)
        for (e in container.elements) block(e, sb, lists)
        lists.closeAll()
    }

    private fun block(e: XEl, sb: StringBuilder, lists: ListStack) {
        when {
            e.isA(w, "p") -> paragraph(e, sb, lists)
            e.isA(w, "tbl") -> { lists.closeAll(); table(e, sb) }
            e.isA(w, "sdt") -> e.child(w, "sdtContent")?.elements?.forEach { block(it, sb, lists) }
            e.isA(w, "customXml") || e.isA(w, "ins") || e.isA(w, "smartTag") -> e.elements.forEach { block(it, sb, lists) }
            e.isA(Ns.MC, "AlternateContent") -> alternate(e)?.elements?.forEach { block(it, sb, lists) }
            // A bookmark between paragraphs anchors the next one (kept inside it so lists aren't broken).
            e.isA(w, "bookmarkStart") -> e.attr(w, "name")?.takeIf { !it.startsWith("_GoBack") }?.let { pendingAnchors.append("<a id=\"${esc(it)}\"></a>") }
            else -> Unit
        }
    }

    /** mc:AlternateContent: the first Choice (what Office itself shows), else the Fallback. */
    private fun alternate(e: XEl): XEl? = e.child(Ns.MC, "Choice") ?: e.child(Ns.MC, "Fallback")

    private class ParaOut(val html: StringBuilder = StringBuilder(), var breakBefore: Boolean = false, var breakAfter: Boolean = false, val after: StringBuilder = StringBuilder()) {
        var visible = false
    }

    private fun paragraph(p: XEl, sb: StringBuilder, lists: ListStack) {
        val pPr = p.child(w, "pPr")
        val styleId = pPr?.child(w, "pStyle")?.attr(w, "val") ?: defaultParaStyle
        val chain = styleChain(styleId)
        val level = headingLevel(pPr, styleId)
        // Paragraph-style run properties are the base; headings take size and weight from the stylesheet.
        var base = RunWriter.PLAIN
        for (s in chain.asReversed()) base = applyRPr(base, s.rPr, withSize = false)
        if (level != null) base = base.copy(bold = false, sizePt = null)
        val out = ParaOut()
        if (on(pPr?.child(w, "pageBreakBefore")) == true) out.breakBefore = true
        if (pendingAnchors.isNotEmpty()) { out.html.append(pendingAnchors); pendingAnchors.setLength(0) }
        val runs = RunWriter(out.html)
        inlines(p, base, runs, out, level != null)
        runs.close()
        if (fieldLinkOpen) { out.html.append("</a>"); fieldLinkOpen = false }

        val html = out.html.toString()
        val align = when (pPr?.child(w, "jc")?.attr(w, "val")) {
            "center" -> " class=\"c\""; "right", "end" -> " class=\"r\""; "both", "distribute" -> " class=\"j\""; else -> ""
        }
        // Numbering: the paragraph's own numPr, else its style's.
        val numPr = pPr?.child(w, "numPr")
        val numId = numPr?.child(w, "numId")?.attr(w, "val") ?: chain.firstNotNullOfOrNull { it.numId }
        val ilvl = numPr?.child(w, "ilvl")?.attr(w, "val")?.toIntOrNull() ?: chain.firstNotNullOfOrNull { it.ilvl } ?: 0
        // A level whose number format is "none" is an indented paragraph, not a list item.
        val abstractId = numId?.takeIf { it != "0" }?.let { numToAbstract[it] }
            ?.takeIf { abstractLevels[it]?.get(ilvl)?.fmt != "none" }

        if (out.breakBefore) { lists.closeAll(); sb.append("<hr class=\"pb\">") }
        if (abstractId != null && level == null) {
            val lvl = abstractLevels[abstractId]?.get(ilvl)
            val type = olType(lvl?.fmt)
            val c = counters.getOrPut(abstractId) { IntArray(9) }
            val li = ilvl.coerceIn(0, 8)
            val start = numId?.let { numStartOverride[it] }?.get(li) ?: lvl?.start ?: 1
            c[li] = if (c[li] == 0) start else c[li] + 1
            for (k in li + 1 until 9) c[k] = 0
            val attrs = if (type == null) "" else buildString {
                if (type != "1") append(" type=\"").append(type).append('"')
                if (c[li] != 1) append(" start=\"").append(c[li]).append('"')
            }
            lists.item(li, type != null, attrs, html)
        } else {
            lists.closeAll()
            val plain = Regex("<[^>]+>").replace(html, "").trim()
            when {
                !out.visible && plain.isEmpty() -> if (!out.breakBefore && !out.breakAfter) sb.append("<p class=\"e\"></p>")
                level == 0 -> { if (firstHeading == null) firstHeading = plain; sb.append("<p class=\"title\"$align>").append(html).append("</p>") }
                level == -1 -> sb.append("<p class=\"subtitle\"$align>").append(html).append("</p>")
                level != null -> { if (firstHeading == null) firstHeading = plain; sb.append("<h$level$align>").append(html).append("</h$level>") }
                styleId != null && styleChain(styleId).any { it.name.equals("Quote", true) || it.name.equals("Intense Quote", true) || it.name.equals("Block Text", true) } ->
                    sb.append("<blockquote><p>").append(html).append("</p></blockquote>")
                else -> sb.append("<p$align>").append(html).append("</p>")
            }
        }
        if (out.after.isNotEmpty()) { lists.closeAll(); sb.append(out.after) }
        if (out.breakAfter) { lists.closeAll(); sb.append("<hr class=\"pb\">") }
    }

    /** Paragraph content: runs, links, fields, insertions, content controls. */
    private fun inlines(e: XEl, base: RunWriter.Fmt, runs: RunWriter, out: ParaOut, heading: Boolean) {
        for (c in e.elements) {
            when {
                c.isA(w, "r") -> run(c, base, runs, out, heading)
                c.isA(w, "hyperlink") -> {
                    val href = rels[c.attr(ns.r, "id")]?.takeIf { it.external }?.target
                        ?: c.attr(w, "anchor")?.let { "#$it" }
                    val safe = href?.let(::safeHref)
                    if (safe != null) { runs.raw("<a href=\"${esc(safe)}\">", RunWriter.PLAIN); linkDepth++ }
                    inlines(c, base, runs, out, heading)
                    if (safe != null) { runs.raw("</a>", RunWriter.PLAIN); linkDepth-- }
                }
                c.isA(w, "fldSimple") -> {
                    val href = hyperlinkOf(c.attr(w, "instr").orEmpty())
                    if (href != null) { runs.raw("<a href=\"${esc(href)}\">", RunWriter.PLAIN); linkDepth++ }
                    inlines(c, base, runs, out, heading)
                    if (href != null) { runs.raw("</a>", RunWriter.PLAIN); linkDepth-- }
                }
                c.isA(w, "ins") || c.isA(w, "smartTag") || c.isA(w, "customXml") || c.isA(w, "dir") || c.isA(w, "bdo") -> inlines(c, base, runs, out, heading)
                c.isA(w, "sdt") -> c.child(w, "sdtContent")?.let { inlines(it, base, runs, out, heading) }
                c.isA(Ns.MC, "AlternateContent") -> alternate(c)?.let { inlines(it, base, runs, out, heading) }
                c.isA(w, "bookmarkStart") -> c.attr(w, "name")?.takeIf { !it.startsWith("_GoBack") }?.let { runs.raw("<a id=\"${esc(it)}\"></a>", RunWriter.PLAIN) }
                c.name == "oMath" || c.name == "oMathPara" -> { runs.text(c.descendants().filter { it.name == "t" }.joinToString("") { it.text() }, base.copy(italic = true)); out.visible = true }
                else -> Unit  // w:del, w:proofErr, w:pPr, comments…
            }
        }
    }

    private fun hyperlinkOf(instruction: String): String? {
        val i = instruction.trim()
        if (!i.startsWith("HYPERLINK", ignoreCase = true)) return null
        val q = Regex("\"([^\"]*)\"").find(i)?.groupValues?.get(1) ?: return null
        return safeHref(if (Regex("\\\\l\\b").containsMatchIn(i)) "#$q" else q)
    }

    private fun run(r: XEl, base: RunWriter.Fmt, runs: RunWriter, out: ParaOut, heading: Boolean) {
        val rPr = r.child(w, "rPr")
        if (isHidden(rPr)) return
        var f = base
        rPr?.child(w, "rStyle")?.attr(w, "val")?.let { id -> for (s in styleChain(id).asReversed()) f = applyRPr(f, s.rPr) }
        f = applyRPr(f, rPr, withSize = !heading)
        if (heading) f = f.copy(sizePt = null)
        runContent(r, f, runs, out)
    }

    private fun runContent(r: XEl, f0: RunWriter.Fmt, runs: RunWriter, out: ParaOut) {
        val f = if (linkDepth > 0 || fieldLinkOpen) f0.copy(underline = false, color = null) else f0
        for (c in r.elements) {
            when {
                c.isA(w, "fldChar") -> when (c.attr(w, "fldCharType")) {
                    "begin" -> { fieldDepth++; inInstr = true; instr.setLength(0) }
                    "separate" -> {
                        inInstr = false
                        if (!fieldLinkOpen && linkDepth == 0) hyperlinkOf(instr.toString())?.let {
                            runs.raw("<a href=\"${esc(it)}\">", RunWriter.PLAIN)
                            fieldLinkOpen = true
                            fieldLinkDepth = fieldDepth
                        }
                    }
                    "end" -> {
                        if (fieldLinkOpen && fieldDepth == fieldLinkDepth) { runs.raw("</a>", RunWriter.PLAIN); fieldLinkOpen = false }
                        if (fieldDepth > 0) fieldDepth--
                        inInstr = false
                    }
                }
                c.isA(w, "instrText") -> if (inInstr) instr.append(c.text())
                inInstr -> Unit
                c.isA(w, "t") -> { val t = c.text(); if (t.isNotEmpty()) { runs.text(t, f); if (t.isNotBlank()) out.visible = true } }
                c.isA(w, "tab") || c.isA(w, "ptab") -> runs.text("\u2003", f)
                c.isA(w, "br") -> when (c.attr(w, "type")) {
                    "page" -> if (out.visible) out.breakAfter = true else out.breakBefore = true
                    "column" -> Unit
                    else -> runs.raw("<br>", f)
                }
                c.isA(w, "cr") -> runs.raw("<br>", f)
                c.isA(w, "noBreakHyphen") -> runs.text("\u2011", f)
                c.isA(w, "sym") -> symbol(c)?.let { runs.text(it, f) }
                c.isA(w, "drawing") -> drawing(c, runs, out)
                c.isA(w, "pict") || c.isA(w, "object") -> vml(c, runs, out)
                c.isA(Ns.MC, "AlternateContent") -> alternate(c)?.let { runContent(it, f0, runs, out) }
                c.isA(w, "footnoteReference") -> noteRef(c.attr(w, "id"), footnotes, footRels, runs)
                c.isA(w, "endnoteReference") -> noteRef(c.attr(w, "id"), endnotes, endRels, runs)
                else -> Unit  // footnoteRef marks, lastRenderedPageBreak, softHyphen, rPr, delText
            }
        }
    }

    private fun symbol(sym: XEl): String? {
        val font = sym.attr(w, "font").orEmpty()
        val code = sym.attr(w, "char")?.toIntOrNull(16) ?: return null
        if (font.contains("Symbol", true) || font.contains("Wingdings", true) || font.contains("Webdings", true)) {
            return when (code and 0xFF) { 0xB7 -> "•"; 0xA7 -> "▪"; 0xFC -> "✓"; 0xD8 -> "➢"; else -> null }
        }
        return String(Character.toChars(code))
    }

    private fun noteRef(id: String?, from: Map<String, XEl>, noteRels: Rels, runs: RunWriter) {
        val note = from[id ?: return] ?: return
        val n = notes.size + 1
        val saved = rels
        rels = noteRels
        val body = StringBuilder()
        // A footnote's own fields, lists and pictures are independent of the body's.
        val savedDepth = fieldDepth; val savedInstr = inInstr; val savedLink = fieldLinkOpen
        fieldDepth = 0; inInstr = false; fieldLinkOpen = false
        blocks(note, body)
        fieldDepth = savedDepth; inInstr = savedInstr; fieldLinkOpen = savedLink
        rels = saved
        notes.add(body.toString().replaceFirst(Regex("</p>\\s*$"), " <a href=\"#fnref-$n\">↩</a></p>"))
        runs.raw("<sup class=\"fn\"><a id=\"fnref-$n\" href=\"#fn-$n\">$n</a></sup>", RunWriter.PLAIN)
    }

    private fun appendNotes(sb: StringBuilder) {
        if (notes.isEmpty()) return
        sb.append("<section class=\"footnotes\"><hr><ol>")
        notes.forEachIndexed { i, n -> sb.append("<li id=\"fn-${i + 1}\">").append(n.trim()).append("</li>") }
        sb.append("</ol></section>")
    }

    // ── Pictures and text boxes ──

    private fun drawing(d: XEl, runs: RunWriter, out: ParaOut) {
        val extent = d.descendants().firstOrNull { it.name == "extent" && it.ns == Ns.WP }
        val alt = d.descendants().firstOrNull { it.name == "docPr" }?.let { it.attr("descr") ?: it.attr("title") }.orEmpty()
        val blip = d.descendants().firstOrNull { it.name == "blip" && it.ns == ns.a }
        if (blip != null) {
            runs.raw(picture(blip.attr(ns.r, "embed") ?: blip.attr(ns.r, "link"), emuToPx(extent?.attr("cx")), alt), RunWriter.PLAIN)
            out.visible = true
        }
        d.descendants().filter { it.isA(w, "txbxContent") }.forEach { blocks(it, out.after) }
        if (blip == null && d.descendants().any { it.name == "chart" }) { runs.raw("<span class=\"noimg\">Chart</span>", RunWriter.PLAIN); out.visible = true }
    }

    private fun vml(e: XEl, runs: RunWriter, out: ParaOut) {
        e.descendants().filter { it.ns == Ns.V && it.name == "imagedata" }.forEach { img ->
            runs.raw(picture(img.attr(ns.r, "id") ?: img.attr(ns.r, "pict"), null, img.attr("title").orEmpty()), RunWriter.PLAIN)
            out.visible = true
        }
        e.descendants().filter { it.isA(w, "txbxContent") }.forEach { blocks(it, out.after) }
    }

    private fun picture(relId: String?, widthPx: Int?, alt: String): String {
        val rel = rels[relId] ?: return "<span class=\"noimg\">Picture</span>"
        if (rel.external) return "<span class=\"noimg\">Linked picture</span>"
        val bytes = runCatching { zip.bytes(rel.target) }.getOrNull() ?: return "<span class=\"noimg\">Picture</span>"
        val url = assets.put(rel.target, bytes)
            ?: return "<span class=\"noimg\">Picture (${esc(rel.target.substringAfterLast('.').uppercase())})</span>"
        val size = if (widthPx != null) " style=\"width:${widthPx}px\"" else ""
        return "<img src=\"${esc(url)}\"$size alt=\"${esc(alt)}\">"
    }

    // ── Tables ──

    private class TCell(val el: XEl, val col: Int, val span: Int, val merge: String?) {
        var rowspan = 1
    }

    private fun table(t: XEl, sb: StringBuilder) {
        val rows = ArrayList<Pair<Boolean, List<TCell>>>()
        fun collectRows(container: XEl) {
            for (tr in container.elements) {
                when {
                    tr.isA(w, "tr") -> {
                        var col = 0
                        val cells = ArrayList<TCell>()
                        val trPr = tr.child(w, "trPr")
                        // Cells skipped at the start of a row (gridBefore) shift the rest.
                        col += trPr?.child(w, "gridBefore")?.attr(w, "val")?.toIntOrNull() ?: 0
                        fun addCell(tc: XEl) {
                            val tcPr = tc.child(w, "tcPr")
                            val span = (tcPr?.child(w, "gridSpan")?.attr(w, "val")?.toIntOrNull() ?: 1).coerceAtLeast(1)
                            val vm = tcPr?.child(w, "vMerge")?.let { it.attr(w, "val") ?: "continue" }
                            cells.add(TCell(tc, col, span, vm))
                            col += span
                        }
                        for (c in tr.elements) {
                            when {
                                c.isA(w, "tc") -> addCell(c)
                                c.isA(w, "sdt") -> c.child(w, "sdtContent")?.children(w, "tc")?.forEach(::addCell)
                                c.isA(w, "customXml") -> c.children(w, "tc").forEach(::addCell)
                            }
                        }
                        rows.add((on(trPr?.child(w, "tblHeader")) == true) to cells)
                    }
                    tr.isA(w, "sdt") -> tr.child(w, "sdtContent")?.let(::collectRows)
                    tr.isA(w, "customXml") -> collectRows(tr)
                }
            }
        }
        collectRows(t)
        if (rows.isEmpty()) return
        // Vertical merges: a "restart" cell spans the "continue" cells below it in the same column.
        for ((ri, row) in rows.withIndex()) {
            for (c in row.second) {
                if (c.merge != "restart") continue
                var k = ri + 1
                while (k < rows.size && rows[k].second.any { it.col == c.col && it.merge == "continue" }) { c.rowspan++; k++ }
            }
        }
        sb.append("<div class=\"tw\"><table>")
        for ((header, cells) in rows) {
            sb.append("<tr>")
            for (c in cells) {
                if (c.merge == "continue") continue
                val tag = if (header) "th" else "td"
                sb.append('<').append(tag)
                if (c.span > 1) sb.append(" colspan=\"").append(c.span).append('"')
                if (c.rowspan > 1) sb.append(" rowspan=\"").append(c.rowspan).append('"')
                Colors.hex(c.el.child(w, "tcPr")?.child(w, "shd")?.attr(w, "fill"))?.takeIf { it != "#ffffff" && it != "#000000" }?.let {
                    sb.append(" style=\"background-color:").append(it).append('"')
                }
                sb.append('>')
                val inner = StringBuilder()
                blocks(c.el, inner)
                sb.append(unwrapSingleParagraph(inner.toString()))
                sb.append("</").append(tag).append('>')
            }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
    }
}
