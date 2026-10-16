package com.the412banner.bfe.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the web page and JSON the HTTP server returns. The folder page is rendered on the server —
 * links work with scripting off — and `app.js` layers sorting, filtering, uploads, the media viewer
 * and file actions on top of the same markup. Pure JVM.
 */
internal object HttpPages {

    const val ASSET_PREFIX = "/__bfe/"

    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            '\'' -> sb.append("&#39;")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    /** Percent-encodes one path segment (UTF-8), leaving only RFC 3986 unreserved characters bare. */
    fun encodeSegment(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
            ) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
        }
        return sb.toString()
    }

    fun encodePath(segs: List<String>, dir: Boolean): String {
        if (segs.isEmpty()) return "/"
        val p = segs.joinToString("/", prefix = "/") { encodeSegment(it) }
        return if (dir) "$p/" else p
    }

    fun json(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i]) else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    private fun localDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

    private fun isoDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    /** Folders first, then by name (case-insensitive) — the order app.js also starts from. */
    fun sorted(entries: List<VirtualFs.Entry>): List<VirtualFs.Entry> =
        entries.sortedWith(compareBy<VirtualFs.Entry>({ !it.isDir }, { it.name.lowercase() }, { it.name }))

    private fun icon(kind: String): String = when (kind) {
        "folder" -> "folder"
        "image", "image-file" -> "image"
        "video", "video-file" -> "video"
        "audio", "audio-file" -> "audio"
        "text" -> "text"
        "pdf" -> "pdf"
        "archive" -> "archive"
        "apk" -> "apk"
        else -> "file"
    }

    fun listing(
        fs: VirtualFs,
        node: VirtualFs.Node,
        segs: List<String>,
        entries: List<VirtualFs.Entry>,
        assetVersion: String,
    ): String {
        val top = node is VirtualFs.Top
        val writable = node is VirtualFs.Real && !node.readOnly
        val title = when {
            segs.isNotEmpty() -> segs.last()
            fs.single -> fs.mounts.first().name
            else -> "Shared folders"
        }
        val rootLabel = if (fs.single) fs.mounts.first().name else "Shared folders"
        val list = sorted(entries)
        val sb = StringBuilder(4096 + list.size * 420)
        sb.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<meta name=\"color-scheme\" content=\"light dark\"><meta name=\"referrer\" content=\"no-referrer\">")
            .append("<title>").append(esc(title)).append(" · Banners File Explorer</title>")
            .append("<link rel=\"icon\" href=\"data:,\">")
            .append("<link rel=\"stylesheet\" href=\"").append(ASSET_PREFIX).append("app.css?v=").append(assetVersion).append("\">")
            .append("<script>document.documentElement.className='js'</script>")
            .append("</head>\n<body data-rw=\"").append(if (writable) '1' else '0')
            .append("\" data-top=\"").append(if (top) '1' else '0').append("\">\n")

        // ── Header: brand, breadcrumb, toolbar ──
        sb.append("<header class=\"bar\"><div class=\"bar-row\">")
            .append("<a class=\"brand\" href=\"/\" title=\"").append(esc(rootLabel)).append("\"><svg class=\"ic\"><use href=\"#i-wifi\"/></svg><span>BFE</span></a>")
            .append("<nav class=\"crumbs\" aria-label=\"Folder path\">")
            .append("<a href=\"/\">").append(esc(rootLabel)).append("</a>")
        for (i in segs.indices) {
            sb.append("<span class=\"sep\">/</span>")
            val href = encodePath(segs.subList(0, i + 1), true)
            if (i == segs.lastIndex) {
                sb.append("<a href=\"").append(href).append("\" aria-current=\"page\">").append(esc(segs[i])).append("</a>")
            } else {
                sb.append("<a href=\"").append(href).append("\">").append(esc(segs[i])).append("</a>")
            }
        }
        sb.append("</nav>")
        if (node is VirtualFs.Real && node.readOnly) {
            sb.append("<span class=\"badge\" title=\"Uploads and changes are turned off for this folder\"><svg class=\"ic\"><use href=\"#i-lock\"/></svg>Read-only</span>")
        }
        sb.append("</div><div class=\"tools\" role=\"toolbar\">")
        if (segs.isNotEmpty()) {
            sb.append("<a class=\"btn icon\" href=\"").append(encodePath(segs.dropLast(1), true))
                .append("\" title=\"Up one folder\" aria-label=\"Up one folder\"><svg><use href=\"#i-up\"/></svg></a>")
        }
        sb.append("<button class=\"btn rw-only\" id=\"b-upload\" type=\"button\"><svg><use href=\"#i-upload\"/></svg><span>Upload</span></button>")
            .append("<button class=\"btn rw-only\" id=\"b-upload-dir\" type=\"button\" title=\"Upload a whole folder\"><svg><use href=\"#i-folder-up\"/></svg><span>Upload folder</span></button>")
            .append("<button class=\"btn rw-only\" id=\"b-mkdir\" type=\"button\" title=\"New folder\"><svg><use href=\"#i-new-folder\"/></svg><span>New folder</span></button>")
            .append("<button class=\"btn js-only\" id=\"b-select\" type=\"button\" title=\"Select items\" aria-pressed=\"false\"><svg><use href=\"#i-check\"/></svg><span>Select</span></button>")
        if (list.isNotEmpty()) {
            sb.append("<a class=\"btn\" id=\"b-zip\" href=\"?op=zip\" title=\"Download this folder as a .zip\"><svg><use href=\"#i-zip\"/></svg><span>Download all</span></a>")
        }
        sb.append("<span class=\"grow\"></span>")
            .append("<input class=\"filter js-only\" id=\"q\" type=\"search\" placeholder=\"Filter\" aria-label=\"Filter this folder\" autocomplete=\"off\">")
            .append("<button class=\"btn icon js-only\" id=\"b-hidden\" type=\"button\" title=\"Show hidden files\" aria-pressed=\"false\"><svg><use href=\"#i-eye\"/></svg></button>")
            .append("</div></header>\n")

        // ── Listing ──
        sb.append("<main><div class=\"head js-only\"><span class=\"c-ck\"></span>")
            .append("<button class=\"sort c-n\" data-k=\"n\" type=\"button\">Name</button>")
            .append("<button class=\"sort c-s\" data-k=\"s\" type=\"button\">Size</button>")
            .append("<button class=\"sort c-t\" data-k=\"t\" type=\"button\">Modified</button><span class=\"c-m\"></span></div>")
            .append("<ul id=\"list\" class=\"list\">\n")
        var dirs = 0
        var files = 0
        var total = 0L
        val base = encodePath(segs, true)
        for (e in list) {
            val kind = MimeTypes.kind(e.name, e.isDir)
            val href = base + encodeSegment(e.name) + if (e.isDir) "/" else ""
            if (e.isDir) dirs++ else { files++; total += e.size }
            val hidden = e.name.startsWith(".")
            sb.append("<li class=\"it").append(if (hidden) " hid" else "").append("\" data-n=\"").append(esc(e.name))
                .append("\" data-s=\"").append(e.size).append("\" data-t=\"").append(e.mtime)
                .append("\" data-d=\"").append(if (e.isDir) '1' else '0').append("\" data-k=\"").append(kind)
                .append("\" data-ro=\"").append(if (e.node.readOnly) '1' else '0').append("\">")
                .append("<input class=\"ck\" type=\"checkbox\" aria-label=\"Select ").append(esc(e.name)).append("\">")
                .append("<a class=\"nm\" href=\"").append(href).append("\"><svg class=\"ic k-").append(icon(kind))
                .append("\"><use href=\"#i-").append(icon(kind)).append("\"/></svg><span class=\"tx\">").append(esc(e.name)).append("</span>")
            if (top && e.node.readOnly) sb.append("<span class=\"chip\">Read-only</span>")
            sb.append("</a><span class=\"sz\">").append(if (e.isDir) "" else formatSize(e.size)).append("</span>")
                .append("<time class=\"tm\" datetime=\"").append(isoDate(e.mtime)).append("\">")
                .append(if (e.mtime > 0) localDate(e.mtime) else "").append("</time>")
                .append("<button class=\"mn js-only\" type=\"button\" aria-label=\"Actions for ").append(esc(e.name))
                .append("\"><svg><use href=\"#i-more\"/></svg></button></li>\n")
        }
        sb.append("</ul><p class=\"empty\"").append(if (list.isEmpty()) "" else " hidden").append(">")
            .append(if (top) "Nothing is shared yet." else "This folder is empty.").append("</p></main>\n")

        // ── Footer + the containers app.js fills ──
        val summary = buildString {
            append(dirs).append(if (dirs == 1) " folder" else " folders")
            append(" · ").append(files).append(if (files == 1) " file" else " files")
            if (files > 0) append(" · ").append(formatSize(total))
        }
        sb.append("<footer class=\"foot\"><span id=\"summary\">").append(summary).append("</span><span>Shared from Banners File Explorer</span></footer>\n")
            .append("<input type=\"file\" id=\"pick\" multiple hidden><input type=\"file\" id=\"pick-dir\" webkitdirectory multiple hidden>")
            .append("<div id=\"selbar\" class=\"selbar\" hidden></div><section id=\"uploads\" class=\"uploads\" hidden></section>")
            .append("<div id=\"drop\" class=\"drop\" hidden><div>Drop to upload to <b>").append(esc(title)).append("</b></div></div>")
            .append("<div id=\"viewer\" class=\"viewer\" hidden></div><div id=\"menu\" class=\"menu\" hidden></div><div id=\"toast\" class=\"toast\" hidden></div>\n")
            .append(SPRITE)
            .append("\n<script src=\"").append(ASSET_PREFIX).append("app.js?v=").append(assetVersion).append("\" defer></script>\n</body></html>\n")
        return sb.toString()
    }

    fun listingJson(node: VirtualFs.Node, entries: List<VirtualFs.Entry>): String {
        val sb = StringBuilder(256 + entries.size * 96)
        sb.append("{\"path\":").append(json(node.path))
            .append(",\"top\":").append(node is VirtualFs.Top)
            .append(",\"writable\":").append(node is VirtualFs.Real && !node.readOnly)
            .append(",\"entries\":[")
        sorted(entries).forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            sb.append("{\"name\":").append(json(e.name)).append(",\"dir\":").append(e.isDir)
                .append(",\"size\":").append(e.size).append(",\"mtime\":").append(e.mtime)
                .append(",\"readOnly\":").append(e.node.readOnly).append('}')
        }
        return sb.append("]}").toString()
    }

    /** A small standalone page for errors and the sign-in prompt's cancel case. */
    fun message(title: String, body: String, assetVersion: String): String =
        "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"color-scheme\" content=\"light dark\">" +
            "<title>" + esc(title) + " · Banners File Explorer</title><link rel=\"icon\" href=\"data:,\">" +
            "<link rel=\"stylesheet\" href=\"" + ASSET_PREFIX + "app.css?v=" + assetVersion + "\"></head>" +
            "<body class=\"msg\"><main class=\"msgbox\"><h1>" + esc(title) + "</h1><p>" + esc(body) + "</p>" +
            "<p><a class=\"btn\" href=\"/\">Back to shared folders</a></p></main></body></html>\n"

    private val HEX = "0123456789ABCDEF".toCharArray()

    // Material Symbols/Icons paths (Apache License 2.0), referenced by id from the page and app.js.
    private val SPRITE = """<svg xmlns="http://www.w3.org/2000/svg" style="display:none">""" +
        sym("folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z") +
        sym("file", "M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5L18.5 9H13z") +
        sym("image", "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z") +
        sym("video", "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z") +
        sym("audio", "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z") +
        sym("text", "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z") +
        sym("pdf", "M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8.5 7.5c0 .83-.67 1.5-1.5 1.5H9v2H7.5V7H10c.83 0 1.5.67 1.5 1.5v1zm5 2c0 .83-.67 1.5-1.5 1.5h-2.5V7H15c.83 0 1.5.67 1.5 1.5v3zm4-3H19v1h1.5V11H19v2h-1.5V7h3v1.5zM9 9.5h1v-1H9v1zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm10 5.5h1v-3h-1v3z") +
        sym("archive", "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z") +
        sym("apk", "M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85-.29-.15-.65-.06-.83.22l-1.88 3.24c-2.86-1.21-6.08-1.21-8.94 0L5.65 5.67c-.19-.29-.58-.38-.87-.2-.28.18-.37.54-.22.83L6.4 9.48C3.3 11.25 1.28 14.44 1 18h22c-.28-3.56-2.3-6.75-5.4-8.52zM7 15.25c-.69 0-1.25-.56-1.25-1.25s.56-1.25 1.25-1.25 1.25.56 1.25 1.25-.56 1.25-1.25 1.25zm10 0c-.69 0-1.25-.56-1.25-1.25s.56-1.25 1.25-1.25 1.25.56 1.25 1.25-.56 1.25-1.25 1.25z") +
        sym("up", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z") +
        sym("upload", "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z") +
        sym("folder-up", "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10zM8 13.01l1.41 1.41L11 12.84V17h2v-4.16l1.59 1.59L16 13.01 12.01 9 8 13.01z") +
        sym("new-folder", "M20 6h-8l-2-2H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-1 8h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z") +
        sym("check", "M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.11 0 2-.9 2-2V5c0-1.1-.89-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z") +
        sym("zip", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z") +
        sym("download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z") +
        sym("delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z") +
        sym("edit", "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z") +
        sym("more", "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z") +
        sym("close", "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z") +
        sym("prev", "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z") +
        sym("next", "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z") +
        sym("eye", "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z") +
        sym("open", "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z") +
        sym("lock", "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z") +
        sym("wifi", "M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z") +
        "</svg>"

    private fun sym(id: String, d: String) = """<symbol id="i-$id" viewBox="0 0 24 24"><path d="$d"/></symbol>"""
}
