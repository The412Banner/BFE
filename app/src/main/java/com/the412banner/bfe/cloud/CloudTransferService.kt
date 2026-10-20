package com.the412banner.bfe.cloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.the412banner.bfe.MainActivity
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * Foreground service (dataSync) that runs the [CloudTransfers] queue one item at a time through the
 * shared rcd: each item is an rclone job (`operations/copyfile` for a file, `sync/copy` for a folder)
 * whose stats are polled into the queue and an ongoing notification with "Cancel all". Holds a partial
 * wake lock and a Wi-Fi lock so a long transfer keeps going with the screen off, like the extraction
 * and conversion services. Downloads and uploads never overwrite: a name that already exists at the
 * destination gets " (1)" etc.; folders merge into an existing folder of the same name. A file-manager
 * paste ([TransferKind.COPY], also `operations/movefile` / `sync/move` for Cut) uses the target the
 * paste settled after asking about conflicts. A job that fails is re-run up to [MAX_ATTEMPTS] times;
 * whatever still failed can be retried from the summary notification or the transfers sheet.
 */
class CloudTransferService : Service() {

    companion object {
        private const val TAG = "BFE_TRANSFER"
        private const val CHANNEL_ID = "cloud_transfer_channel"
        private const val NOTIFICATION_ID = 9008
        const val ACTION_START = "com.the412banner.bfe.cloud.TRANSFER_START"
        const val ACTION_CANCEL_ALL = "com.the412banner.bfe.cloud.TRANSFER_CANCEL_ALL"
        const val ACTION_RETRY_FAILED = "com.the412banner.bfe.cloud.TRANSFER_RETRY_FAILED"
        /** Tries per item: the first run plus two re-runs after a failure. */
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5_000L
        private val loops = AtomicInteger()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var running = false
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Cloud transfers", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows cloud and remote upload/download progress"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                CloudTransfers.cancelAll()
                return START_NOT_STICKY
            }
            ACTION_RETRY_FAILED -> {
                // From the summary notification: queue the failed items again (that starts the loop).
                CloudTransfers.retryFailed(applicationContext)
                if (!synchronized(lock) { running } && CloudTransfers.items.value.none { it.isActive }) stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundCompat(buildNotification())
                kick()
                return START_NOT_STICKY
            }
        }
        // A restart after the process died: the queue lived in memory and is gone.
        if (!synchronized(lock) { running }) stopNow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun kick() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        scope.launch { loop() }
    }

    private suspend fun loop() {
        val holder = "transfers-${loops.incrementAndGet()}"
        RcloneDaemon.retain(holder)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:cloud-transfer").apply { setReferenceCounted(false); acquire() }
        @Suppress("DEPRECATION")
        val wifi = runCatching {
            (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bfe:cloud-transfer").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val finished = ArrayList<TransferItem>()
        try {
            while (true) {
                val item = synchronized(lock) { CloudTransfers.nextQueued().also { if (it == null) running = false } } ?: break
                runItem(item)
                CloudTransfers.get(item.id)?.let { finished += it; CloudTransfers.emitFinished(it) }
            }
        } finally {
            RcloneDaemon.release(holder)
            runCatching { if (wake.isHeld) wake.release() }
            runCatching { wifi?.let { if (it.isHeld) it.release() } }
            // A new batch that arrived while this one wound down owns the service now.
            val restarted = synchronized(lock) { running }
            if (!restarted) {
                summarize(finished)
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private suspend fun runItem(item: TransferItem) {
        val ctx = applicationContext
        var staged: File? = null
        try {
            val api = RcloneDaemon.api(ctx)
            var source = item.localPath
            if (item.kind == TransferKind.UPLOAD && source.isEmpty() && item.sourceUri != null) {
                CloudTransfers.update(item.id) { it.copy(status = TransferStatus.PREPARING) }
                refresh(force = true)
                val uri = Uri.parse(item.sourceUri)
                val copied = try {
                    stage(item)
                } finally {
                    // The picker's read grant was only kept for this copy.
                    runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                staged = copied ?: run {
                    CloudTransfers.update(item.id) { it.copy(status = TransferStatus.CANCELLED) }
                    return
                }
                source = staged.absolutePath
            }
            CloudTransfers.update(item.id) { it.copy(status = TransferStatus.RUNNING, bytes = 0, totalBytes = it.size) }
            refresh(force = true)

            // Where it lands is settled once, so a retry writes to the same place (and doesn't pick
            // "name (1)" because the first try left something behind).
            val result: String
            val start: () -> Long
            when (item.kind) {
                TransferKind.DOWNLOAD -> {
                    val dir = File(item.localDir).apply { mkdirs() }
                    if (item.isDir) {
                        val dest = File(dir, item.name)
                        result = dest.absolutePath
                        start = { api.startCopyDir(RcloneApi.fsOf(item.remote, item.remotePath), dest.absolutePath) }
                    } else {
                        val dest = uniqueLocal(dir, item.name)
                        result = dest.absolutePath
                        start = { api.startCopyFile(RcloneApi.fsOf(item.remote), item.remotePath, RcloneApi.LOCAL_FS, RcloneApi.localPath(dest.absolutePath)) }
                    }
                }
                TransferKind.UPLOAD -> {
                    if (item.isDir) {
                        result = RcloneApi.join(item.remoteDir, item.name)
                        start = { api.startCopyDir(source, RcloneApi.fsOf(item.remote, result)) }
                    } else {
                        result = uniqueRemote(api, item.remote, item.remoteDir, item.name)
                        start = { api.startCopyFile(RcloneApi.LOCAL_FS, RcloneApi.localPath(source), RcloneApi.fsOf(item.remote), result) }
                    }
                }
                TransferKind.COPY -> {
                    result = item.toLabel
                    val srcDir = RcloneApi.folderFs(item.srcFs, item.srcPath)
                    val dstDir = RcloneApi.folderFs(item.dstFs, item.dstPath)
                    start = when {
                        item.isDir && item.move -> ({ api.startMoveDir(srcDir, dstDir) })
                        item.isDir -> ({ api.startCopyDir(srcDir, dstDir) })
                        item.move -> ({ api.startMoveFile(item.srcFs, item.srcPath, item.dstFs, item.dstPath) })
                        else -> ({ api.startCopyFile(item.srcFs, item.srcPath, item.dstFs, item.dstPath) })
                    }
                }
            }

            // rclone's own retries only cover one request or one file; like the rclone command line, a
            // job that still failed is run again a couple of times. A folder copy then skips what already
            // arrived, so a retry only re-sends the files that failed.
            var attempt = 1
            while (true) {
                val outcome = runJob(api, item.id, start())
                if (outcome.success) {
                    CloudTransfers.update(item.id) {
                        it.copy(status = TransferStatus.DONE, bytes = maxOf(it.bytes, it.totalBytes), resultPath = result, speed = 0.0, etaSec = -1, failedFiles = 0)
                    }
                    break
                }
                if (outcome.cancelled) {
                    CloudTransfers.update(item.id) { it.copy(status = TransferStatus.CANCELLED, speed = 0.0, etaSec = -1) }
                    break
                }
                if (attempt >= MAX_ATTEMPTS) {
                    val why = CloudSetup.friendly(outcome.error)
                    ErrorLog.cloud(ctx, "Cloud transfer", remotesOf(item), outcome.error, why, logDetails(item, attempt, outcome.errors))
                    CloudTransfers.update(item.id) {
                        it.copy(
                            status = TransferStatus.FAILED, speed = 0.0, etaSec = -1, failedFiles = outcome.errors,
                            error = if (item.isDir && outcome.errors > 0) "${outcome.errors} file${if (outcome.errors == 1L) "" else "s"} failed: $why" else why,
                        )
                    }
                    break
                }
                attempt++
                Log.w(TAG, "${item.name}: try ${attempt - 1} failed (${outcome.error}); retrying")
                // Otherwise the re-run would pick up the same dead pooled SMB connections.
                if (RcloneApi.isDroppedConnection(outcome.error)) api.clearFsCache()
                CloudTransfers.update(item.id) { it.copy(attempt = attempt, speed = 0.0, etaSec = -1) }
                refresh(force = true)
                // A short back-off (a NAS waking up, Wi-Fi coming back); Cancel still works meanwhile.
                val until = System.currentTimeMillis() + RETRY_DELAY_MS * (attempt - 1)
                while (System.currentTimeMillis() < until && CloudTransfers.get(item.id)?.cancelRequested != true) delay(250)
                if (CloudTransfers.get(item.id)?.cancelRequested == true) {
                    CloudTransfers.update(item.id) { it.copy(status = TransferStatus.CANCELLED) }
                    break
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "transfer ${item.name} failed", e)
            val why = CloudSetup.friendly(e.message)
            ErrorLog.cloud(applicationContext, "Cloud transfer", remotesOf(item), e, why, logDetails(item, item.attempt, 0L))
            CloudTransfers.update(item.id) { it.copy(status = TransferStatus.FAILED, error = why) }
        } finally {
            staged?.parentFile?.deleteRecursively()
            refresh(force = true)
        }
    }

    /** The remotes [item] touches, by name (the error log writes their types, never the names). */
    private fun remotesOf(item: TransferItem): List<String> =
        (listOf(item.remote) + listOf(item.srcFs, item.dstFs).filter { it.endsWith(":") }.map { it.removeSuffix(":") })
            .filter { it.isNotBlank() }

    /** What the error log may say about [item]: kind and sizes, no names or paths. */
    private fun logDetails(item: TransferItem, attempts: Int, failedFiles: Long): List<String> {
        val ext = item.name.substringAfterLast('.', "").takeIf { !item.isDir && it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
        return listOf(
            "Transfer: ${item.verb.lowercase()} ${if (item.isDir) "folder" else "file" + (ext?.let { " (.${it.lowercase()})" } ?: "")}, " +
                "size ${item.size} B, ${item.bytes} B done, attempts $attempts" + (if (failedFiles > 0) ", $failedFiles files failed" else ""),
        )
    }

    /** How one rclone job ended: [errors] is rclone's count of files that failed in it. */
    private class JobOutcome(val success: Boolean, val cancelled: Boolean, val error: String, val errors: Long)

    /** Polls job [jobId] into item [id]'s progress until it finishes; stops it when Cancel is asked. */
    private suspend fun runJob(api: RcloneApi, id: Long, jobId: Long): JobOutcome {
        val group = "job/$jobId"
        var stopSent = false
        var errors = 0L
        while (true) {
            coroutineContext.ensureActive()
            delay(500)
            if (!stopSent && CloudTransfers.get(id)?.cancelRequested == true) {
                api.rc.stopJob(jobId)
                stopSent = true
            }
            val st = api.rc.jobStatus(jobId)
            runCatching { api.rc.stats(group) }.getOrNull()?.let { s ->
                errors = s.optLong("errors", errors)
                CloudTransfers.update(id) {
                    it.copy(
                        bytes = s.optLong("bytes", it.bytes),
                        totalBytes = s.optLong("totalBytes", -1L).takeIf { t -> t > 0 } ?: it.totalBytes,
                        speed = s.optDouble("speed", 0.0).takeIf { v -> !v.isNaN() } ?: 0.0,
                        etaSec = if (s.isNull("eta")) -1L else s.optLong("eta", -1L),
                        files = s.optLong("transfers", 0L),
                        totalFiles = s.optLong("totalTransfers", 0L),
                    )
                }
            }
            refresh()
            if (st.finished) {
                api.rc.deleteStats(group)
                val cancelled = CloudTransfers.get(id)?.cancelRequested == true
                return JobOutcome(st.success, !st.success && cancelled, st.error, errors)
            }
        }
    }

    /** Copies a picker Uri with no real path into the cache; null if cancelled meanwhile. */
    private suspend fun stage(item: TransferItem): File? {
        val dir = File(cacheDir, "cloud-upload/${item.id}").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, item.name)
        val input = contentResolver.openInputStream(Uri.parse(item.sourceUri)) ?: throw IllegalStateException("The file can't be read")
        input.use { src ->
            out.outputStream().use { dst ->
                val buf = ByteArray(1 shl 20)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    if (CloudTransfers.get(item.id)?.cancelRequested == true) { dir.deleteRecursively(); return null }
                    val n = src.read(buf)
                    if (n < 0) break
                    dst.write(buf, 0, n)
                    total += n
                    CloudTransfers.update(item.id) { it.copy(bytes = total, totalBytes = it.size) }
                    refresh()
                }
            }
        }
        return out
    }

    private fun splitName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
    }

    private fun uniqueLocal(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val (base, ext) = splitName(name)
        var i = 1
        while (f.exists()) f = File(dir, "$base (${i++})$ext")
        return f
    }

    private fun uniqueRemote(api: RcloneApi, remote: String, dir: String, name: String): String {
        var path = RcloneApi.join(dir, name)
        if (runCatching { api.stat(remote, path) }.getOrNull() == null) return path
        val (base, ext) = splitName(name)
        for (i in 1..50) {
            path = RcloneApi.join(dir, "$base ($i)$ext")
            if (runCatching { api.stat(remote, path) }.getOrNull() == null) return path
        }
        return path
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val items = CloudTransfers.items.value
        val cur = items.firstOrNull { it.status == TransferStatus.RUNNING || it.status == TransferStatus.PREPARING }
        val queued = items.count { it.status == TransferStatus.QUEUED }
        val title = cur?.let { it.verb + " " + it.name } ?: "Starting transfers…"
        val text = buildString {
            if (cur != null) {
                if (cur.status == TransferStatus.PREPARING) append("Preparing…")
                else {
                    if (cur.attempt > 1) append("Retrying (${cur.attempt}/$MAX_ATTEMPTS)  •  ")
                    append("${cur.percent}%")
                    if (cur.speed > 0) append("  •  ${StringUtils.formatBytes(cur.speed.toLong())}/s")
                    if (cur.etaSec >= 0) append("  •  ETA ${StringUtils.humanDuration(cur.etaSec * 1000)}")
                }
            }
            if (queued > 0) append(if (isEmpty()) "$queued queued" else "  •  $queued more queued")
        }
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, CloudTransferService::class.java).setAction(ACTION_CANCEL_ALL), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (cur?.kind == TransferKind.DOWNLOAD) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, cur?.percent ?: 0, cur == null || cur.totalBytes <= 0)
            .setContentIntent(tapIntent())
            .addAction(Notification.Action.Builder(null, "Cancel all", cancel).build())
            .build()
    }

    private fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotify < 1000) return
        lastNotify = now
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun summarize(done: List<TransferItem>) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (done.isEmpty()) { nm.cancel(NOTIFICATION_ID); return }
        val ok = done.count { it.status == TransferStatus.DONE }
        val failed = done.filter { it.status == TransferStatus.FAILED }
        val cancelled = done.count { it.status == TransferStatus.CANCELLED }
        val (title, text) = if (done.size == 1) {
            val t = done[0]
            val verb = when (t.kind) {
                TransferKind.DOWNLOAD -> "Download"
                TransferKind.UPLOAD -> "Upload"
                TransferKind.COPY -> if (t.move) "Move" else "Copy"
            }
            val past = when (t.kind) {
                TransferKind.COPY -> if (t.move) "Moved" else "Copied"
                else -> "${verb}ed"
            }
            when (t.status) {
                TransferStatus.DONE -> "$past ${t.name}" to (t.resultPath ?: t.remote)
                TransferStatus.CANCELLED -> "$verb cancelled" to t.name
                else -> "$verb failed" to "${t.name}: ${t.error ?: "error"}"
            }
        } else {
            "Transfers finished" to buildString {
                append("$ok done")
                if (failed.isNotEmpty()) append(" • ${failed.size} failed")
                if (cancelled > 0) append(" • $cancelled cancelled")
            }
        }
        AppSnackbar.show(applicationContext, if (done.size == 1) title else "$title: $text", long = failed.isNotEmpty())
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(tapIntent())
        // Failed ones can be run again from here (a folder copy then only sends what's still missing).
        if (failed.any { it.canRetry }) {
            val retry = PendingIntent.getService(
                this, 2, Intent(this, CloudTransferService::class.java).setAction(ACTION_RETRY_FAILED), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(Notification.Action.Builder(null, "Retry failed", retry).build())
        }
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun stopNow() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
