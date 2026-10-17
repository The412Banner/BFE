package com.the412banner.bfe.security

import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The format of one sealed [SecretVault] entry: base64(IV ‖ ciphertext ‖ tag), AES-GCM with the 96-bit
 * IV the cipher picks itself and a 128-bit tag, and the entry's NAME as associated data — so a blob
 * copied over another entry (or renamed) fails to open instead of handing back the wrong secret.
 *
 * Plain javax.crypto against any [SecretKey]: on the device that's the Android Keystore key (which
 * insists on choosing the IV), in the JVM unit tests a software AES key.
 */
object VaultCodec {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BYTES = 16

    fun seal(key: SecretKey, name: String, value: String): String {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(name.toByteArray(Charsets.UTF_8))
        val sealed = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = c.iv
        check(iv != null && iv.size == IV_BYTES) { "unexpected IV size ${iv?.size}" }
        return Base64.getEncoder().encodeToString(iv + sealed)
    }

    /** The value sealed under [name]. Throws [AEADBadTagException] for anything that doesn't open with [key] (wrong key, wrong name, damaged). */
    fun open(key: SecretKey, name: String, blob: String): String {
        val raw = try {
            Base64.getDecoder().decode(blob)
        } catch (e: IllegalArgumentException) {
            throw AEADBadTagException("not a vault entry")
        }
        if (raw.size < IV_BYTES + TAG_BYTES) throw AEADBadTagException("vault entry too short")
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, raw, 0, IV_BYTES))
        c.updateAAD(name.toByteArray(Charsets.UTF_8))
        return String(c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }
}
