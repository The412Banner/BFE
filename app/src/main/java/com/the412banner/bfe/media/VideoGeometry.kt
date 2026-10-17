package com.the412banner.bfe.media

import kotlin.math.roundToInt

/** How the picture is fitted to the screen: VLC's scale set plus its fixed aspect-ratio overrides. */
enum class AspectMode(val label: String, val ratio: Double?) {
    BEST_FIT("Best fit", null),
    FIT_SCREEN("Fit screen", null),
    FILL("Fill", null),
    R16_9("16:9", 16.0 / 9.0),
    R4_3("4:3", 4.0 / 3.0),
    R21_9("21:9", 21.0 / 9.0),
    R235_1("2.35:1", 2.35),
    R16_10("16:10", 16.0 / 10.0),
    R5_4("5:4", 5.0 / 4.0),
    R1_1("1:1", 1.0),
    ORIGINAL("Original", null),
}

/** Cuts the picture to this shape (centred) before fitting it — e.g. to remove baked-in black bars. */
enum class CropMode(val label: String, val ratio: Double?) {
    NONE("None", null),
    C16_9("16:9", 16.0 / 9.0),
    C4_3("4:3", 4.0 / 3.0),
    C21_9("21:9", 21.0 / 9.0),
    C235_1("2.35:1", 2.35),
    C16_10("16:10", 16.0 / 10.0),
    C1_1("1:1", 1.0),
}

/** What libVLC reports for the current video (IVLCVout.OnNewVideoLayoutListener). */
data class VideoLayout(
    val width: Int,
    val height: Int,
    val visibleWidth: Int,
    val visibleHeight: Int,
    val sarNum: Int,
    val sarDen: Int,
) {
    val sar: Double get() = if (sarNum > 0 && sarDen > 0) sarNum.toDouble() / sarDen else 1.0
    /** Display aspect ratio of the visible picture. */
    val aspect: Double get() = if (visibleHeight > 0) visibleWidth * sar / visibleHeight else 16.0 / 9.0
    val isValid: Boolean get() = width > 0 && height > 0 && visibleWidth > 0 && visibleHeight > 0
}

/**
 * Where the video goes, in pixels. [frameW]×[frameH] is the visible window (centred on screen,
 * clipped by it when larger, and rotated as a whole); inside it the video surface is
 * [surfaceW]×[surfaceH] at ([surfaceX], [surfaceY]) from the frame's top-left — bigger than the frame
 * when the picture is cropped, and scaled up by buffer/visible when the decoder pads its buffers.
 */
data class Placement(
    val frameW: Int,
    val frameH: Int,
    val surfaceW: Int,
    val surfaceH: Int,
    val surfaceX: Int,
    val surfaceY: Int,
) {
    /** On-screen width / height once the frame is turned by [rotation] degrees. */
    fun displayedAspect(rotation: Int): Double {
        val quarter = (rotation / 90) % 2 != 0
        val w = if (quarter) frameH else frameW
        val h = if (quarter) frameW else frameH
        return if (h > 0) w.toDouble() / h else 1.0
    }
}

/**
 * The player's sizing, the same model libVLC's own VideoHelper uses (fit the visible picture with
 * its sample aspect ratio, then grow the surface by buffer/visible), extended with a centred crop,
 * zoom and 90° rotation (fitting against the swapped screen so a turned picture still fits).
 */
object VideoGeometry {
    fun place(
        screenW: Int,
        screenH: Int,
        layout: VideoLayout,
        aspect: AspectMode,
        crop: CropMode = CropMode.NONE,
        zoom: Float = 1f,
        rotation: Int = 0,
    ): Placement? {
        if (screenW <= 0 || screenH <= 0 || !layout.isValid) return null
        val quarter = (rotation / 90) % 2 != 0
        val ew = (if (quarter) screenH else screenW).toDouble()
        val eh = (if (quarter) screenW else screenH).toDouble()

        val fullAr = aspect.ratio ?: layout.aspect
        val contentAr = crop.ratio ?: fullAr

        var cw: Double
        var ch: Double
        var fw: Double
        var fh: Double
        if (aspect == AspectMode.ORIGINAL) {
            // 1:1 pixels (with the sample aspect ratio applied), then the crop window inside it.
            fh = layout.visibleHeight.toDouble()
            fw = fh * fullAr
            if (contentAr < fullAr) { ch = fh; cw = ch * contentAr } else { cw = fw; ch = cw / contentAr }
        } else {
            val cover = aspect == AspectMode.FIT_SCREEN
            val screenAr = ew / eh
            if ((screenAr > contentAr) != cover) { ch = eh; cw = eh * contentAr } else { cw = ew; ch = ew / contentAr }
            if (contentAr < fullAr) { fh = ch; fw = fh * fullAr } else { fw = cw; fh = fw / fullAr }
            if (aspect == AspectMode.FILL) {
                // Stretch the (cropped) picture to the whole screen, both axes independently.
                val sx = ew / cw
                val sy = eh / ch
                cw *= sx; fw *= sx
                ch *= sy; fh *= sy
            }
        }

        val z = zoom.coerceIn(1f, 8f).toDouble()
        cw *= z; ch *= z; fw *= z; fh *= z

        val sw = fw * layout.width / layout.visibleWidth
        val sh = fh * layout.height / layout.visibleHeight
        return Placement(
            frameW = cw.roundToInt().coerceAtLeast(1),
            frameH = ch.roundToInt().coerceAtLeast(1),
            surfaceW = kotlin.math.ceil(sw).toInt().coerceAtLeast(1),
            surfaceH = kotlin.math.ceil(sh).toInt().coerceAtLeast(1),
            surfaceX = ((cw - fw) / 2).roundToInt(),
            surfaceY = ((ch - fh) / 2).roundToInt(),
        )
    }

    /** Picture-in-picture accepts aspect ratios between 1:2.39 and 2.39:1. */
    fun pipAspect(aspect: Double): Pair<Int, Int> {
        val a = aspect.coerceIn(1 / 2.39 + 0.001, 2.39 - 0.001)
        return if (a >= 1) (a * 1000).roundToInt() to 1000 else 1000 to (1000 / a).roundToInt()
    }
}
