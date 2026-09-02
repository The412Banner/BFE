package com.the412banner.bfe.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

/** Encode effort → x264 preset + CRF. */
enum class ConvertQuality(val label: String, val preset: String, val crf: Int) {
    FAST("Fast", "veryfast", 26),
    BALANCED("Balanced", "medium", 23),
    HIGH("High", "slow", 20),
}

/** Output height cap (aspect kept, even dimensions); null = keep the source size. */
enum class ConvertResolution(val label: String, val height: Int?) {
    KEEP("Keep", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
}

/** One conversion job: several inputs run back-to-back into [outputDir]. */
data class ConvertRequest(
    val inputs: List<String>,
    val outputNames: List<String>,
    val outputDir: String,
    val quality: ConvertQuality,
    val resolution: ConvertResolution,
    val keepAudio: Boolean,
)

enum class ConvertPhase { IDLE, PROBING, CONVERTING, DONE, ERROR, CANCELLED }

/** Live snapshot of the running (or last) conversion — [ConvertService] writes, the pill reads. */
data class ConvertState(
    val phase: ConvertPhase = ConvertPhase.IDLE,
    val jobId: Long = 0L,
    val outputDir: String = "",
    val batchTotal: Int = 1,
    /** 1-based index of the file being converted. */
    val batchIndex: Int = 0,
    val currentName: String = "",
    /** Percent of the CURRENT file. */
    val percent: Int = 0,
    /** Percent across the whole batch. */
    val overallPercent: Int = 0,
    val durationMs: Long = 0L,
    val outTimeMs: Long = 0L,
    val outputBytes: Long = 0L,
    /** ffmpeg's own speed multiplier (1.0 = realtime). */
    val speed: Float = 0f,
    val etaSeconds: Long = -1L,
    val elapsedMs: Long = 0L,
    val done: Int = 0,
    val failed: Int = 0,
    val errorTail: String? = null,
    /** Output files that finished (absolute paths). */
    val outputs: List<String> = emptyList(),
) {
    val isRunning: Boolean get() = phase == ConvertPhase.PROBING || phase == ConvertPhase.CONVERTING
}

object ConvertManager {
    private val _state = MutableStateFlow(ConvertState())
    val state: StateFlow<ConvertState> = _state.asStateFlow()
    val current: ConvertState get() = _state.value
    fun set(next: ConvertState) { _state.value = next }
    fun update(block: (ConvertState) -> ConvertState) { _state.value = block(_state.value) }
    fun clearIfTerminal() {
        val p = _state.value.phase
        if (p == ConvertPhase.DONE || p == ConvertPhase.ERROR || p == ConvertPhase.CANCELLED) _state.value = ConvertState()
    }
}

/** Which files "Convert to MP4…" is offered for, and the Bink 2 trap. */
object VideoFormats {
    val EXTENSIONS = setOf(
        "bik", "smk", "wmv", "asf", "avi", "mkv", "webm", "mov", "mp4", "m4v", "mpg", "mpeg", "m2v",
        "ts", "ogv", "ogg", "flv", "3gp", "vob",
    )

    fun isVideoName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    fun isBink2Name(name: String): Boolean = name.lowercase().endsWith(".bk2")

    /** Bink 2 files start with "KB2"; Bink 1 with "BIK". Only reads 3 bytes. */
    fun isBink2Magic(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val b = ByteArray(3)
            raf.read(b) == 3 && b[0] == 'K'.code.toByte() && b[1] == 'B'.code.toByte() && b[2] == '2'.code.toByte()
        }
    }.getOrDefault(false)

    const val BINK2_NOTE = "Bink 2 (.bk2) is a proprietary RAD codec with no open decoder — ffmpeg can't read it, " +
        "so these files can't be converted. Bink 1 (.bik) and Smacker (.smk) are fine."

    fun suggestedOutputName(input: String): String = input.substringBeforeLast('.', input).ifBlank { input } + ".mp4"
}
