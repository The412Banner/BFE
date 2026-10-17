package com.the412banner.bfe.docs

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * A parsed XML element: namespace URI + local name, its attributes and its children in document order
 * (elements and text runs). The document converters walk these instead of a DOM so the same code runs
 * on the device (Expat-backed SAX) and in JVM unit tests (the JDK's SAX), with namespaces resolved by
 * URI — a .docx written with `ns0:` prefixes reads the same as Word's own `w:`.
 */
class XEl internal constructor(val ns: String, val name: String, private val attrs: Array<String>) {
    /** Child [XEl]s and text [String]s, in order. */
    val children: MutableList<Any> = ArrayList(2)

    /** The attribute [local] in namespace [ns] ("" = no namespace). */
    fun attr(ns: String, local: String): String? {
        var i = 0
        while (i < attrs.size) {
            if (attrs[i + 1] == local && attrs[i] == ns) return attrs[i + 2]
            i += 3
        }
        return null
    }

    /** The first attribute called [local], whatever its namespace. */
    fun attr(local: String): String? {
        var i = 0
        while (i < attrs.size) {
            if (attrs[i + 1] == local) return attrs[i + 2]
            i += 3
        }
        return null
    }

    fun isA(ns: String, local: String): Boolean = name == local && this.ns == ns

    val elements: Sequence<XEl> get() = children.asSequence().filterIsInstance<XEl>()

    fun child(ns: String, local: String): XEl? = children.firstOrNull { it is XEl && it.isA(ns, local) } as XEl?

    fun children(ns: String, local: String): Sequence<XEl> = elements.filter { it.isA(ns, local) }

    /** Every element below this one (depth first, document order). */
    fun descendants(): Sequence<XEl> = sequence {
        val stack = ArrayDeque<Iterator<Any>>()
        stack.addLast(children.iterator())
        while (stack.isNotEmpty()) {
            val it = stack.last()
            if (!it.hasNext()) { stack.removeLast(); continue }
            val c = it.next()
            if (c is XEl) { yield(c); stack.addLast(c.children.iterator()) }
        }
    }

    fun descendants(ns: String, local: String): Sequence<XEl> = descendants().filter { it.isA(ns, local) }

    /** All the text below this element, concatenated. */
    fun text(): String {
        val sb = StringBuilder()
        fun walk(e: XEl) { e.children.forEach { if (it is String) sb.append(it) else walk(it as XEl) } }
        walk(this)
        return sb.toString()
    }

    override fun toString(): String = "<$name>"
}

object Xml {
    /**
     * Parses [input] into an [XEl] tree. [keep], when given, is asked before each element is built and
     * can drop it with its whole subtree (used to cap huge spreadsheets while parsing). External
     * entities and DTDs are never fetched.
     */
    fun parse(input: InputStream, keep: ((parent: XEl, ns: String, name: String) -> Boolean)? = null): XEl {
        val handler = TreeHandler(keep)
        newParser().parse(InputSource(input), handler)
        return handler.root ?: throw DocException("The document is empty")
    }

    /** Streams [input] through [handler] (for parts too big to hold as a tree, e.g. worksheets). */
    fun stream(input: InputStream, handler: DefaultHandler) {
        newParser().parse(InputSource(input), handler)
    }

    private fun newParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        isValidating = false
        // Best effort: Android's factory rejects features it doesn't know, the JDK's honours them.
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }.newSAXParser()

    /** Never resolve an external entity: answer with an empty document instead. */
    internal val NoEntities = { _: String?, _: String? -> InputSource(StringReader("")) }

    private class TreeHandler(private val keep: ((XEl, String, String) -> Boolean)?) : DefaultHandler() {
        var root: XEl? = null
        private val stack = ArrayList<XEl>()
        private val text = StringBuilder()
        /** Depth inside a dropped subtree (0 = building). */
        private var skipping = 0

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = NoEntities(publicId, systemId)

        private fun flush() {
            if (text.isNotEmpty()) {
                stack.lastOrNull()?.children?.add(text.toString())
                text.setLength(0)
            }
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            if (skipping > 0) { skipping++; return }
            val ns = uri.orEmpty()
            val local = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
            val parent = stack.lastOrNull()
            if (parent != null && keep != null && !keep.invoke(parent, ns, local)) { flush(); skipping = 1; return }
            flush()
            val a = arrayOfNulls<String>(attributes.length * 3)
            for (i in 0 until attributes.length) {
                a[i * 3] = attributes.getURI(i).orEmpty()
                a[i * 3 + 1] = attributes.getLocalName(i)?.takeIf { it.isNotEmpty() } ?: attributes.getQName(i).substringAfter(':')
                a[i * 3 + 2] = attributes.getValue(i)
            }
            @Suppress("UNCHECKED_CAST")
            val el = XEl(ns, local, a as Array<String>)
            if (parent == null) root = el else parent.children.add(el)
            stack.add(el)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (skipping > 0) { skipping--; return }
            flush()
            stack.removeAt(stack.size - 1)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (skipping == 0 && stack.isNotEmpty()) text.append(ch, start, length)
        }
    }
}

/** A document problem worth showing to the user as it is ("This file is damaged…"). */
class DocException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** XML namespaces the converters read. */
internal object Ns {
    // OpenDocument
    const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    const val PRESENTATION = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0"
    const val XLINK = "http://www.w3.org/1999/xlink"
    const val DC = "http://purl.org/dc/elements/1.1/"

    // Office Open XML
    const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    const val S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    const val PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val MC = "http://schemas.openxmlformats.org/markup-compatibility/2006"
    const val V = "urn:schemas-microsoft-com:vml"
    const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"

    // EPUB
    const val OPF = "http://www.idpf.org/2007/opf"
    const val CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container"
    const val NCX = "http://www.daisy.org/z3986/2005/ncx/"
    const val XHTML = "http://www.w3.org/1999/xhtml"
    const val EPUB = "http://www.idpf.org/2007/ops"
}
