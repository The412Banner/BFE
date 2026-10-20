package com.the412banner.bfe.apk.internals

import com.reandroid.apk.AndroidFrameworks
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.xml.XMLFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.zip.ZipFile

/**
 * Android's binary XML (AXML: a compiled AndroidManifest.xml or an XML file under res/) to readable XML and back,
 * on ARSCLib. Resource ids are shown as `@type/name` when the APK's resources.arsc is at hand (and
 * `@android:…` names always, from the Android resource tables ARSCLib carries); ids it can't name stay
 * as `@0x7f…`, which encode back to the same id.
 */
object Axml {

    /** True when [bytes] start like a compiled XML chunk (not plain text XML). */
    fun isBinary(bytes: ByteArray): Boolean = bytes.size >= 8 && runCatching { ResXmlDocument.isResXmlBlock(bytes) }.getOrDefault(false)

    /**
     * The resource table names are looked up in: [apk]'s resources.arsc when it has one, else an empty
     * table. Either way Android's own resources are attached, for `android:` attribute names.
     */
    fun tableFor(apk: File?): TableBlock {
        val table = apk?.let { f ->
            runCatching {
                ZipFile(f).use { z -> z.getEntry(TableBlock.FILE_NAME)?.let { e -> z.getInputStream(e).use { TableBlock.load(it) } } }
            }.getOrNull()
        } ?: TableBlock()
        withFramework(table)
        return table
    }

    /** Attaches Android's framework resources to [table] once (attribute names, `@android:` refs). */
    fun withFramework(table: TableBlock): TableBlock {
        if (!table.hasFramework()) runCatching { table.addFramework(framework) }
        return table
    }

    private val framework: TableBlock by lazy { AndroidFrameworks.getLatest().tableBlock }

    private fun packageOf(table: TableBlock): PackageBlock = table.pickOne() ?: table.pickOrEmptyPackage()

    /** Decodes compiled XML [bytes] to text. Throws with a reason when they aren't binary XML. */
    fun decode(bytes: ByteArray, table: TableBlock): String {
        if (!isBinary(bytes)) throw IOException("This isn't compiled (binary) XML.")
        val doc = ResXmlDocument()
        doc.readBytes(ByteArrayInputStream(bytes))
        doc.setPackageBlock(packageOf(table))
        return doc.serializeToXml()
    }

    /**
     * Compiles XML [text] back to binary XML against [table]. A name it can't resolve
     * (`@string/missing`, an unknown attribute) fails with ARSCLib's message, including the line.
     */
    fun encode(text: String, table: TableBlock): ByteArray {
        val doc = ResXmlDocument()
        doc.setPackageBlock(packageOf(table))
        try {
            doc.parse(XMLFactory.newPullParser(StringReader(text)))
        } catch (e: Exception) {
            throw IOException(e.message?.replace("\n", " ")?.trim()?.ifBlank { null } ?: "The XML couldn't be compiled.", e)
        }
        doc.refreshFull()
        return doc.bytes
    }
}
