package com.the412banner.bfe.media

/** VLC's deinterlacers. AUTO leaves VLC's own default (deinterlace only what is flagged interlaced). */
enum class Deinterlace(val label: String, val mode: String?) {
    AUTO("Auto", null),
    OFF("Off", null),
    YADIF("Yadif", "yadif"),
    YADIF2X("Yadif 2×", "yadif2x"),
    BLEND("Blend", "blend"),
    BOB("Bob", "bob"),
    LINEAR("Linear", "linear"),
    MEAN("Mean", "mean"),
    DISCARD("Discard", "discard"),
    X("X", "x"),
    PHOSPHOR("Phosphor", "phosphor"),
    IVTC("Film (IVTC)", "ivtc"),
}

enum class Strength(val label: String) { OFF("Off"), LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/** VLC video filters that are compiled into libVLC for Android 3.6 and make sense on a phone. */
enum class VideoLook(val label: String, val filter: String) {
    OLD_MOVIE("Old film", "oldmovie"),
    VHS("VHS", "vhs"),
    WAVE("Waves", "wave"),
    EDGES("Edges", "edgedetection"),
    BLUR("Soft blur", "gaussianblur"),
    ANTIFLICKER("Anti-flicker", "antiflicker"),
}

/** Volume levelling from the ReplayGain tags in music files. */
enum class ReplayGain(val label: String, val vlc: String) { OFF("Off", "none"), TRACK("Track", "track"), ALBUM("Album", "album") }

/** Subtitle text size: VLC's relative font size, where a SMALLER number means BIGGER text. */
enum class SubSize(val label: String, val rel: Int) { SMALL("Small", 20), NORMAL("Normal", 16), LARGE("Large", 12), HUGE("Huge", 8) }

enum class SubColor(val label: String, val rgb: Int) { WHITE("White", 0xFFFFFF), YELLOW("Yellow", 0xFFFF00), CYAN("Cyan", 0x7FFFFF) }

/**
 * Everything the player's effects sheet adjusts. Picture, geometry, sharpen, grain, equalizer,
 * volume boost, delays and speed apply instantly; the rest are libVLC instance options (see
 * [VlcArgs]) and apply by re-opening the file at the same position.
 */
data class PlayerSettings(
    // Picture (TextureView colour matrix)
    val preset: ColorPreset = ColorPreset.NORMAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val gamma: Float = 1f,
    // Geometry
    val aspect: AspectMode = AspectMode.BEST_FIT,
    val crop: CropMode = CropMode.NONE,
    val zoom: Float = 1f,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    // Effects
    val deinterlace: Deinterlace = Deinterlace.AUTO,
    val denoise: Strength = Strength.OFF,
    val debanding: Strength = Strength.OFF,
    val deblocking: Strength = Strength.OFF,
    val looks: Set<VideoLook> = emptySet(),
    val sharpen: Float = 0f,
    val grain: Float = 0f,
    // Audio
    val eqOn: Boolean = false,
    val eqPreset: Int = -1,
    val eqPreamp: Float = 0f,
    val eqBands: List<Float> = List(EQ_BANDS) { 0f },
    val boost: Int = 100,
    val audioDelayMs: Long = 0L,
    val speed: Float = 1f,
    val keepPitch: Boolean = true,
    val replayGain: ReplayGain = ReplayGain.OFF,
    // Subtitles
    val subDelayMs: Long = 0L,
    val subSize: SubSize = SubSize.NORMAL,
    val subColor: SubColor = SubColor.WHITE,
    val subBackground: Boolean = false,
) {
    val colorMatrix: FloatArray get() = ColorAdjust.matrix(preset, brightness, contrast, saturation, hue, gamma)
    val hasColorChange: Boolean get() = !ColorAdjust.isIdentity(colorMatrix)

    fun picturePart(): PlayerSettings = DEFAULT.copy(
        preset = preset, brightness = brightness, contrast = contrast, saturation = saturation, hue = hue, gamma = gamma,
        aspect = aspect, crop = crop, zoom = zoom, rotation = rotation, flipH = flipH, flipV = flipV,
    )

    fun encode(): String = buildString {
        fun kv(k: String, v: Any) { if (isNotEmpty()) append(';'); append(k).append('=').append(v) }
        kv("preset", preset.name); kv("br", brightness); kv("ct", contrast); kv("sat", saturation); kv("hue", hue); kv("gam", gamma)
        kv("asp", aspect.name); kv("crop", crop.name); kv("zoom", zoom); kv("rot", rotation); kv("fh", flipH); kv("fv", flipV)
        kv("dei", deinterlace.name); kv("dn", denoise.name); kv("db", debanding.name); kv("dbl", deblocking.name)
        kv("looks", looks.joinToString(",") { it.name }); kv("sh", sharpen); kv("gr", grain)
        kv("eq", eqOn); kv("eqp", eqPreset); kv("pre", eqPreamp); kv("bands", eqBands.joinToString(","))
        kv("boost", boost); kv("ad", audioDelayMs); kv("spd", speed); kv("pitch", keepPitch); kv("rg", replayGain.name)
        kv("sd", subDelayMs); kv("ss", subSize.name); kv("sc", subColor.name); kv("sb", subBackground)
    }

    companion object {
        const val EQ_BANDS = 10
        val DEFAULT = PlayerSettings()

        fun decode(raw: String?): PlayerSettings {
            if (raw.isNullOrBlank()) return DEFAULT
            val m = raw.split(';').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
            fun f(k: String, d: Float) = m[k]?.toFloatOrNull()?.takeIf { it.isFinite() } ?: d
            fun i(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
            fun l(k: String, d: Long) = m[k]?.toLongOrNull() ?: d
            fun b(k: String, d: Boolean) = m[k]?.toBooleanStrictOrNull() ?: d
            fun <E : Enum<E>> e(k: String, values: Array<E>, d: E) = values.firstOrNull { it.name == m[k] } ?: d
            val d = DEFAULT
            val bands = m["bands"]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == EQ_BANDS } ?: d.eqBands
            return PlayerSettings(
                preset = e("preset", ColorPreset.values(), d.preset),
                brightness = f("br", d.brightness).coerceIn(-1f, 1f),
                contrast = f("ct", d.contrast).coerceIn(0f, 2f),
                saturation = f("sat", d.saturation).coerceIn(0f, 2f),
                hue = f("hue", d.hue).coerceIn(-180f, 180f),
                gamma = f("gam", d.gamma).coerceIn(0.4f, 2.5f),
                aspect = e("asp", AspectMode.values(), d.aspect),
                crop = e("crop", CropMode.values(), d.crop),
                zoom = f("zoom", d.zoom).coerceIn(1f, 3f),
                rotation = i("rot", d.rotation).let { ((it % 360) + 360) % 360 / 90 * 90 },
                flipH = b("fh", d.flipH),
                flipV = b("fv", d.flipV),
                deinterlace = e("dei", Deinterlace.values(), d.deinterlace),
                denoise = e("dn", Strength.values(), d.denoise),
                debanding = e("db", Strength.values(), d.debanding),
                deblocking = e("dbl", Strength.values(), d.deblocking),
                looks = m["looks"].orEmpty().split(',').mapNotNull { n -> VideoLook.values().firstOrNull { it.name == n } }.toSet(),
                sharpen = f("sh", d.sharpen).coerceIn(0f, 1f),
                grain = f("gr", d.grain).coerceIn(0f, 1f),
                eqOn = b("eq", d.eqOn),
                eqPreset = i("eqp", d.eqPreset),
                eqPreamp = f("pre", d.eqPreamp).coerceIn(-20f, 20f),
                eqBands = bands.map { it.coerceIn(-20f, 20f) },
                boost = i("boost", d.boost).coerceIn(100, 200),
                audioDelayMs = l("ad", d.audioDelayMs).coerceIn(-10_000L, 10_000L),
                speed = f("spd", d.speed).coerceIn(0.25f, 4f),
                keepPitch = b("pitch", d.keepPitch),
                replayGain = e("rg", ReplayGain.values(), d.replayGain),
                subDelayMs = l("sd", d.subDelayMs).coerceIn(-30_000L, 30_000L),
                subSize = e("ss", SubSize.values(), d.subSize),
                subColor = e("sc", SubColor.values(), d.subColor),
                subBackground = b("sb", d.subBackground),
            )
        }
    }
}

/**
 * libVLC instance options for the settings that libVLC only reads when it starts (libVLC's own
 * documentation: most audio/video options have no effect per media). The player compares this list
 * between changes and rebuilds its LibVLC + MediaPlayer at the current position when it differs.
 */
object VlcArgs {
    /** Always passed. RV32 output instead of libVLC's RV16 default: no banding on software-decoded video. */
    val BASE = listOf("--android-display-chroma=RV32", "--audio-resampler=soxr")

    fun build(s: PlayerSettings): List<String> {
        val out = ArrayList<String>()
        val filters = ArrayList<String>()
        when (s.denoise) {
            Strength.OFF -> Unit
            Strength.LOW -> { filters += "hqdn3d"; out += hqdn3d(2.0, 1.5, 3.0, 2.25) }
            Strength.MEDIUM -> { filters += "hqdn3d"; out += hqdn3d(4.0, 3.0, 6.0, 4.5) }
            Strength.HIGH -> { filters += "hqdn3d"; out += hqdn3d(8.0, 6.0, 12.0, 9.0) }
        }
        if (s.debanding != Strength.OFF) {
            filters += "gradfun"
            val strength = when (s.debanding) { Strength.LOW -> "0.8"; Strength.MEDIUM -> "1.2"; else -> "2.0" }
            out += "--gradfun-strength=$strength"
            out += "--gradfun-radius=16"
        }
        if (s.deblocking != Strength.OFF) {
            filters += "postproc"
            out += "--postproc-q=" + when (s.deblocking) { Strength.LOW -> 2; Strength.MEDIUM -> 4; else -> 6 }
        }
        VideoLook.values().filter { it in s.looks }.forEach { look ->
            filters += look.filter
            if (look == VideoLook.BLUR) out += "--gaussianblur-sigma=1.5"
        }
        if (filters.isNotEmpty()) out += "--video-filter=" + filters.joinToString(":")
        when {
            s.deinterlace == Deinterlace.OFF -> out += "--deinterlace=0"
            s.deinterlace.mode != null -> { out += "--deinterlace=1"; out += "--deinterlace-mode=${s.deinterlace.mode}" }
        }
        if (!s.keepPitch) out += "--no-audio-time-stretch"
        if (s.replayGain != ReplayGain.OFF) out += "--audio-replay-gain-mode=${s.replayGain.vlc}"
        if (s.subSize != SubSize.NORMAL) out += "--freetype-rel-fontsize=${s.subSize.rel}"
        if (s.subColor != SubColor.WHITE) out += "--freetype-color=${s.subColor.rgb}"
        if (s.subBackground) { out += "--freetype-background-opacity=160"; out += "--freetype-background-color=0" }
        return out
    }

    /**
     * VLC's CPU video filters need decoded frames in memory, so with any of them on, MediaCodec still
     * decodes but copies frames out (":no-mediacodec-dr") instead of rendering straight to the surface.
     */
    fun needsCpuFrames(s: PlayerSettings): Boolean =
        s.denoise != Strength.OFF || s.debanding != Strength.OFF || s.deblocking != Strength.OFF ||
            s.looks.isNotEmpty() || s.deinterlace.mode != null

    private fun hqdn3d(ls: Double, cs: Double, lt: Double, ct: Double) = listOf(
        "--hqdn3d-luma-spat=$ls", "--hqdn3d-chroma-spat=$cs", "--hqdn3d-luma-temp=$lt", "--hqdn3d-chroma-temp=$ct",
    )
}
