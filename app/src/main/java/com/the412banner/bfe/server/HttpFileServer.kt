package com.the412banner.bfe.server

import fi.iki.elonen.NanoHTTPD
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The browser side of "Access from network": one NanoHTTPD listener bound to one LAN address.
 * Pure JVM (no android.*).
 *
 * Routes (paths are virtual, see [VirtualFs]):
 *  - `GET folder/` — the web page (`?json` for a JSON listing, `?op=zip[&f=name...]` for a streamed
 *    .zip of the folder or of the chosen items; POST with a form body works too, for long selections)
 *  - `GET file` — the file, inline (`?dl` = as an attachment), with single-range `Range` support,
 *    ETag / Last-Modified revalidation and long lengths (no 2/4 GB limits)
 *  - `PUT file` — upload the request body (`?mtime=ms` keeps the timestamp, `?mkdirs` creates parent
 *    folders for folder uploads); written to a temp file, then renamed into place
 *  - `DELETE path`, `POST folder/?op=mkdir&name=`, `POST path?op=rename&to=`
 *
 * Every mutating request must carry an `X-BFE-Request` header. Browsers can't add a custom header
 * cross-site without a CORS preflight (which this server never approves), so another web page the
 * user visits can't drive uploads or deletes with the cached Basic-auth login.
 *
 * NanoHTTPD 2.3.1 quirks handled here: it sends the body of a fixed-length response even for HEAD
 * (so HEAD gets an empty stream with the real length), it never drains a request body serve() didn't
 * read (so such responses close the connection), and it would gzip every text/... response — which
 * breaks Range on text files — so only generated pages are compressed.
 */
class HttpFileServer(bindAddress: String, port: Int, private val host: ServerHost) : NanoHTTPD(bindAddress, port) {

    fun startServer() = start(SOCKET_TIMEOUT_MS, true)

    /** A generated page or JSON document: the only responses we let NanoHTTPD gzip. */
    private class Page(status: Response.IStatus, mime: String, bytes: ByteArray) :
        Response(status, mime, ByteArrayInputStream(bytes), bytes.size.toLong()) {
        val compressible = bytes.size > 1024
    }

    override fun useGzipWhenAccepted(r: Response): Boolean = r is Page && r.compressible

    private class Req(val s: IHTTPSession, val ip: String) {
        var bodyConsumed = false
        val method: Method get() = s.method
        val head: Boolean get() = s.method == Method.HEAD
        fun header(name: String): String? = s.headers[name]
        fun param(name: String): String? = s.parameters[name]?.firstOrNull()
        fun has(name: String): Boolean = s.parameters.containsKey(name)
        fun params(name: String): List<String> = s.parameters[name].orEmpty()
    }

    private val authFailures = ConcurrentHashMap<String, LongArray>()

    override fun serve(session: IHTTPSession): Response {
        val req = Req(session, session.remoteIpAddress ?: "?")
        val declared = session.headers["content-length"]?.trim()?.toLongOrNull() ?: 0L
        val r = try {
            route(req)
        } catch (e: Throwable) {
            text(Response.Status.INTERNAL_ERROR, "Server error: ${e.message ?: e.javaClass.simpleName}")
        }
        // A body we didn't read would be parsed as the next request on a kept-alive connection.
        if (declared > 0 && !req.bodyConsumed) r.closeConnection(true)
        r.addHeader("X-Content-Type-Options", "nosniff")
        return r
    }

    private fun route(req: Req): Response {
        val uri = req.s.uri ?: "/"
        if (uri.startsWith(HttpPages.ASSET_PREFIX)) return asset(req, uri.substring(HttpPages.ASSET_PREFIX.length))

        val creds = host.credentials
        // Without a password, refuse requests addressed to a DNS name: that's what a DNS-rebinding
        // page would send, while people type the IP shown on the phone (or a .local name).
        if (creds.anonymous && !hostAllowed(req.header("host"))) {
            return text(Response.Status.FORBIDDEN, "Open this server by its IP address, as shown on the phone.")
        }
        if (!creds.anonymous) authCheck(req, creds)?.let { return it }
        host.activity.seen(req.ip)

        val fs = host.fs
        val segs = fs.segments(uri) ?: return text(Response.Status.BAD_REQUEST, "Invalid path: \"..\" isn't allowed.")
        return when (req.method) {
            Method.GET, Method.HEAD -> get(req, fs, segs)
            Method.PUT -> put(req, fs, segs)
            Method.DELETE -> delete(req, fs, segs)
            Method.POST -> post(req, fs, segs)
            Method.OPTIONS -> empty(Response.Status.NO_CONTENT).also { it.addHeader("Allow", "GET, HEAD, PUT, POST, DELETE, OPTIONS") }
            else -> text(Response.Status.METHOD_NOT_ALLOWED, "Method not allowed")
        }
    }

    // ── Auth ──

    private fun authCheck(req: Req, creds: Credentials): Response? {
        val now = System.currentTimeMillis()
        val rec = authFailures[req.ip]
        if (rec != null && now - rec[1] < LOCKOUT_WINDOW_MS && rec[0] >= MAX_AUTH_FAILURES) {
            return text(Response.Status.TOO_MANY_REQUESTS, "Too many wrong passwords. Wait a minute and try again.")
        }
        val h = req.header("authorization")
        if (h != null && h.regionMatches(0, "Basic ", 0, 6, ignoreCase = true)) {
            val decoded = runCatching { String(Base64.getDecoder().decode(h.substring(6).trim()), Charsets.UTF_8) }.getOrNull()
            val colon = decoded?.indexOf(':') ?: -1
            if (decoded != null && colon >= 0 && creds.matches(decoded.substring(0, colon), decoded.substring(colon + 1))) {
                authFailures.remove(req.ip)
                return null
            }
            // Only a wrong password counts; the browser's first, credential-less request doesn't.
            val r = authFailures.compute(req.ip) { _, old ->
                if (old == null || now - old[1] >= LOCKOUT_WINDOW_MS) longArrayOf(1, now) else old.also { it[0]++ }
            }
            if (r != null && r[0] == MAX_AUTH_FAILURES.toLong()) host.activity.event(req.ip, "HTTP", "Blocked for a minute after wrong passwords")
        }
        val page = HttpPages.message("Sign in required", "Enter the user name and password shown in BFE on the phone.", assetVersion)
        return Page(Response.Status.UNAUTHORIZED, MIME_HTML_UTF8, page.toByteArray(Charsets.UTF_8)).also {
            it.addHeader("WWW-Authenticate", "Basic realm=\"BFE\", charset=\"UTF-8\"")
        }
    }

    private fun hostAllowed(hostHeader: String?): Boolean {
        val h = hostHeader?.trim()?.lowercase() ?: return true
        if (h.startsWith("[")) return true
        val name = h.substringBefore(':')
        return IPV4.matches(name) || name == "localhost" || name.endsWith(".local")
    }

    // ── GET / HEAD ──

    private fun get(req: Req, fs: VirtualFs, segs: List<String>): Response {
        val node = fs.resolve(segs) ?: return notFound()
        if (fs.isDir(node)) {
            if (!req.s.uri.endsWith("/")) {
                val q = req.s.queryParameterString?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
                return redirect(HttpPages.encodePath(segs, true) + q)
            }
            if (req.param("op") == "zip") return zip(req, fs, node, req.params("f"))
            val entries = fs.list(node) ?: return text(Response.Status.FORBIDDEN, "This folder can't be read.")
            if (req.has("json")) return page(req, Response.Status.OK, MIME_JSON, HttpPages.listingJson(node, entries))
            return page(req, Response.Status.OK, MIME_HTML_UTF8, HttpPages.listing(fs, node, segs, entries, assetVersion)).also {
                it.addHeader("Cache-Control", "no-store")
            }
        }
        val real = node as VirtualFs.Real
        if (!real.file.isFile) return notFound()
        return serveFile(req, real)
    }

    private fun serveFile(req: Req, node: VirtualFs.Real): Response {
        val f = node.file
        val len = f.length()
        val mtime = f.lastModified()
        val etag = "\"" + len.toString(16) + "-" + mtime.toString(16) + "\""
        val mime = MimeTypes.forName(f.name)
        val lastModified = httpDate(mtime)

        val inm = req.header("if-none-match")
        val ims = req.header("if-modified-since")?.let { parseHttpDate(it) }
        if ((inm != null && (inm == "*" || inm.split(',').any { it.trim().removePrefix("W/") == etag })) ||
            (inm == null && ims != null && mtime / 1000 <= ims / 1000)
        ) {
            return empty(Response.Status.NOT_MODIFIED).also { it.addHeader("ETag", etag); it.addHeader("Last-Modified", lastModified) }
        }

        var start = 0L
        var end = len - 1
        var partial = false
        val range = req.header("range")
        val ifRange = req.header("if-range")
        val rangeApplies = ifRange == null || ifRange.trim() == etag || parseHttpDate(ifRange) == mtime / 1000 * 1000
        if (range != null && rangeApplies) {
            when (val r = parseRange(range, len)) {
                RangeResult.Ignore -> {}
                RangeResult.Unsatisfiable -> return text(Response.Status.RANGE_NOT_SATISFIABLE, "Requested range not satisfiable").also {
                    it.addHeader("Content-Range", "bytes */$len")
                    it.addHeader("Accept-Ranges", "bytes")
                }
                is RangeResult.Span -> { start = r.start; end = r.end; partial = true }
            }
        }
        val count = if (len == 0L) 0L else end - start + 1
        val status = if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK

        val body: InputStream = if (req.head || count == 0L) {
            ByteArrayInputStream(ByteArray(0))
        } else {
            val fin = FileInputStream(f)
            if (start > 0) fin.channel.position(start)
            host.activity.transferStarted()
            val complete = start == 0L && count == len
            Tracked(fin) { sent ->
                host.activity.transferEnded()
                if (start == 0L && sent > 0) {
                    host.activity.event(req.ip, "HTTP", (if (complete && sent >= len) "Downloaded " else "Opened ") + node.path)
                }
            }
        }
        val r = newFixedLengthResponse(status, mime, body, count)
        r.addHeader("Accept-Ranges", "bytes")
        r.addHeader("ETag", etag)
        r.addHeader("Last-Modified", lastModified)
        r.addHeader("Cache-Control", "no-cache")
        if (partial) r.addHeader("Content-Range", "bytes $start-$end/$len")
        r.addHeader("Content-Disposition", disposition(if (req.has("dl")) "attachment" else "inline", f.name))
        // A shared .html/.svg must not run as this site (it could call the upload/delete endpoints).
        if (MimeTypes.isActive(mime)) r.addHeader("Content-Security-Policy", "sandbox")
        return r
    }

    private sealed class RangeResult {
        object Ignore : RangeResult()
        object Unsatisfiable : RangeResult()
        class Span(val start: Long, val end: Long) : RangeResult()
    }

    /** One "bytes=" range; several ranges or anything malformed is ignored (the whole file is sent). */
    private fun parseRange(header: String, len: Long): RangeResult {
        val h = header.trim()
        if (!h.regionMatches(0, "bytes=", 0, 6, ignoreCase = true)) return RangeResult.Ignore
        val spec = h.substring(6).trim()
        if (spec.contains(',')) return RangeResult.Ignore
        val dash = spec.indexOf('-')
        if (dash < 0) return RangeResult.Ignore
        val a = spec.substring(0, dash).trim()
        val b = spec.substring(dash + 1).trim()
        return if (a.isEmpty()) {
            val n = b.toLongOrNull() ?: return RangeResult.Ignore
            if (n <= 0 || len == 0L) RangeResult.Unsatisfiable else RangeResult.Span((len - n).coerceAtLeast(0), len - 1)
        } else {
            val s = a.toLongOrNull() ?: return RangeResult.Ignore
            val e = if (b.isEmpty()) len - 1 else (b.toLongOrNull() ?: return RangeResult.Ignore)
            when {
                s >= len -> RangeResult.Unsatisfiable
                e < s -> RangeResult.Ignore
                else -> RangeResult.Span(s, e.coerceAtMost(len - 1))
            }
        }
    }

    /** Counts what NanoHTTPD actually sent; [onDone] runs once, when it closes the stream. */
    private class Tracked(input: InputStream, private val onDone: (Long) -> Unit) : FilterInputStream(input) {
        private var sent = 0L
        private var closed = false
        override fun read(): Int = super.read().also { if (it >= 0) sent++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) sent += it }
        override fun close() {
            if (closed) return
            closed = true
            try { super.close() } finally { onDone(sent) }
        }
    }

    // ── Zip (streamed: nothing is staged on the phone) ──

    private fun zip(req: Req, fs: VirtualFs, dir: VirtualFs.Node, selected: List<String>): Response {
        val items: List<Pair<VirtualFs.Real, String>>
        val dirName = (dir as? VirtualFs.Real)?.name ?: "Shared folders"
        val zipName: String
        if (selected.isEmpty()) {
            when (dir) {
                is VirtualFs.Top -> {
                    items = fs.list(dir).orEmpty().map { it.node to it.name }
                    zipName = "$dirName.zip"
                }
                is VirtualFs.Real -> {
                    items = listOf(dir to dir.name)
                    zipName = "$dirName.zip"
                }
            }
        } else {
            items = selected.distinct().mapNotNull { n -> fs.child(dir, n)?.takeIf { it.file.exists() }?.let { it to n } }
            if (items.isEmpty()) return notFound()
            zipName = if (items.size == 1) items[0].second + ".zip" else "$dirName.zip"
        }
        if (req.head) {
            return newFixedLengthResponse(Response.Status.OK, "application/zip", ByteArrayInputStream(ByteArray(0)), 0)
        }

        val pin = PipedInputStream(PIPE_BYTES)
        val pout = PipedOutputStream(pin)
        host.activity.transferStarted()
        Thread({
            var ok = false
            try {
                ZipOutputStream(BufferedOutputStream(pout, 64 * 1024)).use { zos ->
                    for ((node, name) in items) addToZip(zos, node.mount, node.file, name)
                }
                ok = true
            } catch (e: IOException) {
                // The client cancelled or went away; the pipe closing below ends the response.
            } finally {
                runCatching { pout.close() }
                host.activity.transferEnded()
                if (ok) host.activity.event(req.ip, "HTTP", "Downloaded $zipName")
            }
        }, "bfe-http-zip").apply { isDaemon = true; start() }

        val r = newChunkedResponse(Response.Status.OK, "application/zip", pin)
        r.addHeader("Content-Disposition", disposition("attachment", zipName))
        r.addHeader("Cache-Control", "no-store")
        return r
    }

    private fun addToZip(zos: ZipOutputStream, mount: VirtualFs.Mount, f: File, path: String) {
        val a = VirtualFs.lstat(f) ?: return
        if (a.isSymbolicLink) {
            if (!mount.contains(f)) return
            if (f.isDirectory) return   // never follow folder links: no loops, no duplicated trees
        }
        if (f.isDirectory) {
            zos.putNextEntry(ZipEntry("$path/").also { it.time = f.lastModified() })
            zos.closeEntry()
            f.listFiles()?.sortedBy { it.name }?.forEach { addToZip(zos, mount, it, "$path/${it.name}") }
        } else if (f.isFile) {
            val input = try { FileInputStream(f) } catch (e: IOException) { return }   // unreadable: skipped
            input.use {
                // Already-compressed media/archives are stored as-is; everything else gets fast deflate.
                zos.setLevel(if (MimeTypes.isIncompressible(f.name)) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED)
                zos.putNextEntry(ZipEntry(path).also { e -> e.time = f.lastModified() })
                it.copyTo(zos, COPY_BYTES)
                zos.closeEntry()
            }
        }
    }

    // ── Writes ──

    private fun writeDenied(node: VirtualFs.Node?): Response? = when {
        node == null -> notFound()
        node !is VirtualFs.Real -> text(Response.Status.FORBIDDEN, "Choose a shared folder first.")
        node.readOnly -> text(Response.Status.FORBIDDEN, "This folder is read-only.")
        else -> null
    }

    private fun put(req: Req, fs: VirtualFs, segs: List<String>): Response {
        if (req.header("x-bfe-request") == null) return csrfDenied()
        val node = fs.resolve(segs)
        writeDenied(node)?.let { return it }
        node as VirtualFs.Real
        if (node.isMountRoot) return text(Response.Status.CONFLICT, "Give the upload a file name.")
        val target = node.file
        if (target.isDirectory) return text(Response.Status.CONFLICT, "A folder with that name already exists.")
        val len = req.header("content-length")?.trim()?.toLongOrNull()
            ?: return text(Response.Status.LENGTH_REQUIRED, "Content-Length is required.")
        val parent = target.parentFile ?: return notFound()
        if (!parent.isDirectory) {
            if (!req.has("mkdirs")) return text(Response.Status.CONFLICT, "The destination folder doesn't exist.")
            if (!parent.mkdirs() && !parent.isDirectory) return text(Response.Status.INTERNAL_ERROR, "Couldn't create the folder.")
        }
        val free = parent.usableSpace
        if (free in 1 until len) return text(STATUS_INSUFFICIENT_STORAGE, "Not enough free space on the phone.")

        val tmp = File(parent, ".bfe-upload-" + java.lang.Long.toHexString(System.nanoTime()) + ".part")
        host.activity.transferStarted()
        try {
            val input = req.s.inputStream
            val buf = ByteArray(COPY_BYTES)
            var remaining = len
            tmp.outputStream().use { out ->
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) throw IOException("The upload was interrupted.")
                    out.write(buf, 0, n)
                    remaining -= n
                }
            }
            req.bodyConsumed = true
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("Couldn't save ${target.name}.")
            }
            req.param("mtime")?.toLongOrNull()?.takeIf { it > 0 }?.let { target.setLastModified(it) }
            host.activity.event(req.ip, "HTTP", "Uploaded ${node.path}")
            return page(req, Response.Status.CREATED, MIME_JSON, "{\"name\":${HttpPages.json(target.name)},\"size\":$len}")
        } catch (e: IOException) {
            tmp.delete()
            return text(Response.Status.INTERNAL_ERROR, e.message ?: "Upload failed.")
        } finally {
            host.activity.transferEnded()
        }
    }

    private fun delete(req: Req, fs: VirtualFs, segs: List<String>): Response {
        if (req.header("x-bfe-request") == null) return csrfDenied()
        val node = fs.resolve(segs)
        writeDenied(node)?.let { return it }
        node as VirtualFs.Real
        if (node.isMountRoot) return text(Response.Status.FORBIDDEN, "A shared folder itself can't be deleted from here.")
        if (VirtualFs.lstat(node.file) == null) return notFound()
        if (!VirtualFs.deleteTree(node.file)) return text(Response.Status.INTERNAL_ERROR, "Some items couldn't be deleted.")
        host.activity.event(req.ip, "HTTP", "Deleted ${node.path}")
        return empty(Response.Status.NO_CONTENT)
    }

    private fun post(req: Req, fs: VirtualFs, segs: List<String>): Response {
        // Form bodies (a long "download selected as .zip") are small; NanoHTTPD parses them into params.
        if (req.header("content-type")?.startsWith("application/x-www-form-urlencoded", true) == true) {
            runCatching { req.s.parseBody(HashMap()) }
            req.bodyConsumed = true
        }
        val node = fs.resolve(segs) ?: return notFound()
        val op = req.param("op")
        if (op == "zip") {
            if (!fs.isDir(node)) return notFound()
            return zip(req, fs, node, req.params("f"))
        }
        if (req.header("x-bfe-request") == null) return csrfDenied()
        when (op) {
            "mkdir" -> {
                writeDenied(node)?.let { return it }
                if (!fs.isDir(node)) return notFound()
                val name = req.param("name")?.trim().orEmpty()
                if (!VirtualFs.isValidName(name)) return text(Response.Status.BAD_REQUEST, "That isn't a valid folder name.")
                val child = fs.child(node, name) ?: return text(Response.Status.BAD_REQUEST, "That isn't a valid folder name.")
                if (child.file.exists()) return text(Response.Status.CONFLICT, "\"$name\" already exists.")
                if (!child.file.mkdir()) return text(Response.Status.INTERNAL_ERROR, "Couldn't create the folder.")
                host.activity.event(req.ip, "HTTP", "Created folder ${child.path}")
                return page(req, Response.Status.CREATED, MIME_JSON, "{\"name\":${HttpPages.json(name)}}")
            }
            "rename" -> {
                writeDenied(node)?.let { return it }
                node as VirtualFs.Real
                if (node.isMountRoot) return text(Response.Status.FORBIDDEN, "A shared folder itself can't be renamed from here.")
                if (VirtualFs.lstat(node.file) == null) return notFound()
                val to = req.param("to")?.trim().orEmpty()
                if (!VirtualFs.isValidName(to)) return text(Response.Status.BAD_REQUEST, "That isn't a valid name.")
                val dest = File(node.file.parentFile, to)
                if (!node.mount.contains(dest)) return text(Response.Status.BAD_REQUEST, "That isn't a valid name.")
                if (dest.exists() && !dest.name.equals(node.file.name, ignoreCase = true)) {
                    return text(Response.Status.CONFLICT, "\"$to\" already exists.")
                }
                if (!node.file.renameTo(dest)) return text(Response.Status.INTERNAL_ERROR, "Couldn't rename.")
                host.activity.event(req.ip, "HTTP", "Renamed ${node.path} to $to")
                return page(req, Response.Status.OK, MIME_JSON, "{\"name\":${HttpPages.json(to)}}")
            }
        }
        return text(Response.Status.BAD_REQUEST, "Unknown action.")
    }

    // ── Static assets (the page's CSS/JS, bundled as Java resources) ──

    private fun asset(req: Req, name: String): Response {
        val a = ASSETS[name] ?: return notFound()
        val bytes = a.second ?: return notFound()
        return page(req, Response.Status.OK, a.first, bytes).also { it.addHeader("Cache-Control", "public, max-age=604800, immutable") }
    }

    // ── Helpers ──

    private fun page(req: Req, status: Response.IStatus, mime: String, body: String): Response =
        page(req, status, mime, body.toByteArray(Charsets.UTF_8))

    private fun page(req: Req, status: Response.IStatus, mime: String, bytes: ByteArray): Response =
        if (req.head) newFixedLengthResponse(status, mime, ByteArrayInputStream(ByteArray(0)), bytes.size.toLong())
        else Page(status, mime, bytes)

    private fun text(status: Response.IStatus, msg: String): Response =
        newFixedLengthResponse(status, MIME_TEXT_UTF8, msg + "\n")

    private fun empty(status: Response.IStatus): Response =
        newFixedLengthResponse(status, null, ByteArrayInputStream(ByteArray(0)), 0)

    private fun notFound() = text(Response.Status.NOT_FOUND, "Not found")

    private fun csrfDenied() = text(Response.Status.FORBIDDEN, "Use the BFE web page for changes (missing X-BFE-Request header).")

    private fun redirect(location: String): Response =
        newFixedLengthResponse(Response.Status.REDIRECT, MIME_TEXT_UTF8, "Moved\n").also { it.addHeader("Location", location) }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 60_000
        private const val PIPE_BYTES = 256 * 1024
        private const val COPY_BYTES = 256 * 1024
        private const val MAX_AUTH_FAILURES = 10
        private const val LOCKOUT_WINDOW_MS = 60_000L
        private const val MIME_HTML_UTF8 = "text/html; charset=utf-8"
        private const val MIME_TEXT_UTF8 = "text/plain; charset=utf-8"
        private const val MIME_JSON = "application/json; charset=utf-8"
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

        private val STATUS_INSUFFICIENT_STORAGE = object : Response.IStatus {
            override fun getDescription() = "507 Insufficient Storage"
            override fun getRequestStatus() = 507
        }

        private fun resource(name: String): ByteArray? =
            HttpFileServer::class.java.getResourceAsStream("web/$name")?.use { it.readBytes() }

        /** name -> (Content-Type, bytes); loaded once from the bundled Java resources. */
        private val ASSETS: Map<String, Pair<String, ByteArray?>> by lazy {
            mapOf(
                "app.css" to ("text/css; charset=utf-8" to resource("app.css")),
                "app.js" to ("text/javascript; charset=utf-8" to resource("app.js")),
            )
        }

        /** Cache-buster for the asset URLs: changes whenever the bundled CSS/JS changes. */
        val assetVersion: String by lazy {
            val crc = CRC32()
            ASSETS.values.forEach { it.second?.let { b -> crc.update(b) } }
            java.lang.Long.toHexString(crc.value)
        }

        private fun httpFormat() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        fun httpDate(ms: Long): String = httpFormat().format(Date(ms))

        fun parseHttpDate(s: String): Long? = runCatching { httpFormat().parse(s.trim())?.time }.getOrNull()

        /** Content-Disposition with an ASCII fallback plus the exact UTF-8 name (RFC 6266 / 5987). */
        fun disposition(type: String, name: String): String {
            val ascii = buildString {
                for (c in name) append(if (c.code in 0x20..0x7e && c != '"' && c != '\\' && c != ';') c else '_')
            }
            return "$type; filename=\"$ascii\"; filename*=UTF-8''${HttpPages.encodeSegment(name)}"
        }
    }
}
