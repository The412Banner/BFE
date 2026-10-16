package com.the412banner.bfe.cloud

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.the412banner.bfe.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/**
 * The rclone engine behind Cloud and Remote. It is NOT bundled in the APK (it would add ~15 MB to every
 * install and update); the first time either screen opens, the user is told what it is and how big it
 * is, and on their OK it is downloaded once from BFE's own GitHub release, checked against the SHA-256
 * pinned below, unpacked to `filesDir/bin/rclone` and run from there (exec from app storage is allowed
 * at targetSdk 28).
 *
 * The binary is a trimmed rclone built for Android (bionic, cgo) by `.github/workflows/build-rclone.yml`
 * from `rclone/main.go`. A new build means: run the workflow, publish its `.gz` under a new release tag,
 * and update the constants here (the old binary is then replaced on next open because its marker no
 * longer matches [BIN_SHA256]).
 *
 * Offline / testing: a copy of [ASSET_NAME] in the Download folder is offered instead of the network.
 */
object RcloneManager {
    private const val TAG = "BFE_RCLONE"

    const val VERSION = "v1.75.2"
    const val RELEASE_TAG = "rclone-v1.75.2-android-arm64"
    const val ASSET_NAME = "rclone-v1.75.2-android-arm64.gz"
    const val DOWNLOAD_URL = "https://github.com/The412Banner/BFE/releases/download/$RELEASE_TAG/$ASSET_NAME"
    /** The download (.gz), as produced by build-rclone.yml run 38976867583 (bit-identical to 38976010135). */
    const val GZ_SHA256 = "259868b5842e39200828cd853d7948339ab989aeb47381ee37dc360f7ceed34b"
    const val GZ_SIZE = 14_998_988L
    /** The unpacked executable. */
    const val BIN_SHA256 = "190151335ba679eda9b67bd17099e9af9fb6162a9f5bdd9a52c099b52ca2c6c2"
    const val BIN_SIZE = 47_616_352L

    sealed class State {
        object Unknown : State()
        object NotInstalled : State()
        data class Downloading(val bytes: Long, val total: Long, val fromLocal: Boolean) : State()
        object Verifying : State()
        object Installed : State()
        data class Failed(val reason: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Unknown)
    val state: StateFlow<State> get() = _state
    private var job: Job? = null

    fun binary(ctx: Context): File = File(ctx.filesDir, "bin/rclone")
    private fun marker(ctx: Context): File = File(ctx.filesDir, "bin/rclone.sha256")

    fun isInstalled(ctx: Context): Boolean {
        val bin = binary(ctx)
        return bin.isFile && bin.canExecute() && bin.length() == BIN_SIZE &&
            runCatching { marker(ctx).readText().trim() }.getOrNull() == BIN_SHA256
    }

    /** Re-reads the install state (cheap: a size check and a 64-byte marker). */
    fun refresh(ctx: Context) {
        val s = _state.value
        if (s is State.Downloading || s is State.Verifying) return
        _state.value = if (isInstalled(ctx)) State.Installed else if (s is State.Failed) s else State.NotInstalled
    }

    /** A copy of the release asset sitting in Download/, usable without the network. */
    fun localCopy(): File? = runCatching {
        @Suppress("DEPRECATION")
        File(Environment.getExternalStorageDirectory(), "Download/$ASSET_NAME").takeIf { it.isFile && it.length() == GZ_SIZE }
    }.getOrNull()

    fun install(ctx: Context, fromLocal: File? = null) {
        val s = _state.value
        if (s is State.Downloading || s is State.Verifying) return
        val app = ctx.applicationContext
        _state.value = State.Downloading(0L, GZ_SIZE, fromLocal != null)
        job = scope.launch {
            val dir = File(app.filesDir, "bin").apply { mkdirs() }
            val gzPart = File(dir, "rclone.gz.part")
            val binPart = File(dir, "rclone.part")
            try {
                val need = GZ_SIZE + BIN_SIZE + 8L * 1024 * 1024
                val free = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
                if (free < need) throw UserFacing("Not enough free space: rclone needs about ${mb(need)} MB while installing.")

                val gzSha = if (fromLocal != null) {
                    fromLocal.inputStream().use { copyHashing(it, gzPart, GZ_SIZE, true) }
                } else {
                    download(gzPart)
                }
                _state.value = State.Verifying
                if (gzSha != GZ_SHA256) {
                    Log.w(TAG, "download sha256 $gzSha != $GZ_SHA256")
                    throw UserFacing("The download didn't match its checksum, so it wasn't used. Try again.")
                }
                val binSha = GZIPInputStream(gzPart.inputStream().buffered(256 * 1024)).use { copyHashing(it, binPart, BIN_SIZE, false) }
                gzPart.delete()
                if (binSha != BIN_SHA256) throw UserFacing("The unpacked engine didn't match its checksum, so it wasn't used.")
                val bin = binary(app)
                bin.delete()
                if (!binPart.renameTo(bin)) throw IOException("rename failed")
                bin.setExecutable(true, true)
                bin.setReadable(true, true)
                selfTest(bin)
                marker(app).writeText(BIN_SHA256)
                Log.i(TAG, "rclone $VERSION installed (${bin.length()} bytes)")
                _state.value = State.Installed
            } catch (e: kotlinx.coroutines.CancellationException) {
                gzPart.delete(); binPart.delete()
                _state.value = State.NotInstalled
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "install failed", e)
                gzPart.delete(); binPart.delete()
                // Never leave a half-checked engine behind (it's only renamed into place once verified).
                runCatching { binary(app).delete(); marker(app).delete() }
                _state.value = State.Failed(
                    when (e) {
                        is UserFacing -> e.message ?: "Install failed"
                        is HttpStatus -> if (e.code == 404) "The rclone download isn't published yet (HTTP 404). Try again later." else "The download failed (HTTP ${e.code}). Try again."
                        else -> "The download didn't finish. Check your connection and try again."
                    }
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Deletes the engine (Cloud/Remote then offer the download again). Accounts stay configured. */
    fun uninstall(ctx: Context) {
        RcloneDaemon.stopNow()
        binary(ctx).delete()
        marker(ctx).delete()
        _state.value = State.NotInstalled
    }

    // ── internals ─────────────────────────────────────────────────────────
    private class UserFacing(msg: String) : Exception(msg)
    private class HttpStatus(val code: Int) : IOException("HTTP $code")

    private fun mb(bytes: Long) = (bytes + 1024 * 1024 - 1) / (1024 * 1024)

    private suspend fun download(dest: File): String {
        var target = URL(DOWNLOAD_URL)
        var conn: HttpURLConnection? = null
        // GitHub answers with a redirect to its object store; follow a few hops by hand.
        for (hop in 0 until 6) {
            val c = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "BFE/${BuildConfig.VERSION_NAME} (Android)")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("redirect without location")
                c.disconnect()
                target = URL(target, loc)
                continue
            }
            if (code !in 200..299) { c.disconnect(); throw HttpStatus(code) }
            conn = c
            break
        }
        val c = conn ?: throw IOException("too many redirects")
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: GZ_SIZE
            return c.inputStream.use { copyHashing(it, dest, total, true) }
        } finally {
            c.disconnect()
        }
    }

    /** Copies [input] to [dest] while hashing; reports progress when [report] (the download phase). */
    private suspend fun copyHashing(input: InputStream, dest: File, total: Long, report: Boolean): String {
        val md = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var lastEmit = 0L
        dest.outputStream().use { out ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                md.update(buf, 0, n)
                done += n
                if (report) {
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 200) {
                        lastEmit = now
                        _state.value = State.Downloading(done, total, (_state.value as? State.Downloading)?.fromLocal == true)
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Runs `rclone version` once so a binary this device can't execute fails here, not later. */
    private fun selfTest(bin: File) {
        val p = ProcessBuilder(bin.absolutePath, "version").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            throw UserFacing("rclone didn't start on this device.")
        }
        if (p.exitValue() != 0 || !out.contains(VERSION)) {
            Log.w(TAG, "self-test failed (${p.exitValue()}): $out")
            throw UserFacing("rclone couldn't run on this device: ${out.lineSequence().firstOrNull().orEmpty().take(120)}")
        }
    }
}
