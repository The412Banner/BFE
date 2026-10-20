package com.the412banner.bfe.apk.internals

import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.coder.ValueCoder
import com.reandroid.arsc.value.Entry
import com.reandroid.arsc.value.ValueType
import java.io.ByteArrayInputStream

/** The kinds of value the resources browser can change in place; everything else is view-only. */
enum class ArscKind(val label: String) { STRING("string"), BOOL("bool"), INT("integer"), COLOR("color"), DIMEN("dimension"), OTHER("") }

/** One configuration's value of a resource: its qualifiers ("" = default), the text shown, the kind. */
class ArscValue internal constructor(val config: String, val text: String, val kind: ArscKind, internal val entry: Entry) {
    val editable: Boolean get() = kind != ArscKind.OTHER
    /** "default" for the unqualified value, else the qualifiers without the leading dash ("de", "night-xxhdpi"). */
    val configLabel: String get() = config.trimStart('-').ifEmpty { "default" }
}

/** One resource (`@type/name`) with its values in every configuration. */
class ArscResource internal constructor(val id: Int, val type: String, val name: String, val values: List<ArscValue>) {
    val hexId: String get() = "0x%08x".format(id)
}

/** A resource type of one package ("string", "drawable", …) and how many resources it has. */
class ArscType(val name: String, val count: Int)

/** A hit of the resources search: the resource and the value that matched (null = its name matched). */
class ArscHit(val resource: ArscResource, val value: ArscValue?)

/**
 * A resources.arsc opened for browsing and editing (ARSCLib): packages → types → resources → values per
 * configuration (locale, density, night, …). Strings and the simple scalar values (bool, integer,
 * color, dimension) can be changed; [bytes] writes the table back.
 */
class ArscDoc private constructor(val table: TableBlock) {
    val packages: List<PackageBlock> = table.listPackages().toList()
    private val cache = HashMap<String, List<ArscResource>>()
    @Volatile var edits = 0
        private set

    fun packageLabel(p: PackageBlock): String = "${p.name} (0x%02x)".format(p.id)

    fun types(p: PackageBlock): List<ArscType> = p.listSpecTypePairs().map { s ->
        var n = 0
        val it = s.resources
        while (it.hasNext()) { it.next(); n++ }
        ArscType(s.typeName, n)
    }

    /** The resources of [type] in [p], decoded once and kept. */
    @Synchronized
    fun resources(p: PackageBlock, type: String): List<ArscResource> = cache.getOrPut("${p.id}/$type") {
        val pair = p.getSpecTypePair(type) ?: return@getOrPut emptyList()
        val out = ArrayList<ArscResource>()
        val it = pair.resources
        while (it.hasNext()) {
            val re = it.next()
            val values = ArrayList<ArscValue>()
            for (e in re) values += valueOf(e, type)
            out += ArscResource(re.resourceId, type, re.name ?: re.hexId, values)
        }
        out
    }

    /** Resources whose name or (any configuration's) value contains [query], at most [limit]. */
    fun search(query: String, limit: Int = 2000, cancelled: () -> Boolean = { false }): List<ArscHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<ArscHit>()
        for (p in packages) for (t in types(p)) {
            if (cancelled()) return hits
            for (r in resources(p, t.name)) {
                if (r.name.contains(q, ignoreCase = true) || r.hexId.equals(q, ignoreCase = true)) { hits += ArscHit(r, null); if (hits.size >= limit) return hits; continue }
                for (v in r.values) if (v.text.contains(q, ignoreCase = true)) { hits += ArscHit(r, v); if (hits.size >= limit) return hits }
            }
        }
        return hits
    }

    /**
     * Sets [v] from what the user typed, checked for its kind (a bool must be true / false, a color
     * #RGB / #ARGB / #RRGGBB / #AARRGGBB, a dimension like 16dp). Returns the new value, or throws
     * IllegalArgumentException with the reason.
     */
    @Synchronized
    fun setValue(r: ArscResource, v: ArscValue, input: String): ArscValue {
        val e = v.entry
        when (v.kind) {
            ArscKind.STRING -> e.setValueAsString(input)
            ArscKind.BOOL -> e.setValueAsBoolean(
                when (input.trim().lowercase()) { "true" -> true; "false" -> false; else -> throw IllegalArgumentException("Type true or false") },
            )
            ArscKind.INT -> encodeInto(e, input, "Type a whole number (or 0x… hex)", ValueType.DEC, ValueType.HEX)
            ArscKind.COLOR -> encodeInto(e, input, "Type a color like #FF8800 or #80FF8800",
                ValueType.COLOR_ARGB8, ValueType.COLOR_RGB8, ValueType.COLOR_ARGB4, ValueType.COLOR_RGB4)
            ArscKind.DIMEN -> encodeInto(e, input, "Type a size like 16dp, 14sp, 2px or 50%", ValueType.DIMENSION, ValueType.FRACTION, ValueType.FLOAT)
            ArscKind.OTHER -> throw IllegalArgumentException("This kind of value can't be edited here")
        }
        edits++
        val nv = valueOf(e, r.type)
        // The cached list holds the old value object: swap it in place.
        (r.values as? MutableList<ArscValue>)?.let { list -> val i = list.indexOf(v); if (i >= 0) list[i] = nv }
        return nv
    }

    private fun encodeInto(e: Entry, input: String, problem: String, vararg types: ValueType) {
        val res = ValueCoder.encode(input.trim(), *types)
        if (res == null || res.isError) throw IllegalArgumentException(problem)
        val rv = e.resValue ?: throw IllegalArgumentException(problem)
        rv.setValue(res)
    }

    /** The table as resources.arsc bytes, with the edits. */
    @Synchronized
    fun bytes(): ByteArray {
        table.refresh()
        return table.bytes
    }

    // A string in any type but `string` is a file path (res/drawable/x.png): shown, never edited.
    private fun valueOf(e: Entry, resType: String): ArscValue {
        val config = runCatching { e.resConfig?.qualifiers }.getOrNull().orEmpty()
        val rv = e.resValue
        if (rv == null) {
            // A bag: style, array, plurals, attr — shown as its items, not editable.
            val text = runCatching {
                val items = ArrayList<String>()
                val it = e.resValueMapArray.iterator(true)
                while (it.hasNext() && items.size < 200) {
                    val m = it.next()
                    val n = runCatching { m.decodeName(true) }.getOrNull() ?: "[${items.size}]"
                    items += "$n = ${runCatching { m.decodeValue() }.getOrNull() ?: ""}"
                }
                if (items.isEmpty()) "(empty)" else items.joinToString("\n")
            }.getOrDefault("(complex value)")
            return ArscValue(config, text, ArscKind.OTHER, e)
        }
        val type = rv.valueType
        val kind = when (type) {
            ValueType.STRING -> if (resType == "string") ArscKind.STRING else ArscKind.OTHER
            ValueType.BOOLEAN -> ArscKind.BOOL
            ValueType.DEC, ValueType.HEX -> ArscKind.INT
            ValueType.COLOR_ARGB8, ValueType.COLOR_RGB8, ValueType.COLOR_ARGB4, ValueType.COLOR_RGB4 -> ArscKind.COLOR
            ValueType.DIMENSION, ValueType.FRACTION, ValueType.FLOAT -> ArscKind.DIMEN
            else -> ArscKind.OTHER
        }
        val text = runCatching { if (type == ValueType.STRING) rv.valueAsString else rv.decodeValue() }.getOrNull() ?: ""
        return ArscValue(config, text, kind, e)
    }

    companion object {
        fun open(bytes: ByteArray): ArscDoc = ArscDoc(Axml.withFramework(TableBlock.load(ByteArrayInputStream(bytes))))
    }
}
