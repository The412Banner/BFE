package com.the412banner.bfe.server

/**
 * Content types for files served over HTTP, plus the coarse "kind" the web page uses to pick an icon
 * and decide what opens in its viewer. A fixed table (pure JVM, no android.webkit.MimeTypeMap) so the
 * server behaves identically on and off device.
 */
object MimeTypes {

    private val types = HashMap<String, String>().apply {
        fun put(mime: String, vararg exts: String) = exts.forEach { this[it] = mime }
        // Text: shown in the browser rather than downloaded (the row menu always offers Download).
        put("text/plain", "txt", "log", "md", "markdown", "ini", "cfg", "conf", "csv", "tsv", "srt", "ass", "ssa",
            "sub", "nfo", "properties", "yaml", "yml", "toml", "sh", "bat", "cmd", "ps1", "py", "kt", "kts", "java",
            "c", "h", "cpp", "hpp", "cs", "go", "rs", "lua", "gradle", "sql", "diff", "patch", "reg", "inf", "cue")
        put("text/html", "html", "htm")
        put("text/css", "css")
        put("text/javascript", "js", "mjs")
        put("application/json", "json")
        put("text/xml", "xml")
        put("text/vtt", "vtt")
        // Images
        put("image/jpeg", "jpg", "jpeg", "jpe", "jfif")
        put("image/png", "png")
        put("image/gif", "gif")
        put("image/webp", "webp")
        put("image/bmp", "bmp")
        put("image/svg+xml", "svg")
        put("image/x-icon", "ico")
        put("image/avif", "avif")
        put("image/heic", "heic")
        put("image/heif", "heif")
        put("image/tiff", "tif", "tiff")
        // Video
        put("video/mp4", "mp4", "m4v")
        put("video/webm", "webm")
        put("video/quicktime", "mov")
        put("video/x-matroska", "mkv")
        put("video/x-msvideo", "avi")
        put("video/x-ms-wmv", "wmv")
        put("video/x-flv", "flv")
        put("video/3gpp", "3gp")
        put("video/mp2t", "ts", "m2ts", "mts")
        put("video/mpeg", "mpg", "mpeg", "vob")
        put("video/ogg", "ogv")
        // Audio
        put("audio/mpeg", "mp3")
        put("audio/mp4", "m4a", "m4b")
        put("audio/aac", "aac")
        put("audio/flac", "flac")
        put("audio/ogg", "ogg", "oga")
        put("audio/opus", "opus")
        put("audio/wav", "wav")
        put("audio/x-ms-wma", "wma")
        put("audio/midi", "mid", "midi")
        put("audio/amr", "amr")
        // Documents
        put("application/pdf", "pdf")
        put("application/epub+zip", "epub")
        put("application/msword", "doc")
        put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
        put("application/vnd.ms-excel", "xls")
        put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx")
        put("application/vnd.ms-powerpoint", "ppt")
        put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx")
        put("application/vnd.oasis.opendocument.text", "odt")
        put("application/rtf", "rtf")
        // Archives / packages
        put("application/zip", "zip")
        put("application/x-7z-compressed", "7z")
        put("application/vnd.rar", "rar")
        put("application/x-tar", "tar")
        put("application/gzip", "gz", "tgz")
        put("application/x-bzip2", "bz2")
        put("application/x-xz", "xz")
        put("application/zstd", "zst", "tzst", "wcp")
        put("application/x-iso9660-image", "iso")
        put("application/vnd.android.package-archive", "apk")
        put("application/java-archive", "jar")
        put("application/x-msdownload", "exe", "dll")
        put("application/x-msi", "msi")
        // Fonts
        put("font/ttf", "ttf")
        put("font/otf", "otf")
        put("font/woff", "woff")
        put("font/woff2", "woff2")
    }

    private val viewableImages = setOf("jpg", "jpeg", "jpe", "jfif", "png", "gif", "webp", "bmp", "svg", "ico", "avif")
    private val playableVideo = setOf("mp4", "m4v", "webm", "mov", "mkv", "ogv")
    private val playableAudio = setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav")
    private val archives = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "zst", "tzst", "wcp", "iso", "jar")

    /**
     * Formats that are already compressed: the folder-zip stores them (deflate level 0) instead of
     * burning phone CPU trying to shrink them.
     */
    private val incompressible = playableVideo + playableAudio + archives + setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "gif", "webp", "avif", "heic", "heif", "avi", "wmv", "flv", "3gp", "ts",
        "m2ts", "mts", "mpg", "mpeg", "vob", "wma", "amr", "apk", "xapk", "apks", "obb", "docx", "xlsx", "pptx",
        "odt", "epub", "pdf", "woff", "woff2", "bin", "cso", "chd", "pak", "pck",
    )

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** Content-Type header value for [name]; text types carry charset=utf-8. */
    fun forName(name: String): String {
        val t = types[ext(name)] ?: return "application/octet-stream"
        return if (t.startsWith("text/") || t == "application/json") "$t; charset=utf-8" else t
    }

    /** Coarse kind for the web page: folder, image, video, audio, text, pdf, archive, apk or file. */
    fun kind(name: String, isDir: Boolean): String {
        if (isDir) return "folder"
        val e = ext(name)
        return when {
            e in viewableImages -> "image"
            e in playableVideo -> "video"
            e in playableAudio -> "audio"
            e == "pdf" -> "pdf"
            e == "apk" || e == "xapk" || e == "apks" -> "apk"
            e in archives -> "archive"
            types[e]?.let { it.startsWith("text/") || it == "application/json" } == true -> "text"
            types[e]?.startsWith("video/") == true -> "video-file"
            types[e]?.startsWith("audio/") == true -> "audio-file"
            types[e]?.startsWith("image/") == true -> "image-file"
            else -> "file"
        }
    }

    fun isIncompressible(name: String): Boolean = ext(name) in incompressible

    /** Types a browser could execute script from: served with a sandbox CSP so they can't act as the page. */
    fun isActive(mime: String): Boolean =
        mime.startsWith("text/html") || mime.startsWith("image/svg") || mime.startsWith("text/xml") ||
            mime.startsWith("application/xhtml") || mime.startsWith("application/xml")
}
