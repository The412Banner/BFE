package com.the412banner.bfe.util

import com.the412banner.bfe.cloud.RcloneApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the error log keeps of an error (its shape) and what it drops (anything personal). */
class LogScrubTest {
    private val names = listOf("NAS", "GDrive")

    private fun scrub(s: String) = LogScrub.scrub(s, names)

    private fun assertGone(out: String, vararg secrets: String) {
        secrets.forEach { assertFalse("\"$it\" left in: $out", out.contains(it, ignoreCase = true)) }
    }

    @Test fun keepsThePlainError() {
        assertEquals("error in ListJSON: connection error: EOF", scrub("error in ListJSON: connection error: EOF"))
        assertEquals("context deadline exceeded", scrub("context deadline exceeded"))
    }

    @Test fun rcloneLineKeepsTimeAndLevelButNotTheFile() {
        val out = scrub("2026/10/20 18:05:12 ERROR : Movies/My Film (2020).mkv: Failed to copy: connection error: EOF")
        assertEquals("2026/10/20 18:05:12 ERROR : <item>: Failed to copy: connection error: EOF", out)
    }

    /** Real lib7zz 24.08 output for a corrupted zip (device, 2026-10-20), plus an archive it can't open. */
    @Test fun sevenZipOutputKeepsTheReasonButNotTheNames() {
        val out = scrub(
            """
            Extracting archive: /sdcard/Download/BFE-errlog-test/broken-test.zip
            Path = /sdcard/Download/BFE-errlog-test/broken-test.zip
            Type = zip
            - secret folder/My Private File.txt
            ERROR: Data Error : secret folder/My Private File.txt
            ERROR: /storage/emulated/0/Download/My Stuff.rar
            Can not open the file as archive
            Sub items Errors: 1
            """.trimIndent(),
        )
        assertGone(out, "secret", "Private", "broken-test", "errlog", "Stuff", "storage")
        assertTrue(out, out.contains("ERROR: Data Error : <item>"))
        assertTrue(out, out.contains("Can not open the file as archive"))
        assertTrue(out, out.contains("Sub items Errors: 1"))
    }

    @Test fun addressesAndHosts() {
        val out = scrub("couldn't connect SMB: dial tcp 192.168.1.50:445: connect: connection refused")
        assertEquals("couldn't connect SMB: dial tcp <ip>: connect: connection refused", out)
        assertGone(scrub("dial tcp: lookup mynas on 8.8.8.8:53: no such host"), "mynas", "8.8.8.8")
        assertGone(scrub("smb://bob@nas.local/share/x: file not found"), "bob", "nas.local", "share")
        assertGone(scrub("fe80::1ff:fe23:4567:890a and 2001:db8:85a3:0:0:8a2e:370:7334"), "fe80", "2001:db8")
        assertGone(scrub("NOTICE: Serving remote control on http://127.0.0.1:43123/"), "127.0.0.1", "43123")
    }

    @Test fun remoteNamesPathsAndFileNames() {
        val out = scrub("NAS: error reading /storage/emulated/0/Download/secret plan.pdf")
        assertGone(out, "NAS", "storage", "Download", "secret", "plan.pdf")
        assertGone(scrub("failed to open \"Photos/Kids Birthday.jpg\": permission denied"), "Photos", "Kids", "Birthday")
        assertGone(scrub("gdrive: object not found"), "gdrive")
        assertGone(scrub("at \\\\NAS\\Share\\docs or C:\\Users\\Jane\\x.txt or WORKGROUP\\jane"), "Share", "Users", "Jane", "WORKGROUP")
    }

    /** A tester's real SMB error (rclone prints the share path with backslashes). */
    @Test fun smbAbortFromATester() {
        val out = scrub("error in ListJSON: open Photos\\PhotoLibrary: connection error: read tcp 192.168.50.224:56516->192.168.50.100:445: read: software caused connection abort")
        assertEquals("error in ListJSON: open <path>: connection error: read tcp <ip>-><ip>: read: software caused connection abort", out)
    }

    @Test fun credentials() {
        val out = scrub("password=hunter2 token: abcDEF1234567890abcdef12 Authorization: Bearer eyJhbGc.xyz user=bob")
        assertGone(out, "hunter2", "abcDEF1234567890abcdef12", "eyJhbGc", "bob")
        assertGone(scrub("contact jane.doe@gmail.com"), "jane", "gmail")
        assertGone(scrub("key 0123456789abcdef0123456789abcdef"), "0123456789abcdef")
    }

    @Test fun droppedConnectionIsRecognised() {
        assertTrue(RcloneApi.isDroppedConnection("error in ListJSON: connection error: EOF"))
        assertTrue(RcloneApi.isDroppedConnection("read tcp 10.0.0.2:5000->10.0.0.9:445: read: connection reset by peer"))
        assertTrue(RcloneApi.isDroppedConnection("write: broken pipe"))
        assertTrue(RcloneApi.isDroppedConnection("error in ListJSON: open x: connection error: read tcp 10.0.0.2:5000->10.0.0.9:445: read: software caused connection abort"))
        assertFalse(RcloneApi.isDroppedConnection("dial tcp 10.0.0.9:445: connect: connection refused"))
        assertFalse(RcloneApi.isDroppedConnection("directory not found"))
        assertFalse(RcloneApi.isDroppedConnection(null))
    }
}
