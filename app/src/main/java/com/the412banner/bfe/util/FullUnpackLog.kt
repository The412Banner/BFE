package com.the412banner.bfe.util

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.the412banner.bfe.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Full output of a failed unpack (Settings > Troubleshooting > Keep full unpack logs, off by default).
 *
 * While an extraction runs, every line its engine prints is written to a file in the app's cache. When
 * the job fails, the file moves to `Download/BFE/BFE-unpack-<UTC time>.log`; when it succeeds or is
 * cancelled it is deleted. Unlike [ErrorLog] nothing is scrubbed: the log names the archive, the
 * destination and the files, which is what debugging a failure needs. Only one extraction runs at a
 * time (UnpackService), so one open log is enough. Writing never throws.
 */
object FullUnpackLog {
    private const val PREF = "full_unpack_log_enabled"
    private const val KEEP_FILES = 10
    private const val MAX_BYTES = 20L shl 20

    private val lock = Any()
    private var writer: FileWriter? = null
    private var file: File? = null
    private var written = 0L

    fun isEnabled(ctx: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(PREF, false)

    fun setEnabled(ctx: Context, on: Boolean) = PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(PREF, on).apply()

    private fun stamp(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    /** Starts the log for one extraction; a no-op when the setting is off. */
    fun begin(ctx: Context, archive: String, dest: String, engine: String) = synchronized(lock) {
        closeQuietly()
        if (!isEnabled(ctx)) return@synchronized
        runCatching {
            val f = File(ctx.cacheDir, "unpack-current.log")
            writer = FileWriter(f, false)
            file = f
            written = 0
            raw("BFE ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            raw("Started ${stamp("yyyy-MM-dd HH:mm:ss")} UTC")
            raw("Archive: $archive")
            raw("Destination: $dest")
            raw("Engine: $engine")
            raw("---- engine output ----")
        }.onFailure { closeQuietly() }
    }

    /** One line of engine output (or a note from BFE). */
    fun line(text: String) = synchronized(lock) { raw(text) }

    private fun raw(text: String) {
        val w = writer ?: return
        if (written > MAX_BYTES) return
        runCatching {
            w.write(text)
            w.write("\n")
            written += text.length + 1
            if (written > MAX_BYTES) w.write("[BFE: log capped at ${MAX_BYTES shr 20} MB]\n")
        }
    }

    /** Ends the log: kept in Download/BFE when [failed], deleted otherwise. Returns the kept file. */
    fun finish(failed: Boolean, summary: String? = null): File? = synchronized(lock) {
        val f = file
        if (writer == null || f == null) return@synchronized null
        summary?.let { raw("---- result ----"); raw(it) }
        closeQuietly()
        if (!failed) {
            f.delete()
            return@synchronized null
        }
        runCatching {
            val dir = ErrorLog.folder().apply { mkdirs() }
            val out = File(dir, "BFE-unpack-${stamp("yyyyMMdd-HHmmss")}Z.log")
            f.copyTo(out, overwrite = true)
            f.delete()
            dir.listFiles { x -> x.name.startsWith("BFE-unpack-") && x.name.endsWith(".log") }
                ?.sortedByDescending { it.name }?.drop(KEEP_FILES)?.forEach { it.delete() }
            out
        }.getOrNull()
    }

    private fun closeQuietly() {
        runCatching { writer?.close() }
        writer = null
    }
}
