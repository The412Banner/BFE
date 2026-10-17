package com.the412banner.bfe.docs

/** One OPC relationship: a part (resolved zip path) or an external URL. */
internal data class Rel(val id: String, val type: String, val target: String, val external: Boolean)

/** A part's relationships (`<dir>/_rels/<name>.rels`), by id. */
internal class Rels(private val byId: Map<String, Rel>) {
    operator fun get(id: String?): Rel? = id?.let { byId[it] }
    fun ofType(suffix: String): List<Rel> = byId.values.filter { it.type.endsWith(suffix) }

    companion object {
        val EMPTY = Rels(emptyMap())

        fun of(zip: DocZip, part: String): Rels {
            val dir = part.substringBeforeLast('/', "")
            val name = part.substringAfterLast('/')
            val relsPath = if (dir.isEmpty()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
            val root = runCatching { zip.xml(relsPath) }.getOrNull() ?: return EMPTY
            val map = HashMap<String, Rel>()
            for (r in root.elements) {
                if (r.name != "Relationship") continue
                val id = r.attr("Id") ?: continue
                val target = r.attr("Target") ?: continue
                val external = r.attr("TargetMode").equals("External", ignoreCase = true)
                map[id] = Rel(id, r.attr("Type").orEmpty(), if (external) target else resolveZipPath(part, target), external)
            }
            return Rels(map)
        }
    }
}

/** Namespace URIs for a package, Transitional (what Office writes) or Strict (ISO 29500 "strict"). */
internal class OoxmlNs(strict: Boolean) {
    val w = if (strict) "http://purl.oclc.org/ooxml/wordprocessingml/main" else Ns.W
    val r = if (strict) "http://purl.oclc.org/ooxml/officeDocument/relationships" else Ns.R
    val a = if (strict) "http://purl.oclc.org/ooxml/drawingml/main" else Ns.A
    val p = if (strict) "http://purl.oclc.org/ooxml/presentationml/main" else Ns.P
    val s = if (strict) "http://purl.oclc.org/ooxml/spreadsheetml/main" else Ns.S

    companion object {
        fun of(root: XEl): OoxmlNs = OoxmlNs(root.ns.startsWith("http://purl.oclc.org/ooxml/"))
    }
}

/** docProps/core.xml's title, if the author set one. */
internal fun coreTitle(zip: DocZip): String? =
    runCatching { zip.xml("docProps/core.xml") }.getOrNull()?.child(Ns.DC, "title")?.text()?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Nested HTML lists built from a flat run of numbered / bulleted paragraphs with a level each (how
 * Word and PowerPoint store lists). Deeper items open a list inside the previous item; a change of
 * kind at the same level starts a new list.
 */
internal class ListStack(private val out: StringBuilder) {
    private class Level(val ordered: Boolean, var itemOpen: Boolean)
    private val stack = ArrayList<Level>()

    val open: Boolean get() = stack.isNotEmpty()

    /** [olAttrs] (` type="a" start="3"`) apply when this item opens a new numbered list. */
    fun item(level: Int, ordered: Boolean, olAttrs: String, content: String) {
        val lvl = level.coerceIn(0, 8)
        while (stack.size > lvl + 1) pop()
        if (stack.size == lvl + 1 && stack.last().ordered != ordered) pop()
        while (stack.size < lvl + 1) {
            val parent = stack.lastOrNull()
            if (parent != null && !parent.itemOpen) { out.append("<li style=\"list-style:none\">"); parent.itemOpen = true }
            val target = stack.size == lvl
            val o = target && ordered
            out.append(if (o) "<ol$olAttrs>" else "<ul>")
            stack.add(Level(o, false))
        }
        val top = stack.last()
        if (top.itemOpen) out.append("</li>")
        out.append("<li>").append(content)
        top.itemOpen = true
    }

    fun closeAll() { while (stack.isNotEmpty()) pop() }

    private fun pop() {
        val l = stack.removeAt(stack.size - 1)
        if (l.itemOpen) out.append("</li>")
        out.append(if (l.ordered) "</ol>" else "</ul>")
    }
}

/** HTML `<ol type>` for an OOXML number format, or null for bullets / none. */
internal fun olType(numFmt: String?): String? = when (numFmt) {
    "decimal", "decimalZero", "decimalEnclosedCircle", "decimalFullWidth", "arabicPeriod", "arabicParenR", "arabicParenBoth", "arabicPlain" -> "1"
    "lowerLetter", "alphaLcPeriod", "alphaLcParenR", "alphaLcParenBoth" -> "a"
    "upperLetter", "alphaUcPeriod", "alphaUcParenR", "alphaUcParenBoth" -> "A"
    "lowerRoman", "romanLcPeriod", "romanLcParenR", "romanLcParenBoth" -> "i"
    "upperRoman", "romanUcPeriod", "romanUcParenR", "romanUcParenBoth" -> "I"
    null, "bullet", "none" -> null
    else -> if (numFmt.contains("Roman", true) || numFmt.contains("alpha", true) || numFmt.contains("arabic", true) || numFmt.contains("decimal", true)) "1" else null
}

/** EMU (English Metric Units, 914 400 per inch) → CSS px. */
internal fun emuToPx(emu: String?): Int? = emu?.toLongOrNull()?.let { (it / 9525L).toInt() }?.takeIf { it > 0 }
