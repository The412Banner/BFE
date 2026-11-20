package com.the412banner.bfe.apk

import com.the412banner.bfe.apk.bundle.Bundles
import com.the412banner.bfe.apk.bundle.Bundles.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Which part of a bundle is what, and which parts a device gets. */
class BundlesTest {

    private fun zip(vararg entries: Pair<String, String>): File {
        val f = File.createTempFile("bundle", ".zip")
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, body) -> z.putNextEntry(ZipEntry(n)); z.write(body.toByteArray()); z.closeEntry() }
        }
        f.deleteOnExit()
        return f
    }

    @Test fun apkmNamesClassify() {
        val parts = Bundles.classifyAll(
            listOf("base.apk", "split_config.arm64_v8a.apk", "split_config.armeabi_v7a.apk", "split_config.xxhdpi.apk",
                "split_config.en.apk", "split_config.de.apk", "split_feature_maps.apk", "split_feature_maps.config.xhdpi.apk").map { it to 1L },
            emptyMap(),
        )
        val by = parts.associateBy { it.entry }
        assertEquals(Kind.BASE, by["base.apk"]!!.kind)
        assertEquals(Kind.ABI, by["split_config.arm64_v8a.apk"]!!.kind)
        assertEquals(Kind.DENSITY, by["split_config.xxhdpi.apk"]!!.kind)
        assertEquals(Kind.LANGUAGE, by["split_config.de.apk"]!!.kind)
        assertEquals(Kind.FEATURE, by["split_feature_maps.apk"]!!.kind)
        val fcfg = by["split_feature_maps.config.xhdpi.apk"]!!
        assertEquals(Kind.DENSITY, fcfg.kind); assertEquals("feature_maps", fcfg.module)
    }

    @Test fun xapkBaseIsThePackageApkWithoutIds() {
        val parts = Bundles.classifyAll(listOf("com.example.game.apk" to 9L, "config.arm64_v8a.apk" to 2L, "config.en.apk" to 1L), emptyMap())
        assertEquals(Kind.BASE, parts.first { it.entry == "com.example.game.apk" }.kind)
        assertEquals(Kind.ABI, parts.first { it.entry == "config.arm64_v8a.apk" }.kind)
    }

    @Test fun bundletoolSplits() {
        val parts = Bundles.classifyAll(
            listOf("splits/base-master.apk", "splits/base-arm64_v8a.apk", "splits/base-xxxhdpi.apk", "splits/base-fr.apk", "splits/extra-master.apk").map { it to 1L },
            emptyMap(),
        )
        val by = parts.associateBy { it.entry }
        assertEquals(Kind.BASE, by["splits/base-master.apk"]!!.kind)
        assertEquals(Kind.ABI, by["splits/base-arm64_v8a.apk"]!!.kind)
        assertEquals(Kind.LANGUAGE, by["splits/base-fr.apk"]!!.kind)
        assertEquals(Kind.FEATURE, by["splits/extra-master.apk"]!!.kind)
    }

    @Test fun autoSelectFitsTheDevice() {
        val parts = Bundles.classifyAll(
            listOf("base.apk", "split_config.arm64_v8a.apk", "split_config.armeabi_v7a.apk", "split_config.x86_64.apk",
                "split_config.mdpi.apk", "split_config.xhdpi.apk", "split_config.xxhdpi.apk", "split_config.xxxhdpi.apk",
                "split_config.en.apk", "split_config.de.apk", "split_config.fr.apk").map { it to 1L },
            emptyMap(),
        )
        // Pocket FIT: arm64 only, 345 dpi, English (US).
        val sel = Bundles.autoSelect(parts, listOf("arm64-v8a"), 345, listOf("en"))
        assertEquals(setOf("base.apk", "split_config.arm64_v8a.apk", "split_config.xxhdpi.apk", "split_config.en.apk"), sel)
        assertFalse(Bundles.missingAbi(parts, sel))
        // 32-bit only device takes the v7a part; a German device takes German, not English.
        val sel2 = Bundles.autoSelect(parts, listOf("armeabi-v7a", "armeabi"), 160, listOf("de", "en"))
        assertTrue("split_config.armeabi_v7a.apk" in sel2)
        assertTrue("split_config.mdpi.apk" in sel2)
        assertTrue("split_config.de.apk" in sel2 && "split_config.en.apk" in sel2)
        // Above the largest density: the largest one.
        assertTrue("split_config.xxxhdpi.apk" in Bundles.autoSelect(parts, listOf("arm64-v8a"), 800, listOf("en")))
        // No matching language at all: English.
        assertTrue("split_config.en.apk" in Bundles.autoSelect(parts, listOf("arm64-v8a"), 345, listOf("ja")))
    }

    @Test fun missingAbiIsReported() {
        val parts = Bundles.classifyAll(listOf("base.apk" to 1L, "split_config.x86.apk" to 1L), emptyMap())
        val sel = Bundles.autoSelect(parts, listOf("arm64-v8a"), 345, listOf("en"))
        assertTrue(Bundles.missingAbi(parts, sel))
    }

    @Test fun xapkManifestAndObb() {
        val manifest = """{"package_name":"com.example.game","name":"Example","version_name":"1.2","version_code":"12",
            "min_sdk_version":"24","split_apks":[{"file":"com.example.game.apk","id":"base"},{"file":"config.arm64_v8a.apk","id":"config.arm64_v8a"}],
            "expansions":[{"file":"Android/obb/com.example.game/main.12.com.example.game.obb","install_location":"EXTERNAL_STORAGE",
            "install_path":"Android/obb/com.example.game/main.12.com.example.game.obb"}]}"""
        val f = zip(
            "manifest.json" to manifest, "icon.png" to "x",
            "com.example.game.apk" to "apk", "config.arm64_v8a.apk" to "apk",
            "Android/obb/com.example.game/main.12.com.example.game.obb" to "data",
        )
        val info = Bundles.refineWithManifests(Bundles.read(f))
        assertEquals("com.example.game", info.packageName)
        assertEquals("Example", info.label)
        assertEquals(12L, info.versionCode)
        assertEquals("com.example.game.apk", info.base!!.entry)
        assertEquals(1, info.obbs.size)
        assertEquals("Android/obb/com.example.game/main.12.com.example.game.obb", info.obbs[0].target)
        assertEquals("icon.png", info.iconEntry)
    }

    @Test fun apkmInfoJson() {
        val f = zip("info.json" to """{"pname":"com.example.app","app_name":"Ex","release_version":"3.0","versioncode":"300","min_api":"26"}""",
            "base.apk" to "a", "split_config.en.apk" to "b")
        val info = Bundles.read(f)
        assertEquals("com.example.app", info.packageName)
        assertEquals("3.0", info.versionName)
        assertNull(info.iconEntry)
    }

    @Test fun bundletoolIgnoresStandalones() {
        val f = zip("toc.pb" to "", "splits/base-master.apk" to "a", "splits/base-arm64_v8a.apk" to "b", "standalones/standalone-arm64_v8a_xxhdpi.apk" to "c")
        val info = Bundles.read(f)
        assertEquals(listOf("splits/base-master.apk", "splits/base-arm64_v8a.apk"), info.parts.map { it.entry })
    }

    @Test(expected = Bundles.NotABundle::class) fun onlySplitsIsNotABundle() {
        Bundles.read(zip("split_config.en.apk" to "a", "split_config.de.apk" to "b"))
    }

    @Test fun bareObbNameGetsItsPackage() {
        val dir = File(System.getProperty("java.io.tmpdir"), "bfe-bundle-${System.nanoTime()}").apply { mkdirs() }
        File(dir, "base.apk").writeText("a"); File(dir, "split_config.arm64_v8a.apk").writeText("b")
        File(dir, "main.7.com.example.x.obb").writeText("c")
        val info = Bundles.read(dir)
        assertTrue(info.isFolder)
        assertEquals("Android/obb/com.example.x/main.7.com.example.x.obb", info.obbs.single().target)
        dir.deleteRecursively()
    }

    @Test fun describeIsShort() {
        val p = Bundles.classify("split_config.arm64_v8a.apk", 1, null)
        assertEquals("Processor: arm64-v8a", Bundles.describe(p))
    }
}
