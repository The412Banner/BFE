package com.the412banner.bfe.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteEditTest {
    private val sftp = listOf(
        ServiceField("host", "Host", required = true),
        ServiceField(RemoteHosts.KEY, "Home address"),
        ServiceField("port", "Port", FieldKind.NUMBER, default = "22"),
        ServiceField("user", "Username", required = true),
        ServiceField("pass", "Password", FieldKind.PASSWORD),
    )
    private val saved = mapOf("type" to "sftp", "host" to "203.0.113.7", "port" to "2222", "user" to "nas", "pass" to "OBSCURED")

    @Test fun formStartsFromTheSavedSettingsWithoutThePassword() {
        val v = remoteEditValues(sftp, saved, "")
        assertEquals("203.0.113.7", v["host"])
        assertEquals("", v[RemoteHosts.KEY])
        assertEquals("2222", v["port"])
        assertEquals("", v["pass"])
    }

    @Test fun savesOnlyWhatChanged() {
        val v = remoteEditValues(sftp, saved, "").toMutableMap()
        v[RemoteHosts.KEY] = "192.168.1.20"
        val e = remoteEdit(sftp, saved, v)
        assertEquals(mapOf(RemoteHosts.KEY to "192.168.1.20"), e.changes)
        assertNull(e.startPath)
    }

    @Test fun nothingChangedSavesNothing() {
        assertEquals(emptyMap<String, String>(), remoteEdit(sftp, saved, remoteEditValues(sftp, saved, "")).changes)
    }

    @Test fun newPasswordIsSentEmptyKeepsTheOld() {
        val v = remoteEditValues(sftp, saved, "").toMutableMap()
        v["pass"] = "new secret"
        assertEquals(mapOf("pass" to "new secret"), remoteEdit(sftp, saved, v).changes)
    }

    @Test fun clearingFields() {
        val withHome = saved + (RemoteHosts.KEY to "192.168.1.20")
        val v = remoteEditValues(sftp, withHome, "").toMutableMap()
        v[RemoteHosts.KEY] = ""
        v["port"] = ""
        assertEquals(mapOf(RemoteHosts.KEY to "", "port" to "22"), remoteEdit(sftp, withHome, v).changes)
    }

    @Test fun blankValueFieldsRoundTrip() {
        val ftp = listOf(ServiceField("host", "Host"), ServiceField("user", "Username", blankValue = "anonymous"))
        val s = mapOf("host" to "h", "user" to "anonymous")
        val v = remoteEditValues(ftp, s, "")
        assertEquals("", v["user"])
        assertEquals(emptyMap<String, String>(), remoteEdit(ftp, s, v).changes)
    }

    @Test fun startFolderIsReturnedSeparately() {
        val s3 = listOf(ServiceField("access_key_id", "Key"), ServiceField(CloudServices.START_PATH_KEY, "Bucket"))
        val e = remoteEdit(s3, mapOf("access_key_id" to "k"), mapOf("access_key_id" to "k", CloudServices.START_PATH_KEY to "/photos/"))
        assertEquals("photos", e.startPath)
        assertEquals(emptyMap<String, String>(), e.changes)
    }
}
