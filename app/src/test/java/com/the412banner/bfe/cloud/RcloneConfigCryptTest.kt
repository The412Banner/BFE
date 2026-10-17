package com.the412banner.bfe.cloud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.Base64

/**
 * [RcloneConfigCrypt] against rclone itself: resources/rclone/encrypted.conf was written by rclone
 * v1.75.2 (`rclone config encryption set`, password [PASSWORD] from --password-command) from a config
 * with a Drive token and an SFTP password; encrypted.plain is what `rclone config show` printed for it.
 */
class RcloneConfigCryptTest {
    private companion object {
        const val PASSWORD = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    }

    private fun res(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResource("rclone/$name")) { "missing fixture $name" }.readBytes()

    @Test fun decryptsWhatRcloneWrote() {
        val plain = RcloneConfigCrypt.decrypt(res("encrypted.conf"), PASSWORD)
        assertArrayEquals(res("encrypted.plain"), plain)
        assertEquals(listOf("gdrive", "nas"), RcloneConfigCrypt.sectionNames(plain))
    }

    @Test fun sealsByteForByteLikeRclone() {
        // Same nonce + key + text must give rclone's exact file: header lines, base64 body, no trailing newline.
        val file = res("encrypted.conf")
        val body = String(file, Charsets.US_ASCII).substringAfter("RCLONE_ENCRYPT_V0:\n")
        val nonce = Base64.getDecoder().decode(body).copyOfRange(0, RcloneConfigCrypt.NONCE_BYTES)
        assertArrayEquals(file, RcloneConfigCrypt.encrypt(res("encrypted.plain"), PASSWORD, nonce))
    }

    @Test fun wrongPasswordDoesNotOpen() {
        assertFalse(RcloneConfigCrypt.opens(res("encrypted.conf"), PASSWORD.reversed()))
        try {
            RcloneConfigCrypt.decrypt(res("encrypted.conf"), "nope")
            fail("decrypted with the wrong password")
        } catch (expected: IOException) {
        }
    }

    @Test fun tamperedFileDoesNotOpen() {
        val file = res("encrypted.conf").copyOf()
        val i = file.size - 10
        file[i] = (if (file[i] == 'A'.code.toByte()) 'B' else 'A').code.toByte()
        assertFalse(RcloneConfigCrypt.opens(file, PASSWORD))
    }

    @Test fun roundTripWithFreshNonces() {
        val plain = "[box]\ntype = sftp\npass = x\n\n".toByteArray()
        val a = RcloneConfigCrypt.encrypt(plain, PASSWORD)
        val b = RcloneConfigCrypt.encrypt(plain, PASSWORD)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain, RcloneConfigCrypt.decrypt(a, PASSWORD))
        assertArrayEquals(plain, RcloneConfigCrypt.decrypt(b, PASSWORD))
        // An empty config (what rcd starts from on a fresh install) round-trips too.
        assertArrayEquals(ByteArray(0), RcloneConfigCrypt.decrypt(RcloneConfigCrypt.encrypt(ByteArray(0), PASSWORD), PASSWORD))
    }

    @Test fun detectsEncryptionLikeRclone() {
        assertTrue(RcloneConfigCrypt.isEncrypted(res("encrypted.conf")))
        assertFalse(RcloneConfigCrypt.isEncrypted(res("encrypted.plain")))
        assertFalse(RcloneConfigCrypt.isEncrypted(ByteArray(0)))
        assertFalse(RcloneConfigCrypt.isEncrypted("\n; just a comment\n# another\n".toByteArray()))
        // Comments and blank lines before the marker don't matter; the first real line decides.
        assertTrue(RcloneConfigCrypt.isEncrypted("\n\n; note\r\nRCLONE_ENCRYPT_V0:\r\nAAAA\n".toByteArray()))
        assertTrue(RcloneConfigCrypt.isEncrypted("RCLONE_ENCRYPT_V1:\nAAAA".toByteArray()))
        assertFalse(RcloneConfigCrypt.isEncrypted("[RCLONE_ENCRYPT_V0:]\ntype = local\n".toByteArray()))
    }

    @Test fun keyIsRclonesSha256Construction() {
        // sha256("[" + password + "][rclone-config]") for the fixture's password.
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest("[$PASSWORD][rclone-config]".toByteArray())
        assertArrayEquals(expected, RcloneConfigCrypt.key(PASSWORD))
    }
}
