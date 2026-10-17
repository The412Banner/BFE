package com.the412banner.bfe.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureMathTest {
    private val hd = VideoLayout(1920, 1080, 1920, 1080, 1, 1)

    @Test fun bestFitLetterboxesAndFitScreenCovers() {
        val fit = VideoGeometry.place(2400, 1080, hd, AspectMode.BEST_FIT)!!
        assertEquals(1920 to 1080, fit.frameW to fit.frameH)
        assertEquals(Placement(1920, 1080, 1920, 1080, 0, 0), fit)
        val cover = VideoGeometry.place(2400, 1080, hd, AspectMode.FIT_SCREEN)!!
        assertEquals(2400 to 1350, cover.frameW to cover.frameH)
        val fill = VideoGeometry.place(2400, 1080, hd, AspectMode.FILL)!!
        assertEquals(2400 to 1080, fill.frameW to fill.frameH)
        assertEquals(2400 to 1080, fill.surfaceW to fill.surfaceH)
    }

    @Test fun fixedRatiosAndOriginal() {
        val r43 = VideoGeometry.place(2400, 1080, hd, AspectMode.R4_3)!!
        assertEquals(1440 to 1080, r43.frameW to r43.frameH)
        val sq = VideoGeometry.place(1000, 2000, hd, AspectMode.R1_1)!!
        assertEquals(1000 to 1000, sq.frameW to sq.frameH)
        val small = VideoLayout(640, 360, 640, 360, 1, 1)
        val orig = VideoGeometry.place(2400, 1080, small, AspectMode.ORIGINAL)!!
        assertEquals(640 to 360, orig.frameW to orig.frameH)
    }

    @Test fun sampleAspectRatioIsApplied() {
        // DVD: 720x480 storage with 32:27 SAR displays as 16:9.
        val dvd = VideoLayout(720, 480, 720, 480, 32, 27)
        val p = VideoGeometry.place(1920, 1080, dvd, AspectMode.BEST_FIT)!!
        assertEquals(1920 to 1080, p.frameW to p.frameH)
    }

    @Test fun paddedBuffersGrowTheSurface() {
        // 1088-line buffer, 1080 visible: the surface is taller than the frame so the padding is cut off.
        val padded = VideoLayout(1920, 1088, 1920, 1080, 1, 1)
        val p = VideoGeometry.place(1920, 1080, padded, AspectMode.BEST_FIT)!!
        assertEquals(1920 to 1080, p.frameW to p.frameH)
        assertEquals(1088, p.surfaceH)
        assertEquals(0, p.surfaceY)
    }

    @Test fun quarterRotationFitsTheSwappedScreen() {
        val p = VideoGeometry.place(1080, 2400, hd, AspectMode.BEST_FIT, rotation = 90)!!
        // Laid out landscape (1920x1080), shown turned: 1080 wide x 1920 tall on a portrait screen.
        assertEquals(1920 to 1080, p.frameW to p.frameH)
        assertEquals(1080.0 / 1920.0, p.displayedAspect(90), 1e-3)
        assertEquals(16.0 / 9.0, p.displayedAspect(180), 1e-3)
    }

    @Test fun cropCutsCentreAndZoomScales() {
        // 2.35:1 crop of a 16:9 frame on a 21:9-ish screen: the window is 2.35:1, the surface overhangs it.
        val p = VideoGeometry.place(2520, 1080, hd, AspectMode.BEST_FIT, CropMode.C235_1)!!
        assertEquals(2520, p.frameW)
        assertEquals(1072, p.frameH)
        assertEquals(2520, p.surfaceW)
        assertTrue("surface taller than the window", p.surfaceH > p.frameH)
        assertTrue(p.surfaceY < 0)
        val z = VideoGeometry.place(2400, 1080, hd, AspectMode.BEST_FIT, zoom = 2f)!!
        assertEquals(3840 to 2160, z.frameW to z.frameH)
        assertNull(VideoGeometry.place(0, 1080, hd, AspectMode.BEST_FIT))
        assertNull(VideoGeometry.place(100, 100, VideoLayout(0, 0, 0, 0, 0, 0), AspectMode.BEST_FIT))
    }

    @Test fun pipAspectIsClamped() {
        assertEquals(1778 to 1000, VideoGeometry.pipAspect(16.0 / 9.0))
        val tall = VideoGeometry.pipAspect(0.1)
        assertTrue(tall.first.toDouble() / tall.second > 1 / 2.39)
        val wide = VideoGeometry.pipAspect(5.0)
        assertTrue(wide.first.toDouble() / wide.second < 2.39)
    }

    @Test fun colourMatrixIdentityAndPresets() {
        assertTrue(ColorAdjust.isIdentity(ColorAdjust.matrix()))
        assertTrue(ColorAdjust.isIdentity(ColorAdjust.matrix(hue = 0f, gamma = 1f)))
        val px = floatArrayOf(200f, 100f, 50f, 255f)
        // Grayscale: all channels equal the Rec.709 luma.
        val g = ColorAdjust.apply(ColorAdjust.matrix(ColorPreset.GRAYSCALE), px)
        assertEquals(g[0], g[1], 0.01f)
        assertEquals(g[1], g[2], 0.01f)
        assertEquals(0.2126f * 200 + 0.7152f * 100 + 0.0722f * 50, g[0], 0.05f)
        // Invert.
        val inv = ColorAdjust.apply(ColorAdjust.matrix(ColorPreset.INVERT), px)
        assertEquals(55f, inv[0], 0.01f)
        assertEquals(255f, inv[3], 0.01f)
        // Saturation 0 == grayscale preset.
        val s0 = ColorAdjust.apply(ColorAdjust.matrix(saturation = 0f), px)
        assertEquals(g[0], s0[0], 0.05f)
    }

    @Test fun brightnessContrastGammaBehave() {
        val mid = floatArrayOf(127.5f, 127.5f, 127.5f, 255f)
        assertEquals(127.5f, ColorAdjust.apply(ColorAdjust.matrix(contrast = 1.8f), mid)[0], 0.01f)
        assertEquals(127.5f + 0.4f * 255f * 0.5f, ColorAdjust.apply(ColorAdjust.matrix(brightness = 0.5f), mid)[0], 0.05f)
        // Gamma > 1 brightens the mid-tones (x^(1/γ)), < 1 darkens them.
        val bright = ColorAdjust.apply(ColorAdjust.matrix(gamma = 2f), mid)[0]
        val dark = ColorAdjust.apply(ColorAdjust.matrix(gamma = 0.5f), mid)[0]
        assertEquals(255f * Math.pow(0.5, 0.5).toFloat(), bright, 0.1f)
        assertTrue(dark < 127.5f)
        // Hue 360 is a full turn back to the original.
        val px = floatArrayOf(200f, 100f, 50f, 255f)
        val turned = ColorAdjust.apply(ColorAdjust.matrix(hue = 180f), ColorAdjust.apply(ColorAdjust.matrix(hue = 180f), px))
        assertEquals(px[0], turned[0], 1.5f)
        // Luminance is (about) preserved by a hue rotation.
        val h = ColorAdjust.apply(ColorAdjust.matrix(hue = 90f), px)
        val luma = { p: FloatArray -> 0.213f * p[0] + 0.715f * p[1] + 0.072f * p[2] }
        assertEquals(luma(px), luma(h), 1.0f)
    }

    @Test fun settingsRoundTripAndClampOnDecode() {
        val s = PlayerSettings(
            preset = ColorPreset.WARM, brightness = 0.2f, contrast = 1.3f, aspect = AspectMode.R21_9, crop = CropMode.C4_3,
            zoom = 1.5f, rotation = 270, flipH = true, deinterlace = Deinterlace.YADIF2X, denoise = Strength.HIGH,
            looks = setOf(VideoLook.VHS, VideoLook.OLD_MOVIE), sharpen = 0.4f, grain = 0.3f, eqOn = true, eqPreset = 3,
            eqBands = List(10) { it - 5f }, boost = 150, audioDelayMs = -250, speed = 1.25f, keepPitch = false,
            replayGain = ReplayGain.ALBUM, subDelayMs = 1500, subSize = SubSize.LARGE, subColor = SubColor.YELLOW, subBackground = true,
        )
        assertEquals(s, PlayerSettings.decode(s.encode()))
        assertEquals(PlayerSettings.DEFAULT, PlayerSettings.decode(null))
        assertEquals(PlayerSettings.DEFAULT, PlayerSettings.decode("nonsense"))
        val clamped = PlayerSettings.decode("boost=900;spd=99;rot=450;zoom=-3;asp=NOPE")
        assertEquals(200, clamped.boost)
        assertEquals(4f, clamped.speed, 0f)
        assertEquals(90, clamped.rotation)
        assertEquals(1f, clamped.zoom, 0f)
        assertEquals(AspectMode.BEST_FIT, clamped.aspect)
        assertFalse(PlayerSettings.DEFAULT.hasColorChange)
        assertTrue(s.hasColorChange)
    }

    @Test fun vlcArgsOnlyForWhatIsOn() {
        assertTrue(VlcArgs.build(PlayerSettings.DEFAULT).isEmpty())
        assertFalse(VlcArgs.needsCpuFrames(PlayerSettings.DEFAULT))
        // Picture-only changes never rebuild the engine.
        assertTrue(VlcArgs.build(PlayerSettings(brightness = 0.5f, aspect = AspectMode.FILL, sharpen = 1f, eqOn = true, speed = 2f)).isEmpty())

        val fx = PlayerSettings(denoise = Strength.MEDIUM, debanding = Strength.LOW, looks = setOf(VideoLook.BLUR, VideoLook.OLD_MOVIE))
        val args = VlcArgs.build(fx)
        assertEquals("--video-filter=hqdn3d:gradfun:oldmovie:gaussianblur", args.single { it.startsWith("--video-filter=") })
        assertTrue("--hqdn3d-luma-spat=4.0" in args)
        assertTrue("--gradfun-strength=0.8" in args)
        assertTrue("--gaussianblur-sigma=1.5" in args)
        assertTrue(VlcArgs.needsCpuFrames(fx))

        val di = VlcArgs.build(PlayerSettings(deinterlace = Deinterlace.BOB))
        assertEquals(listOf("--deinterlace=1", "--deinterlace-mode=bob"), di)
        assertEquals(listOf("--deinterlace=0"), VlcArgs.build(PlayerSettings(deinterlace = Deinterlace.OFF)))
        assertFalse(VlcArgs.needsCpuFrames(PlayerSettings(deinterlace = Deinterlace.OFF)))

        val audio = VlcArgs.build(PlayerSettings(keepPitch = false, replayGain = ReplayGain.TRACK))
        assertEquals(listOf("--no-audio-time-stretch", "--audio-replay-gain-mode=track"), audio)

        val subs = VlcArgs.build(PlayerSettings(subSize = SubSize.HUGE, subColor = SubColor.YELLOW, subBackground = true))
        assertTrue("--freetype-rel-fontsize=8" in subs)
        assertTrue("--freetype-color=${0xFFFF00}" in subs)
        assertTrue("--freetype-background-opacity=160" in subs)
        assertNotNull(VlcArgs.BASE.firstOrNull { it.startsWith("--android-display-chroma=") })
    }
}
