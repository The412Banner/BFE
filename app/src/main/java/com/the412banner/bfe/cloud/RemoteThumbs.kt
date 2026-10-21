package com.the412banner.bfe.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Image previews for files in remote folders (SMB shares, SFTP, cloud accounts), through BFE's rclone
 * daemon (Settings > Photos, video & music > Thumbnails for network folders, on by default).
 *
 * A [RemoteThumb] is a Coil model. Its [Fetcher] makes a small JPEG once and keeps it in
 * `cacheDir/remote-thumbs/`, so a folder only costs network the first time:
 * - JPEG: the preview a camera stores in the EXIF header is read from the file's first bytes, without
 *   downloading the photo;
 * - otherwise (or without an EXIF preview) the whole image is fetched when it is at most [MAX_FULL_BYTES]
 *   and scaled down;
 * - at most [PARALLEL] files are read at once, so a folder of photos doesn't open a burst of connections
 *   to a NAS. Coil only asks for rows on screen and cancels the ones scrolled away.
 * Videos keep their type icon (a frame would need large parts of the file).
 */
data class RemoteThumb(val remote: String, val path: String, val size: Long, val modTime: Long) {
    val cacheKey: String get() = "remote-thumb:$remote:$path|$size|$modTime"
}

object RemoteThumbs {
    private const val PREF = "remote_thumbnails"
    private const val MAX_FULL_BYTES = 30L * 1024 * 1024
    private const val HEADER_BYTES = 128 * 1024
    private const val TARGET_PX = 320
    private const val PARALLEL = 2
    private const val KEEP_FILES = 3000

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
    private val EXIF_EXTS = setOf("jpg", "jpeg")

    private val gate = Semaphore(PARALLEL)
    private lateinit var app: Context
    private var writes = 0

    var enabled by mutableStateOf(true)
        private set

    fun install(ctx: Context) {
        app = ctx.applicationContext
        enabled = PreferenceManager.getDefaultSharedPreferences(app).getBoolean(PREF, true)
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        enabled = on
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(PREF, on).apply()
    }

    /** The Coil model for a remote file's preview, or null when it gets its type icon instead. */
    fun modelFor(remote: String, path: String, name: String, size: Long, modTime: Long, isDir: Boolean): RemoteThumb? {
        if (!enabled || isDir) return null
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in IMAGE_EXTS) return null
        if (ext !in EXIF_EXTS && size > MAX_FULL_BYTES) return null
        return RemoteThumb(remote, path, size, modTime)
    }

    private fun cacheDir(): File = File(app.cacheDir, "remote-thumbs").apply { mkdirs() }

    private fun cacheFile(t: RemoteThumb): File {
        val hex = MessageDigest.getInstance("SHA-1").digest(t.cacheKey.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir(), "$hex.jpg")
    }

    /** The cached preview for [t], making it first if needed; null when the image can't be previewed. */
    private suspend fun thumbnail(t: RemoteThumb): File? {
        val out = cacheFile(t)
        if (out.isFile && out.length() > 0) return out
        val bmp = gate.withPermit {
            coroutineContext.ensureActive()
            val api = RcloneDaemon.api(app)
            val fs = RcloneApi.fsOf(t.remote)
            val ext = t.path.substringAfterLast('.', "").lowercase()
            val fromExif = if (ext in EXIF_EXTS) runCatching { exifPreview(api.rc, fs, t.path) }.getOrNull() else null
            fromExif ?: if (t.size in 1..MAX_FULL_BYTES) fullImage(api.rc, fs, t.path) else null
        } ?: return null
        val tmp = File(out.path + ".part")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        if (!tmp.renameTo(out)) { tmp.delete(); return null }
        if (++writes % 100 == 0) prune()
        return out
    }

    /** The EXIF preview from the start of a JPEG, turned the way the photo is. */
    private suspend fun exifPreview(rc: RcClient, fs: String, path: String): Bitmap? {
        val head = rc.openRead(fs, path, range = 0L until HEADER_BYTES.toLong()).use { input ->
            val buf = ByteArrayOutputStream(HEADER_BYTES)
            val chunk = ByteArray(16 * 1024)
            while (buf.size() < HEADER_BYTES) {
                coroutineContext.ensureActive()
                val n = input.read(chunk, 0, minOf(chunk.size, HEADER_BYTES - buf.size()))
                if (n < 0) break
                buf.write(chunk, 0, n)
            }
            buf.toByteArray()
        }
        val exif = ExifInterface(ByteArrayInputStream(head))
        val thumb = exif.thumbnailBitmap ?: return null
        return rotated(thumb, exif.rotationDegrees)
    }

    /** The whole image, fetched to a temp file and decoded at about [TARGET_PX]. */
    private suspend fun fullImage(rc: RcClient, fs: String, path: String): Bitmap? {
        val tmp = File(cacheDir(), "fetch-${System.nanoTime()}.tmp")
        try {
            rc.openRead(fs, path).use { input ->
                tmp.outputStream().use { output ->
                    val chunk = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FULL_BYTES) return null
                        output.write(chunk, 0, n)
                    }
                }
            }
            return decodeScaled(tmp)
        } finally {
            tmp.delete()
        }
    }

    private fun decodeScaled(file: File): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Applies the EXIF rotation and reads HEIF/AVIF where the device can.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val s = info.size
                val scale = maxOf(1, maxOf(s.width, s.height) / TARGET_PX)
                decoder.setTargetSize(maxOf(1, s.width / scale), maxOf(1, s.height / scale))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET_PX) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            rotated(bmp, runCatching { ExifInterface(file).rotationDegrees }.getOrDefault(0))
        }
    }.getOrNull()

    private fun rotated(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    /** Keeps the newest [KEEP_FILES] previews. */
    private fun prune() {
        val files = cacheDir().listFiles { f -> f.name.endsWith(".jpg") } ?: return
        if (files.size <= KEEP_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(KEEP_FILES).forEach { it.delete() }
    }

    class Keyer : coil.key.Keyer<RemoteThumb> {
        override fun key(data: RemoteThumb, options: Options): String = data.cacheKey
    }

    class Factory : Fetcher.Factory<RemoteThumb> {
        override fun create(data: RemoteThumb, options: Options, imageLoader: ImageLoader): Fetcher = object : Fetcher {
            override suspend fun fetch(): FetchResult {
                val file = thumbnail(data) ?: throw IllegalStateException("no preview")
                return SourceResult(
                    source = ImageSource(file = file.toOkioPath()),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK,
                )
            }
        }
    }
}
