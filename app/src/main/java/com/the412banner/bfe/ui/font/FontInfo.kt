package com.the412banner.bfe.ui.font

import java.io.File
import java.io.RandomAccessFile

/**
 * What the font viewer says about a font file: its [format] (from the first four bytes), names from
 * its `name` table and the glyph count from its `maxp` table. Names and glyphs are null when the file
 * doesn't carry them (or is a compressed web font, whose tables aren't read). [faces] > 1 for a
 * collection (.ttc), described by its first font.
 */
internal data class FontInfo(
    val format: String,
    val family: String?,
    val style: String?,
    val fullName: String?,
    val glyphs: Int?,
    val faces: Int,
)

/** Reads [FontInfo] from a TrueType / OpenType file (sfnt tables), without loading it whole. */
internal object FontInfoReader {

    /** Null when [file] doesn't start like a font file. */
    fun read(file: File): FontInfo? = RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 12) return null
        val tag = raf.readInt()
        when (tag) {
            0x00010000, TRUE -> sfnt(raf, 0, "TrueType", 1)
            OTTO -> sfnt(raf, 0, "OpenType (CFF)", 1)
            TTCF -> {
                raf.seek(8)
                val count = raf.readInt()
                val first = raf.readInt().toLong() and 0xFFFFFFFFL
                sfnt(raf, first, "TrueType collection", count.coerceAtLeast(1))
            }
            WOFF -> FontInfo("WOFF web font", null, null, null, null, 1)
            WOF2 -> FontInfo("WOFF2 web font", null, null, null, null, 1)
            else -> null
        }
    }

    private fun sfnt(raf: RandomAccessFile, base: Long, format: String, faces: Int): FontInfo = runCatching {
        raf.seek(base + 4)
        val numTables = raf.readUnsignedShort().coerceAtMost(512)
        var maxp = -1L
        var name = -1L
        for (i in 0 until numTables) {
            raf.seek(base + 12 + i * 16L)
            val t = raf.readInt()
            raf.readInt() // checksum
            val off = raf.readInt().toLong() and 0xFFFFFFFFL
            when (t) {
                MAXP -> maxp = off
                NAME -> name = off
            }
        }
        val glyphs = if (maxp >= 0) { raf.seek(maxp + 4); raf.readUnsignedShort() } else null
        val names = if (name >= 0) names(raf, name) else emptyMap()
        FontInfo(format, names[1], names[2], names[4], glyphs, faces)
    }.getOrElse { FontInfo(format, null, null, null, null, faces) }

    // nameID → text: Windows Unicode (US English first) wins over Mac Roman.
    private fun names(raf: RandomAccessFile, table: Long): Map<Int, String> {
        raf.seek(table + 2)
        val count = raf.readUnsignedShort().coerceAtMost(2048)
        val strings = table + raf.readUnsignedShort()
        data class Rec(val platform: Int, val lang: Int, val id: Int, val len: Int, val off: Int)
        val recs = (0 until count).map {
            raf.seek(table + 6 + it * 12L)
            val platform = raf.readUnsignedShort()
            raf.readUnsignedShort() // encoding
            val lang = raf.readUnsignedShort()
            Rec(platform, lang, raf.readUnsignedShort(), raf.readUnsignedShort(), raf.readUnsignedShort())
        }
        val out = HashMap<Int, String>()
        for (id in listOf(1, 2, 4)) {
            val r = recs.filter { it.id == id && it.len in 1..1024 }
                .minByOrNull { if (it.platform == 3) (if (it.lang == 0x409) 0 else 1) else if (it.platform == 0) 2 else 3 } ?: continue
            val bytes = ByteArray(r.len)
            raf.seek(strings + r.off)
            raf.readFully(bytes)
            val s = if (r.platform == 3 || r.platform == 0) String(bytes, Charsets.UTF_16BE) else String(bytes, Charsets.ISO_8859_1)
            s.trim().takeIf { it.isNotEmpty() }?.let { out[id] = it }
        }
        return out
    }

    private const val TRUE = 0x74727565 // 'true'
    private const val OTTO = 0x4F54544F // 'OTTO'
    private const val TTCF = 0x74746366 // 'ttcf'
    private const val WOFF = 0x774F4646 // 'wOFF'
    private const val WOF2 = 0x774F4632 // 'wOF2'
    private const val MAXP = 0x6D617870 // 'maxp'
    private const val NAME = 0x6E616D65 // 'name'
}
