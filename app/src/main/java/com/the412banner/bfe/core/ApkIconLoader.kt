package com.the412banner.bfe.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.storage.RootBackend
import java.io.File

/**
 * The launcher icon of an `.apk`, the way the PE-icon path does it for `.exe`: read off the main
 * thread, cached, generic icon on any failure. Uses the platform parser
 * (`PackageManager.getPackageArchiveInfo` + `applicationInfo.loadIcon`), which handles adaptive
 * icons and every density for us.
 *
 * The parser needs a PATH it can open:
 *  - File locations are handed over directly;
 *  - SAF documents are opened as a file descriptor and passed as `/proc/self/fd/N` — no copy;
 *  - root locations (unreadable to the app) are staged into the cache when they're small enough
 *    (big APKs are skipped rather than copied for an icon).
 *
 * Cache key = location id + size + mtime + pixel size, so an overwritten APK refreshes.
 */
object ApkIconLoader {
    private const val TAG = "ApkIconLoader"
    private const val MAX_ROOT_STAGE_BYTES = 64L * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(64) {}
    // Remembered failures so a broken APK isn't re-parsed on every scroll.
    private val misses = LruCache<String, Boolean>(256)

    fun isApk(name: String): Boolean = name.lowercase().endsWith(".apk")

    private fun key(loc: Loc, sizePx: Int) = "${loc.id}|${loc.size}|${loc.lastModified}|$sizePx"

    /** Blocking — call from Dispatchers.IO. Null when the APK can't be parsed. */
    fun load(context: Context, loc: Loc, sizePx: Int): Bitmap? {
        val k = key(loc, sizePx)
        cache.get(k)?.let { return it }
        if (misses.get(k) == true) return null
        val bmp = runCatching { decode(context, loc, sizePx) }.onFailure { Log.d(TAG, "icon failed for ${loc.name}: ${it.message}") }.getOrNull()
        if (bmp != null) cache.put(k, bmp) else misses.put(k, true)
        return bmp
    }

    /** Same as [load] for a plain file path (the APK editor's header). */
    fun load(context: Context, file: File, sizePx: Int): Bitmap? = load(context, Loc.FileLoc(file), sizePx)

    private fun decode(context: Context, loc: Loc, sizePx: Int): Bitmap? {
        val pm = context.packageManager
        fun fromPath(path: String): Bitmap? {
            val info = pm.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return null
            info.sourceDir = path
            info.publicSourceDir = path
            return info.loadIcon(pm).toBitmap(sizePx, sizePx)
        }
        return when (loc) {
            is Loc.FileLoc -> fromPath(loc.file.absolutePath)
            is Loc.SafLoc -> context.contentResolver.openFileDescriptor(loc.docUri, "r")?.use { pfd ->
                fromPath("/proc/self/fd/${pfd.fd}")
            }
            is Loc.RootLoc -> {
                if (loc.size > MAX_ROOT_STAGE_BYTES) return null
                val dir = File(context.cacheDir, "apkicons").apply { mkdirs() }
                val staged = File(dir, "${loc.id.hashCode()}.apk")
                try {
                    RootBackend.openInputStream(context, loc)?.use { i -> staged.outputStream().use { o -> i.copyTo(o) } } ?: return null
                    fromPath(staged.absolutePath)
                } finally {
                    staged.delete()
                }
            }
        }
    }
}
