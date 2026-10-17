package com.the412banner.bfe.docs

import java.io.File
import java.nio.charset.Charset

/**
 * EPUB 2 / 3 → one page per spine chapter, in reading order, plus the table of contents (EPUB 3 nav
 * document, else the EPUB 2 NCX). Each chapter's body is kept as the book wrote it, with: pictures
 * (also SVG covers) pulled out through [DocAssets]; links between chapters pointed at the viewer's
 * page files ([pageFile]); scripts, frames and event handlers removed; and the book's CSS filtered to
 * layout only (alignment, emphasis, indents, margins) so BFE's reading style sets fonts and colours
 * in both themes.
 */
object EpubConverter {
    /** The file name the viewer writes page [i] as (chapter links point here). */
    fun pageFile(i: Int) = "page-$i.html"

    private class Item(val id: String, val path: String, val type: String, val props: String)

    fun convert(file: File, fileName: String, assets: DocAssets): DocResult = DocZip(file).use { z ->
        val container = z.xml("META-INF/container.xml") ?: throw DocException("This isn't an EPUB book (META-INF/container.xml is missing).")
        val opfPath = container.descendants().firstOrNull { it.name == "rootfile" && (it.attr("media-type") == null || it.attr("media-type") == "application/oebps-package+xml") }
            ?.attr("full-path") ?: throw DocException("The book's package file is missing.")
        val opf = z.xml(opfPath) ?: throw DocException("The book's package file ($opfPath) is missing.")
        val title = opf.descendants().firstOrNull { it.ns == Ns.DC && it.name == "title" }?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val items = HashMap<String, Item>()
        opf.descendants().filter { it.name == "item" }.forEach { e ->
            val id = e.attr("id") ?: return@forEach
            val href = e.attr("href") ?: return@forEach
            items[id] = Item(id, resolveZipPath(opfPath, href), e.attr("media-type").orEmpty(), e.attr("properties").orEmpty())
        }
        val spineEl = opf.descendants().firstOrNull { it.name == "spine" } ?: throw DocException("The book has no reading order (spine).")
        val spine = spineEl.elements.filter { it.name == "itemref" }
            .mapNotNull { ref -> ref.attr("idref")?.let { items[it] } }
            .filter { it.type.contains("html") || it.type.isEmpty() || it.path.endsWith(".xhtml") || it.path.endsWith(".html") || it.path.endsWith(".htm") }
            .distinctBy { it.path }
            .toList()
        if (spine.isEmpty()) throw DocException("The book has no chapters.")
        val pageOf = spine.withIndex().associate { (i, it) -> it.path to i }

        val toc = navToc(z, items.values.firstOrNull { it.props.split(' ').contains("nav") }, pageOf)
            .ifEmpty { ncxToc(z, spineEl.attr("toc")?.let { items[it] } ?: items.values.firstOrNull { it.type == "application/x-dtbncx+xml" }, pageOf) }

        val cssCache = HashMap<String, String>()
        val pages = spine.mapIndexed { i, item ->
            val raw = z.bytes(item.path) ?: return@mapIndexed DocPage("Chapter ${i + 1}", "<p class=\"subtitle\">This chapter is missing from the book.</p>")
            val src = decode(raw)
            val css = StringBuilder()
            headStyles(src, item.path, z, cssCache, css)
            val body = chapterBody(src, item.path, z, assets, pageOf, i, css)
            val pageTitle = toc.firstOrNull { it.page == i }?.title
                ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(src)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != title }
                ?: Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body)?.groupValues?.get(1)
                    ?.let { Regex("<[^>]+>").replace(it, "").trim() }?.takeIf { it.isNotEmpty() }
                ?: "Chapter ${i + 1}"
            DocPage(unescape(pageTitle), body, css.toString())
        }
        DocResult(DocKind.EPUB, title ?: fileName, pages, toc)
    }

    /** Text in the encoding the XML declaration names (UTF-8 unless it says otherwise). */
    private fun decode(raw: ByteArray): String {
        val head = String(raw, 0, minOf(raw.size, 200), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([A-Za-z0-9._-]+)[\"']").find(head)?.groupValues?.get(1)
        val cs = enc?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(raw, cs).removePrefix("\uFEFF")
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    // ── Table of contents ──

    private fun navToc(z: DocZip, nav: Item?, pageOf: Map<String, Int>): List<TocEntry> {
        nav ?: return emptyList()
        val root = runCatching { z.bytes(nav.path)?.let { Xml.parse(decode(it).byteInputStream()) } }.getOrNull() ?: return emptyList()
        val navs = root.descendants().filter { it.name == "nav" }.toList()
        val tocNav = navs.firstOrNull { it.attr(Ns.EPUB, "type")?.split(' ')?.contains("toc") == true } ?: navs.firstOrNull() ?: return emptyList()
        val out = ArrayList<TocEntry>()
        fun walk(ol: XEl, depth: Int) {
            for (li in ol.elements.filter { it.name == "li" }) {
                val a = li.elements.firstOrNull { it.name == "a" || it.name == "span" }
                val label = a?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val href = a?.attr("href")
                if (label.isNotEmpty() && href != null) {
                    val path = resolveZipPath(nav.path, href)
                    pageOf[path]?.let { out.add(TocEntry(label, it, href.substringAfter('#', "").ifEmpty { null }, depth)) }
                }
                li.elements.firstOrNull { it.name == "ol" || it.name == "ul" }?.let { walk(it, depth + 1) }
            }
        }
        tocNav.descendants().firstOrNull { it.name == "ol" || it.name == "ul" }?.let { walk(it, 0) }
        return out
    }

    private fun ncxToc(z: DocZip, ncx: Item?, pageOf: Map<String, Int>): List<TocEntry> {
        ncx ?: return emptyList()
        val root = runCatching { z.xml(ncx.path) }.getOrNull() ?: return emptyList()
        val out = ArrayList<TocEntry>()
        fun walk(e: XEl, depth: Int) {
            for (np in e.elements.filter { it.name == "navPoint" }) {
                val label = np.child(Ns.NCX, "navLabel")?.text()?.replace(Regex("\\s+"), " ")?.trim()
                    ?: np.elements.firstOrNull { it.name == "navLabel" }?.text()?.trim().orEmpty()
                val src = np.elements.firstOrNull { it.name == "content" }?.attr("src")
                if (!label.isNullOrEmpty() && src != null) {
                    pageOf[resolveZipPath(ncx.path, src)]?.let { out.add(TocEntry(label, it, src.substringAfter('#', "").ifEmpty { null }, depth)) }
                }
                walk(np, depth + 1)
            }
        }
        root.descendants().firstOrNull { it.name == "navMap" }?.let { walk(it, 0) }
        return out
    }

    // ── Chapters ──

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr",
        "image", "path", "circle", "rect", "line", "polyline", "polygon", "ellipse", "use", "stop",
    )
    private val SELF_CLOSING = Regex("<([A-Za-z][\\w:.-]*)((?:\\s[^<>]*?)?)\\s*/>")
    private val TAG = Regex("<([A-Za-z][\\w:.-]*)(\\s[^<>]*)?>")
    private val ATTR = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'>]+)")
    private val DROP_BLOCKS = Regex("<(script|style|iframe|object|embed|noscript)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val DROP_TAGS = Regex("</?(script|iframe|object|embed|frame|frameset|base|meta|link)\\b[^>]*>", RegexOption.IGNORE_CASE)

    private fun chapterBody(src: String, path: String, z: DocZip, assets: DocAssets, pageOf: Map<String, Int>, self: Int, css: StringBuilder): String {
        val bodyMatch = Regex("<body\\b[^>]*>(.*)</body\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(src)
        var html = bodyMatch?.groupValues?.get(1) ?: src.substringAfter("</head>", src)
        // In-body <style> blocks join the (filtered) book CSS; the rest of the dangerous blocks go.
        Regex("<style\\b[^>]*>(.*?)</style\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html).forEach { css.append(filterCss(it.groupValues[1])) }
        html = DROP_BLOCKS.replace(html, "")
        html = DROP_TAGS.replace(html, "")
        // XHTML's <span id="p12"/> means an empty element; an HTML parser would read an open tag.
        html = SELF_CLOSING.replace(html) { m ->
            val name = m.groupValues[1]
            if (name.lowercase() in VOID || name.contains(':')) m.value else "<$name${m.groupValues[2]}></$name>"
        }
        val images = HashMap<String, String?>()
        html = TAG.replace(html) { m -> rewriteTag(m.groupValues[1], m.groupValues[2], path, z, assets, pageOf, self, images) }
        return html
    }

    private fun rewriteTag(name: String, attrs: String, path: String, z: DocZip, assets: DocAssets, pageOf: Map<String, Int>, self: Int, images: MutableMap<String, String?>): String {
        if (attrs.isBlank()) return "<$name>"
        val lname = name.lowercase()
        val sb = StringBuilder("<").append(name)
        for (m in ATTR.findAll(attrs)) {
            val key = m.groupValues[1]
            val lkey = key.lowercase()
            var value = m.groupValues[2].let { if (it.startsWith("\"") || it.startsWith("'")) it.substring(1, it.length - 1) else it }
            if (lkey.startsWith("on") || lkey == "xmlns" || lkey.startsWith("xmlns:")) continue
            when {
                lkey == "style" -> {
                    val kept = filterDecls(value)
                    if (kept.isEmpty()) continue
                    value = kept
                }
                (lkey == "src" && (lname == "img" || lname == "source")) || ((lkey == "xlink:href" || lkey == "href") && lname == "image") -> {
                    if (value.startsWith("data:image/")) Unit
                    else {
                        val target = resolveZipPath(path, unescape(value))
                        value = images.getOrPut(target) { z.bytes(target)?.let { assets.put(target, it) } } ?: continue
                    }
                }
                lkey == "href" && lname == "a" -> value = linkTarget(unescape(value), path, pageOf, self) ?: continue
                lkey == "srcset" -> continue
            }
            sb.append(' ').append(key).append("=\"").append(value.replace("\"", "&quot;")).append('"')
        }
        if (attrs.trimEnd().endsWith("/")) sb.append(" /")
        return sb.append('>').toString()
    }

    private fun linkTarget(href: String, path: String, pageOf: Map<String, Int>, self: Int): String? {
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(href)) return safeHref(href)
        if (href.startsWith("#")) return href
        val frag = href.substringAfter('#', "")
        val page = pageOf[resolveZipPath(path, href)] ?: return null
        val anchor = if (frag.isEmpty()) "" else "#$frag"
        return if (page == self) anchor.ifEmpty { "#" } else pageFile(page) + anchor
    }

    // ── The book's CSS, filtered to layout ──

    private val KEEP_PROPS = setOf(
        "text-align", "font-style", "font-weight", "font-variant", "text-decoration", "text-indent", "text-transform",
        "margin", "margin-top", "margin-bottom", "margin-left", "margin-right", "padding-left", "padding-right",
        "display", "vertical-align", "list-style", "list-style-type", "white-space", "float", "clear", "width",
        "max-width", "font-size", "page-break-before", "break-before", "letter-spacing",
    )

    /** Keeps the [KEEP_PROPS] declarations; sizes only in relative units (em, %, rem, keywords). */
    internal fun filterDecls(decls: String): String = decls.split(';').mapNotNull { d ->
        val prop = d.substringBefore(':').trim().lowercase()
        val value = d.substringAfter(':', "").trim()
        if (prop !in KEEP_PROPS || value.isEmpty() || value.contains("expression", true) || value.contains("url(", true)) return@mapNotNull null
        if ((prop == "font-size" || prop == "width" || prop == "max-width") && Regex("\\d(px|pt|cm|mm|in|pc)\\b").containsMatchIn(value)) return@mapNotNull null
        if (prop == "display" && value.lowercase().startsWith("none").not() && value.lowercase() !in setOf("block", "inline", "inline-block", "list-item")) return@mapNotNull null
        "$prop:$value"
    }.joinToString(";")

    internal fun filterCss(css: String): String {
        val src = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(css, "")
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val open = src.indexOf('{', i)
            if (open < 0) break
            val selector = src.substring(i, open).trim()
            // Find the matching close brace (at-rules nest).
            var depth = 1
            var j = open + 1
            while (j < src.length && depth > 0) { if (src[j] == '{') depth++ else if (src[j] == '}') depth--; j++ }
            val block = src.substring(open + 1, (j - 1).coerceAtLeast(open + 1))
            i = j
            if (selector.startsWith("@")) continue  // @font-face, @page, @media, @import…
            val sel = selector.split(',').map { it.trim() }.filter { s ->
                s.isNotEmpty() && !Regex("(?i)(^|[\\s>+~])(html|body|:root)\\b").containsMatchIn(s)
            }
            if (sel.isEmpty()) continue
            val decls = filterDecls(block)
            if (decls.isNotEmpty()) out.append(sel.joinToString(",")).append('{').append(decls).append('}').append('\n')
        }
        return out.toString()
    }

    private fun headStyles(src: String, path: String, z: DocZip, cache: MutableMap<String, String>, css: StringBuilder) {
        val head = src.substringBefore("<body", src.substringBefore("<BODY", ""))
        Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(head).forEach { m ->
            val tag = m.value
            if (!tag.contains("stylesheet", true)) return@forEach
            val href = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return@forEach
            val target = resolveZipPath(path, unescape(href))
            css.append(cache.getOrPut(target) { z.text(target)?.let(::filterCss).orEmpty() })
        }
        Regex("<style\\b[^>]*>(.*?)</style\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(head).forEach { css.append(filterCss(it.groupValues[1])) }
    }
}
