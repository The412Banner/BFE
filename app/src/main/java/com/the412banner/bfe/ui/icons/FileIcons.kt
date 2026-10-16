package com.the412banner.bfe.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.the412banner.bfe.R

/**
 * BFE's own file and folder artwork (`res/drawable/ic_bfe_*.xml`) and the one place that decides which
 * picture a name gets. Every listing — file manager list/compact/grid, archive panes, categories,
 * cloud/remote browser, Open-with header, favourites — goes through [fileIconRes] / [FileTypeIcon], so
 * a new format only has to be added here.
 *
 * The drawables are full-colour (amber manila folders with a paper peeking out; white pages with a
 * coloured type band; a silver disc), so they are drawn with [Image], never `Icon` — an Icon tint
 * would flatten them to one colour. They sit on a 48×48 grid with their own keyline padding, so give
 * them a fixed square box (rows and grid tiles stay aligned whatever the type).
 */
object FileIcons {

    /** Faded hidden (dot) entries: same picture, drawn lighter. */
    const val HIDDEN_ALPHA = 0.5f

    val FOLDER = R.drawable.ic_bfe_folder

    /** The open folder: "Open as folder" / browsing inside an archive, empty-folder states. */
    val FOLDER_OPEN = R.drawable.ic_bfe_folder_open

    // Folders that get an emblem on the flap, matched on the folder's own name (case-insensitive).
    private val SPECIAL_FOLDERS: Map<String, Int> = buildMap {
        listOf("download", "downloads").forEach { put(it, R.drawable.ic_bfe_folder_download) }
        listOf("dcim", "pictures", "screenshots", "camera", "photos", "images", "wallpapers")
            .forEach { put(it, R.drawable.ic_bfe_folder_pictures) }
        listOf("music", "audio", "ringtones", "notifications", "alarms", "podcasts", "audiobooks", "recordings")
            .forEach { put(it, R.drawable.ic_bfe_folder_music) }
        listOf("movies", "videos", "video").forEach { put(it, R.drawable.ic_bfe_folder_movies) }
        listOf("documents", "docs").forEach { put(it, R.drawable.ic_bfe_folder_documents) }
        put("android", R.drawable.ic_bfe_folder_android)
        listOf("games", "game", "winlator", "roms", "emulation").forEach { put(it, R.drawable.ic_bfe_folder_games) }
        listOf("bfe backups", "backups", "backup").forEach { put(it, R.drawable.ic_bfe_folder_backups) }
    }

    // Everything BFE can unpack (SevenZip.SUPPORTED + ExtraFormats) plus the usual container formats.
    // Disc / disk images and the Winlator packages are split out below and checked first.
    private val ARCHIVE = setOf(
        "zip", "7z", "rar", "tar", "gz", "gzip", "xz", "bz2", "bz", "zst", "zstd", "lz", "lzo", "lz4", "lzma", "z",
        "tgz", "txz", "tbz", "tbz2", "tlz", "tzo", "taz", "tpz",
        "cab", "arj", "lzh", "lha", "alz", "egg", "ace", "lzx", "arc", "zipx", "jar",
        "xapk", "apks", "apkm", "mtz", "cpio", "deb", "udeb", "rpm", "xar", "wim", "esd", "swm",
    )
    private val DISC = setOf(
        "iso", "img", "bin", "cue", "cso", "zso", "udf", "ecm", "dmg", "vhd", "vhdx", "vmdk", "vdi", "qcow", "qcow2",
        "simg", "squashfs", "cramfs", "chd", "rvz", "wia", "wbfs", "gcm", "gdi", "cdi", "nrg", "mdf", "mds", "ccd", "pbp", "isz",
    )
    private val PACKAGE = setOf("wcp", "tzst")
    private val APK = setOf("apk", "aab", "apex")
    private val EXE = setOf("exe", "msi", "bat", "cmd", "com", "scr")
    private val IMAGE = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "jxl", "svg", "tif", "tiff", "ico",
        "dng", "raw", "cr2", "nef", "arw", "psd", "tga", "dds",
    )
    private val VIDEO = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "wmv", "flv", "f4v", "3gp", "ts", "mts", "m2ts", "mpg", "mpeg",
        "vob", "ogv", "asf", "rmvb", "divx", "bik", "bk2",
    )
    private val AUDIO = setOf(
        "mp3", "flac", "ogg", "oga", "opus", "wav", "m4a", "aac", "wma", "amr", "mid", "midi", "ape", "alac",
        "aiff", "aif", "wv", "mka", "xm", "mod", "it", "s3m",
    )
    private val DOCUMENT = setOf("pdf", "doc", "docx", "docm", "odt", "rtf", "epub", "md", "mobi", "azw3", "fb2", "djvu", "xps", "chm")
    private val SPREADSHEET = setOf("xls", "xlsx", "xlsm", "ods", "csv", "tsv")
    private val PRESENTATION = setOf("ppt", "pptx", "pps", "ppsx", "odp")
    private val TEXT = setOf("txt", "log", "ini", "cfg", "conf", "properties", "toml", "nfo", "diz", "inf", "vdf", "env")
    private val CODE = setOf(
        "json", "xml", "yml", "yaml", "js", "mjs", "kt", "kts", "java", "py", "sh", "bash", "reg", "html", "htm",
        "css", "c", "h", "cpp", "hpp", "cc", "rs", "go", "rb", "php", "lua", "gradle", "ps1", "sql", "smali", "glsl", "hlsl",
    )

    // Numbered split volumes: name.7z.001, name.r00, name.z01.
    private val SPLIT_VOLUME = Regex("""\d{3}|r\d{2}|z\d{2}""")

    /** The drawable for [name]; [mime] (when known) is the fallback for names without a useful extension. */
    @DrawableRes
    fun forName(name: String, isDir: Boolean, mime: String? = null): Int {
        val lower = name.lowercase()
        if (isDir) return SPECIAL_FOLDERS[lower.trim()] ?: FOLDER
        if (lower == "payload.bin") return R.drawable.ic_bfe_file_payload
        val ext = lower.substringAfterLast('.', "")
        return when {
            ext.isEmpty() -> null
            ext in PACKAGE -> R.drawable.ic_bfe_file_package
            ext in DISC -> R.drawable.ic_bfe_file_disc
            ext in ARCHIVE || SPLIT_VOLUME.matches(ext) -> R.drawable.ic_bfe_file_archive
            ext in APK -> R.drawable.ic_bfe_file_apk
            ext in EXE -> R.drawable.ic_bfe_file_exe
            ext in IMAGE -> R.drawable.ic_bfe_file_image
            ext in VIDEO -> R.drawable.ic_bfe_file_video
            ext in AUDIO -> R.drawable.ic_bfe_file_audio
            ext in DOCUMENT -> R.drawable.ic_bfe_file_document
            ext in SPREADSHEET -> R.drawable.ic_bfe_file_spreadsheet
            ext in PRESENTATION -> R.drawable.ic_bfe_file_presentation
            ext in TEXT -> R.drawable.ic_bfe_file_text
            ext in CODE -> R.drawable.ic_bfe_file_code
            else -> null
        } ?: forMime(mime)
    }

    @DrawableRes
    private fun forMime(mime: String?): Int {
        val m = mime?.lowercase() ?: return R.drawable.ic_bfe_file
        return when {
            m.startsWith("image/") -> R.drawable.ic_bfe_file_image
            m.startsWith("video/") -> R.drawable.ic_bfe_file_video
            m.startsWith("audio/") -> R.drawable.ic_bfe_file_audio
            m == "application/vnd.android.package-archive" -> R.drawable.ic_bfe_file_apk
            m == "application/pdf" -> R.drawable.ic_bfe_file_document
            m.startsWith("text/") -> R.drawable.ic_bfe_file_text
            m.contains("zip") || m.contains("compressed") || m.contains("tar") || m.contains("rar") ||
                m.contains("7z") || m.contains("gzip") || m.contains("xz") || m.contains("zstd") -> R.drawable.ic_bfe_file_archive
            m.contains("iso9660") || m.contains("cd-image") || m.contains("disk-image") -> R.drawable.ic_bfe_file_disc
            else -> R.drawable.ic_bfe_file
        }
    }
}

/** The BFE drawable for a file or folder called [name] (special folders by name, files by extension). */
@DrawableRes
fun fileIconRes(name: String, isDir: Boolean, mime: String? = null): Int = FileIcons.forName(name, isDir, mime)

/** [fileIconRes] as a Painter — for Coil placeholders / error images under real thumbnails. */
@Composable
fun fileIconPainter(name: String, isDir: Boolean, mime: String? = null): Painter =
    painterResource(fileIconRes(name, isDir, mime))

/** One BFE drawable in a fixed [size] box, full colour. */
@Composable
fun BfeIcon(@DrawableRes res: Int, modifier: Modifier = Modifier, size: Dp = 24.dp, alpha: Float = 1f) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        alpha = alpha,
        modifier = modifier.size(size),
    )
}

/**
 * The type icon for a listing entry: special folders by name, files by extension (then [mime]), drawn
 * at [size]. Hidden (dot) entries get the same picture at [FileIcons.HIDDEN_ALPHA].
 */
@Composable
fun FileTypeIcon(
    name: String,
    isDir: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    mime: String? = null,
) {
    BfeIcon(
        res = fileIconRes(name, isDir, mime),
        modifier = modifier,
        size = size,
        alpha = if (name.startsWith(".")) FileIcons.HIDDEN_ALPHA else 1f,
    )
}
