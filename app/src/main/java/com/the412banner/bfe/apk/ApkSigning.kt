package com.the412banner.bfe.apk

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Signing + verification on Google's apksig (pure Java — the same code `apksigner` runs).
 *
 * Alignment: apksig with `setAlignmentPreserved(false)` re-pads every STORED entry itself while it
 * copies the zip — 4 bytes by default and [libPageAlignment] for `*.so` — writing the proper
 * `0xD935` zipalign extra record, so the output is what `zipalign -p -v` would accept. (The
 * ARSCLib writer already aligned the clone the same way; for the standalone "Sign APK…" path this
 * is the only alignment pass, so it's what makes an unaligned input come out aligned.)
 */
object ApkSigning {

    class Verification(val verified: Boolean, val v1: Boolean, val v2: Boolean, val v3: Boolean, val signerDn: String?, val errors: List<String>)

    /**
     * Signs [input] to [output] with [key] and [schemes]; any signature already on [input] (JAR
     * files, signing block) is stripped by apksig. [libPageAlignment] = 16384 when the APK maps its
     * native libs in place (extractNativeLibs=false), else 4096.
     */
    fun sign(input: File, output: File, key: KeyRef, schemes: SignSchemes, libPageAlignment: Int) {
        if (!schemes.v1 && !schemes.v2 && !schemes.v3) throw IOException("Pick at least one signature scheme")
        val loaded = SigningKeys.load(key)
        val config = ApkSigner.SignerConfig.Builder(
            key.alias.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "KEY" }.uppercase().take(8),
            KeyConfig.Jca(loaded.privateKey),
            loaded.certificates,
        ).build()
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        ApkSigner.Builder(listOf(config))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(schemes.v1)
            .setV2SigningEnabled(schemes.v2)
            .setV3SigningEnabled(schemes.v3)
            .setV4SigningEnabled(false)
            .setAlignmentPreserved(false)
            .setLibraryPageAlignmentBytes(libPageAlignment)
            .setDebuggableApkPermitted(true)
            .setCreatedBy("BFE")
            .build()
            .sign()
    }

    /** apksig's ApkVerifier over [apk]: which schemes verified and the signer's subject DN. */
    fun verify(apk: File, minSdkForV1: Int = 21): Verification {
        val r = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdkForV1).build().verify()
        val dn = r.signerCertificates.firstOrNull()?.subjectX500Principal?.name
        val errs = r.allErrors.map { it.toString() }
        return Verification(r.isVerified, r.isVerifiedUsingV1Scheme, r.isVerifiedUsingV2Scheme, r.isVerifiedUsingV3Scheme, dn, errs)
    }

    /**
     * Independent alignment audit of a finished APK (not apksig's own code): walks the central
     * directory and checks every STORED entry's data offset — 4-byte, and [libPageAlignment] for
     * every native .so under lib/ (no slash-star glob in this comment: it would nest a block comment).
     * Returns the offending entry names (empty = aligned).
     */
    fun misalignedEntries(apk: File, libPageAlignment: Int): List<String> {
        val bad = ArrayList<String>()
        RandomAccessFile(apk, "r").use { raf ->
            val len = raf.length()
            // Locate the EOCD (scan back over a possible comment).
            var eocd = -1L
            val maxBack = minOf(len, 65557L)
            val tail = ByteArray(maxBack.toInt())
            raf.seek(len - maxBack); raf.readFully(tail)
            var i = tail.size - 22
            while (i >= 0) {
                if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4B.toByte() && tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()) { eocd = len - maxBack + i; break }
                i--
            }
            if (eocd < 0) throw IOException("Not a zip: no end-of-central-directory")
            raf.seek(eocd + 10)
            val entries = readU16(raf)
            raf.seek(eocd + 16)
            val cdOffset = readU32(raf)
            raf.seek(cdOffset)
            repeat(entries) {
                val sig = readU32(raf)
                if (sig != 0x02014b50L) throw IOException("Bad central directory")
                raf.skipBytes(6)            // versions + flags
                val method = readU16(raf)
                raf.skipBytes(16)           // time, date, crc, csize, usize
                val nameLen = readU16(raf); val extraLen = readU16(raf); val commentLen = readU16(raf)
                raf.skipBytes(8)            // disk, int attrs, ext attrs
                val lho = readU32(raf)
                val nameBytes = ByteArray(nameLen); raf.readFully(nameBytes)
                raf.skipBytes(extraLen + commentLen)
                val name = String(nameBytes, Charsets.UTF_8)
                if (method == 0) {
                    val here = raf.filePointer
                    raf.seek(lho + 26)
                    val lNameLen = readU16(raf); val lExtraLen = readU16(raf)
                    val dataOff = lho + 30 + lNameLen + lExtraLen
                    val want = if (name.startsWith("lib/") && name.endsWith(".so")) libPageAlignment else 4
                    if (dataOff % want != 0L) bad += "$name (offset $dataOff, wanted ×$want)"
                    raf.seek(here)
                }
            }
        }
        return bad
    }

    private fun readU16(raf: RandomAccessFile): Int { val a = raf.read(); val b = raf.read(); return (a or (b shl 8)) }
    private fun readU32(raf: RandomAccessFile): Long {
        val a = raf.read().toLong(); val b = raf.read().toLong(); val c = raf.read().toLong(); val d = raf.read().toLong()
        return a or (b shl 8) or (c shl 16) or (d shl 24)
    }
}
