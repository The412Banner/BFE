package com.the412banner.bfe.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePolicyTest {

    @Test fun geometryThatNeedsTheReportedSize() {
        assertFalse(FramePolicy.needsLayoutGeometry(PlayerSettings.DEFAULT))
        // Aspect modes and a half turn work on the GL output too.
        assertFalse(FramePolicy.needsLayoutGeometry(PlayerSettings(aspect = AspectMode.R21_9, rotation = 180, flipH = true)))
        assertTrue(FramePolicy.needsLayoutGeometry(PlayerSettings(crop = CropMode.C4_3)))
        assertTrue(FramePolicy.needsLayoutGeometry(PlayerSettings(zoom = 1.5f)))
        assertTrue(FramePolicy.needsLayoutGeometry(PlayerSettings(rotation = 90)))
        assertTrue(FramePolicy.needsLayoutGeometry(PlayerSettings(rotation = 270)))
    }

    @Test fun copyOutAtOpenOnlyWhenSomethingNeedsIt() {
        assertFalse(FramePolicy.copyAtOpen(PlayerSettings.DEFAULT))
        assertFalse(FramePolicy.copyAtOpen(PlayerSettings(brightness = 0.4f, sharpen = 0.5f, grain = 0.3f, subSize = SubSize.LARGE)))
        assertTrue(FramePolicy.copyAtOpen(PlayerSettings(denoise = Strength.LOW)))
        assertTrue(FramePolicy.copyAtOpen(PlayerSettings(zoom = 2f)))
    }

    @Test fun reopenDecisionsPerOutput() {
        val plain = PlayerSettings.DEFAULT
        // Nothing is decided before the output is known.
        assertFalse(FramePolicy.mustCopyNow(RenderMode.UNKNOWN, PlayerSettings(zoom = 2f), subtitleOn = true))
        // GL blends subtitles itself; only layout geometry forces copy-out there.
        assertFalse(FramePolicy.mustCopyNow(RenderMode.GL, plain, subtitleOn = true))
        assertTrue(FramePolicy.mustCopyNow(RenderMode.GL, PlayerSettings(crop = CropMode.C16_9), subtitleOn = false))
        // The Android display over hardware frames can't draw subtitles.
        assertTrue(FramePolicy.mustCopyNow(RenderMode.NATIVE, plain, subtitleOn = true))
        assertFalse(FramePolicy.mustCopyNow(RenderMode.NATIVE, PlayerSettings(zoom = 2f), subtitleOn = false))
    }

    @Test fun coverScaleMatchesVideoHelper() {
        // 1920x1080 into 2400x1080: the view is wider, so scale by width.
        assertEquals(2400f / 1920f, FramePolicy.coverScale(1920, 1080, 1, 1, false, 2400, 1080), 1e-4f)
        // Into a portrait 1080x2400 view: scale by height.
        assertEquals(2400f / 1080f, FramePolicy.coverScale(1920, 1080, 1, 1, false, 1080, 2400), 1e-4f)
        // A 90°-flagged stream swaps its sides first.
        assertEquals(2400f / 1920f, FramePolicy.coverScale(1920, 1080, 1, 1, true, 1080, 2400), 1e-4f)
        // DVD 720x480 with 32:27 SAR is 853 wide.
        assertEquals(1920f / 853f, FramePolicy.coverScale(720, 480, 32, 27, false, 1920, 1080), 1e-3f)
        assertEquals(0f, FramePolicy.coverScale(0, 0, 0, 0, false, 100, 100), 0f)
    }

    @Test fun vlcAspectStrings() {
        assertEquals("16:9", FramePolicy.vlcAspect(AspectMode.R16_9))
        assertEquals("2.35:1", FramePolicy.vlcAspect(AspectMode.R235_1))
        assertNull(FramePolicy.vlcAspect(AspectMode.BEST_FIT))
        assertNull(FramePolicy.vlcAspect(AspectMode.ORIGINAL))
    }
}
