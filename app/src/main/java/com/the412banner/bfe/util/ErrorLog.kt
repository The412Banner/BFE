package com.the412banner.bfe.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import com.the412banner.bfe.BuildConfig
import com.the412banner.bfe.cloud.RcloneDaemon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Error logs a user can attach to a bug report (Settings > Troubleshooting > Save error logs, on by
 * default): when something fails, a dated entry is appended to `Download/BFE/BFE-errors-<date>.txt`.
 *
 * An entry holds the app and Android version, the device model, what failed, the error and (for Cloud
 * and Remote) rclone's last log lines and the remote's type. Everything taken from the error passes
 * through [LogScrub] first, so no file or folder names, account or remote names, addresses, user
 * names, passwords or tokens are written. Writing never throws; a log BFE can't write is skipped.
 */
object ErrorLog {
    private const val TAG = "BFE_ERRORLOG"
    private const val PREF = "error_log_enabled"
    private const val KEEP_FILES = 14
    private const val MAX_FILE_BYTES = 1_000_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    fun folder(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BFE")

    fun isEnabled(ctx: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(PREF, true)

    fun setEnabled(ctx: Context, on: Boolean) = PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(PREF, on).apply()

    /**
     * Logs a failure of [what] ("Remote folder listing"). [shown] is the message the user saw; [details]
     * are extra lines BFE chose (sizes, counts), written as given, so they must not hold names.
     */
    fun record(ctx: Context, what: String, error: Throwable?, shown: String? = null, details: List<String> = emptyList()) {
        write(ctx, what, error?.let { describe(it) }, shown, details, emptyList(), rclone = false)
    }

    /**
     * A Cloud / Remote failure: like [record], plus the type of each of [remotes] ("smb", "drive")
     * instead of its name, and rclone's last log lines. [error] may be rclone's error text.
     */
    fun cloud(ctx: Context, what: String, remotes: List<String>, error: String?, shown: String? = null, details: List<String> = emptyList()) {
        write(ctx, what, error, shown, details, remotes.filter { it.isNotBlank() }.distinct(), rclone = true)
    }

    fun cloud(ctx: Context, what: String, remotes: List<String>, error: Throwable?, shown: String? = null, details: List<String> = emptyList()) =
        cloud(ctx, what, remotes, error?.let { describe(it) }, shown, details)

    private fun describe(t: Throwable): String {
        val msg = t.message?.takeIf { it.isNotBlank() }
        val cause = t.cause?.takeIf { it !== t }?.let { c -> c.javaClass.simpleName + (c.message?.let { ": $it" } ?: "") }
        return listOfNotNull(t.javaClass.simpleName + (msg?.let { ": $it" } ?: ""), cause?.let { "caused by $it" }).joinToString("\n")
    }

    private fun write(ctx: Context, what: String, error: String?, shown: String?, details: List<String>, remotes: List<String>, rclone: Boolean) {
        val app = ctx.applicationContext
        if (!isEnabled(app)) return
        val now = Date()
        val tail = if (rclone) RcloneDaemon.logTail() else ""
        scope.launch {
            runCatching {
                val known = if (rclone) remoteTypes(app) else emptyMap()
                val names = known.keys + remotes
                val sb = StringBuilder()
                sb.append("── ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now)).append(" ── ").append(what).append('\n')
                sb.append("BFE ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") · Android ")
                    .append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
                    .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                if (remotes.isNotEmpty()) {
                    sb.append("Remote type: ").append(remotes.joinToString(", ") { known[it] ?: "unknown" }).append('\n')
                }
                details.forEach { sb.append(it).append('\n') }
                error?.let { sb.append("Error: ").append(LogScrub.scrub(it, names).replace("\n", "\n  ")).append('\n') }
                shown?.let { sb.append("Shown: ").append(LogScrub.scrub(it, names)).append('\n') }
                if (tail.isNotBlank()) {
                    sb.append("rclone log (last lines):\n")
                    LogScrub.scrub(tail, names).lineSequence().forEach { sb.append("  ").append(it).append('\n') }
                }
                sb.append('\n')
                append(now, sb.toString())
            }.onFailure { Log.w(TAG, "Couldn't write the error log: ${it.message}") }
        }
    }

    /** Remote name -> rclone type, only when the daemon is already running (never starts it for this). */
    private suspend fun remoteTypes(app: Context): Map<String, String> =
        if (!RcloneDaemon.isRunning()) emptyMap()
        else runCatching { RcloneDaemon.api(app).remotes().associate { it.name to it.type } }.getOrDefault(emptyMap())

    private fun append(now: Date, text: String) = synchronized(lock) {
        val dir = folder().apply { mkdirs() }
        val file = File(dir, "BFE-errors-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now) + ".txt")
        if (file.length() > MAX_FILE_BYTES) return@synchronized
        file.appendText(text)
        dir.listFiles { f -> f.isFile && f.name.startsWith("BFE-errors-") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_FILES)
            ?.forEach { it.delete() }
    }
}
