package com.the412banner.bfe.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.the412banner.bfe.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * In-app updater: reads the latest STABLE release's `update.json`, compares versionCode, and on the
 * user's say-so downloads the APK, checks its sha256 and hands it to the system installer.
 *
 * Source of truth = the integer [BuildConfig.VERSION_CODE]. `release.yml` writes update.json both as
 * a release asset and onto the orphan `manifest` branch; the app reads the branch copy raw (no API
 * rate limit, no download counter) and falls back to `releases/latest/download/update.json`, which
 * only ever resolves to the newest non-prerelease.
 *
 * update.json:
 * {
 *   "versionCode": 12, "versionName": "1.0",
 *   "notes": "What changed…",
 *   "apkName": "BFE-1.0.apk",
 *   "apkUrl": "https://github.com/The412Banner/BFE/releases/download/v1.0/BFE-1.0.apk",
 *   "sha256": "…64 hex…", "size": 28000000,
 *   "publishedAt": "2026-10-13", "minSupported": 1
 * }
 * minSupported = the oldest installed versionCode that may update in-app; anything older is sent to
 * the releases page instead.
 *
 * Updates install over the top only because every release is signed with the same key.
 */
object UpdateManager {
    private const val TAG = "BFE_UPDATE"
    private const val REPO = "The412Banner/BFE"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"
    const val REPO_PAGE = "https://github.com/$REPO"
    private const val MANIFEST_URL = "https://raw.githubusercontent.com/$REPO/manifest/update.json"
    private const val FALLBACK_URL = "https://github.com/$REPO/releases/latest/download/update.json"

    /** Automatic (launch) checks reuse the last answer this long; the Settings button always asks. */
    private const val CHECK_COOLDOWN_MS = 6L * 60 * 60 * 1000

    private const val PREF_AUTO = "update_auto_check"
    private const val PREF_SKIP = "update_skip_version"
    private const val PREF_LAST_CHECK = "update_last_check"
    private const val CACHE_NAME = "update_latest.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val sha256: String?,
        /** Bytes, or -1 when the manifest did not say. */
        val size: Long,
        val publishedAt: String,
        val minSupported: Int,
    ) {
        val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
        /** False when this install is too old to update in-app (send the user to the releases page). */
        val canUpdateInApp: Boolean get() = BuildConfig.VERSION_CODE >= minSupported
    }

    /** Result of a check, for the Settings row. */
    sealed class CheckState {
        object Idle : CheckState()
        object Checking : CheckState()
        data class UpToDate(val latest: UpdateInfo?) : CheckState()
        data class Available(val info: UpdateInfo) : CheckState()
        object Failed : CheckState()
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val info: UpdateInfo, val bytes: Long, val total: Long) : DownloadState()
        data class Verifying(val info: UpdateInfo) : DownloadState()
        /** Downloaded + verified, waiting on the "install unknown apps" grant. */
        data class NeedsPermission(val info: UpdateInfo, val apk: File) : DownloadState()
        data class Failed(val info: UpdateInfo, val reason: String) : DownloadState()
        /** Handed to the system installer. */
        data class Installing(val info: UpdateInfo, val apk: File) : DownloadState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _check = MutableStateFlow<CheckState>(CheckState.Idle)
    val check: StateFlow<CheckState> get() = _check

    /** The update the app should offer right now (set by a check, cleared by Later/Skip). */
    private val _offer = MutableStateFlow<UpdateInfo?>(null)
    val offer: StateFlow<UpdateInfo?> get() = _offer

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> get() = _download
    private var downloadJob: Job? = null

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    // ── Preferences ──────────────────────────────────────────────────────
    fun isAutoCheck(ctx: Context) = prefs(ctx).getBoolean(PREF_AUTO, true)
    fun setAutoCheck(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(PREF_AUTO, on).apply()
    fun lastCheck(ctx: Context) = prefs(ctx).getLong(PREF_LAST_CHECK, 0L)
    private fun skipped(ctx: Context) = prefs(ctx).getInt(PREF_SKIP, 0)

    // ── Checking ─────────────────────────────────────────────────────────
    /**
     * Launch check: only when auto-check is on, at most once per [CHECK_COOLDOWN_MS] (inside the
     * cooldown it re-reads the cached answer, so a skipped/later'd version stays quiet), and silent on
     * any failure. Offers the update unless the user skipped that exact version.
     */
    fun checkOnLaunch(ctx: Context) {
        if (!isAutoCheck(ctx)) return
        val app = ctx.applicationContext
        scope.launch {
            val fresh = System.currentTimeMillis() - lastCheck(app) in 0 until CHECK_COOLDOWN_MS
            val info = if (fresh) loadCached(app) else fetch(app)
            if (info != null && info.isNewer && info.versionCode != skipped(app)) _offer.value = info
        }
    }

    /** Settings button: always hits the network and reports into [check]; offers any newer version. */
    fun checkNow(ctx: Context) {
        if (_check.value == CheckState.Checking) return
        val app = ctx.applicationContext
        _check.value = CheckState.Checking
        scope.launch {
            val info = fetch(app)
            _check.value = when {
                info == null -> CheckState.Failed
                info.isNewer -> CheckState.Available(info).also { _offer.value = info }
                else -> CheckState.UpToDate(info)
            }
        }
    }

    /** Re-show the dialog for a version found earlier (Settings "Update to X" button). */
    fun showOffer(info: UpdateInfo) { _offer.value = info }

    /** "Later": hide for this session; the next launch check (after the cooldown) offers it again. */
    fun later() { _offer.value = null }

    /** "Skip this version": never offer this versionCode automatically again. */
    fun skip(ctx: Context, info: UpdateInfo) {
        prefs(ctx).edit().putInt(PREF_SKIP, info.versionCode).apply()
        _offer.value = null
    }

    private suspend fun fetch(ctx: Context): UpdateInfo? {
        for (url in listOf(MANIFEST_URL, FALLBACK_URL)) {
            val body = runCatching { httpGetText(url) }.onFailure { Log.i(TAG, "check $url: ${it.message}") }.getOrNull()
            val info = body?.let { parse(it) }
            if (info != null) {
                runCatching { File(ctx.cacheDir, CACHE_NAME).writeText(body) }
                prefs(ctx).edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
                return info
            }
        }
        return null
    }

    private fun loadCached(ctx: Context): UpdateInfo? = runCatching {
        File(ctx.cacheDir, CACHE_NAME).takeIf { it.isFile }?.readText()?.let { parse(it) }
    }.getOrNull()

    internal fun parse(body: String): UpdateInfo? = runCatching {
        val o = JSONObject(body)
        val name = o.optString("apkName", "").ifBlank { "BFE-${o.optString("versionName", "update")}.apk" }
        val url = o.optString("apkUrl", "").takeIf { it.startsWith("https://") } ?: return null
        UpdateInfo(
            versionCode = o.getInt("versionCode"),
            versionName = o.optString("versionName", ""),
            notes = o.optString("notes", "").trim(),
            apkName = name.substringAfterLast('/'),
            apkUrl = url,
            sha256 = o.optString("sha256", "").lowercase().takeIf { it.length == 64 },
            size = o.optLong("size", -1L),
            publishedAt = o.optString("publishedAt", ""),
            minSupported = o.optInt("minSupported", 1),
        )
    }.getOrNull()

    // ── Download + install ───────────────────────────────────────────────
    /**
     * Downloads the APK into cache/update (cleared first, so old installers never pile up), verifies
     * its sha256 against the manifest, then installs — or parks in [DownloadState.NeedsPermission]
     * until the "install unknown apps" grant exists. One download at a time; [cancel] stops it.
     */
    fun startDownload(ctx: Context, info: UpdateInfo) {
        if (_download.value is DownloadState.Downloading || _download.value is DownloadState.Verifying) return
        _offer.value = null
        val app = ctx.applicationContext
        _download.value = DownloadState.Downloading(info, 0L, info.size)
        downloadJob = scope.launch {
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val apk = File(dir, info.apkName)
            val part = File(dir, info.apkName + ".part")
            try {
                httpDownload(info.apkUrl, part) { bytes, total ->
                    _download.value = DownloadState.Downloading(info, bytes, if (total > 0) total else info.size)
                }
                _download.value = DownloadState.Verifying(info)
                if (info.sha256 != null) {
                    val actual = sha256(part)
                    if (actual != info.sha256) {
                        part.delete()
                        Log.w(TAG, "sha256 mismatch: got $actual, manifest says ${info.sha256}")
                        _download.value = DownloadState.Failed(info, "The download was corrupted (checksum mismatch). Try again.")
                        return@launch
                    }
                }
                if (!part.renameTo(apk)) throw IOException("rename failed")
                withContext(Dispatchers.Main) { proceedToInstall(app, info, apk) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                part.delete()
                throw e
            } catch (e: Exception) {
                part.delete()
                Log.w(TAG, "download failed", e)
                _download.value = DownloadState.Failed(info, "The download didn't finish. Check your connection and try again.")
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        _download.value = DownloadState.Idle
    }

    /** Close a Failed / Installing / NeedsPermission card. */
    fun dismiss() {
        if (_download.value !is DownloadState.Downloading && _download.value !is DownloadState.Verifying) {
            _download.value = DownloadState.Idle
        }
    }

    fun canInstallPackages(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Called on resume: if we were waiting on the install grant and it is now there, install. */
    fun resumeIfPermitted(ctx: Context) {
        val s = _download.value
        if (s is DownloadState.NeedsPermission && canInstallPackages(ctx)) proceedToInstall(ctx, s.info, s.apk)
    }

    /** Re-open the system installer for an already verified download (Installing card's "Install"). */
    fun proceedToInstall(ctx: Context, info: UpdateInfo, apk: File) {
        if (!apk.isFile) {
            _download.value = DownloadState.Failed(info, "The downloaded update is gone. Download it again.")
            return
        }
        if (!canInstallPackages(ctx)) {
            _download.value = DownloadState.NeedsPermission(info, apk)
            return
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            _download.value = DownloadState.Installing(info, apk)
        } catch (e: Exception) {
            Log.w(TAG, "installer launch failed", e)
            _download.value = DownloadState.Failed(info, "Couldn't open the system installer.")
        }
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────
    private fun open(url: String): HttpURLConnection {
        // GitHub release downloads redirect to objects.githubusercontent.com (https→https), which
        // HttpURLConnection follows on its own; a few hops are allowed by hand as a safety net.
        var target = URL(url)
        repeat(5) {
            val c = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BFE/${BuildConfig.VERSION_NAME} (Android)")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("redirect without location")
                c.disconnect()
                target = URL(target, loc)
                return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw IOException("HTTP $code for $target") }
            return c
        }
        throw IOException("too many redirects")
    }

    private fun httpGetText(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun httpDownload(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val c = open(url)
        try {
            val total = c.contentLengthLong
            var done = 0L
            var lastEmit = 0L
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) { onProgress(done, total); lastEmit = now }
                    }
                }
            }
            onProgress(done, total)
            if (total > 0 && done != total) throw IOException("short read $done/$total")
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
