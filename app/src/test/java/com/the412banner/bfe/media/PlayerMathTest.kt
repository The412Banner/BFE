package com.the412banner.bfe.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMathTest {

    @Test fun resumeOnlyForLongFilesAndNotNearTheEnds() {
        assertFalse("short clip", ResumePolicy.shouldStore(30_000, 59_000))
        assertFalse("just started", ResumePolicy.shouldStore(4_000, 600_000))
        assertTrue(ResumePolicy.shouldStore(754_000, 3_600_000))
        assertFalse("watched to the end", ResumePolicy.shouldStore(3_590_000, 3_600_000))
        assertTrue(ResumePolicy.shouldOffer(754_000, 3_600_000))
        assertTrue("length not known yet", ResumePolicy.shouldOffer(754_000, -1))
        assertFalse(ResumePolicy.shouldOffer(2_000, 3_600_000))
    }

    @Test fun resumeEntriesRoundTripAndTrimOldest() {
        val raw = ResumePolicy.encode(754_000, 3_600_000, 1_700_000_000_000)
        assertEquals(ResumePolicy.Entry(754_000, 3_600_000, 1_700_000_000_000), ResumePolicy.decode(raw))
        assertNull(ResumePolicy.decode("garbage"))
        assertNull(ResumePolicy.decode(null))
        val stamps = mapOf("a" to 5L, "b" to 1L, "c" to 9L, "d" to 3L)
        assertEquals(listOf("b", "d"), ResumePolicy.victims(stamps, max = 2))
        assertTrue(ResumePolicy.victims(stamps, max = 10).isEmpty())
    }

    @Test fun brightnessFollowsTheFingerAndClamps() {
        // Swiping up (negative dy) brightens; a full-height swipe covers the whole range.
        assertEquals(0.5f + 0.25f * 1.25f, GestureMath.brightnessAfterDrag(0.5f, -250f, 1000f), 1e-4f)
        assertEquals(1f, GestureMath.brightnessAfterDrag(0.9f, -900f, 1000f), 0f)
        assertEquals(0.01f, GestureMath.brightnessAfterDrag(0.1f, 900f, 1000f), 0f)
    }

    @Test fun volumeBoostOnlyWhenAllowed() {
        assertEquals(1f, GestureMath.volumeAfterDrag(0.9f, -500f, 1000f, maxLevel = 1f), 0f)
        assertEquals(1.525f, GestureMath.volumeAfterDrag(0.9f, -500f, 1000f, maxLevel = 2f), 1e-4f)
        assertEquals(15 to 100, GestureMath.splitVolume(1f, 15))
        assertEquals(8 to 100, GestureMath.splitVolume(0.5f, 15))
        assertEquals(15 to 150, GestureMath.splitVolume(1.5f, 15))
        assertEquals(15 to 200, GestureMath.splitVolume(9f, 15))
        assertEquals(1.5f, GestureMath.levelOf(15, 15, 150), 1e-4f)
        assertEquals(0.4f, GestureMath.levelOf(6, 15, 150), 1e-4f)
    }

    @Test fun seekSwipeScalesWithLengthAndClamps() {
        assertEquals(20_000L, GestureMath.seekSpanMs(5_000))
        assertEquals(60_000L, GestureMath.seekSpanMs(60_000))
        assertEquals(180_000L, GestureMath.seekSpanMs(7_200_000))
        assertEquals(100_000L + 90_000L, GestureMath.seekTarget(100_000, 500f, 1000f, 7_200_000))
        assertEquals(0L, GestureMath.seekTarget(10_000, -1000f, 1000f, 7_200_000))
        assertEquals(60_000L, GestureMath.seekTarget(50_000, 1000f, 1000f, 60_000))
    }

    @Test fun doubleTapSides() {
        assertEquals(-1, GestureMath.tapSide(100f, 1000f))
        assertEquals(0, GestureMath.tapSide(500f, 1000f))
        assertEquals(1, GestureMath.tapSide(900f, 1000f))
    }

    @Test fun timeAndDelayFormatting() {
        assertEquals("0:00", GestureMath.formatTime(-5))
        assertEquals("0:05", GestureMath.formatTime(5_400))
        assertEquals("12:34", GestureMath.formatTime(754_000))
        assertEquals("1:02:03", GestureMath.formatTime(3_723_000))
        assertEquals("+0:35", GestureMath.formatDelta(35_000))
        assertEquals("−1:10", GestureMath.formatDelta(-70_000))
        assertEquals("0 ms", GestureMath.formatDelay(0))
        assertEquals("+250 ms", GestureMath.formatDelay(250))
        assertEquals("−1.5 s", GestureMath.formatDelay(-1_500))
        assertEquals("+2 s", GestureMath.formatDelay(2_000))
        assertEquals("1×", GestureMath.formatRate(1f))
        assertEquals("1.25×", GestureMath.formatRate(1.25f))
        assertEquals("0.5×", GestureMath.formatRate(0.5f))
    }
}
