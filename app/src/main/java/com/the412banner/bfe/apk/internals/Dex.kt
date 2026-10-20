package com.the412banner.bfe.apk.internals

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.TokenSource
import org.antlr.runtime.tree.CommonTree
import org.antlr.runtime.tree.CommonTreeNodeStream
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter

/** smali that doesn't assemble: every error the lexer, parser and tree walker reported ("[line,col] …"). */
class SmaliException(val errors: List<String>) : IOException(errors.firstOrNull() ?: "The smali has errors")

/** A hit of the dex search: the class, and the method / string that matched (null for a class-name hit). */
class DexHit(val classType: String, val detail: String?)

/** What the dex search looks through. */
enum class DexSearchMode(val label: String) { CLASSES("Classes"), METHODS("Methods"), STRINGS("Strings") }

/**
 * One dex file opened with dexlib2: its classes (type descriptors, sorted), each class as smali
 * (baksmali), search, and rebuilding the dex with edited classes. The dex bytes stay in memory only
 * while it is open; [DexSet] keeps the few most recently used ones.
 */
class DexFile(val name: String, bytes: ByteArray) {
    private val dex: DexBackedDexFile
    /** Every class's type descriptor (`Lcom/example/Foo;`), sorted. */
    val classes: List<String>
    val dexVersion: Int = runCatching { HeaderItem.getVersion(bytes, 0) }.getOrDefault(35)

    init {
        val opcodes = runCatching { Opcodes.forDexVersion(dexVersion) }.getOrNull() ?: Opcodes.getDefault()
        dex = DexBackedDexFile(opcodes, bytes)
        classes = dex.classes.map { it.type }.sorted()
    }

    private val byType: Map<String, ClassDef> by lazy { dex.classes.associateBy { it.type } }

    fun classDef(type: String): ClassDef? = byType[type]

    /** [type] as smali, the way baksmali writes it (registers as p0…, .locals, debug info kept). */
    fun smali(type: String): String {
        val def = byType[type] ?: throw IOException("$type isn't in $name")
        return smaliOf(def)
    }

    /** Search by [mode]; at most [limit] hits, stops early when [cancelled] says so. */
    fun search(query: String, mode: DexSearchMode, limit: Int = 2000, cancelled: () -> Boolean = { false }): List<DexHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<DexHit>()
        when (mode) {
            DexSearchMode.CLASSES -> for (c in classes) {
                if (javaName(c).contains(q, ignoreCase = true)) { out += DexHit(c, null); if (out.size >= limit) break }
            }
            DexSearchMode.METHODS -> for (c in dex.classes) {
                if (cancelled()) break
                for (m in c.methods) {
                    if (m.name.contains(q, ignoreCase = true)) {
                        out += DexHit(c.type, m.name + "(" + m.parameterTypes.joinToString("") + ")" + m.returnType)
                        if (out.size >= limit) return out
                    }
                }
            }
            DexSearchMode.STRINGS -> for (c in dex.classes) {
                if (cancelled()) break
                for (m in c.methods) {
                    val impl = m.implementation ?: continue
                    for (ins in impl.instructions) {
                        val ref = (ins as? ReferenceInstruction)?.reference as? StringReference ?: continue
                        if (ref.string.contains(q, ignoreCase = true)) {
                            out += DexHit(c.type, "${m.name}: \"${ref.string.take(120)}\"")
                            if (out.size >= limit) return out
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * Writes this dex to [out] with [replaced] classes swapped in (keyed by the ORIGINAL type, so a
     * class renamed in its smali replaces the old one). [added] classes are appended.
     */
    fun rebuild(replaced: Map<String, ClassDef>, out: File, added: List<ClassDef> = emptyList()) {
        val pool = DexPool(dex.opcodes)
        for (c in dex.classes) pool.internClass(replaced[c.type] ?: c)
        added.forEach { pool.internClass(it) }
        out.parentFile?.mkdirs()
        pool.writeTo(FileDataStore(out))
    }

    companion object {
        /** baksmali's options as BFE uses them: parameter registers, .locals, debug info, tidy labels. */
        private fun options(api: Int) = BaksmaliOptions().apply {
            apiLevel = api
            parameterRegisters = true
            localsDirective = true
            sequentialLabels = true
            debugInfo = true
            accessorComments = false
        }

        fun smaliOf(def: ClassDef, api: Int = 30): String {
            val sw = StringWriter()
            val w = BaksmaliWriter(sw)
            ClassDefinition(options(api), def).writeTo(w)
            w.flush()
            return sw.toString()
        }

        /**
         * Assembles one class from [smali] (smali's lexer → parser → tree walker into a one-class dex),
         * then reads it back so it can go into a [DexPool]. Throws [SmaliException] with every error.
         */
        fun assemble(smali: String, api: Int = 30): ClassDef {
            val errors = ArrayList<String>()
            val builder = DexBuilder(Opcodes.forApi(api))
            val lexer = smaliFlexLexer(StringReader(smali), api)
            val tokens = CommonTokenStream(lexer as TokenSource)
            val parser = object : smaliParser(tokens) {
                override fun emitErrorMessage(msg: String?) { errors += msg ?: "syntax error" }
            }
            parser.setVerboseErrors(false)
            parser.setAllowOdex(false)
            parser.setApiLevel(api)
            val result = parser.smali_file()
            if (lexer.numberOfSyntaxErrors > 0 && errors.isEmpty()) errors += "The smali has ${lexer.numberOfSyntaxErrors} unreadable token(s)"
            if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0 || errors.isNotEmpty()) throw SmaliException(errors)
            val nodes = CommonTreeNodeStream(result.tree as CommonTree)
            nodes.tokenStream = tokens
            val walker = object : smaliTreeWalker(nodes) {
                override fun emitErrorMessage(msg: String?) { errors += msg ?: "error" }
            }
            walker.setApiLevel(api)
            walker.setVerboseErrors(false)
            walker.setDexBuilder(builder)
            val built: ClassDef = try {
                walker.smali_file()
            } catch (e: Exception) {
                throw SmaliException(errors + (e.message ?: e.javaClass.simpleName))
            }
            if (walker.numberOfSyntaxErrors > 0 || errors.isNotEmpty()) throw SmaliException(errors.ifEmpty { listOf("The smali has errors") })
            val store = MemoryDataStore()
            builder.writeTo(store)
            val one = DexBackedDexFile(Opcodes.forApi(api), store.buffer.copyOf(store.size))
            return one.classes.firstOrNull() ?: throw SmaliException(listOf("No class was assembled (missing .class line?)") + built.type)
        }

        /** `Lcom/example/Foo$Bar;` → `com.example.Foo$Bar`. */
        fun javaName(type: String): String = type.removePrefix("L").removeSuffix(";").replace('/', '.')
    }
}

/**
 * The dex files of one APK (or a single .dex), opened on demand: [load] reads and parses one dex the
 * first time it is asked for and keeps the [KEEP] most recently used, so an APK with ten 10 MB dex
 * files never holds them all. Class lists, once read, are remembered for the session.
 */
class DexSet(private val source: File, val entries: List<String>, private val standalone: Boolean) {
    private val open = LinkedHashMap<String, DexFile>(4, 0.75f, true)
    private val classCounts = HashMap<String, Int>()

    @Synchronized
    fun load(entry: String): DexFile {
        open[entry]?.let { return it }
        val bytes = if (standalone) source.readBytes() else ApkEntries.read(source, entry)
        val d = DexFile(entry, bytes)
        open[entry] = d
        classCounts[entry] = d.classes.size
        while (open.size > KEEP) open.remove(open.keys.first())
        return d
    }

    /** Class count of [entry] once it has been opened, else null. */
    @Synchronized
    fun classCount(entry: String): Int? = classCounts[entry]

    companion object {
        const val KEEP = 2

        fun forApk(apk: File): DexSet = DexSet(apk, ApkEntries.dexEntries(apk), standalone = false)
        fun forFile(dex: File): DexSet = DexSet(dex, listOf(dex.name), standalone = true)
    }
}
