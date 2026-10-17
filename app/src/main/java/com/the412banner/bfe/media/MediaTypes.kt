package com.the412banner.bfe.media

import com.the412banner.bfe.util.naturalCompare
import java.io.File

/** What BFE's own viewer / player does with a file: View an image, Play a video or an audio file. */
enum class MediaType { IMAGE, VIDEO, AUDIO }

/**
 * Which files BFE's photo viewer and video player take, by extension (the provider's MIME type as a
 * fallback). The player is libVLC, so the video and audio sets are deliberately wide.
 */
object MediaTypes {
    val IMAGE_EXTS = setOf("jpg", "jpeg", "jpe", "jfif", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "wbmp", "ico")

    /**
     * Formats Android's BitmapRegionDecoder reads, so big ones are shown tile by tile at full
     * sharpness. Everything else (GIF, HEIC, AVIF, BMP…) is decoded whole at a capped size instead.
     */
    val SUBSAMPLE_EXTS = setOf("jpg", "jpeg", "jpe", "jfif", "png", "webp")

    val VIDEO_EXTS = setOf(
        "mp4", "m4v", "mkv", "webm", "mov", "qt", "avi", "wmv", "asf", "flv", "f4v", "3gp", "3g2", "ts", "m2ts",
        "mts", "tp", "trp", "mpg", "mpeg", "mpe", "m1v", "m2v", "vob", "ogv", "divx", "xvid", "rm", "rmvb", "dv",
        "mxf", "nut", "y4m", "ivf", "h264", "h265", "hevc", "264", "265", "wtv", "dvr-ms", "amv",
    )

    val AUDIO_EXTS = setOf(
        "mp3", "flac", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "aiff", "aif", "aifc", "mka",
        "ac3", "eac3", "dts", "mpc", "wv", "tta", "amr", "awb", "spx", "caf", "au", "snd", "mp2", "mpa", "dsf", "dff",
        "tak", "ra", "voc", "w64",
    )

    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun typeOf(name: String, mime: String? = null): MediaType? {
        when (extOf(name)) {
            in IMAGE_EXTS -> return MediaType.IMAGE
            in VIDEO_EXTS -> return MediaType.VIDEO
            in AUDIO_EXTS -> return MediaType.AUDIO
        }
        return typeOfMime(mime)
    }

    fun typeOfMime(mime: String?): MediaType? {
        val m = mime?.lowercase() ?: return null
        return when {
            m.startsWith("image/") -> MediaType.IMAGE
            m.startsWith("video/") -> MediaType.VIDEO
            m.startsWith("audio/") -> MediaType.AUDIO
            m == "application/ogg" -> MediaType.AUDIO
            else -> null
        }
    }

    fun canSubsample(name: String): Boolean = extOf(name) in SUBSAMPLE_EXTS

    /** JPEG rotation can be saved losslessly as an EXIF orientation tag. */
    fun canSaveRotation(name: String): Boolean = extOf(name) in setOf("jpg", "jpeg", "jpe", "jfif")
}

/**
 * One item of a viewer / player playlist. [uri] is what is opened (a file:// or content:// string,
 * kept as a String so this stays plain Kotlin); [path] is the real file when there is one — used for
 * sharing, deleting, resume keys and VLC's own subtitle auto-detection.
 */
data class MediaRef(
    val uri: String,
    val name: String,
    val path: String? = null,
    val size: Long = -1L,
    val modified: Long = 0L,
) {
    /** Stable identity for pager keys and resume positions. */
    val key: String get() = path ?: uri

    companion object {
        fun of(file: File): MediaRef =
            MediaRef(uri = file.toURI().toString(), name = file.name, path = file.absolutePath, size = file.length(), modified = file.lastModified())
    }
}

/** Building playlists out of folders and lists, in the order the user sees them. */
object MediaSiblings {
    /**
     * The files of [type] in [dir] in natural name order (dot files skipped), and the index of
     * [current] among them (0 when it isn't there). Used when BFE is opened from another app with a
     * file whose folder it can read.
     */
    fun inFolder(dir: File, type: MediaType, current: File?): Pair<List<File>, Int> {
        val files = dir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && MediaTypes.typeOf(it.name) == type }
            ?.sortedWith { a, b -> naturalCompare(a.name, b.name) }
            .orEmpty()
        val idx = current?.let { c -> files.indexOfFirst { it.absolutePath == c.absolutePath } } ?: -1
        if (idx < 0 && current != null && current.isFile) {
            // The file itself always plays even when the listing missed it (e.g. a hidden file).
            return (listOf(current) + files) to 0
        }
        return files to idx.coerceAtLeast(0)
    }

    /**
     * Keeps the entries of [all] whose name is of [type], in the given order, and finds where
     * [currentKey] landed. [nameOf] / [keyOf] read the caller's own item type.
     */
    fun <T> pick(all: List<T>, type: MediaType, currentKey: String, nameOf: (T) -> String, keyOf: (T) -> String): Pair<List<T>, Int> {
        val list = all.filter { MediaTypes.typeOf(nameOf(it)) == type }
        val idx = list.indexOfFirst { keyOf(it) == currentKey }
        return list to idx.coerceAtLeast(0)
    }
}

/**
 * Hands a playlist from the screen that opened the viewer / player to the new activity without
 * pushing thousands of entries through the Intent (Binder transactions cap out around 1 MB). The
 * activity also gets the current file's path/Uri, so after a process restart it rebuilds the list
 * from the folder instead.
 */
object MediaHandoff {
    private val lists = HashMap<Long, List<MediaRef>>()
    private var next = 1L

    @Synchronized
    fun put(list: List<MediaRef>): Long {
        val token = next++
        lists[token] = list
        // Only the newest few are ever waiting; drop anything older.
        lists.keys.filter { it < token - 8 }.forEach { lists.remove(it) }
        return token
    }

    @Synchronized
    fun take(token: Long): List<MediaRef>? = lists.remove(token)
}
