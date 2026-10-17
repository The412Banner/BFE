package com.the412banner.bfe.docs

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Excel .xlsx / .xlsm / .xltx → one page per sheet: shared and inline strings, numbers, dates and times,
 * percentages, currency and thousands separators from the cells' number formats (built-in and custom),
 * booleans, errors, formulas' cached results and merged cells. Worksheets are streamed (never held as
 * a tree) and capped at [MAX_SHEET_ROWS] rows × [MAX_COLS] columns each.
 */
object XlsxConverter {
    private class Cell(val text: String, val numeric: Boolean)

    fun convert(file: File, fileName: String): DocResult = DocZip(file).use { z ->
        val wbPart = Rels.of(z, "").ofType("/officeDocument").firstOrNull()?.target?.takeIf { z.has(it) } ?: "xl/workbook.xml"
        val wb = z.xml(wbPart) ?: throw DocException("This isn't an Excel workbook (xl/workbook.xml is missing).")
        val ns = OoxmlNs.of(wb)
        val rels = Rels.of(z, wbPart)
        val date1904 = wb.child(ns.s, "workbookPr")?.attr("date1904").let { it == "1" || it == "true" }
        val strings = sharedStrings(z, rels.ofType("/sharedStrings").firstOrNull()?.target ?: "xl/sharedStrings.xml")
        val formats = cellFormats(z, rels.ofType("/styles").firstOrNull()?.target ?: "xl/styles.xml", ns)
        val pages = ArrayList<DocPage>()
        wb.child(ns.s, "sheets")?.children(ns.s, "sheet")?.forEach { sheet ->
            val name = sheet.attr("name") ?: "Sheet ${pages.size + 1}"
            val state = sheet.attr("state")
            if (state == "veryHidden") return@forEach
            val rel = rels[sheet.attr(ns.r, "id")] ?: return@forEach
            if (!rel.type.endsWith("/worksheet")) return@forEach  // chart sheets, dialog sheets
            val handler = SheetHandler(strings, formats, date1904)
            z.stream(rel.target) { Xml.stream(it, handler) } ?: return@forEach
            pages.add(DocPage(if (state == "hidden") "$name (hidden)" else name, handler.html()))
        }
        if (pages.isEmpty()) throw DocException("The workbook has no worksheets.")
        DocResult(DocKind.XLSX, coreTitle(z) ?: fileName, pages)
    }

    private fun sharedStrings(z: DocZip, part: String): List<String> {
        val out = ArrayList<String>()
        z.stream(part) { input ->
            Xml.stream(input, object : DefaultHandler() {
                private val sb = StringBuilder()
                private var inT = false
                private var inPhonetic = 0
                override fun resolveEntity(publicId: String?, systemId: String?): InputSource = Xml.NoEntities(publicId, systemId)
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                    when (localName) {
                        "si" -> sb.setLength(0)
                        "t" -> inT = inPhonetic == 0
                        "rPh" -> inPhonetic++
                    }
                }
                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    when (localName) {
                        "si" -> out.add(sb.toString())
                        "t" -> inT = false
                        "rPh" -> inPhonetic--
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb.append(ch, start, length) }
            })
        }
        return out
    }

    /** Number-format code of each cell style (cellXfs index → format code). */
    private fun cellFormats(z: DocZip, part: String, ns: OoxmlNs): List<String> {
        val root = runCatching { z.xml(part) }.getOrNull() ?: return emptyList()
        val custom = HashMap<Int, String>()
        root.child(ns.s, "numFmts")?.children(ns.s, "numFmt")?.forEach { f ->
            val id = f.attr("numFmtId")?.toIntOrNull() ?: return@forEach
            custom[id] = f.attr("formatCode") ?: return@forEach
        }
        return root.child(ns.s, "cellXfs")?.children(ns.s, "xf")?.map { xf ->
            val id = xf.attr("numFmtId")?.toIntOrNull() ?: 0
            custom[id] ?: NumFmt.builtin(id)
        }?.toList() ?: emptyList()
    }

    /** One worksheet, streamed into a capped cell map plus its merged ranges. */
    private class SheetHandler(private val strings: List<String>, private val formats: List<String>, private val date1904: Boolean) : DefaultHandler() {
        val cells = HashMap<Long, Cell>()
        var maxRow = -1
        var maxCol = -1
        var truncated = false
        private val merges = ArrayList<IntArray>()   // r0, c0, r1, c1

        private var rowIndex = -1
        private var nextCol = 0
        private var cellRow = 0
        private var cellCol = 0
        private var cellType: String? = null
        private var cellStyle = 0
        private val value = StringBuilder()
        private var inValue = false
        private var inInline = false

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = Xml.NoEntities(publicId, systemId)

        override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
            when (localName) {
                "row" -> { rowIndex = a.getValue("r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1); nextCol = 0 }
                "c" -> {
                    val ref = a.getValue("r")
                    val (r, c) = ref?.let { parseRef(it) } ?: (rowIndex to nextCol)
                    cellRow = r; cellCol = c; nextCol = c + 1
                    cellType = a.getValue("t")
                    cellStyle = a.getValue("s")?.toIntOrNull() ?: 0
                    value.setLength(0)
                }
                "v" -> inValue = true
                "is" -> inInline = true
                "t" -> if (inInline) inValue = true
                "mergeCell" -> a.getValue("ref")?.let { ref ->
                    val parts = ref.split(':')
                    val s = parseRef(parts[0]) ?: return@let
                    val e = parts.getOrNull(1)?.let { parseRef(it) } ?: s
                    merges.add(intArrayOf(s.first, s.second, e.first, e.second))
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (localName) {
                "v" -> inValue = false
                "t" -> if (inInline) inValue = false
                "is" -> inInline = false
                "c" -> commit()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) { if (inValue) value.append(ch, start, length) }

        private fun commit() {
            val raw = value.toString()
            if (raw.isEmpty() && cellType != "inlineStr") return
            if (cellRow >= MAX_SHEET_ROWS || cellCol >= MAX_COLS) { truncated = true; return }
            val cell = when (cellType) {
                "s" -> Cell(raw.trim().toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty(), false)
                "inlineStr", "str" -> Cell(raw, false)
                "b" -> Cell(if (raw.trim() == "1") "TRUE" else "FALSE", false)
                "e" -> Cell(raw, false)
                "d" -> Cell(raw.replace('T', ' ').removeSuffix("Z"), true)
                else -> raw.trim().toDoubleOrNull()?.let { v ->
                    Cell(NumFmt.format(v, formats.getOrNull(cellStyle) ?: "General", date1904), true)
                } ?: Cell(raw, false)
            }
            if (cell.text.isEmpty()) return
            cells[key(cellRow, cellCol)] = cell
            if (cellRow > maxRow) maxRow = cellRow
            if (cellCol > maxCol) maxCol = cellCol
        }

        fun html(): String {
            if (maxRow < 0) return "<p class=\"subtitle\">This sheet is empty.</p>"
            val covered = HashSet<Long>()
            val spans = HashMap<Long, IntArray>()
            for (m in merges) {
                val r1 = minOf(m[2], MAX_SHEET_ROWS - 1); val c1 = minOf(m[3], MAX_COLS - 1)
                if (m[0] > r1 || m[1] > c1) continue
                spans[key(m[0], m[1])] = intArrayOf(r1 - m[0] + 1, c1 - m[1] + 1)
                for (r in m[0]..r1) for (c in m[1]..c1) if (r != m[0] || c != m[1]) covered.add(key(r, c))
                if (r1 > maxRow && cells.containsKey(key(m[0], m[1]))) maxRow = r1
                if (c1 > maxCol && cells.containsKey(key(m[0], m[1]))) maxCol = c1
            }
            val sb = StringBuilder()
            sb.append("<div class=\"tw\"><table class=\"sheet\"><thead><tr><th class=\"ch\"></th>")
            for (c in 0..maxCol) sb.append("<th class=\"ch\">").append(columnName(c)).append("</th>")
            sb.append("</tr></thead><tbody>")
            for (r in 0..maxRow) {
                sb.append("<tr><th class=\"rh\">").append(r + 1).append("</th>")
                for (c in 0..maxCol) {
                    val k = key(r, c)
                    if (k in covered) continue
                    val cell = cells[k]
                    val span = spans[k]
                    sb.append(if (cell?.numeric == true) "<td class=\"n\"" else "<td")
                    if (span != null) {
                        if (span[0] > 1) sb.append(" rowspan=\"").append(span[0]).append('"')
                        if (span[1] > 1) sb.append(" colspan=\"").append(span[1]).append('"')
                    }
                    sb.append('>')
                    if (cell != null) sb.append(esc(cell.text).replace("\n", "<br>"))
                    sb.append("</td>")
                }
                sb.append("</tr>")
            }
            sb.append("</tbody></table></div>")
            if (truncated) sb.append("<p class=\"subtitle\">Only the first $MAX_SHEET_ROWS rows and $MAX_COLS columns are shown.</p>")
            return sb.toString()
        }

        private fun key(r: Int, c: Int): Long = (r.toLong() shl 20) or c.toLong()

        /** "AB12" → (11, 27), 0-based. */
        private fun parseRef(ref: String): Pair<Int, Int>? {
            var i = 0
            var col = 0
            val s = ref.replace("$", "")
            while (i < s.length && s[i].isLetter()) { col = col * 26 + (s[i].uppercaseChar() - 'A' + 1); i++ }
            val row = s.substring(i).toIntOrNull() ?: return null
            if (col == 0) return null
            return (row - 1) to (col - 1)
        }
    }
}

/**
 * Spreadsheet number formats, as far as a reader needs them: General, fixed decimals, thousands
 * separators, percentages, scientific notation, literal prefixes / suffixes (currency signs), the
 * positive;negative;zero sections, and dates / times / durations via their y-m-d-h-s tokens.
 */
object NumFmt {
    fun builtin(id: Int): String = when (id) {
        0 -> "General"; 1 -> "0"; 2 -> "0.00"; 3 -> "#,##0"; 4 -> "#,##0.00"
        9 -> "0%"; 10 -> "0.00%"; 11 -> "0.00E+00"; 12 -> "# ?/?"; 13 -> "# ??/??"
        14 -> "yyyy-mm-dd"; 15 -> "d-mmm-yy"; 16 -> "d-mmm"; 17 -> "mmm-yy"
        18 -> "h:mm AM/PM"; 19 -> "h:mm:ss AM/PM"; 20 -> "h:mm"; 21 -> "h:mm:ss"; 22 -> "yyyy-mm-dd h:mm"
        37 -> "#,##0 ;(#,##0)"; 38 -> "#,##0 ;[Red](#,##0)"; 39 -> "#,##0.00;(#,##0.00)"; 40 -> "#,##0.00;[Red](#,##0.00)"
        45 -> "mm:ss"; 46 -> "[h]:mm:ss"; 47 -> "mm:ss.0"; 48 -> "##0.0E+0"; 49 -> "@"
        else -> "General"
    }

    fun general(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return v.toString()
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        val s = "%.10G".format(Locale.US, v)
        return if (s.contains('E')) {
            val (m, e) = s.split('E')
            m.trimEnd('0').trimEnd('.') + "E" + e.trimStart('+').let { if (it.startsWith("-")) it else "+$it" }
        } else s.trimEnd('0').trimEnd('.')
    }

    /** Strips quoted literals, escapes and [..] blocks so date tokens can be spotted. */
    private fun bare(code: String): String =
        code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\\\."), "").replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("_.|\\*."), "")

    fun isDate(code: String): Boolean {
        val b = bare(code.substringBefore(';')).lowercase()
        if (b == "general" || b.contains("e+") || b.contains("e-")) return false
        return b.any { it == 'y' || it == 'd' || it == 'h' || it == 's' } || (b.contains('m') && !b.contains('0') && !b.contains('#')) ||
            code.contains(Regex("\\[(h+|m+|s+)\\]", RegexOption.IGNORE_CASE))
    }

    fun format(v: Double, code0: String, date1904: Boolean = false): String {
        val code = code0.trim()
        if (code.isEmpty() || code.equals("General", true)) return general(v)
        if (code == "@") return general(v)
        val sections = splitSections(code)
        var sec = sections[0]
        var value = v
        var negPrefix = v < 0
        if (v < 0 && sections.size > 1 && sections[1].isNotBlank()) { sec = sections[1]; value = -v; negPrefix = false }
        else if (v == 0.0 && sections.size > 2 && sections[2].isNotBlank()) sec = sections[2]
        if (isDate(sec)) return date(v, sec, date1904)
        val body = sec.replace(Regex("\\[[^\\]]*\\]")) { m ->
            // [$€-407] style currency blocks keep their symbol; colours and conditions go.
            Regex("^\\[\\$([^-\\]]*)").find(m.value)?.groupValues?.get(1) ?: ""
        }
        if (bare(body).equals("General", true)) return (if (negPrefix) "-" else "") + general(abs(value))
        return number(abs(value), body).let { if (negPrefix && it.any(Char::isDigit) && abs(value) > 0) "-$it" else it }
    }

    private fun splitSections(code: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '"' -> { q = !q; sb.append(c) }
                c == '\\' && i + 1 < code.length -> { sb.append(c).append(code[i + 1]); i++ }
                c == ';' && !q -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** A numeric section: literal prefix + digits (+ thousands, decimals, %, exponent) + literal suffix. */
    private fun number(v0: Double, sec: String): String {
        // Tokenise into literal text and the placeholder run.
        val lit = StringBuilder()
        val prefix = StringBuilder()
        val suffix = StringBuilder()
        val pattern = StringBuilder()
        var seenDigit = false
        var i = 0
        fun literal(s: String) { if (!seenDigit) prefix.append(s) else lit.append(s) }
        while (i < sec.length) {
            val c = sec[i]
            when {
                c == '"' -> { val end = sec.indexOf('"', i + 1).let { if (it < 0) sec.length else it }; literal(sec.substring(i + 1, end)); i = end }
                c == '\\' && i + 1 < sec.length -> { literal(sec[i + 1].toString()); i++ }
                c == '_' && i + 1 < sec.length -> { literal(" "); i++ }
                c == '*' && i + 1 < sec.length -> i++
                c == '0' || c == '#' || c == '?' || ((c == '.' || c == ',') && (seenDigit || (i + 1 < sec.length && sec[i + 1] in "0#?"))) ||
                    ((c == 'E' || c == 'e') && seenDigit && i + 1 < sec.length && sec[i + 1] in "+-") -> {
                    if (lit.isNotEmpty()) { pattern.append(lit); lit.setLength(0) }
                    pattern.append(c)
                    if (c == 'E' || c == 'e') { pattern.append(sec[i + 1]); i++ }
                    seenDigit = true
                }
                c == '%' -> literal("%")
                else -> literal(c.toString())
            }
            i++
        }
        suffix.append(lit)
        val pct = sec.count { it == '%' }
        var v = v0
        repeat(pct) { v *= 100 }
        val pat = pattern.toString().replace(Regex("[^0#?.,Ee+-]"), "")
        if (pat.isEmpty()) return prefix.toString() + suffix.toString()
        val expAt = pat.indexOfFirst { it == 'E' || it == 'e' }
        val mant = if (expAt >= 0) pat.substring(0, expAt) else pat
        // Trailing commas scale by thousands.
        var scaled = mant
        while (scaled.endsWith(',')) { v /= 1000; scaled = scaled.dropLast(1) }
        val intPart = scaled.substringBefore('.')
        val decPart = if (scaled.contains('.')) scaled.substringAfter('.') else ""
        val decimals = decPart.count { it == '0' || it == '#' || it == '?' }
        val minDecimals = decPart.count { it == '0' }
        val grouping = intPart.contains(',')
        val minInt = intPart.count { it == '0' }
        val digits = if (expAt >= 0) {
            val expDigits = pat.substring(expAt + 2).count { it == '0' }.coerceAtLeast(1)
            var e = if (v == 0.0) 0 else floor(kotlin.math.log10(v)).toInt()
            val intDigits = intPart.count { it == '0' || it == '#' }.coerceAtLeast(1)
            if (intDigits > 1) e -= Math.floorMod(e, intDigits)
            val m = v / Math.pow(10.0, e.toDouble())
            fixed(m, decimals, minDecimals, false, 1) + "E" + (if (e < 0) "-" else "+") + abs(e).toString().padStart(expDigits, '0')
        } else fixed(v, decimals, minDecimals, grouping, minInt)
        return prefix.toString() + digits + suffix.toString()
    }

    private fun fixed(v: Double, decimals: Int, minDecimals: Int, grouping: Boolean, minInt: Int): String {
        val rounded = java.math.BigDecimal(v).setScale(decimals, java.math.RoundingMode.HALF_UP)
        var s = rounded.toPlainString()
        var intS = s.substringBefore('.')
        var decS = if (s.contains('.')) s.substringAfter('.') else ""
        while (decS.length > minDecimals && decS.endsWith('0')) decS = decS.dropLast(1)
        if (intS == "0" && minInt == 0 && decS.isNotEmpty()) intS = ""
        intS = intS.padStart(minInt, '0')
        if (grouping) intS = intS.reversed().chunked(3).joinToString(",").reversed()
        s = if (decS.isEmpty()) intS else "$intS.$decS"
        return s.ifEmpty { "0" }
    }

    /** Serial day number → date / time text by the format's tokens. */
    private fun date(serial: Double, code: String, date1904: Boolean): String {
        val base = if (date1904) LocalDateTime.of(1904, 1, 1, 0, 0) else LocalDateTime.of(1899, 12, 30, 0, 0)
        // Excel's 1900 leap-year bug: serials before 1 March 1900 are one day off.
        val s = if (!date1904 && serial < 61) serial + 1 else serial
        val days = floor(s).toLong()
        val secs = ((s - days) * 86400).roundToLong()
        val dt = base.plusDays(days).plusSeconds(secs)
        val totalHours = (serial * 24).toLong()
        val sb = StringBuilder()
        val hasAmPm = code.contains("AM/PM", true) || code.contains("A/P", true)
        var i = 0
        var lastWasHour = false
        val c = code
        fun count(ch: Char): Int { var n = 0; while (i + n < c.length && c[i + n].equals(ch, true)) n++; return n }
        while (i < c.length) {
            val ch = c[i]
            when {
                ch == '"' -> { val end = c.indexOf('"', i + 1).let { if (it < 0) c.length else it }; sb.append(c, i + 1, end); i = end + 1; continue }
                ch == '\\' && i + 1 < c.length -> { sb.append(c[i + 1]); i += 2; continue }
                ch == '[' -> {
                    val end = c.indexOf(']', i).let { if (it < 0) c.length else it }
                    val tok = c.substring(i + 1, end).lowercase()
                    when {
                        tok.startsWith("h") -> { sb.append(totalHours.toString().padStart(tok.length, '0')); lastWasHour = true }
                        tok.startsWith("m") -> sb.append((serial * 1440).toLong().toString().padStart(tok.length, '0'))
                        tok.startsWith("s") -> sb.append((serial * 86400).roundToLong().toString().padStart(tok.length, '0'))
                    }
                    i = end + 1; continue
                }
                c.regionMatches(i, "AM/PM", 0, 5, true) -> { sb.append(if (dt.hour < 12) "AM" else "PM"); i += 5; continue }
                c.regionMatches(i, "A/P", 0, 3, true) -> { sb.append(if (dt.hour < 12) "A" else "P"); i += 3; continue }
                ch.equals('y', true) -> { val n = count('y'); sb.append(if (n <= 2) (dt.year % 100).toString().padStart(2, '0') else dt.year.toString()); i += n; continue }
                ch.equals('d', true) -> {
                    val n = count('d')
                    sb.append(when (n) {
                        1 -> dt.dayOfMonth.toString(); 2 -> dt.dayOfMonth.toString().padStart(2, '0')
                        3 -> dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                        else -> dt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
                    })
                    i += n; continue
                }
                ch.equals('h', true) -> {
                    val n = count('h')
                    val h = if (hasAmPm) (dt.hour % 12).let { if (it == 0) 12 else it } else dt.hour
                    sb.append(if (n >= 2) h.toString().padStart(2, '0') else h.toString())
                    i += n; lastWasHour = true; continue
                }
                ch.equals('m', true) -> {
                    val n = count('m')
                    // "m" after an hour or before seconds is minutes; otherwise months.
                    val rest = c.substring(i + n).trimStart(':', ' ', '.')
                    val minutes = lastWasHour || rest.startsWith("s", true) || rest.startsWith("[s", true)
                    sb.append(when {
                        minutes -> if (n >= 2) dt.minute.toString().padStart(2, '0') else dt.minute.toString()
                        n == 1 -> dt.monthValue.toString()
                        n == 2 -> dt.monthValue.toString().padStart(2, '0')
                        n == 3 -> dt.month.getDisplayName(TextStyle.SHORT, Locale.US)
                        n == 4 -> dt.month.getDisplayName(TextStyle.FULL, Locale.US)
                        else -> dt.month.getDisplayName(TextStyle.NARROW, Locale.US)
                    })
                    i += n; lastWasHour = false; continue
                }
                ch.equals('s', true) -> {
                    val n = count('s')
                    sb.append(if (n >= 2) dt.second.toString().padStart(2, '0') else dt.second.toString())
                    i += n
                    if (i < c.length && c[i] == '.') {
                        var z = 0
                        while (i + 1 + z < c.length && c[i + 1 + z] == '0') z++
                        if (z > 0) {
                            val frac = ((s * 86400) - floor(s * 86400)).let { "%.${z}f".format(Locale.US, it).substringAfter('.') }
                            sb.append('.').append(frac)
                            i += 1 + z
                        }
                    }
                    continue
                }
                ch == '_' && i + 1 < c.length -> { sb.append(' '); i += 2; continue }
                ch == '*' && i + 1 < c.length -> { i += 2; continue }
                ch == '@' -> { i++; continue }
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString().trim().ifEmpty { LocalDate.from(dt).toString() }
    }
}
