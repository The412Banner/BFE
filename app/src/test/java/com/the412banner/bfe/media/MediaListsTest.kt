package com.the412banner.bfe.media

import com.the412banner.bfe.util.naturalCompare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaListsTest {

    @Test fun naturalOrderComparesDigitRunsAsNumbers() {
        val names = listOf("img10.jpg", "img2.jpg", "IMG1.jpg", "img02b.jpg", "Disc 10", "Disc 2", "a", "A1")
        val sorted = names.sortedWith { a, b -> naturalCompare(a, b) }
        assertEquals(listOf("a", "A1", "Disc 2", "Disc 10", "IMG1.jpg", "img2.jpg", "img02b.jpg", "img10.jpg"), sorted)
        assertEquals(0, naturalCompare("Photo.JPG", "photo.jpg"))
    }

    @Test fun typesByExtensionThenMime() {
        assertEquals(MediaType.IMAGE, MediaTypes.typeOf("a.JPG"))
        assertEquals(MediaType.IMAGE, MediaTypes.typeOf("anim.gif"))
        assertEquals(MediaType.VIDEO, MediaTypes.typeOf("movie.MKV"))
        assertEquals(MediaType.VIDEO, MediaTypes.typeOf("clip.rmvb"))
        assertEquals(MediaType.AUDIO, MediaTypes.typeOf("song.flac"))
        assertEquals(MediaType.AUDIO, MediaTypes.typeOf("voice.ogg"))
        assertEquals(MediaType.VIDEO, MediaTypes.typeOf("noext", "video/mp4"))
        assertEquals(MediaType.AUDIO, MediaTypes.typeOf("noext", "application/ogg"))
        assertNull(MediaTypes.typeOf("readme.txt", "text/plain"))
        assertNull(MediaTypes.typeOf("archive.zip"))
    }

    @Test fun subsamplingOnlyForRegionDecoderFormats() {
        assertTrue(MediaTypes.canSubsample("big.jpeg"))
        assertTrue(MediaTypes.canSubsample("shot.PNG"))
        assertTrue(MediaTypes.canSubsample("x.webp"))
        assertEquals(false, MediaTypes.canSubsample("anim.gif"))
        assertEquals(false, MediaTypes.canSubsample("phone.heic"))
        assertEquals(false, MediaTypes.canSubsample("new.avif"))
        assertTrue(MediaTypes.canSaveRotation("a.jpg"))
        assertEquals(false, MediaTypes.canSaveRotation("a.png"))
    }

    @Test fun folderSiblingsAreSameTypeNaturalOrderAndFindCurrent() {
        val dir = Files.createTempDirectory("bfe-sib").toFile()
        try {
            listOf("p10.jpg", "p2.png", "p1.jpg", ".hidden.jpg", "notes.txt", "clip.mp4", "song.mp3").forEach { File(dir, it).writeText("x") }
            File(dir, "sub.jpg").mkdir()
            val (images, idx) = MediaSiblings.inFolder(dir, MediaType.IMAGE, File(dir, "p2.png"))
            assertEquals(listOf("p1.jpg", "p2.png", "p10.jpg"), images.map { it.name })
            assertEquals(1, idx)
            val (videos, vIdx) = MediaSiblings.inFolder(dir, MediaType.VIDEO, File(dir, "clip.mp4"))
            assertEquals(listOf("clip.mp4"), videos.map { it.name })
            assertEquals(0, vIdx)
            // A file the listing skips (hidden) is still played, first.
            val (withHidden, hIdx) = MediaSiblings.inFolder(dir, MediaType.IMAGE, File(dir, ".hidden.jpg"))
            assertEquals(".hidden.jpg", withHidden[hIdx].name)
            assertEquals(4, withHidden.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun pickKeepsCallerOrder() {
        data class Row(val name: String)
        val rows = listOf(Row("z.mp4"), Row("a.txt"), Row("b.mkv"), Row("c.jpg"), Row("a.avi"))
        val (list, idx) = MediaSiblings.pick(rows, MediaType.VIDEO, "b.mkv", { it.name }, { it.name })
        assertEquals(listOf("z.mp4", "b.mkv", "a.avi"), list.map { it.name })
        assertEquals(1, idx)
        val (_, missing) = MediaSiblings.pick(rows, MediaType.VIDEO, "nope", { it.name }, { it.name })
        assertEquals(0, missing)
    }

    @Test fun mediaRefFromFileHasPathAndFileUri() {
        val f = Files.createTempFile("bfe ref", ".jpg").toFile()
        try {
            val r = MediaRef.of(f)
            assertEquals(f.absolutePath, r.path)
            assertEquals(f.absolutePath, r.key)
            assertTrue(r.uri.startsWith("file:"))
            assertTrue("spaces are encoded", r.uri.contains("%20"))
        } finally {
            f.delete()
        }
    }

    @Test fun handoffTokensAreOneShot() {
        val a = MediaHandoff.put(listOf(MediaRef("u1", "a.jpg")))
        val b = MediaHandoff.put(listOf(MediaRef("u2", "b.jpg")))
        assertEquals("b.jpg", MediaHandoff.take(b)?.single()?.name)
        assertNull(MediaHandoff.take(b))
        assertEquals("a.jpg", MediaHandoff.take(a)?.single()?.name)
    }
}
