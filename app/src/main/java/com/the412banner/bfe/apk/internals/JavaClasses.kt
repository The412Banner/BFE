package com.the412banner.bfe.apk.internals

import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.ZipFile

/**
 * Java bytecode, read with ASM: the classes of a jar, and one class as a javap-style listing — the
 * class header (version, access, super class, interfaces), its fields and methods with their
 * descriptors, and each method's bytecode (asm-util's Textifier).
 */
object JavaClasses {

    /** The .class entries of [jar], as their paths inside it, sorted. */
    fun list(jar: File): List<String> = ZipFile(jar).use { z ->
        z.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.endsWith("module-info.class") }.map { it.name }.sorted().toList()
    }

    /** True for a compiled class: the 0xCAFEBABE magic. */
    fun isClass(bytes: ByteArray): Boolean = bytes.size >= 10 &&
        bytes[0] == 0xCA.toByte() && bytes[1] == 0xFE.toByte() && bytes[2] == 0xBA.toByte() && bytes[3] == 0xBE.toByte()

    /** The javap-like listing of class [bytes]. */
    fun listing(bytes: ByteArray): String {
        if (!isClass(bytes)) throw IOException("This isn't a Java class file.")
        val major = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("// ${javaVersion(major)} class file")
        try {
            ClassReader(bytes).accept(TraceClassVisitor(null, Textifier(), pw), 0)
        } catch (e: Exception) {
            throw IOException("ASM couldn't read this class: ${e.message ?: e.javaClass.simpleName}", e)
        }
        pw.flush()
        return sw.toString()
    }

    private fun javaVersion(major: Int): String = when {
        major >= 53 -> "Java ${major - 44}"
        major >= 45 -> "Java 1.${major - 44}"
        else -> "unknown"
    }
}
