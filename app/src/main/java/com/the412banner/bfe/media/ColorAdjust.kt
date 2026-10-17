package com.the412banner.bfe.media

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** One-tap colour looks, applied before the sliders. */
enum class ColorPreset(val label: String) {
    NORMAL("Normal"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    NIGHT("Night"),
    GRAYSCALE("Grayscale"),
    SEPIA("Sepia"),
    INVERT("Invert"),
}

/**
 * The player's real-time picture adjustments as a 4×5 colour matrix in android.graphics.ColorMatrix
 * layout (row-major, RGBA in 0..255, the fifth column an offset in 0..255), applied to the video
 * TextureView's layer paint: instant, no re-decode, works with hardware decoding.
 *
 * [brightness] −1..1 (an offset of up to ±40 % of full scale), [contrast] 0..2 around mid grey,
 * [saturation] 0..2 (0 = grey, Rec. 709 weights), [hue] −180..180 degrees (luminance-preserving
 * rotation, as SVG's hueRotate), [gamma] 0.4..2.5 — a colour matrix is linear, so gamma is the tangent
 * of the x^(1/γ) curve at mid grey: right in the mid-tones, approximate at the ends.
 */
object ColorAdjust {
    val IDENTITY: FloatArray
        get() = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

    fun isIdentity(m: FloatArray, eps: Float = 1e-4f): Boolean {
        val id = IDENTITY
        return m.indices.all { kotlin.math.abs(m[it] - id[it]) < (if (it % 5 == 4) eps * 255 else eps) }
    }

    fun matrix(
        preset: ColorPreset = ColorPreset.NORMAL,
        brightness: Float = 0f,
        contrast: Float = 1f,
        saturation: Float = 1f,
        hue: Float = 0f,
        gamma: Float = 1f,
    ): FloatArray {
        var m = presetMatrix(preset)
        if (saturation != 1f) m = concat(saturationMatrix(saturation), m)
        if (hue != 0f) m = concat(hueMatrix(hue), m)
        if (gamma != 1f) m = concat(gammaMatrix(gamma), m)
        if (contrast != 1f) m = concat(contrastMatrix(contrast), m)
        if (brightness != 0f) m = concat(brightnessMatrix(brightness), m)
        return m
    }

    fun presetMatrix(p: ColorPreset): FloatArray = when (p) {
        ColorPreset.NORMAL -> IDENTITY
        ColorPreset.VIVID -> concat(contrastMatrix(1.08f), saturationMatrix(1.35f))
        ColorPreset.WARM -> scaleMatrix(1.08f, 1.0f, 0.86f, 6f, 2f, -4f)
        ColorPreset.COOL -> scaleMatrix(0.9f, 1.0f, 1.1f, -4f, 0f, 6f)
        ColorPreset.NIGHT -> scaleMatrix(0.95f, 0.8f, 0.55f, 0f, 0f, 0f)
        ColorPreset.GRAYSCALE -> saturationMatrix(0f)
        ColorPreset.SEPIA -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        ColorPreset.INVERT -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun scaleMatrix(r: Float, g: Float, b: Float, ro: Float, go: Float, bo: Float) = floatArrayOf(
        r, 0f, 0f, 0f, ro,
        0f, g, 0f, 0f, go,
        0f, 0f, b, 0f, bo,
        0f, 0f, 0f, 1f, 0f,
    )

    fun brightnessMatrix(b: Float): FloatArray {
        val o = b.coerceIn(-1f, 1f) * 0.4f * 255f
        return scaleMatrix(1f, 1f, 1f, o, o, o)
    }

    fun contrastMatrix(c: Float): FloatArray {
        val k = c.coerceIn(0f, 3f)
        val o = (1f - k) * 0.5f * 255f
        return scaleMatrix(k, k, k, o, o, o)
    }

    fun saturationMatrix(s: Float): FloatArray {
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f
        val k = s.coerceIn(0f, 3f)
        val ir = (1 - k) * lr
        val ig = (1 - k) * lg
        val ib = (1 - k) * lb
        return floatArrayOf(
            ir + k, ig, ib, 0f, 0f,
            ir, ig + k, ib, 0f, 0f,
            ir, ig, ib + k, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun hueMatrix(degrees: Float): FloatArray {
        val a = Math.toRadians(degrees.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Linearised x^(1/γ) around x = 0.5: slope = (1/γ)·0.5^(1/γ−1), offset keeps 0.5 ↦ 0.5^(1/γ). */
    fun gammaMatrix(gamma: Float): FloatArray {
        val g = gamma.coerceIn(0.2f, 5f).toDouble()
        val e = 1.0 / g
        val slope = (e * 0.5.pow(e - 1)).toFloat()
        val mid = 0.5.pow(e).toFloat()
        val o = (mid - slope * 0.5f) * 255f
        return scaleMatrix(slope, slope, slope, o, o, o)
    }

    /** a ∘ b: the matrix that applies [b] first, then [a]. */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (r in 0 until 4) {
            for (c in 0 until 5) {
                var v = if (c == 4) a[r * 5 + 4] else 0f
                for (k in 0 until 4) v += a[r * 5 + k] * b[k * 5 + c]
                out[r * 5 + c] = v
            }
        }
        return out
    }

    /** Applies [m] to one RGBA pixel (0..255 channels) — for tests and previews. */
    fun apply(m: FloatArray, rgba: FloatArray): FloatArray = FloatArray(4) { r ->
        (m[r * 5] * rgba[0] + m[r * 5 + 1] * rgba[1] + m[r * 5 + 2] * rgba[2] + m[r * 5 + 3] * rgba[3] + m[r * 5 + 4]).coerceIn(0f, 255f)
    }
}
