package com.the412banner.bfe.ui.screens.categories

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** The Home dashboard's file categories, each answered by one MediaStore query across all volumes. */
enum class MediaCategory(val title: String) {
    IMAGES("Images"),
    AUDIO("Audio"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    RECENT("New files"),
}

/** What a MediaStore row is, for its icon and thumbnail. */
enum class MediaKind { IMAGE, VIDEO, AUDIO, OTHER }

/** One file from MediaStore. [path] is a real filesystem path (BFE holds All-Files-Access). */
data class MediaItem(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long,
    /** Milliseconds. */
    val modified: Long,
    /** Milliseconds. */
    val added: Long,
    val mime: String?,
    val kind: MediaKind,
) {
    val file: File get() = File(path)

    /** The typed media Uri (images / video / audio) that thumbnails are asked for; null for other files. */
    val mediaUri: Uri?
        get() = when (kind) {
            MediaKind.IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            MediaKind.VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            MediaKind.AUDIO -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            MediaKind.OTHER -> null
        }
}

/** A count and a byte total, for a tile's stats line. */
data class CountSize(val count: Int, val bytes: Long)

/**
 * MediaStore queries behind the category tiles and the category browser. Everything is blocking IO —
 * call from Dispatchers.IO. BFE holds All-Files-Access, which MediaStore treats as permission to see
 * every app's files; a refusal (SecurityException) answers null rather than crashing.
 *
 * "external" is the union of every mounted external volume (internal shared storage + SD/USB) on
 * Android 10+, and the primary volume before that.
 */
object MediaQueries {
    private const val TAG = "MediaQueries"

    /** "New files" = added to storage within this many days. */
    const val RECENT_DAYS = 7

    /** Extensions the Documents category collects (matched on the file path, case-insensitively). */
    val DOCUMENT_EXTS = listOf(
        "pdf", "doc", "docx", "docm", "xls", "xlsx", "xlsm", "ppt", "pptx", "odt", "ods", "odp",
        "txt", "rtf", "epub", "mobi", "azw3", "fb2", "djvu", "xps", "md", "csv", "tsv",
    )

    // MediaStore keeps a row per folder too (MTP's FORMAT_ASSOCIATION); the Files queries drop them.
    private const val FORMAT_FOLDER = 12289

    private class Spec(
        val uri: Uri,
        val selection: String?,
        val args: Array<String>?,
        /** Files-table query: folder rows must be filtered out. */
        val filesTable: Boolean,
        val kind: MediaKind?,
    )

    private fun filesUri(): Uri = MediaStore.Files.getContentUri("external")

    private fun spec(cat: MediaCategory): Spec = when (cat) {
        MediaCategory.IMAGES -> Spec(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, false, MediaKind.IMAGE)
        MediaCategory.AUDIO -> Spec(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, false, MediaKind.AUDIO)
        MediaCategory.VIDEOS -> Spec(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, false, MediaKind.VIDEO)
        // Documents and New files skip hidden paths (".thumbnails", app dot-folders): they are app
        // internals, not something you went looking for.
        MediaCategory.DOCUMENTS -> Spec(
            filesUri(),
            DOCUMENT_EXTS.joinToString(" OR ", "(", ")") { "${MediaStore.MediaColumns.DATA} LIKE ?" } +
                " AND ${MediaStore.MediaColumns.DATA} NOT LIKE ?",
            (DOCUMENT_EXTS.map { "%.$it" } + "%/.%").toTypedArray(),
            true, null,
        )
        MediaCategory.RECENT -> Spec(
            filesUri(),
            "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATA} NOT LIKE ?",
            arrayOf(((System.currentTimeMillis() / 1000L) - RECENT_DAYS * 86_400L).toString(), "%/.%"),
            true, null,
        )
    }

    /**
     * Runs [cat]'s query and hands each row to [row]. The folder filter uses the `format` column; a
     * provider that rejects it gets the query again without it, and folder rows are then dropped by
     * checking the path ([folderCheck] = true).
     */
    private fun scan(context: Context, cat: MediaCategory, projection: Array<String>, row: (Cursor, Boolean) -> Unit): Boolean {
        val s = spec(cat)
        val resolver = context.contentResolver
        fun exec(selection: String?, folderCheck: Boolean): Boolean {
            val c = resolver.query(s.uri, projection, selection, s.args, null) ?: return false
            c.use { while (it.moveToNext()) row(it, folderCheck) }
            return true
        }
        return try {
            if (s.filesTable) {
                val withFormat = listOfNotNull(s.selection?.let { "($it)" }, "format != $FORMAT_FOLDER").joinToString(" AND ")
                try {
                    exec(withFormat, false)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "format column refused, filtering folders by path", e)
                    exec(s.selection, true)
                }
            } else {
                exec(s.selection, false)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "MediaStore refused ${cat.name}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "query failed for ${cat.name}", e)
            false
        }
    }

    private fun isFolderRow(path: String?, mime: String?): Boolean =
        mime == null && path != null && File(path).isDirectory

    /** Count + total bytes of [cat]; null when MediaStore can't be read. */
    fun totals(context: Context, cat: MediaCategory): CountSize? {
        var count = 0
        var bytes = 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE)
        val ok = scan(context, cat, projection) { c, folderCheck ->
            if (folderCheck && isFolderRow(c.getString(1), c.getString(2))) return@scan
            count++
            if (!c.isNull(0)) bytes += c.getLong(0)
        }
        return if (ok) CountSize(count, bytes) else null
    }

    /** Every file in [cat] (unsorted); null when MediaStore can't be read. */
    fun list(context: Context, cat: MediaCategory): List<MediaItem>? {
        val s = spec(cat)
        val projection = buildList {
            add(BaseColumns._ID)
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (s.filesTable) add(MediaStore.Files.FileColumns.MEDIA_TYPE)
        }.toTypedArray()
        val out = ArrayList<MediaItem>()
        val ok = scan(context, cat, projection) { c, folderCheck ->
            val path = c.getString(1) ?: return@scan
            val mime = c.getString(6)
            if (folderCheck && isFolderRow(path, mime)) return@scan
            val kind = s.kind ?: when (c.getInt(7)) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaKind.IMAGE
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaKind.AUDIO
                else -> MediaKind.OTHER
            }
            out.add(
                MediaItem(
                    id = c.getLong(0),
                    path = path,
                    name = c.getString(2)?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'),
                    size = if (c.isNull(3)) 0L else c.getLong(3),
                    modified = if (c.isNull(4)) 0L else c.getLong(4) * 1000L,
                    added = if (c.isNull(5)) 0L else c.getLong(5) * 1000L,
                    mime = mime,
                    kind = kind,
                )
            )
        }
        return if (ok) out else null
    }
}

/**
 * Video / audio thumbnails from MediaStore's own thumbnail cache (Coil, which the image thumbnails
 * use, can't decode a video frame without an extra decoder). Cached by row id; a handful of loads at
 * a time so a fling doesn't queue hundreds of decodes ahead of the visible cells.
 */
object MediaThumbs {
    private val cache = LruCache<Long, ImageBitmap>(256)
    private val misses = LruCache<Long, Boolean>(1024)
    private val gate = Semaphore(4)

    fun cached(item: MediaItem): ImageBitmap? = cache.get(item.id)

    suspend fun load(context: Context, item: MediaItem, sizePx: Int): ImageBitmap? {
        cache.get(item.id)?.let { return it }
        if (misses.get(item.id) == true) return null
        val uri = item.mediaUri ?: return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                val bmp = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
                    } else {
                        @Suppress("DEPRECATION")
                        when (item.kind) {
                            MediaKind.VIDEO -> MediaStore.Video.Thumbnails.getThumbnail(
                                context.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null,
                            )
                            MediaKind.IMAGE -> MediaStore.Images.Thumbnails.getThumbnail(
                                context.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null,
                            )
                            else -> null
                        }
                    }
                }.getOrNull()
                val img = bmp?.asImageBitmap()
                if (img != null) cache.put(item.id, img) else misses.put(item.id, true)
                img
            }
        }
    }
}
