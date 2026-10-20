package com.the412banner.bfe.ui.screens.home

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.Log
import com.the412banner.bfe.core.StorageRoots
import com.the412banner.bfe.ui.screens.apps.AppsRepository
import com.the412banner.bfe.ui.screens.apps.AppsStat
import com.the412banner.bfe.ui.screens.categories.CountSize
import com.the412banner.bfe.ui.screens.categories.MediaCategory
import com.the412banner.bfe.ui.screens.categories.MediaQueries
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** A mounted volume for a storage tile: used / total bytes. */
data class VolumeStat(val label: String, val dir: File, val readable: Boolean, val used: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Everything the Home tiles show. A null field = not measured yet (the tile shows a placeholder);
 * a category mapped to null = MediaStore couldn't be read.
 */
data class HomeStats(
    val main: VolumeStat? = null,
    val removable: List<VolumeStat> = emptyList(),
    val downloads: CountSize? = null,
    val media: Map<MediaCategory, CountSize?> = emptyMap(),
    val apps: AppsStat? = null,
    /** libsu's answer: true = root granted, false = refused/unavailable, null = never asked. */
    val root: Boolean? = null,
)

/**
 * Measures the Home tiles off the main thread and keeps the last answers, so returning to Home shows
 * numbers at once while a refresh runs. Every stat is measured independently and published as soon as
 * it's ready (storage first, then the MediaStore totals, Downloads and apps).
 */
object HomeStatsStore {
    private const val TAG = "HomeStats"
    private const val MIN_INTERVAL_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _stats = MutableStateFlow(HomeStats())
    val stats: StateFlow<HomeStats> = _stats.asStateFlow()

    private var job: Job? = null
    private var lastStart = 0L

    /** Re-measure (skipped while a refresh is running or one just ran, unless [force]). */
    fun refresh(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (job?.isActive == true) return
        if (!force && now - lastStart < MIN_INTERVAL_MS) return
        lastStart = now
        val app = context.applicationContext
        job = scope.launch {
            launch {
                val main = runCatching { mainVolume(app) }.onFailure { Log.w(TAG, "main volume", it) }.getOrNull()
                val removable = runCatching { removableVolumes(app) }.getOrDefault(emptyList())
                _stats.update { it.copy(main = main, removable = removable, root = Shell.isAppGrantedRoot()) }
            }
            MediaCategory.values().forEach { cat ->
                launch {
                    val t = MediaQueries.totals(app, cat)
                    _stats.update { it.copy(media = it.media + (cat to t)) }
                }
            }
            launch {
                val d = runCatching { folderStats(downloadsDir()) }.getOrNull()
                _stats.update { it.copy(downloads = d) }
            }
            launch {
                val a = runCatching { AppsRepository.homeStat(app) }.onFailure { Log.w(TAG, "apps", it) }.getOrNull()
                _stats.update { it.copy(apps = a) }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun downloadsDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * Internal storage the way Android's Settings reports it: StorageStatsManager's total is the
     * device's advertised capacity (e.g. 256 GB, not the 238 GiB data partition) and its free space
     * counts clearable cache — no permission needed for either. StatFs is the fallback, and is also
     * used when the advertised total is far above the data partition: Android rounds the chip size
     * up to the next marketing size, so a chip a little over 1 TB reports 2 TB (seen on a 1 TB phone,
     * which then showed "2.0 TB / 2.0 TB").
     */
    private fun mainVolume(context: Context): VolumeStat {
        val dir = File("/storage/emulated/0")
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val fromFs = runCatching { StatFs(Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes } }.getOrNull()
        val fromStats = runCatching {
            val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
            val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
            total to free
        }.getOrNull()?.takeIf { (total, _) -> fromFs == null || total <= fromFs.first * 3 / 2 }
        val (total, free) = fromStats ?: fromFs ?: (0L to 0L)
        return VolumeStat("Main storage", dir, true, (total - free).coerceAtLeast(0L), total)
    }

    /** One entry per mounted SD card / USB drive (the same volumes the file manager's drive menu lists). */
    private fun removableVolumes(context: Context): List<VolumeStat> =
        StorageRoots.list(context).filter { it.removable }.map { root ->
            val fs = runCatching { StatFs(root.dir.path) }.getOrNull()
            val total = fs?.totalBytes ?: 0L
            val free = fs?.availableBytes ?: 0L
            VolumeStat(root.label, root.dir, root.readable, (total - free).coerceAtLeast(0L), total)
        }

    /**
     * Item count (direct children) and total size (recursive) of a folder. One lstat per entry via
     * walkFileTree; symlinks aren't followed; stops after [maxEntries] so a huge tree can't stall.
     */
    fun folderStats(dir: File, maxEntries: Int = 400_000): CountSize? {
        val top = dir.list() ?: return null
        var bytes = 0L
        var seen = 0
        Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) bytes += attrs.size()
                return if (++seen >= maxEntries) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return CountSize(top.size, bytes)
    }
}
