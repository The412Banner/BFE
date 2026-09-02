package com.the412banner.bfe.video

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Thin wrapper around the bundled ffmpeg (`jniLibs/arm64-v8a/libffmpeg.so` — a static
 * aarch64/bionic GPL build with x264, see NOTICE_FFMPEG.txt), exec'd from nativeLibraryDir exactly
 * like the 7-Zip / innoextract / unarc binaries: no chmod, never from filesDir (exec is blocked
 * there on Android 10+), `extractNativeLibs=true` keeps it on disk.
 *
 * Progress comes from `-progress pipe:1 -nostats`: ffmpeg writes `key=value` lines on stdout
 * (`out_time_us`, `total_size`, `speed=1.5x`, `progress=continue|end`) which are turned into a
 * percent against the duration read from a `-i` probe (the `Duration: HH:MM:SS.cc` line on stderr).
 */
object Ffmpeg {
    private const val TAG = "Ffmpeg"

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    fun isAvailable(context: Context): Boolean = binary(context).canExecute()

    private fun newProcess(context: Context, vararg args: String): ProcessBuilder =
        ProcessBuilder(binary(context).absolutePath, *args).apply {
            environment()["TMPDIR"] = context.cacheDir.absolutePath
            environment()["HOME"] = context.cacheDir.absolutePath
        }

    private val DURATION = Regex("""Duration:\s*(\d+):(\d\d):(\d\d)(?:\.(\d+))?""")

    /** The container duration in ms from `ffmpeg -i`, or 0 when unknown (progress then goes by bytes). */
    fun probeDurationMs(context: Context, input: File): Long {
        return try {
            val proc = newProcess(context, "-nostdin", "-hide_banner", "-i", input.absolutePath)
                .redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val m = DURATION.find(text) ?: return 0L
            val h = m.groupValues[1].toLong(); val mi = m.groupValues[2].toLong(); val s = m.groupValues[3].toLong()
            val frac = m.groupValues[4].let { if (it.isEmpty()) 0L else (it.padEnd(3, '0').take(3)).toLong() }
            ((h * 3600 + mi * 60 + s) * 1000) + frac
        } catch (e: Exception) {
            Log.w(TAG, "probe failed for ${input.name}: ${e.message}")
            0L
        }
    }

    /** `ffmpeg -version` first line, for the About/diagnostics — null when the binary won't run. */
    fun version(context: Context): String? = runCatching {
        val p = newProcess(context, "-version").redirectErrorStream(true).start()
        val first = p.inputStream.bufferedReader().readLine()
        p.waitFor()
        first
    }.getOrNull()

    interface Listener {
        /** [outTimeMs] processed so far, [outputBytes] written, [speed] ffmpeg's realtime multiple. */
        fun onProgress(outTimeMs: Long, outputBytes: Long, speed: Float)
    }

    data class Result(val exitCode: Int, val stderrTail: String)

    /**
     * Transcodes [input] → [output] as H.264 (libx264, [quality] preset/CRF, yuv420p) + AAC 160k
     * (or no audio), optionally capped to [resolution] height with the aspect kept and even
     * dimensions, faststart for streaming-friendly MP4. [onProcess] hands over the Process for cancel.
     */
    fun convert(
        context: Context,
        input: File,
        output: File,
        quality: ConvertQuality,
        resolution: ConvertResolution,
        keepAudio: Boolean,
        threads: Int,
        listener: Listener,
        onProcess: (Process) -> Unit,
    ): Result {
        output.parentFile?.mkdirs()
        val args = ArrayList<String>()
        args += listOf("-nostdin", "-hide_banner", "-y", "-threads", threads.toString(), "-i", input.absolutePath)
        // Video: scale to the height cap keeping aspect; -2 forces even dims (yuv420p needs them).
        val vf = ArrayList<String>()
        resolution.height?.let { h -> vf += "scale=-2:'min($h,ih)'" }
        vf += "format=yuv420p"
        args += listOf("-vf", vf.joinToString(","))
        args += listOf("-c:v", "libx264", "-preset", quality.preset, "-crf", quality.crf.toString(), "-pix_fmt", "yuv420p")
        if (keepAudio) args += listOf("-c:a", "aac", "-b:a", "160k", "-ac", "2")
        else args += "-an"
        args += listOf("-map", "0:v:0")
        if (keepAudio) args += listOf("-map", "0:a:0?")
        args += listOf("-movflags", "+faststart", "-f", "mp4")
        args += listOf("-progress", "pipe:1", "-nostats")
        args += output.absolutePath
        val proc = newProcess(context, *args.toTypedArray()).start()
        onProcess(proc)

        val stderr = StringBuilder()
        val errThread = Thread {
            runCatching {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(stderr) {
                        stderr.append(line).append('\n')
                        if (stderr.length > 8192) stderr.delete(0, stderr.length - 8192)
                    }
                }
            }
        }.also { it.start() }

        var outUs = 0L
        var bytes = 0L
        var speed = 0f
        proc.inputStream.bufferedReader().forEachLine { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEachLine
            val k = line.substring(0, eq).trim(); val v = line.substring(eq + 1).trim()
            when (k) {
                // out_time_us and out_time_ms are BOTH microseconds in ffmpeg (historical naming).
                "out_time_us", "out_time_ms" -> v.toLongOrNull()?.let { if (it >= 0) outUs = it }
                "total_size" -> v.toLongOrNull()?.let { bytes = it }
                "speed" -> v.removeSuffix("x").toFloatOrNull()?.let { speed = it }
                "progress" -> listener.onProgress(outUs / 1000, bytes, speed)
            }
        }
        val exit = proc.waitFor()
        runCatching { errThread.join(500) }
        return Result(exit, synchronized(stderr) { stderr.toString().trim() })
    }
}
