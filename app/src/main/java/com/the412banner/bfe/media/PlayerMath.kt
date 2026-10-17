package com.the412banner.bfe.media

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * When a playback position is worth remembering and offering back ("Resume from 12:34"). Short clips
 * aren't remembered; a file watched to (nearly) the end forgets its position.
 */
object ResumePolicy {
    const val MIN_LENGTH_MS = 60_000L
    const val MIN_POSITION_MS = 5_000L
    const val END_MARGIN_MS = 15_000L
    const val MAX_ENTRIES = 400

    /** True: store [positionMs]. False: clear any stored position for this file. */
    fun shouldStore(positionMs: Long, lengthMs: Long): Boolean =
        lengthMs >= MIN_LENGTH_MS && positionMs >= MIN_POSITION_MS && positionMs <= lengthMs - END_MARGIN_MS

    /** Whether a stored position is still worth offering for a file of [lengthMs] (unknown = -1). */
    fun shouldOffer(storedMs: Long, lengthMs: Long): Boolean =
        storedMs >= MIN_POSITION_MS && (lengthMs <= 0 || storedMs <= lengthMs - END_MARGIN_MS)

    /** The keys to forget so at most [max] remain, oldest ([stamps] value = last-saved time) first. */
    fun victims(stamps: Map<String, Long>, max: Int = MAX_ENTRIES): List<String> {
        if (stamps.size <= max) return emptyList()
        return stamps.entries.sortedBy { it.value }.take(stamps.size - max).map { it.key }
    }

    /** "pos,length,stamp" — the stored value of one entry. */
    fun encode(positionMs: Long, lengthMs: Long, stamp: Long): String = "$positionMs,$lengthMs,$stamp"

    data class Entry(val positionMs: Long, val lengthMs: Long, val stamp: Long)

    fun decode(raw: String?): Entry? {
        val p = raw?.split(',') ?: return null
        if (p.size != 3) return null
        val pos = p[0].toLongOrNull() ?: return null
        val len = p[1].toLongOrNull() ?: return null
        val st = p[2].toLongOrNull() ?: return null
        return Entry(pos, len, st)
    }
}

/**
 * Gesture → value mapping for the player: vertical swipes on the left half set brightness, on the right
 * half volume (and, past the system maximum, VLC's boost up to 200 %), horizontal swipes seek.
 */
object GestureMath {
    /** A full-height swipe moves brightness / volume by this much of their range. */
    private const val VERTICAL_GAIN = 1.25f

    fun brightnessAfterDrag(start: Float, dyPx: Float, heightPx: Float): Float {
        if (heightPx <= 0f) return start
        return (start - dyPx / heightPx * VERTICAL_GAIN).coerceIn(0.01f, 1f)
    }

    /**
     * Volume as one "level": 0..1 is the system media volume, 1..[maxLevel] (at most 2) is VLC's own
     * gain above 100 %. Swiping up from 1 only goes past it when boost is allowed (maxLevel > 1).
     */
    fun volumeAfterDrag(start: Float, dyPx: Float, heightPx: Float, maxLevel: Float): Float {
        if (heightPx <= 0f) return start
        return (start - dyPx / heightPx * VERTICAL_GAIN).coerceIn(0f, maxLevel.coerceIn(1f, 2f))
    }

    /** Splits a [level] into the system volume index (of [maxIndex]) and VLC's volume percent. */
    fun splitVolume(level: Float, maxIndex: Int): Pair<Int, Int> {
        val l = level.coerceIn(0f, 2f)
        return if (l <= 1f) (l * maxIndex).roundToInt().coerceIn(0, maxIndex) to 100
        else maxIndex to (100 + (l - 1f) * 100f).roundToInt().coerceIn(100, 200)
    }

    /** The level for a system volume [index] of [maxIndex] plus VLC's [vlcPercent] (100 = no boost). */
    fun levelOf(index: Int, maxIndex: Int, vlcPercent: Int): Float {
        if (maxIndex <= 0) return 1f
        val base = index.toFloat() / maxIndex
        return if (index >= maxIndex && vlcPercent > 100) 1f + (vlcPercent - 100) / 100f else base.coerceIn(0f, 1f)
    }

    /** How much a full-width horizontal swipe seeks: the whole clip for short ones, at most 3 minutes. */
    fun seekSpanMs(lengthMs: Long): Long = if (lengthMs <= 0) 90_000L else lengthMs.coerceIn(20_000L, 180_000L)

    fun seekTarget(startMs: Long, dxPx: Float, widthPx: Float, lengthMs: Long): Long {
        if (widthPx <= 0f) return startMs
        val t = startMs + (dxPx / widthPx * seekSpanMs(lengthMs)).roundToLong()
        return if (lengthMs > 0) t.coerceIn(0L, lengthMs) else t.coerceAtLeast(0L)
    }

    /** Which side a double tap hit: -1 = left (back), +1 = right (forward), 0 = the middle strip. */
    fun tapSide(x: Float, widthPx: Float): Int = when {
        widthPx <= 0f -> 0
        x < widthPx * 0.38f -> -1
        x > widthPx * 0.62f -> 1
        else -> 0
    }

    /** "0:05", "12:34", "1:02:03". Negative or unknown → "0:00". */
    fun formatTime(ms: Long): String {
        val total = if (ms <= 0) 0L else ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "+0:35" / "−1:10" for a seek preview. */
    fun formatDelta(ms: Long): String = (if (ms < 0) "−" else "+") + formatTime(abs(ms))

    /** "+250 ms" / "−1.5 s" for audio / subtitle delays. */
    fun formatDelay(ms: Long): String {
        if (ms == 0L) return "0 ms"
        val sign = if (ms < 0) "−" else "+"
        val a = abs(ms)
        return if (a < 1000) "$sign$a ms" else sign + "%.2f s".format(a / 1000.0).replace(".00 s", " s").replace("0 s", " s")
    }

    /** "1×", "1.25×", "0.5×". */
    fun formatRate(rate: Float): String {
        val r = (rate * 100).roundToInt() / 100f
        val s = if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString().trimEnd('0').trimEnd('.')
        return "$s×"
    }
}
