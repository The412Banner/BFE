package com.the412banner.bfe.storage

import com.the412banner.bfe.unpack.ExtractHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Destinations survive the trip through an Intent extra (SAF trees need android.net.Uri, so not here). */
class LocCodecTest {

    @Test fun deviceFolder() {
        val back = LocCodec.decode(LocCodec.encode(Loc.FileLoc(File("/storage/emulated/0/Games"))))
        assertTrue(back is Loc.FileLoc)
        assertEquals("/storage/emulated/0/Games", (back as Loc.FileLoc).file.absolutePath)
    }

    @Test fun rootFolder() {
        val back = LocCodec.decode(LocCodec.encode(Loc.RootLoc("/data/data/x", "x", true, 0L, 0L))) as Loc.RootLoc
        assertEquals("/data/data/x", back.path)
        assertEquals("root:/data/data/x", back.id)
    }

    @Test fun remoteFolder() {
        val back = LocCodec.decode(LocCodec.encode(Loc.RemoteLoc("My NAS", "14TB-HDD/Games", "Games", true, 0L, 0L))) as Loc.RemoteLoc
        assertEquals("My NAS", back.remote)
        assertEquals("14TB-HDD/Games", back.path)
        assertTrue(back.isDir)
    }

    @Test fun archiveIsNeverADestination() {
        assertNull(LocCodec.encode(Loc.ArchiveLoc(File("/a.zip"), "", "a.zip", true, 0L, 0L)))
        assertNull(LocCodec.decode("not json"))
        assertNull(LocCodec.decode(null))
    }

    @Test fun handoffTarget() {
        val t = ExtractHandoff.Target(Loc.RemoteLoc("nas", "Games", "Games", true, 0L, 0L), "nas/Games")
        val back = ExtractHandoff.Target.decode(t.encode())!!
        assertEquals("nas/Games", back.label)
        assertEquals("rc:nas:Games", back.loc.id)
        assertNull(ExtractHandoff.Target.decode(""))
    }

    @Test fun whatNeedsStaging() {
        assertTrue(!ExtractHandoff.needed(Loc.FileLoc(File("/storage/emulated/0"))))
        assertTrue(ExtractHandoff.needed(Loc.RemoteLoc("nas", "", "nas", true, 0L, 0L)))
        assertTrue(ExtractHandoff.needed(Loc.RootLoc("/data", "data", true, 0L, 0L)))
    }
}
