package com.the412banner.bfe.docs

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Text for an HTML element body. */
fun esc(s: String): String {
    var plain = true
    for (c in s) if (c == '<' || c == '>' || c == '&' || c == '"') { plain = false; break }
    if (plain) return s
    val sb = StringBuilder(s.length + 16)
    for (c in s) when (c) {
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '&' -> sb.append("&amp;")
        '"' -> sb.append("&quot;")
        else -> sb.append(c)
    }
    return sb.toString()
}

/**
 * A table cell holding one plain paragraph shows its text without the paragraph (and its margins);
 * an aligned one keeps its alignment on a div.
 */
internal fun unwrapSingleParagraph(html: String): String {
    val t = html.trim()
    if (t == "<p class=\"e\"></p>") return ""
    val m = Regex("^<p( class=\"[a-z]\")?>(.*)</p>$", RegexOption.DOT_MATCHES_ALL).find(t) ?: return html
    val inner = m.groupValues[2]
    if (inner.contains("<p") || inner.contains("</p>") || inner.contains("<h") || inner.contains("<div") || inner.contains("<ul") || inner.contains("<ol")) return html
    val cls = m.groupValues[1]
    return if (cls.isEmpty()) inner else "<div$cls>$inner</div>"
}

/** A URL a converted page may link to: web, mail, phone, in-page anchors and relative paths. Never script. */
internal fun safeHref(href: String): String? {
    val h = href.trim()
    if (h.isEmpty()) return null
    val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(h)?.groupValues?.get(1)?.lowercase()
    return if (scheme == null || scheme in setOf("http", "https", "mailto", "tel", "ftp", "file", "geo")) h else null
}

/** Colour helpers for the inline colours converters keep (`color:#rrggbb`). */
internal object Colors {
    /** `#rrggbb` for "RRGGBB" / "#RRGGBB" / "rgb" input, or null (also for "auto"). */
    fun hex(v: String?): String? {
        val s = v?.trim()?.removePrefix("#") ?: return null
        return when {
            s.length == 6 && s.all { it.isHex() } -> "#" + s.lowercase()
            s.length == 3 && s.all { it.isHex() } -> "#" + s.map { "$it$it" }.joinToString("").lowercase()
            else -> null
        }
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** Word's named highlight colours. */
    fun highlight(name: String?): String? = when (name?.lowercase()) {
        "yellow" -> "#ffff00"; "green" -> "#00ff00"; "cyan" -> "#00ffff"; "magenta" -> "#ff00ff"
        "blue" -> "#0000ff"; "red" -> "#ff0000"; "darkblue" -> "#000080"; "darkcyan" -> "#008080"
        "darkgreen" -> "#008000"; "darkmagenta" -> "#800080"; "darkred" -> "#800000"; "darkyellow" -> "#808000"
        "darkgray" -> "#808080"; "lightgray" -> "#c0c0c0"; "black" -> "#000000"; "white" -> "#ffffff"
        else -> null
    }

    /** HSL of `#rrggbb` (h 0..360, s/l 0..1). */
    fun hsl(hex: String): FloatArray {
        val r = hex.substring(1, 3).toInt(16) / 255f
        val g = hex.substring(3, 5).toInt(16) / 255f
        val b = hex.substring(5, 7).toInt(16) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6 else 0))
            g -> ((b - r) / d + 2)
            else -> ((r - g) / d + 4)
        } * 60
        return floatArrayOf(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): String {
        fun f(n: Int): Int {
            val k = (n + h / 30) % 12
            val a = s * min(l, 1 - l)
            val v = l - a * max(-1f, min(k - 3, min(9 - k, 1f)))
            return (v.coerceIn(0f, 1f) * 255).roundToInt()
        }
        return "#%02x%02x%02x".format(f(0), f(8), f(4))
    }

    private val inline = Regex("(?<![-\\w])(color|background-color|background)\\s*:\\s*(#[0-9a-fA-F]{6})")

    /**
     * Keeps a document's own colours readable in the dark theme: dark text colours (Word's navy
     * headings, black) are lifted to light tints of the same hue, light fills (cell shading, highlights)
     * are sunk to dark ones. The light theme shows them as written.
     */
    fun adapt(html: String, dark: Boolean): String {
        if (!dark) return html
        return inline.replace(html) { m ->
            val prop = m.groupValues[1]
            val (h, s, l) = hsl(m.groupValues[2]).let { Triple(it[0], it[1], it[2]) }
            val c = if (prop == "color") {
                if (l >= 0.6f) m.groupValues[2] else fromHsl(h, min(s, 0.85f), 0.62f + (0.6f - l) * 0.5f)
            } else {
                if (l <= 0.35f) m.groupValues[2] else fromHsl(h, min(s, 0.6f), 0.10f + (1 - l) * 0.35f)
            }
            "$prop:$c"
        }
    }
}

/** The colours a converted page is drawn in, from BFE's theme (`#rrggbb` strings). */
data class ReadingTheme(
    val dark: Boolean,
    val text: String,
    val muted: String,
    val link: String,
    val border: String,
    val codeBg: String,
    /** Page ground; "transparent" lets Aurora's glass show through. */
    val page: String,
    /** Header cells / slide cards: a step off the page. */
    val raised: String,
) {
    /** A stable key, so pages are re-written when the theme changes. */
    val key: String get() = listOf(dark, text, muted, link, border, codeBg, page, raised).joinToString(",")

    companion object {
        val Light = ReadingTheme(false, "#1a1f27", "#5b6472", "#2f5bea", "#d4d9e1", "#eef1f5", "#ffffff", "#f4f6f9")
        val Dark = ReadingTheme(true, "#e6eaf2", "#a3aec0", "#8ab4ff", "#2e3a4e", "#1b2434", "#0e1117", "#161b24")
    }
}

/** The reading stylesheet and page shell every converted document is shown in. */
object ReadingStyle {
    fun css(t: ReadingTheme): String = """
:root{color-scheme:${if (t.dark) "dark" else "light"}}
html{-webkit-text-size-adjust:100%}
body{margin:0;padding:18px 18px 40px;background:${t.page};color:${t.text};font:16px/1.6 system-ui,-apple-system,"Roboto","Noto Sans",sans-serif;word-wrap:break-word;overflow-wrap:anywhere}
main{max-width:46em;margin:0 auto}
main.wide{max-width:none}
h1,h2,h3,h4,h5,h6{line-height:1.25;margin:1.3em 0 .5em;font-weight:650}
h1{font-size:1.75em}h2{font-size:1.4em}h3{font-size:1.2em}h4{font-size:1.05em}h5,h6{font-size:1em}
p{margin:0 0 .75em}
p.e{min-height:1em;margin:0}
.title{font-size:2em;font-weight:700;margin:.4em 0 .3em}
.subtitle{font-size:1.2em;color:${t.muted};margin:0 0 1em}
a{color:${t.link}}
img,svg{max-width:100%;height:auto}
.noimg{display:inline-block;padding:.3em .6em;border:1px dashed ${t.border};border-radius:6px;color:${t.muted};font-size:.85em}
blockquote{margin:.8em 0;padding:.2em 1em;border-left:3px solid ${t.border};color:${t.muted}}
code,kbd,samp{font-family:"Roboto Mono","Droid Sans Mono",monospace;font-size:.9em;background:${t.codeBg};padding:.1em .3em;border-radius:4px}
pre{font-family:"Roboto Mono","Droid Sans Mono",monospace;font-size:.85em;background:${t.codeBg};padding:.8em 1em;border-radius:8px;overflow-x:auto;white-space:pre}
pre code{background:none;padding:0}
ul,ol{padding-left:1.6em;margin:0 0 .75em}
li>p{margin:0 0 .3em}
hr{border:0;border-top:1px solid ${t.border};margin:1.5em 0}
hr.pb{border-top:2px dashed ${t.border};margin:2em 0}
.tw{overflow-x:auto;margin:0 0 1em;-webkit-overflow-scrolling:touch}
table{border-collapse:collapse;font-size:.95em}
th,td{border:1px solid ${t.border};padding:.35em .6em;vertical-align:top;text-align:left}
th{background:${t.raised};font-weight:600}
td>p:last-child,th>p:last-child{margin-bottom:0}
table.sheet{font-size:.88em;white-space:nowrap}
table.sheet td.n{text-align:right;font-variant-numeric:tabular-nums}
table.sheet th.rh,table.sheet th.ch{color:${t.muted};font-weight:500;text-align:center;font-size:.85em}
table.sheet thead th{position:sticky;top:0;z-index:1}
table.sheet th.rh{position:sticky;left:0}
.slide{border:1px solid ${t.border};border-radius:14px;padding:1em 1.2em;margin:0 0 1.2em;background:${t.raised}}
.slide .num{font-size:.75em;color:${t.muted};text-transform:uppercase;letter-spacing:.08em}
.slide h2{margin-top:.3em}
.slide img{display:block;margin:.6em auto;max-height:60vh}
details.notes{margin-top:.8em;color:${t.muted};font-size:.9em}
.footnotes{margin-top:2em;font-size:.88em;color:${t.muted}}
.footnotes ol{padding-left:1.4em}
sup.fn a{text-decoration:none}
.note{font-size:.85em;color:${t.muted};border:1px solid ${t.border};border-radius:8px;padding:.5em .8em;margin:0 0 1em}
.task{list-style:none;margin-left:-1.3em}
.c{text-align:center}.r{text-align:right}.j{text-align:justify}
""".trimIndent()

    /**
     * The full page for [page]: the page's own filtered CSS (an e-book's layout rules) under the reading
     * stylesheet in [theme] colours, the document's [notes], then the body. Colours inside the body are
     * adapted to the theme ([Colors.adapt]). [wide] drops the reading-width limit (spreadsheets).
     */
    fun wrap(page: DocPage, theme: ReadingTheme, notes: List<String> = emptyList(), wide: Boolean = false): String {
        val extraCss = page.css
        val sb = StringBuilder(page.body.length + 4096)
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<title>").append(esc(page.title)).append("</title>")
        if (extraCss.isNotBlank()) sb.append("<style>").append(extraCss).append("</style>")
        sb.append("<style>").append(css(theme)).append("</style></head><body><main")
        if (wide) sb.append(" class=\"wide\"")
        sb.append('>')
        notes.forEach { sb.append("<div class=\"note\">").append(esc(it)).append("</div>") }
        sb.append(Colors.adapt(page.body, theme.dark))
        sb.append("</main></body></html>")
        return sb.toString()
    }
}

/**
 * Builds body markup while tracking open inline formatting, so runs with the same bold / italic /
 * colour merge into one span instead of one per word, as Word's runs would otherwise produce.
 */
internal class RunWriter(private val out: StringBuilder) {
    /** The formatting of a piece of text. Null fields = inherit. */
    data class Fmt(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val sup: Boolean = false,
        val sub: Boolean = false,
        val code: Boolean = false,
        val color: String? = null,
        val bg: String? = null,
        val sizePt: Float? = null,
    ) {
        val plain: Boolean get() = this == PLAIN
    }

    private var open: Fmt = PLAIN
    private var openTags = ArrayList<String>()

    fun text(s: String, f: Fmt) {
        if (s.isEmpty()) return
        switchTo(f)
        out.append(esc(s))
    }

    /** Markup that sits inside the current run (a tab, a line break, a picture). */
    fun raw(s: String, f: Fmt = open) {
        switchTo(f)
        out.append(s)
    }

    fun close() = switchTo(PLAIN)

    private fun switchTo(f: Fmt) {
        if (f == open) return
        for (i in openTags.indices.reversed()) out.append("</").append(openTags[i]).append('>')
        openTags.clear()
        open = f
        if (f.plain) return
        fun tag(name: String, attrs: String = "") { out.append('<').append(name).append(attrs).append('>'); openTags.add(name) }
        if (f.bold) tag("b")
        if (f.italic) tag("i")
        if (f.underline) tag("u")
        if (f.strike) tag("s")
        if (f.sup) tag("sup")
        if (f.sub) tag("sub")
        if (f.code) tag("code")
        val style = buildString {
            f.color?.let { append("color:").append(it).append(';') }
            f.bg?.let { append("background-color:").append(it).append(';') }
            f.sizePt?.let { if (it in 1f..200f && (it < 10.5f || it > 12.5f)) append("font-size:").append("%.2f".format(java.util.Locale.US, it / 12f).trimEnd('0').trimEnd('.')).append("em;") }
        }
        if (style.isNotEmpty()) tag("span", " style=\"$style\"")
    }

    companion object {
        val PLAIN = Fmt()
    }
}
