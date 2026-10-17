package com.the412banner.bfe.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The vault's entry format with a software AES-256 key standing in for the Keystore's: the blob is
 * base64(12-byte IV ‖ ciphertext ‖ 16-byte tag), and it opens only under the same key AND name.
 */
class VaultCodecTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun assertDoesNotOpen(key: SecretKey, name: String, blob: String) {
        try {
            VaultCodec.open(key, name, blob)
            fail("opened")
        } catch (expected: AEADBadTagException) {
        }
    }

    @Test fun roundTrip() {
        val k = newKey()
        for (v in listOf("", "hunter2", "pässwörd ✓", "x".repeat(4096))) {
            assertEquals(v, VaultCodec.open(k, "server.password", VaultCodec.seal(k, "server.password", v)))
        }
    }

    @Test fun blobLayoutIsIvCiphertextTag() {
        val v = "0123456789abcdef"
        val raw = Base64.getDecoder().decode(VaultCodec.seal(newKey(), "n", v))
        assertEquals(VaultCodec.IV_BYTES + v.length + VaultCodec.TAG_BYTES, raw.size)
    }

    @Test fun freshIvEverySeal() {
        val k = newKey()
        val a = VaultCodec.seal(k, "n", "same")
        val b = VaultCodec.seal(k, "n", "same")
        assertNotEquals(a, b)
        val ivA = Base64.getDecoder().decode(a).copyOfRange(0, VaultCodec.IV_BYTES)
        val ivB = Base64.getDecoder().decode(b).copyOfRange(0, VaultCodec.IV_BYTES)
        assertNotEquals(ivA.toList(), ivB.toList())
    }

    @Test fun nameIsBoundIn() {
        // A blob moved to another entry must not open there.
        val k = newKey()
        val blob = VaultCodec.seal(k, "apk.key/release.p12", "secret")
        assertDoesNotOpen(k, "apk.key/other.p12", blob)
        assertDoesNotOpen(k, "server.password", blob)
    }

    @Test fun otherKeyDoesNotOpen() {
        assertDoesNotOpen(newKey(), "n", VaultCodec.seal(newKey(), "n", "secret"))
    }

    @Test fun damageIsDetected() {
        val k = newKey()
        val raw = Base64.getDecoder().decode(VaultCodec.seal(k, "n", "secret"))
        for (i in raw.indices) {
            val bad = raw.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertDoesNotOpen(k, "n", Base64.getEncoder().encodeToString(bad))
        }
        assertDoesNotOpen(k, "n", Base64.getEncoder().encodeToString(raw.copyOf(VaultCodec.IV_BYTES + VaultCodec.TAG_BYTES - 1)))
        assertDoesNotOpen(k, "n", "not base64 !")
        assertDoesNotOpen(k, "n", "")
    }
}
