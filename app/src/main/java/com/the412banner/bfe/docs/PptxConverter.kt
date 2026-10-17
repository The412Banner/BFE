package com.the412banner.bfe.docs

import java.io.File

/**
 * PowerPoint .pptx / .pptm / .ppsx / .potx → one card per slide, in the presentation's order: the
 * title, then the other shapes top to bottom (text with bullets / numbering and levels, bold / italic
 * / underline / strike / colour / size, links), pictures, tables (with spans) and the speaker notes
 * folded underneath. Slide numbers, dates and footers are left out. Readable, not a slide render.
 */
class PptxConverter private constructor(private val zip: DocZip, private val assets: DocAssets, private val ns: OoxmlNs) {
    private val a = ns.a
    private val p = ns.p
    private var rels: Rels = Rels.EMPTY

    companion object {
        fun convert(file: File, fileName: String, assets: DocAssets): DocResult = DocZip(file).use { z ->
            val presPart = Rels.of(z, "").ofType("/officeDocument").firstOrNull()?.target?.takeIf { z.has(it) } ?: "ppt/presentation.xml"
            val pres = z.xml(presPart) ?: throw DocException("This isn't a PowerPoint file (ppt/presentation.xml is missing).")
            val ns = OoxmlNs.of(pres)
            val presRels = Rels.of(z, presPart)
            val c = PptxConverter(z, assets, ns)
            val sb = StringBuilder()
            var firstTitle: String? = null
            var n = 0
            pres.child(ns.p, "sldIdLst")?.children(ns.p, "sldId")?.forEach { id ->
                val rel = presRels[id.attr(ns.r, "id")] ?: return@forEach
                val slide = runCatching { z.xml(rel.target) }.getOrNull() ?: return@forEach
                if (slide.attr("show") == "0") return@forEach
                n++
                val t = c.slide(slide, rel.target, n, sb)
                if (firstTitle == null) firstTitle = t
            }
            if (n == 0) throw DocException("The presentation has no slides.")
            val title = coreTitle(z) ?: firstTitle ?: fileName
            DocResult(DocKind.PPTX, title, listOf(DocPage(title, sb.toString())))
        }

        private val SKIP_PLACEHOLDERS = setOf("sldNum", "dt", "ftr", "hdr", "sldImg")
    }

    private class Shape(val el: XEl, val y: Long?, val x: Long?, val title: Boolean)

    /** Renders one slide into [sb]; answers its title text. */
    private fun slide(slide: XEl, part: String, num: Int, sb: StringBuilder): String? {
        rels = Rels.of(zip, part)
        val tree = slide.child(p, "cSld")?.child(p, "spTree") ?: return null
        val shapes = ArrayList<Shape>()
        collect(tree, shapes)
        sb.append("<section class=\"slide\"><div class=\"num\">Slide ").append(num).append("</div>")
        var title: String? = null
        shapes.filter { it.title }.forEach { s ->
            val t = StringBuilder()
            textBody(s.el.child(p, "txBody"), t, bullets = false)
            val plain = Regex("<[^>]+>").replace(t.toString(), "").trim()
            if (plain.isNotEmpty()) {
                if (title == null) title = plain
                sb.append("<h2>").append(t.toString().removePrefix("<p>").removeSuffix("</p>").replace("</p><p>", "<br>")).append("</h2>")
            }
        }
        val rest = shapes.filter { !it.title }
        // Top to bottom when every shape has a position; placeholders that inherit theirs keep file order.
        val ordered = if (rest.all { it.y != null }) rest.sortedWith(compareBy({ it.y }, { it.x })) else rest
        for (s in ordered) shape(s.el, sb)
        notes(sb)
        sb.append("</section>")
        return title
    }

    private fun collect(tree: XEl, out: MutableList<Shape>) {
        for (e in tree.elements) {
            when {
                e.isA(p, "sp") -> {
                    val ph = e.child(p, "nvSpPr")?.child(p, "nvPr")?.child(p, "ph")
                    val type = ph?.attr("type")
                    if (type in SKIP_PLACEHOLDERS) continue
                    val off = e.child(p, "spPr")?.child(a, "xfrm")?.child(a, "off")
                    out.add(Shape(e, off?.attr("y")?.toLongOrNull(), off?.attr("x")?.toLongOrNull(), type == "title" || type == "ctrTitle"))
                }
                e.isA(p, "pic") || e.isA(p, "graphicFrame") -> {
                    val off = (e.child(p, "spPr")?.child(a, "xfrm") ?: e.child(p, "xfrm"))?.child(a, "off")
                    out.add(Shape(e, off?.attr("y")?.toLongOrNull(), off?.attr("x")?.toLongOrNull(), false))
                }
                e.isA(p, "grpSp") -> {
                    val off = e.child(p, "grpSpPr")?.child(a, "xfrm")?.child(a, "off")
                    out.add(Shape(e, off?.attr("y")?.toLongOrNull(), off?.attr("x")?.toLongOrNull(), false))
                }
                e.isA(Ns.MC, "AlternateContent") -> (e.child(Ns.MC, "Choice") ?: e.child(Ns.MC, "Fallback"))?.let { collect(it, out) }
            }
        }
    }

    private fun shape(e: XEl, sb: StringBuilder) {
        when {
            e.isA(p, "sp") -> {
                val ph = e.child(p, "nvSpPr")?.child(p, "nvPr")?.child(p, "ph")
                val type = ph?.attr("type")
                // Body placeholders are bulleted by the slide master even when the paragraphs don't say so.
                val bodyPlaceholder = ph != null && (type == null || type == "body" || type == "obj")
                if (type == "subTitle") {
                    val t = StringBuilder()
                    textBody(e.child(p, "txBody"), t, bullets = false)
                    if (t.isNotEmpty()) sb.append("<div class=\"subtitle\">").append(t).append("</div>")
                } else {
                    textBody(e.child(p, "txBody"), sb, bullets = bodyPlaceholder)
                }
                e.child(p, "spPr")?.child(a, "blipFill")?.child(a, "blip")?.let { sb.append("<p>").append(picture(it.attr(ns.r, "embed"))).append("</p>") }
            }
            e.isA(p, "pic") -> {
                val blip = e.child(p, "blipFill")?.child(a, "blip")
                val alt = e.child(p, "nvPicPr")?.child(p, "cNvPr")?.attr("descr").orEmpty()
                if (blip != null) sb.append("<p>").append(picture(blip.attr(ns.r, "embed"), alt)).append("</p>")
            }
            e.isA(p, "graphicFrame") -> {
                val data = e.child(a, "graphic")?.child(a, "graphicData")
                val tbl = data?.child(a, "tbl")
                when {
                    tbl != null -> table(tbl, sb)
                    data?.attr("uri")?.contains("chart") == true -> sb.append("<p><span class=\"noimg\">Chart</span></p>")
                    data?.attr("uri")?.contains("diagram") == true -> sb.append("<p><span class=\"noimg\">Diagram</span></p>")
                }
            }
            e.isA(p, "grpSp") -> {
                val inner = ArrayList<Shape>()
                collect(e, inner)
                val ordered = if (inner.all { it.y != null }) inner.sortedWith(compareBy({ it.y }, { it.x })) else inner
                ordered.forEach { shape(it.el, sb) }
            }
        }
    }

    /** A text body's paragraphs; bulleted / numbered ones become nested lists by their level. */
    private fun textBody(body: XEl?, sb: StringBuilder, bullets: Boolean) {
        body ?: return
        val lists = ListStack(sb)
        for (para in body.children(a, "p")) {
            val pPr = para.child(a, "pPr")
            val lvl = pPr?.attr("lvl")?.toIntOrNull() ?: 0
            val html = runs(para)
            val plain = Regex("<[^>]+>").replace(html, "").trim()
            val auto = pPr?.child(a, "buAutoNum")
            val char = pPr?.child(a, "buChar")
            val none = pPr?.child(a, "buNone") != null
            val listed = !none && (auto != null || char != null || bullets)
            if (plain.isEmpty() && !html.contains("<img")) { lists.closeAll(); continue }
            if (listed) {
                val type = olType(auto?.attr("type"))
                val start = auto?.attr("startAt")?.toIntOrNull()?.takeIf { it != 1 }
                val attrs = buildString {
                    if (type != null && type != "1") append(" type=\"").append(type).append('"')
                    if (start != null) append(" start=\"").append(start).append('"')
                }
                lists.item(lvl, auto != null, attrs, html)
            } else {
                lists.closeAll()
                val align = when (pPr?.attr("algn")) { "ctr" -> " class=\"c\""; "r" -> " class=\"r\""; "just" -> " class=\"j\""; else -> "" }
                sb.append("<p$align>").append(html).append("</p>")
            }
        }
        lists.closeAll()
    }

    private fun runs(para: XEl): String {
        val sb = StringBuilder()
        val w = RunWriter(sb)
        for (r in para.elements) {
            when {
                r.isA(a, "r") || r.isA(a, "fld") -> {
                    val rPr = r.child(a, "rPr")
                    val text = r.child(a, "t")?.text().orEmpty()
                    if (r.isA(a, "fld") && r.attr("type")?.startsWith("slidenum") == true) continue
                    val link = rPr?.child(a, "hlinkClick")?.let { h -> rels[h.attr(ns.r, "id")]?.takeIf { it.external }?.target?.let(::safeHref) }
                    val f = fmt(rPr).let { if (link != null) it.copy(underline = false, color = null) else it }
                    if (link != null) w.raw("<a href=\"${esc(link)}\">", RunWriter.PLAIN)
                    w.text(text, f)
                    if (link != null) w.raw("</a>", RunWriter.PLAIN)
                }
                r.isA(a, "br") -> w.raw("<br>")
            }
        }
        w.close()
        return sb.toString()
    }

    private fun fmt(rPr: XEl?): RunWriter.Fmt {
        rPr ?: return RunWriter.PLAIN
        fun flag(n: String) = rPr.attr(n)?.let { it == "1" || it == "true" }
        val baseline = rPr.attr("baseline")?.toIntOrNull() ?: 0
        val color = rPr.child(a, "solidFill")?.child(a, "srgbClr")?.attr("val")?.let(Colors::hex)
        return RunWriter.Fmt(
            bold = flag("b") == true, italic = flag("i") == true,
            underline = rPr.attr("u")?.let { it != "none" } == true,
            strike = rPr.attr("strike")?.let { it != "noStrike" } == true,
            sup = baseline > 0, sub = baseline < 0,
            color = color,
            // Slide text sizes (18–44 pt) read too big on a phone: only relative emphasis is kept.
            sizePt = rPr.attr("sz")?.toFloatOrNull()?.let { (it / 100f).let { pt -> if (pt > 28f) 16f else if (pt < 12f) 10f else null } },
        )
    }

    private fun picture(relId: String?, alt: String = ""): String {
        val rel = rels[relId] ?: return "<span class=\"noimg\">Picture</span>"
        if (rel.external) return "<span class=\"noimg\">Linked picture</span>"
        val bytes = runCatching { zip.bytes(rel.target) }.getOrNull() ?: return "<span class=\"noimg\">Picture</span>"
        val url = assets.put(rel.target, bytes) ?: return "<span class=\"noimg\">Picture (${esc(rel.target.substringAfterLast('.').uppercase())})</span>"
        return "<img src=\"${esc(url)}\" alt=\"${esc(alt)}\">"
    }

    private fun table(tbl: XEl, sb: StringBuilder) {
        sb.append("<div class=\"tw\"><table>")
        tbl.children(a, "tr").forEachIndexed { ri, tr ->
            sb.append("<tr>")
            for (tc in tr.children(a, "tc")) {
                if (tc.attr("hMerge") == "1" || tc.attr("vMerge") == "1") continue
                val tag = if (ri == 0) "th" else "td"
                sb.append('<').append(tag)
                tc.attr("gridSpan")?.toIntOrNull()?.takeIf { it > 1 }?.let { sb.append(" colspan=\"").append(it).append('"') }
                tc.attr("rowSpan")?.toIntOrNull()?.takeIf { it > 1 }?.let { sb.append(" rowspan=\"").append(it).append('"') }
                sb.append('>')
                val inner = StringBuilder()
                textBody(tc.child(a, "txBody"), inner, bullets = false)
                sb.append(unwrapSingleParagraph(inner.toString()))
                sb.append("</").append(tag).append('>')
            }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
    }

    private fun notes(sb: StringBuilder) {
        val rel = rels.ofType("/notesSlide").firstOrNull() ?: return
        val notes = runCatching { zip.xml(rel.target) }.getOrNull() ?: return
        val saved = rels
        rels = Rels.of(zip, rel.target)
        val out = StringBuilder()
        notes.child(p, "cSld")?.child(p, "spTree")?.elements?.forEach { sp ->
            val type = sp.child(p, "nvSpPr")?.child(p, "nvPr")?.child(p, "ph")?.attr("type")
            if (sp.isA(p, "sp") && type == "body") textBody(sp.child(p, "txBody"), out, bullets = false)
        }
        rels = saved
        if (out.isNotEmpty()) sb.append("<details class=\"notes\"><summary>Notes</summary>").append(out).append("</details>")
    }
}
