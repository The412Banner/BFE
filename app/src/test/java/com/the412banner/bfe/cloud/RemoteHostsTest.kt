package com.the412banner.bfe.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class RemoteHostsTest {
    @Test fun parsesHomeAddresses() {
        assertEquals(RemoteHosts.Home("sftp", "192.168.1.20", 2222), RemoteHosts.parseHome("192.168.1.20", "sftp", "2222"))
        assertEquals(RemoteHosts.Home("sftp", "192.168.1.20", 22), RemoteHosts.parseHome(" 192.168.1.20:22 ", "sftp", "2222"))
        assertEquals(RemoteHosts.Home("sftp", "nas.local", 22), RemoteHosts.parseHome("nas.local", "sftp", ""))
        assertEquals(RemoteHosts.Home("smb", "NAS", 445), RemoteHosts.parseHome("NAS", "smb", ""))
        assertNull(RemoteHosts.parseHome("", "sftp", "22"))
        assertNull(RemoteHosts.parseHome("1.2.3.4", "drive", ""))
    }

    @Test fun findsTheRemoteName() {
        assertEquals("nas", RemoteHosts.remoteOf("nas:"))
        assertEquals("nas", RemoteHosts.remoteOf("nas:a/b"))
        assertNull(RemoteHosts.remoteOf("/"))
        assertNull(RemoteHosts.remoteOf("/storage/emulated/0"))
        assertNull(RemoteHosts.remoteOf("nas,host=\"1.2.3.4\":a"))
    }

    // rclone v1.75.2 (BFE's build) listed an SFTP remote through exactly this form on 2026-11-15.
    @Test fun buildsRcloneConnectionStrings() {
        val home = RemoteHosts.Home("sftp", "192.168.1.20", 2222)
        assertEquals("nas,host=\"192.168.1.20\",port=2222:", RemoteHosts.override("nas:", "nas", home))
        assertEquals("nas,host=\"192.168.1.20\",port=2222:Media/Games", RemoteHosts.override("nas:Media/Games", "nas", home))
        assertEquals("n,host=\"a\"\"b\",port=1:", RemoteHosts.override("n:", "n", RemoteHosts.Home("sftp", "a\"b", 1)))
    }

    @Test fun probeChecksTheGreeting() {
        ServerSocket(0).use { ssh ->
            thread { runCatching { ssh.accept().use { it.getOutputStream().write("SSH-2.0-test\r\n".toByteArray()) } } }
            assertTrue(RemoteHosts.answers(RemoteHosts.Home("sftp", "127.0.0.1", ssh.localPort)))
        }
        ServerSocket(0).use { other ->
            thread { runCatching { other.accept().use { it.getOutputStream().write("HTTP/1.1 400\r\n".toByteArray()) } } }
            assertFalse(RemoteHosts.answers(RemoteHosts.Home("sftp", "127.0.0.1", other.localPort)))
        }
        val closed = ServerSocket(0).let { val p = it.localPort; it.close(); p }
        assertFalse(RemoteHosts.answers(RemoteHosts.Home("sftp", "127.0.0.1", closed)))
    }
}
