package com.the412banner.bfe.media

/**
 * Which libVLC video output ended up drawing into the player's TextureView.
 *
 * libVLC for Android 3.6 has two: the Android display ("android_display", NATIVE), which reports the
 * picture size through IVLCVout.OnNewVideoLayoutListener so the app sizes the view itself, and the
 * OpenGL ES 2 output (GL), which fills the whole view and is placed with MediaPlayer.setAspectRatio /
 * setScale instead. With MediaCodec rendering straight to the surface and no separate subtitles
 * surface (a TextureView can't have one), the Android display declines ("cannot blend subtitles
 * with an opaque surface, trying next vout") and the GL output takes over — it blends subtitles
 * itself. With frames copied out of MediaCodec (":no-mediacodec-dr") the Android display is used and
 * blends subtitles in software.
 */
enum class RenderMode { UNKNOWN, NATIVE, GL }

/**
 * When the current file must be re-opened with frames copied out of MediaCodec. Everything here only
 * ever turns copy-out ON for a file (it stays on until the next file), so nothing can flip back and
 * forth.
 */
object FramePolicy {
    /** Crop, zoom and quarter turns need the picture size, which only the Android display reports. */
    fun needsLayoutGeometry(s: PlayerSettings): Boolean =
        s.crop != CropMode.NONE || s.zoom > 1.001f || (s.rotation / 90) % 2 != 0

    /** Copy-out from the start: VLC's CPU filters, or geometry that needs the reported picture size. */
    fun copyAtOpen(s: PlayerSettings): Boolean = VlcArgs.needsCpuFrames(s) || needsLayoutGeometry(s)

    /**
     * Whether a file playing with direct rendering has to be re-opened with copy-out:
     * on the GL output, for crop / zoom / a quarter turn; on the Android display with direct
     * rendering, for any subtitle track, which it can't draw over a hardware frame. (VLC's CPU
     * filters aren't decided here: changing them rebuilds libVLC, and [copyAtOpen] covers them.)
     */
    fun mustCopyNow(mode: RenderMode, s: PlayerSettings, subtitleOn: Boolean): Boolean = when (mode) {
        RenderMode.GL -> needsLayoutGeometry(s)
        RenderMode.NATIVE -> subtitleOn
        RenderMode.UNKNOWN -> false
    }

    /**
     * MediaPlayer.setScale for "Fit screen" on the GL output (cover the view, cropping the overflow),
     * as libVLC's own VideoHelper computes it: the larger of the two fit factors. [swapped] = the
     * stream's orientation flag turns it 90°.
     */
    fun coverScale(videoW: Int, videoH: Int, sarNum: Int, sarDen: Int, swapped: Boolean, viewW: Int, viewH: Int): Float {
        if (videoW <= 0 || videoH <= 0 || viewW <= 0 || viewH <= 0) return 0f
        var w = if (swapped) videoH else videoW
        val h = if (swapped) videoW else videoH
        if (sarNum > 0 && sarDen > 0 && sarNum != sarDen) w = w * sarNum / sarDen
        val ar = w / h.toFloat()
        val dar = viewW / viewH.toFloat()
        return if (dar >= ar) viewW / w.toFloat() else viewH / h.toFloat()
    }

    /** MediaPlayer.setAspectRatio for a fixed-ratio mode on the GL output ("16:9", "2.35:1"…). */
    fun vlcAspect(mode: AspectMode): String? = if (mode.ratio != null) mode.label else null
}
