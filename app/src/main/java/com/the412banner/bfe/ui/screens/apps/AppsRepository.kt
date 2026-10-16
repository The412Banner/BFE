package com.the412banner.bfe.ui.screens.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One installed package, as the Apps list shows it. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val firstInstall: Long,
    val lastUpdate: Long,
    /** base.apk */
    val sourceDir: String,
    /** split_*.apk of a split / bundle install (empty for a single APK). */
    val splitDirs: List<String>,
    /** base + splits, readable without any permission. */
    val apkBytes: Long,
    val storageUuid: UUID?,
) {
    val isSplit: Boolean get() = splitDirs.isNotEmpty()
    val wasUpdated: Boolean get() = lastUpdate > firstInstall + 1000
}

/** Storage an app uses. [data] includes its cache and its external (Android/data) files. */
data class AppSize(val app: Long, val data: Long, val cache: Long) {
    val total: Long get() = app + data
}

/** Where full app sizes come from: Usage access (StorageStatsManager), root (`du`), or nowhere. */
enum class SizeSource { USAGE_ACCESS, ROOT, NONE }

/** Count (+ total size when it can be measured) of user-installed apps, for the Home tile. */
data class AppsStat(val count: Int, val bytes: Long?)

/**
 * Installed apps for the Apps screen and the Home tile. Blocking IO — call from Dispatchers.IO.
 *
 * Sizes: Android only reveals other apps' data sizes through [StorageStatsManager] with Usage access
 * (PACKAGE_USAGE_STATS, granted by the user in Settings), or to root. Without either only the APK
 * size is known (base.apk + splits are world-readable).
 */
object AppsRepository {
    private const val TAG = "AppsRepository"

    /** Every installed package ([userOnly] = only those not shipped on the system image). */
    @Suppress("DEPRECATION")
    fun list(context: Context, userOnly: Boolean = false): List<AppEntry> {
        val pm = context.packageManager
        val pkgs: List<PackageInfo> = runCatching { pm.getInstalledPackages(0) }.getOrElse {
            Log.e(TAG, "getInstalledPackages failed", it); emptyList()
        }
        return pkgs.mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            if (userOnly && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) null else entryOf(pm, pi, ai)
        }
    }

    @Suppress("DEPRECATION")
    private fun entryOf(pm: PackageManager, pi: PackageInfo, ai: ApplicationInfo): AppEntry {
        val splits = ai.splitSourceDirs?.toList().orEmpty()
        val apkBytes = (listOf(ai.sourceDir) + splits).sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        return AppEntry(
            packageName = pi.packageName,
            label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName).ifBlank { pi.packageName },
            versionName = pi.versionName.orEmpty(),
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode.toLong(),
            isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            firstInstall = pi.firstInstallTime,
            lastUpdate = pi.lastUpdateTime,
            sourceDir = ai.sourceDir,
            splitDirs = splits,
            apkBytes = apkBytes,
            storageUuid = runCatching { ai.storageUuid }.getOrNull(),
        )
    }

    /** One package, re-read (after an uninstall/update the list is refreshed from this). */
    fun get(context: Context, pkg: String): AppEntry? = runCatching {
        val pm = context.packageManager
        val pi = pm.getPackageInfo(pkg, 0)
        pi.applicationInfo?.let { entryOf(pm, pi, it) }
    }.getOrNull()

    // ── Usage access ──

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens Settings → Usage access (BFE's own toggle where the ROM supports it). */
    fun openUsageAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Root counts only once it has been granted — the Apps list never triggers the Magisk prompt itself. */
    fun sizeSource(context: Context): SizeSource = when {
        hasUsageAccess(context) -> SizeSource.USAGE_ACCESS
        Shell.isAppGrantedRoot() == true -> SizeSource.ROOT
        else -> SizeSource.NONE
    }

    // ── Sizes ──

    private class Cached(val lastUpdate: Long, val at: Long, val size: AppSize)
    private val sizeCache = ConcurrentHashMap<String, Cached>()
    private const val SIZE_TTL_MS = 120_000L

    /** The cached size for [app] when it's still fresh. Cheap; safe on the main thread. */
    fun cachedSize(app: AppEntry): AppSize? {
        val c = sizeCache[app.packageName] ?: return null
        return if (c.lastUpdate == app.lastUpdate && SystemClock.elapsedRealtime() - c.at < SIZE_TTL_MS) c.size else null
    }

    /**
     * Sizes for [apps] from [source], reported to [onBatch] as they arrive (accumulated map). Usage
     * access answers per app (one installd call each); root answers everything with one `du` pass.
     */
    fun loadSizes(context: Context, apps: List<AppEntry>, source: SizeSource, onBatch: (Map<String, AppSize>) -> Unit) {
        val out = HashMap<String, AppSize>()
        val todo = apps.filter { a -> cachedSize(a)?.also { out[a.packageName] = it } == null }
        if (out.isNotEmpty()) onBatch(HashMap(out))
        if (todo.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        when (source) {
            SizeSource.USAGE_ACCESS -> {
                val ssm = context.getSystemService(StorageStatsManager::class.java) ?: return
                val user = Process.myUserHandle()
                todo.forEachIndexed { i, a ->
                    runCatching { ssm.queryStatsForPackage(a.storageUuid ?: StorageManager.UUID_DEFAULT, a.packageName, user) }
                        .getOrNull()?.let { s ->
                            val size = AppSize(s.appBytes, s.dataBytes, s.cacheBytes)
                            out[a.packageName] = size
                            sizeCache[a.packageName] = Cached(a.lastUpdate, now, size)
                        }
                    if (i % 16 == 15) onBatch(HashMap(out))
                }
            }
            SizeSource.ROOT -> {
                val data = rootDu(listOf("/data/data/*", "/data/media/0/Android/data/*", "/data/media/0/Android/obb/*"))
                todo.forEach { a ->
                    val size = AppSize(a.apkBytes, data[a.packageName] ?: 0L, 0L)
                    out[a.packageName] = size
                    sizeCache[a.packageName] = Cached(a.lastUpdate, now, size)
                }
            }
            SizeSource.NONE -> Unit
        }
        onBatch(HashMap(out))
    }

    /** `du -sk` over [globs] as root → bytes per package (the last path segment), summed. */
    private fun rootDu(globs: List<String>): Map<String, Long> {
        val r = Shell.cmd(*globs.map { "du -sk $it 2>/dev/null" }.toTypedArray()).exec()
        val sums = HashMap<String, Long>()
        r.out.forEach { line ->
            val tab = line.indexOfFirst { it == '\t' || it == ' ' }
            if (tab <= 0) return@forEach
            val kb = line.substring(0, tab).trim().toLongOrNull() ?: return@forEach
            val pkg = line.substring(tab + 1).trim().trimEnd('/').substringAfterLast('/')
            if (pkg.isNotEmpty()) sums[pkg] = (sums[pkg] ?: 0L) + kb * 1024L
        }
        return sums
    }

    // ── Home tile ──

    @Volatile private var homeCache: Pair<Long, AppsStat>? = null

    /** User apps: count always, total size when Usage access or root allows (cached for a minute). */
    fun homeStat(context: Context): AppsStat {
        homeCache?.let { (at, stat) -> if (SystemClock.elapsedRealtime() - at < 60_000L && stat.bytes != null) return stat }
        val users = list(context, userOnly = true)
        val source = sizeSource(context)
        val bytes: Long? = if (source == SizeSource.NONE) null else {
            var sizes: Map<String, AppSize> = emptyMap()
            loadSizes(context, users, source) { sizes = it }
            users.sumOf { sizes[it.packageName]?.total ?: it.apkBytes }
        }
        return AppsStat(users.size, bytes).also { homeCache = SystemClock.elapsedRealtime() to it }
    }

    // ── Icons ──

    private val icons = LruCache<String, ImageBitmap>(384)

    fun cachedIcon(pkg: String): ImageBitmap? = icons.get(pkg)

    fun loadIcon(context: Context, pkg: String, sizePx: Int): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        val bmp = runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
        if (bmp != null) icons.put(pkg, bmp)
        return bmp
    }
}

/**
 * APK backups and share staging, run on an app-level scope so leaving the Apps screen doesn't cut a
 * backup short. A single APK is copied as `<label>_<version>.apk`; a split install is written as a
 * `.apks` bundle (a zip of base.apk + every split, the layout SAI and other split installers read).
 */
object AppBackups {
    data class Progress(val label: String, val done: Long, val total: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private const val PREF_DIR = "appBackupDir"

    @Suppress("DEPRECATION")
    fun defaultDir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BFE Backups")

    fun dir(context: Context): File =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_DIR, null)?.let(::File) ?: defaultDir()

    fun setDir(context: Context, dir: File) =
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_DIR, dir.absolutePath).apply()

    /** `<label>_<version>.apk` (or `.apks` for a split install), with characters Android/FAT reject replaced. */
    fun fileName(app: AppEntry): String {
        val version = app.versionName.ifBlank { app.versionCode.toString() }
        val base = "${app.label}_$version".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return base.ifBlank { app.packageName } + if (app.isSplit) ".apks" else ".apk"
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }

    /** Back [app] up into [dir]; [onDone] runs on the main thread with the written file (null = failed). */
    fun backup(context: Context, app: AppEntry, dir: File, onDone: (File?) -> Unit) {
        if (_progress.value != null) { onDone(null); return }
        val appCtx = context.applicationContext
        scope.launch {
            val out = runCatching {
                if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("Can't create ${dir.absolutePath}")
                val target = unique(dir, fileName(app))
                write(app, target)
                android.media.MediaScannerConnection.scanFile(appCtx, arrayOf(target.absolutePath), null, null)
                target
            }.onFailure { Log.e("AppBackups", "backup of ${app.packageName} failed", it) }.getOrNull()
            _progress.value = null
            withContext(Dispatchers.Main) { onDone(out) }
        }
    }

    /** A nicely named copy in the cache for sharing (receivers would otherwise see "base.apk"). */
    fun stageForShare(context: Context, app: AppEntry, onReady: (File?) -> Unit) {
        if (_progress.value != null) { onReady(null); return }
        val dir = File(context.cacheDir, "apkshare")
        scope.launch {
            val out = runCatching {
                dir.deleteRecursively()
                dir.mkdirs()
                File(dir, fileName(app)).also { write(app, it) }
            }.getOrNull()
            _progress.value = null
            withContext(Dispatchers.Main) { onReady(out) }
        }
    }

    private fun write(app: AppEntry, target: File) {
        val parts = listOf(File(app.sourceDir)) + app.splitDirs.map(::File)
        val total = parts.sumOf { it.length() }.coerceAtLeast(1L)
        var done = 0L
        _progress.value = Progress(app.label, 0L, total)
        val buf = ByteArray(1 shl 20)
        fun pump(src: File, sink: java.io.OutputStream) {
            src.inputStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    sink.write(buf, 0, n)
                    done += n
                    _progress.value = Progress(app.label, done, total)
                }
            }
        }
        try {
            if (!app.isSplit) {
                target.outputStream().use { pump(parts.first(), it) }
            } else {
                // The APKs are already compressed; store them (level 0) so the bundle is quick to write.
                ZipOutputStream(target.outputStream().buffered()).use { zip ->
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    parts.forEachIndexed { i, f ->
                        zip.putNextEntry(ZipEntry(if (i == 0) "base.apk" else f.name))
                        pump(f, zip)
                        zip.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }
}
