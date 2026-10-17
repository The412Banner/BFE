package com.the412banner.bfe.cloud

import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64

/**
 * rclone's own config-file encryption, "RCLONE_ENCRYPT_V0" (rclone fs/config/crypt.go), for the few
 * places BFE handles rclone.conf itself: checking it opens before rcd is started on it, renaming a
 * remote, and sealing a plain file when the engine isn't there to do it.
 *
 * Format: the key is SHA-256("[" + password + "][rclone-config]") (password NFKC-normalised); the file
 * is two header lines, "RCLONE_ENCRYPT_V0:", then base64(24-byte nonce ‖ NaCl secretbox), and the
 * secretbox is XSalsa20 + Poly1305 (Bouncy Castle): the first 32 keystream bytes key Poly1305, the
 * rest encrypt the text, and the 16-byte tag comes first. Pure JVM; the unit test checks it byte for
 * byte against a file rclone v1.75.2 wrote.
 */
object RcloneConfigCrypt {
    private const val MARKER = "RCLONE_ENCRYPT_V0:"
    private val HEADER = "# Encrypted rclone configuration File\n\n$MARKER\n".toByteArray(Charsets.UTF_8)
    const val NONCE_BYTES = 24
    private const val TAG_BYTES = 16

    /**
     * True when [data] is an encrypted config. Like rclone: the first line that isn't blank or a
     * ";" / "#" comment decides (any "RCLONE_ENCRYPT_V…" counts — it is certainly not plain text).
     */
    fun isEncrypted(data: ByteArray): Boolean = lines(data).firstOrNull()?.first?.startsWith("RCLONE_ENCRYPT_V") == true

    fun encrypt(plain: ByteArray, password: String, nonce: ByteArray = randomNonce()): ByteArray {
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        val stream = stream(key(password), nonce)
        val polyKey = ByteArray(32).also { stream.processBytes(ByteArray(32), 0, 32, it, 0) }
        val ct = ByteArray(plain.size).also { stream.processBytes(plain, 0, plain.size, it, 0) }
        val body = Base64.getEncoder().encode(nonce + mac(polyKey, ct) + ct)
        return HEADER + body
    }

    /** The plain config inside [data]. Throws [IOException] when it isn't encrypted, is damaged, or [password] is wrong. */
    fun decrypt(data: ByteArray, password: String): ByteArray {
        val start = markerEnd(data) ?: throw IOException("not an encrypted rclone config")
        val text = String(data, start, data.size - start, Charsets.US_ASCII).filterNot { it.isWhitespace() }
        val raw = try {
            Base64.getDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            throw IOException("damaged rclone config")
        }
        if (raw.size < NONCE_BYTES + TAG_BYTES) throw IOException("rclone config too short")
        val stream = stream(key(password), raw.copyOfRange(0, NONCE_BYTES))
        val polyKey = ByteArray(32).also { stream.processBytes(ByteArray(32), 0, 32, it, 0) }
        val tag = raw.copyOfRange(NONCE_BYTES, NONCE_BYTES + TAG_BYTES)
        val ct = raw.copyOfRange(NONCE_BYTES + TAG_BYTES, raw.size)
        if (!MessageDigest.isEqual(tag, mac(polyKey, ct))) throw IOException("the rclone config doesn't open with this password")
        return ByteArray(ct.size).also { stream.processBytes(ct, 0, ct.size, it, 0) }
    }

    /** Whether [data] decrypts with [password]. */
    fun opens(data: ByteArray, password: String): Boolean = runCatching { decrypt(data, password) }.isSuccess

    /** The remote names (INI section headers) in a plain config. */
    fun sectionNames(plain: ByteArray): List<String> =
        String(plain, Charsets.UTF_8).lineSequence().map { it.trim() }
            .filter { it.length > 2 && it.startsWith("[") && it.endsWith("]") }
            .map { it.substring(1, it.length - 1) }.toList()

    internal fun key(password: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(("[" + Normalizer.normalize(password, Normalizer.Form.NFKC) + "][rclone-config]").toByteArray(Charsets.UTF_8))

    private fun randomNonce() = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }

    private fun stream(key: ByteArray, nonce: ByteArray) =
        XSalsa20Engine().apply { init(true, ParametersWithIV(KeyParameter(key), nonce)) }

    private fun mac(polyKey: ByteArray, ct: ByteArray): ByteArray {
        val m = Poly1305()
        m.init(KeyParameter(polyKey))
        m.update(ct, 0, ct.size)
        return ByteArray(TAG_BYTES).also { m.doFinal(it, 0) }
    }

    /** Lines, skipping blanks and comments; yields each line's trimmed text and the offset just past its newline. */
    private fun lines(data: ByteArray): Sequence<Pair<String, Int>> = sequence {
        var pos = 0
        while (pos < data.size) {
            var end = pos
            while (end < data.size && data[end] != '\n'.code.toByte()) end++
            val line = String(data, pos, end - pos, Charsets.UTF_8).trim()
            val next = minOf(end + 1, data.size)
            if (line.isNotEmpty() && !line.startsWith(";") && !line.startsWith("#")) yield(line to next)
            pos = next
        }
    }

    /** Offset of the base64 body when the first significant line is the V0 marker, else null. */
    private fun markerEnd(data: ByteArray): Int? = lines(data).firstOrNull()?.takeIf { it.first == MARKER }?.second
}
