package com.the412banner.bfe.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The document converters against real files: everything under resources/docs was written by pandoc
 * 3.9 from sample.md, by scripts/docfixtures/make_office.py (xlsx / ods / odp / fodt, plus merged
 * cells, colours and page breaks added to pandoc's docx / odt), or is a LibreOffice document with its
 * text replaced (libreoffice-interview.odt). See scripts/docfixtures/make_fixtures.sh.
 */
class DocConvertersTest {
    private fun fixture(name: String): File =
        File(requireNotNull(javaClass.classLoader?.getResource("docs/$name")) { "missing fixture $name" }.toURI())

    private fun convert(name: String, assets: DocAssets = MemoryAssets()): DocResult {
        val f = fixture(name)
        val kind = requireNotNull(DocFormats.kindOf(f.name)) { "no kind for $name" }
        return DocConverter.convert(f, kind, f.name, assets)
    }

    /** (level, text) of every heading, tags stripped. */
    private fun headings(html: String): List<Pair<Int, String>> =
        Regex("<h([1-6])[^>]*>(.*?)</h\\1>", RegexOption.DOT_MATCHES_ALL).findAll(html)
            .map { it.groupValues[1].toInt() to plain(it.groupValues[2]) }.toList()

    private fun plain(html: String): String =
        Regex("\\s+").replace(Regex("<[^>]+>").replace(html, ""), " ").trim()

    private fun assertHas(html: String, part: String) = assertTrue("expected `$part` in:\n$html", html.contains(part))
    private fun assertMatches(html: String, re: String) = assertTrue("expected /$re/ in:\n$html", Regex(re, RegexOption.DOT_MATCHES_ALL).containsMatchIn(html))

    private val pngSignature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    private fun MemoryAssets.hasPng() = files.values.any { it.size > 8 && it.copyOf(4).contentEquals(pngSignature) }

    private val lists =
        "<ul><li>First bullet</li><li>Second bullet<ul><li>Nested bullet alpha</li><li>Nested bullet beta</li></ul></li>" +
            "<li>Third bullet</li></ul><ol><li>Numbered one</li><li>Numbered two</li><li>Numbered three</li></ol>"

    @Test fun formatsAreRecognised() {
        assertEquals(DocKind.PDF, DocFormats.kindOf("a.PDF"))
        assertEquals(DocKind.ODT, DocFormats.kindOf("DroidDeck Feature Article - way too long.odt"))
        assertEquals(DocKind.ODT, DocFormats.kindOf("flat.fodt"))
        assertEquals(DocKind.DOCX, DocFormats.kindOf("x.docm"))
        assertEquals(DocKind.XLSX, DocFormats.kindOf("x.xlsx"))
        assertEquals(DocKind.PPTX, DocFormats.kindOf("x.pptx"))
        assertEquals(DocKind.LEGACY_OFFICE, DocFormats.kindOf("old.doc"))
        assertEquals(DocKind.LEGACY_OFFICE, DocFormats.kindOf("old.xls"))
        assertEquals(DocKind.TEXT, DocFormats.kindOf("latest.log"))
        assertEquals(DocKind.TEXT, DocFormats.kindOf("conf.ini"))
        assertEquals(DocKind.MARKDOWN, DocFormats.kindOf("README.md"))
        assertEquals(DocKind.CSV, DocFormats.kindOf("x.csv"))
        assertEquals(DocKind.TSV, DocFormats.kindOf("x.tsv"))
        assertEquals(DocKind.EPUB, DocFormats.kindOf("book.epub"))
        // No extension: the MIME type decides; HTML belongs to the HTML viewer.
        assertEquals(DocKind.PDF, DocFormats.kindOf("download", "application/pdf"))
        assertEquals(DocKind.TEXT, DocFormats.kindOf("notes", "text/plain"))
        assertEquals(null, DocFormats.kindOf("page", "text/html"))
        assertEquals(null, DocFormats.kindOf("song.mp3", "audio/mpeg"))
        // Content sniffing for nameless files.
        assertEquals(DocKind.DOCX, DocFormats.sniff(fixture("sample.docx")))
        assertEquals(DocKind.ODT, DocFormats.sniff(fixture("sample.odt")))
        assertEquals(DocKind.ODS, DocFormats.sniff(fixture("sample.ods")))
        assertEquals(DocKind.XLSX, DocFormats.sniff(fixture("sample.xlsx")))
        assertEquals(DocKind.PPTX, DocFormats.sniff(fixture("sample.pptx")))
        assertEquals(DocKind.EPUB, DocFormats.sniff(fixture("sample.epub")))
        assertEquals(DocKind.RTF, DocFormats.sniff(fixture("sample.rtf")))
        assertEquals(DocKind.ODT, DocFormats.sniff(fixture("sample.fodt")))
    }

    @Test fun docx() {
        val assets = MemoryAssets()
        val r = convert("sample.docx", assets)
        assertEquals(DocKind.DOCX, r.kind)
        assertEquals("BFE Sample Document", r.title)
        val html = r.pages.single().body
        assertEquals(
            listOf(1 to "Chapter One Heading", 2 to "Section Lists", 2 to "Section Table", 1 to "Chapter Two Heading"),
            headings(html),
        )
        assertHas(html, "<p class=\"title\">BFE Sample Document</p>")
        assertHas(html, "This paragraph has <b>bold words</b>, <i>italic words</i>, <s>struck words</s> and <u>underlined words</u>.")
        assertHas(html, "<a href=\"https://github.com/The412Banner/BFE\">the BFE repository</a>")
        assertHas(html, lists)
        assertHas(html, "<tr><th>Game</th><th>Year</th><th>Rating</th></tr><tr><td>Half-Life</td><td>1998</td><td>96</td></tr>")
        // Footnote: a numbered reference and the text at the end, linked both ways.
        assertHas(html, "<sup class=\"fn\"><a id=\"fnref-1\" href=\"#fn-1\">1</a></sup>")
        assertMatches(html, "<li id=\"fn-1\"><p>\\s*The footnote text lives here\\. <a href=\"#fnref-1\">")
        // Picture pulled out of word/media, sized from its extent.
        assertMatches(html, "<img src=\"res/rId12\\.png\" style=\"width:10px\" alt=\"Red square picture\">")
        assertTrue(assets.hasPng())
        assertHas(html, "<code>inline code</code>")
        // Added by make_office.py: page break, direct colour + size + centring, merged cells, a field result.
        assertHas(html, "<hr class=\"pb\">")
        assertHas(html, "<p class=\"c\"><span style=\"color:#c00000;font-size:2em;\">Big red centred text</span></p>")
        assertHas(html, "<td colspan=\"2\">Spans two columns</td><td rowspan=\"2\">Spans two rows</td></tr><tr><td>Cell A2</td><td>Cell B2</td></tr>")
        assertHas(html, "FieldResult7")
        assertFalse(html.contains("PAGE"))
    }

    @Test fun odt() {
        val assets = MemoryAssets()
        val r = convert("sample.odt", assets)
        assertEquals(DocKind.ODT, r.kind)
        assertEquals("BFE Sample Document", r.title)
        val html = r.pages.single().body
        assertEquals(
            listOf(1 to "Chapter One Heading", 2 to "Section Lists", 2 to "Section Table", 1 to "Chapter Two Heading"),
            headings(html),
        )
        assertHas(html, "<p class=\"title\">BFE Sample Document</p>")
        assertHas(html, "This paragraph has <b>bold words</b>, <i>italic words</i>, <s>struck words</s> and <u>underlined words</u>.")
        assertMatches(html, "<a href=\"https://github.com/The412Banner/BFE\">.*?the BFE repository.*?</a>")
        assertHas(html, lists)
        // pandoc's "Table Heading" paragraph style is bold, so the header text keeps its <b>.
        assertMatches(html, "<tr><th>(<b>)?Game(</b>)?</th><th>(<b>)?Year(</b>)?</th><th>(<b>)?Rating(</b>)?</th></tr><tr><td>Half-Life</td><td>1998</td><td>96</td></tr>")
        assertHas(html, "<sup class=\"fn\"><a id=\"fnref-1\" href=\"#fn-1\">1</a></sup>")
        assertMatches(html, "<li id=\"fn-1\"><p>The footnote text lives here\\. <a href=\"#fnref-1\">")
        assertMatches(html, "<img src=\"res/0\\.png\" style=\"width:10px\"")
        assertTrue(assets.hasPng())
        // Added by make_office.py.
        assertHas(html, "<hr class=\"pb\"><p>After the page break</p>")
        assertHas(html, "Spaced\u00A0\u00A0\u00A0out and <span style=\"color:#c00000;font-size:2em;\">big red text</span>\u2003tabbed<br>new line")
        assertHas(html, "<td colspan=\"2\">Spans two columns</td><td rowspan=\"2\">Spans two rows</td></tr><tr><td>Cell A2</td><td>Cell B2</td></tr>")
    }

    @Test fun libreOfficeDocument() {
        // The owner's interview document (text replaced): Heading 2 sections with soft page breaks inside
        // the headings and runs of empty paragraphs left for answers.
        val r = convert("libreoffice-interview.odt")
        val html = r.pages.single().body
        val hs = headings(html)
        assertEquals(17, hs.size)
        assertTrue(hs.all { it.first == 2 })
        assertEquals("Section heading 1", hs.first().second)
        assertEquals("Section heading 2", hs[1].second)
        assertEquals("Section heading 1", r.title)
        assertHas(html, "<p>Paragraph 1 ipsum dolor")
        assertTrue(Regex("<p class=\"e\"></p>").findAll(html).count() > 100)
        assertFalse(html.contains("soft-page-break"))
    }

    @Test fun flatOdt() {
        val assets = MemoryAssets()
        val html = convert("sample.fodt", assets).pages.single().body
        assertEquals(listOf(1 to "Flat Heading Via Style"), headings(html))
        assertHas(html, "Flat body with <b>bold flat</b> text.")
        assertHas(html, "<img src=\"res/image.png\"")
        assertTrue(assets.hasPng())
    }

    @Test fun ods() {
        val r = convert("sample.ods")
        assertEquals(DocKind.ODS, r.kind)
        assertEquals(listOf("Scores", "Second"), r.pages.map { it.title })
        val html = r.pages[0].body
        assertHas(html, "<td>Player</td><td>Points</td><td>Date</td>")
        assertHas(html, "<td>Alice</td><td class=\"n\">1,500.50</td><td>16/10/26</td>")
        assertHas(html, "<td colspan=\"2\">Spanned total</td><td class=\"n\">7</td>")
        assertEquals(3, Regex("Repeated row").findAll(html).count())
        // A million repeated empty rows and a thousand empty columns are trimmed away.
        assertHas(html, "<th class=\"rh\">6</th>")
        assertFalse(html.contains("<th class=\"rh\">7</th>"))
        assertFalse(html.contains("<th class=\"ch\">D</th>"))
        assertHas(r.pages[1].body, "Other sheet")
    }

    @Test fun odp() {
        val assets = MemoryAssets()
        val r = convert("sample.odp", assets)
        val html = r.pages.single().body
        assertEquals(2, Regex("<section class=\"slide\">").findAll(html).count())
        assertHas(html, "Slide 1 · Intro")
        assertHas(html, "<h2>Welcome Slide Title</h2>")
        assertHas(html, "<p>The subtitle line</p>")
        assertHas(html, "<h2>Second Slide Title</h2>")
        // Top to bottom, then left to right: the bullet list (x 1 cm) before the picture (x 12 cm).
        assertMatches(html, "<ul><li>Point one</li><li>Point two</li></ul><p><img src=\"res/blue\\.png\"")
        assertHas(html, "<details class=\"notes\"><summary>Notes</summary><p>Speaker note text</p></details>")
        assertTrue(assets.hasPng())
    }

    @Test fun xlsx() {
        val r = convert("sample.xlsx")
        assertEquals(DocKind.XLSX, r.kind)
        assertEquals(listOf("Games", "Notes"), r.pages.map { it.title })
        val html = r.pages[0].body
        assertHas(html, "<td>Game</td><td>Released</td><td>Score</td><td>Price</td>")
        // Rich shared string, custom date format, built-in percentage, custom currency.
        assertHas(html, "<td>Half-Life</td><td class=\"n\">1998-11-02</td><td class=\"n\">96.00%</td><td class=\"n\">$9.99</td>")
        assertHas(html, "<td>Portal</td><td class=\"n\">2007-10-18</td><td class=\"n\">90.00%</td><td class=\"n\">$1,234.50</td>")
        // Formula's cached text, boolean, General number, thousands separator.
        assertHas(html, "<td>Formula text</td><td>TRUE</td><td class=\"n\">42</td><td class=\"n\">1,234,567</td>")
        assertHas(html, "<td rowspan=\"2\" colspan=\"3\">Merged title</td>")
        assertHas(html, "<th class=\"rh\">7</th>")
        assertHas(r.pages[1].body, "Second sheet cell")
    }

    @Test fun pptx() {
        val assets = MemoryAssets()
        val r = convert("sample.pptx", assets)
        assertEquals("BFE Sample Document", r.title)
        val html = r.pages.single().body
        assertEquals(5, Regex("<section class=\"slide\">").findAll(html).count())
        assertHas(html, "<h2>BFE Sample Document</h2>")
        assertHas(html, "<div class=\"subtitle\"><p>BFE fixtures</p></div>")
        assertHas(html, "<h2>Chapter One Heading</h2>")
        assertHas(html, "This paragraph has <b>bold words</b>, <i>italic words</i>, <s>struck words</s> and <u>underlined words</u>.")
        assertHas(html, "<a href=\"https://github.com/The412Banner/BFE\">the BFE repository</a>")
        assertHas(html, lists)
        assertHas(html, "<th>Game</th><th>Year</th><th>Rating</th>")
        assertMatches(html, "<img src=\"res/image1\\.png\" alt=\"red\\.png\">")
        assertTrue(assets.hasPng())
    }

    @Test fun rtf() {
        val assets = MemoryAssets()
        val html = convert("sample.rtf", assets).pages.single().body
        assertEquals(listOf("Chapter One Heading", "Section Lists", "Section Table", "Chapter Two Heading"), headings(html).map { it.second })
        assertEquals(listOf(1, 2, 2, 1), headings(html).map { it.first })
        assertHas(html, "This paragraph has <b>bold words</b>, <i>italic words</i>, <s>struck words</s> and <u>underlined words</u>.")
        assertHas(html, "<a href=\"https://github.com/The412Banner/BFE\">the BFE repository</a>")
        assertHas(html, "<tr><td>Game</td><td>Year</td><td>Rating</td></tr><tr><td>Half-Life</td><td>1998</td><td>96</td></tr>")
        assertHas(html, "<sup class=\"fn\"><a id=\"fnref-1\" href=\"#fn-1\">1</a></sup>")
        assertMatches(html, "<li id=\"fn-1\"><p>.?The footnote text lives here\\. <a href=\"#fnref-1\">")
        assertMatches(html, "<img src=\"res/picture\\.png\" width=\"10\"")
        assertTrue(assets.hasPng())
        assertHas(html, "First bullet")
        assertHas(html, "code block line one<br>code block line two")
    }

    @Test fun rtfCodePageUnicodeAndBreaks() {
        val html = convert("extras.rtf").pages.single().body
        assertHas(html, "<p class=\"c\"><b>Café Title</b></p>")
        assertHas(html, "Price: 5 € euro, <span style=\"color:#c00000;\">red text</span>, <i>italic</i> and <b>bold</b> again.<br>Second line.")
        assertHas(html, "<hr class=\"pb\"><p>After the break.</p>")
    }

    @Test fun markdown() {
        val r = convert("sample.md")
        assertEquals("BFE Sample Document", r.title)
        val html = r.pages.single().body
        assertHas(html, "<h1 id=\"chapter-one-heading\">Chapter One Heading</h1>")
        assertHas(html, "<h2 id=\"section-lists\">Section Lists</h2>")
        assertHas(html, "<strong>bold words</strong>")
        assertHas(html, "<em>italic words</em>")
        assertHas(html, "<del>struck words</del>")
        assertHas(html, "<a href=\"https://github.com/The412Banner/BFE\">the BFE repository</a>")
        assertHas(html, "<div class=\"tw\"><table>")
        assertHas(html, "<th>Game</th>")
        assertHas(html, "<pre><code>code block line one\ncode block line two\n</code></pre>")
        // The front matter is gone; relative pictures load from the file's own folder.
        assertFalse(html.contains("author:"))
        assertMatches(html, "<img src=\"file:[^\"]*/docs/red\\.png\" alt=\"Red square picture\"")
    }

    @Test fun epub() {
        val assets = MemoryAssets()
        val r = convert("sample.epub", assets)
        assertEquals("BFE Sample Document", r.title)
        assertEquals(3, r.pages.size)
        assertEquals(
            listOf(
                TocEntry("Chapter One Heading", 1, "chapter-one-heading", 0),
                TocEntry("Section Lists", 1, "section-lists", 1),
                TocEntry("Section Table", 1, "section-table", 1),
                TocEntry("Chapter Two Heading", 2, "chapter-two-heading", 0),
            ),
            r.toc,
        )
        assertEquals("Chapter One Heading", r.pages[1].title)
        val ch1 = r.pages[1].body
        assertHas(ch1, "<strong>bold words</strong>")
        assertHas(ch1, "<td>Half-Life</td>")
        assertMatches(ch1, "<img src=\"res/file0\\.png\" alt=\"Red square picture\"")
        assertTrue(assets.hasPng())
        // In-chapter footnote links stay anchors; the book's CSS keeps layout only.
        assertHas(ch1, "href=\"#fn1\"")
        assertFalse(r.pages[1].css.contains("font-family"))
        assertFalse(Regex("(^|\\n)body\\b").containsMatchIn(r.pages[1].css))
        assertHas(r.pages[2].body, "Chapter Two Heading")
    }

    @Test fun epubLinksBetweenChaptersPointAtPageFiles() {
        assertEquals("p {text-align:center}".replace(" ", ""), EpubConverter.filterCss("p { text-align: center; color: red; font-family: Serif }").trim().replace(" ", ""))
        assertEquals("", EpubConverter.filterCss("body { margin: 5% } @font-face { src: url(x.ttf) }").trim())
        assertEquals("font-style:italic", EpubConverter.filterDecls("font-style: italic; background: #000; font-size: 12px"))
    }

    @Test fun csvTable() {
        val f = File.createTempFile("bfe", ".csv")
        try {
            f.writeText("Name;Score;Note\nAlice;10;ok\n\"Bob; Jr\";20;\"says \"\"hi\"\"\"\n")
            val html = DocConverter.convert(f, DocKind.CSV, "scores.csv", MemoryAssets()).pages.single().body
            assertHas(html, "<thead><tr><th>Name</th><th>Score</th><th>Note</th></tr></thead>")
            assertHas(html, "<tr><td>Alice</td><td class=\"n\">10</td><td>ok</td></tr>")
            assertHas(html, "<tr><td>Bob; Jr</td><td class=\"n\">20</td><td>says &quot;hi&quot;</td></tr>")
        } finally {
            f.delete()
        }
    }

    @Test fun legacyOfficeIsNotConverted() {
        val f = File.createTempFile("bfe", ".doc")
        try {
            f.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))
            assertEquals(DocKind.LEGACY_OFFICE, DocFormats.sniff(f))
            assertTrue(runCatching { DocConverter.convert(f, DocKind.LEGACY_OFFICE, f.name, MemoryAssets()) }.exceptionOrNull() is DocException)
        } finally {
            f.delete()
        }
    }

    @Test fun damagedFilesFailWithAMessage() {
        val f = File.createTempFile("bfe", ".docx")
        try {
            f.writeText("this is not a zip")
            val e = runCatching { DocConverter.convert(f, DocKind.DOCX, f.name, MemoryAssets()) }.exceptionOrNull()
            assertTrue(e is DocException)
            assertTrue(e!!.message!!.contains("damaged"))
        } finally {
            f.delete()
        }
    }

    @Test fun pageShellCarriesThemeAndAdaptsColours() {
        val page = DocPage("T", "<p><span style=\"color:#0f4761;\">navy</span> <span style=\"background-color:#ffff00;\">hl</span></p>", "p{text-align:center}")
        val light = ReadingStyle.wrap(page, ReadingTheme.Light)
        assertHas(light, "color:#0f4761")
        assertHas(light, "p{text-align:center}")
        assertHas(light, "background:#ffffff")
        val dark = ReadingStyle.wrap(page, ReadingTheme.Dark, notes = listOf("A note"))
        assertFalse(dark.contains("color:#0f4761"))
        assertFalse(dark.contains("background-color:#ffff00"))
        assertHas(dark, "<div class=\"note\">A note</div>")
        // Lifted text keeps its hue and becomes light; the highlight becomes dark.
        val lifted = Regex("color:(#[0-9a-f]{6})").findAll(Colors.adapt(page.body, true)).map { it.groupValues[1] }.toList()
        assertTrue(Colors.hsl(lifted[0])[2] > 0.6f)
        assertTrue(Colors.hsl(lifted[1])[2] < 0.4f)
    }
}
