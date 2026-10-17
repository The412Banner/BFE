package com.the412banner.bfe.docs

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.parser.Parser
import java.io.File

/**
 * Markdown → HTML with commonmark-java (CommonMark + GitHub tables, strikethrough, task lists and
 * bare-URL links). Raw HTML in the file is kept (READMEs use it); the viewer runs pages without
 * JavaScript. Relative pictures load from the file's own folder ([baseDir]); headings get GitHub-style
 * ids so `[see](#install)` links work. A YAML front-matter block is dropped (its title is kept).
 */
object MarkdownConverter {
    private val extensions = listOf(
        TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create(), TaskListItemsExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(extensions).build()

    fun convert(source: String, fileName: String, baseDir: File?): DocResult {
        val (frontTitle, body) = splitFrontMatter(source)
        val doc = parser.parse(body)
        val slugs = HashMap<String, Int>()
        val renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .attributeProviderFactory { _ ->
                AttributeProvider { node, tagName, attributes ->
                    if (node is Image && tagName == "img") {
                        val local = attributes["src"]?.let { localUrl(it, baseDir) }
                        if (local != null) attributes["src"] = local
                    }
                    if (node is Heading) attributes["id"] = slug(plainText(node), slugs)
                }
            }
            .build()
        val html = renderer.render(doc)
            .replace("<table>", "<div class=\"tw\"><table>")
            .replace("</table>", "</table></div>")
        var firstHeading: String? = null
        doc.accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) { if (firstHeading == null) firstHeading = plainText(heading) }
        })
        val title = frontTitle ?: firstHeading?.takeIf { it.isNotBlank() } ?: fileName
        return DocResult(DocKind.MARKDOWN, title, listOf(DocPage(title, html)))
    }

    /** `---` YAML header at the very top: removed from the body; `title:` becomes the document title. */
    internal fun splitFrontMatter(src: String): Pair<String?, String> {
        val s = src.removePrefix("\uFEFF")
        if (!s.startsWith("---")) return null to s
        val lines = s.lines()
        if (lines.first().trim() != "---") return null to s
        val end = (1 until minOf(lines.size, 200)).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." } ?: return null to s
        val title = lines.subList(1, end).firstOrNull { it.startsWith("title:") }
            ?.substringAfter("title:")?.trim()?.trim('"', '\'')?.takeIf { it.isNotEmpty() }
        return title to lines.drop(end + 1).joinToString("\n")
    }

    private fun localUrl(src: String, baseDir: File?): String? {
        if (baseDir == null || src.isBlank() || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(src) || src.startsWith("//") || src.startsWith("#")) return null
        val rel = runCatching { java.net.URLDecoder.decode(src.substringBefore('?').substringBefore('#').replace("+", "%2B"), "UTF-8") }.getOrDefault(src)
        val f = if (rel.startsWith("/")) File(rel) else File(baseDir, rel)
        return f.toURI().toString()
    }

    private fun plainText(n: Node): String {
        val sb = StringBuilder()
        n.accept(object : AbstractVisitor() {
            override fun visit(text: Text) { sb.append(text.literal) }
            override fun visit(code: Code) { sb.append(code.literal) }
        })
        return sb.toString()
    }

    /** GitHub's heading anchors: lower case, punctuation dropped, spaces to hyphens, -1 / -2 for repeats. */
    internal fun slug(text: String, seen: MutableMap<String, Int>): String {
        val base = text.trim().lowercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
            .replace(' ', '-')
        val n = seen[base]
        seen[base] = (n ?: 0) + 1
        return if (n == null) base else "$base-$n"
    }
}
