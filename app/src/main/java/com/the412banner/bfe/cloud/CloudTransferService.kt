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
 * and conversion services. Nothing is ever overwritten: a name that already exists at the destination
 * gets " (1)" etc.; folders merge into an existing folder of the same name.
 */
class CloudTransferService : Service() {

    companion object {
        private const val TAG = "BFE_TRANSFER"
        private const val CHANNEL_ID = "cloud_transfer_channel"
        private const val NOTIFICATION_ID = 9008
        const val ACTION_START = "com.the412banner.bfe.cloud.TRANSFER_START"
        const val ACTION_CANCEL_ALL = "com.the412banner.bfe.cloud.TRANSFER_CANCEL_ALL"
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

            val result: String
            val jobId: Long
            when (item.kind) {
                TransferKind.DOWNLOAD -> {
                    val dir = File(item.localDir).apply { mkdirs() }
                    if (item.isDir) {
                        val dest = File(dir, item.name)
                        result = dest.absolutePath
                        jobId = api.startCopyDir(RcloneApi.fsOf(item.remote, item.remotePath), dest.absolutePath)
                    } else {
                        val dest = uniqueLocal(dir, item.name)
                        result = dest.absolutePath
                        jobId = api.startCopyFile(RcloneApi.fsOf(item.remote), item.remotePath, RcloneApi.LOCAL_FS, RcloneApi.localPath(dest.absolutePath))
                    }
                }
                TransferKind.UPLOAD -> {
                    if (item.isDir) {
                        result = RcloneApi.join(item.remoteDir, item.name)
                        jobId = api.startCopyDir(source, RcloneApi.fsOf(item.remote, result))
                    } else {
                        result = uniqueRemote(api, item.remote, item.remoteDir, item.name)
                        jobId = api.startCopyFile(RcloneApi.LOCAL_FS, RcloneApi.localPath(source), RcloneApi.fsOf(item.remote), result)
                    }
                }
            }

            val group = "job/$jobId"
            var stopSent = false
            while (true) {
                coroutineContext.ensureActive()
                delay(500)
                if (!stopSent && CloudTransfers.get(item.id)?.cancelRequested == true) {
                    api.rc.stopJob(jobId)
                    stopSent = true
                }
                val st = api.rc.jobStatus(jobId)
                runCatching { api.rc.stats(group) }.getOrNull()?.let { s ->
                    CloudTransfers.update(item.id) {
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
                    val cancelled = CloudTransfers.get(item.id)?.cancelRequested == true
                    CloudTransfers.update(item.id) {
                        when {
                            st.success -> it.copy(status = TransferStatus.DONE, bytes = maxOf(it.bytes, it.totalBytes), resultPath = result, speed = 0.0, etaSec = -1)
                            cancelled -> it.copy(status = TransferStatus.CANCELLED, speed = 0.0, etaSec = -1)
                            else -> it.copy(status = TransferStatus.FAILED, error = CloudSetup.friendly(st.error), speed = 0.0, etaSec = -1)
                        }
                    }
                    break
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "transfer ${item.name} failed", e)
            CloudTransfers.update(item.id) { it.copy(status = TransferStatus.FAILED, error = CloudSetup.friendly(e.message)) }
        } finally {
            staged?.parentFile?.deleteRecursively()
            refresh(force = true)
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
        val title = cur?.let { (if (it.kind == TransferKind.DOWNLOAD) "Downloading " else "Uploading ") + it.name } ?: "Starting transfers…"
        val text = buildString {
            if (cur != null) {
                if (cur.status == TransferStatus.PREPARING) append("Preparing…")
                else {
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
            .setSmallIcon(if (cur?.kind == TransferKind.UPLOAD) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_download)
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
            val verb = if (t.kind == TransferKind.DOWNLOAD) "Download" else "Upload"
            when (t.status) {
                TransferStatus.DONE -> "${verb}ed ${t.name}" to (t.resultPath ?: t.remote)
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
        nm.notify(
            NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(tapIntent())
                .build(),
        )
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
