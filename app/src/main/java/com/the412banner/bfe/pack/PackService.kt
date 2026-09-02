package com.the412banner.bfe.pack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.the412banner.bfe.MainActivity
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.unpack.ReadBuffer
import com.the412banner.bfe.unpack.SevenZip
import com.the412banner.bfe.unpack.UnpackManager
import java.io.File

/**
 * Foreground service that builds ONE archive to completion — the creation-side sibling of
 * [com.the412banner.bfe.unpack.UnpackService], with the same shape: a worker thread, a partial
 * wake lock so a long job survives screen-off, progress mirrored into [PackManager] (the File
 * Manager pill collects it) and an ongoing notification with Cancel. Cancel kills the 7zz
 * process(es) or trips the Java tar's cancel flag, and the partial archive is deleted.
 *
 * One pack at a time; it also refuses to start while an unpack is running (two native engines
 * hammering the same flash at once helps nobody).
 */
class PackService : Service() {

    companion object {
        private const val TAG = "PackService"
        private const val CHANNEL_ID = "pack_channel"
        private const val NOTIFICATION_ID = 9004

        const val ACTION_START = "com.the412banner.bfe.pack.START"
        const val ACTION_CANCEL = "com.the412banner.bfe.pack.CANCEL"
        const val EXTRA_FORMAT = "format"
        const val EXTRA_OUT = "out"
        const val EXTRA_INPUTS = "inputs"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_MMT = "mmt"
        const val EXTRA_PROFILE_JSON = "profileJson"
        const val EXTRA_JOB_ID = "jobId"

        @Volatile private var handle: SevenZipPack.Handle? = null
        @Volatile private var javaJob: TarZstPack.Job? = null
        @Volatile private var cancelled = false

        /** Why a start was refused, or null when the job was handed to the service. */
        fun busyReason(): String? = when {
            PackManager.current.isRunning -> "Another archive is already being created"
            UnpackManager.current.isRunning -> "Wait for the running unpack to finish first"
            else -> null
        }

        /**
         * Start building [out] from [inputs]. Returns the job id the caller can match against
         * [PackState.jobId] (e.g. to post-copy the result into a SAF/root folder on DONE).
         */
        fun start(
            ctx: Context,
            format: PackFormat,
            out: File,
            inputs: List<File>,
            level: PackLevel,
            password: String?,
            mmt: Int,
            profileJson: String?,
        ): Long {
            val app = ctx.applicationContext
            val jobId = System.currentTimeMillis()
            val i = Intent(app, PackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORMAT, format.name)
                putExtra(EXTRA_OUT, out.absolutePath)
                putStringArrayListExtra(EXTRA_INPUTS, ArrayList(inputs.map { it.absolutePath }))
                putExtra(EXTRA_LEVEL, level.name)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_MMT, mmt)
                putExtra(EXTRA_PROFILE_JSON, profileJson)
                putExtra(EXTRA_JOB_ID, jobId)
            }
            // Publish SCANNING synchronously so a second tap can't slip in before onStartCommand runs.
            PackManager.set(PackState(phase = PackPhase.SCANNING, jobId = jobId, archivePath = out.absolutePath, archiveName = out.name, format = format))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
            return jobId
        }

        fun cancel(ctx: Context) {
            val app = ctx.applicationContext
            app.startService(Intent(app, PackService::class.java).apply { action = ACTION_CANCEL })
        }
    }

    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:pack").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A sticky restart re-delivers null: the worker + engine died with the old process and the
        // static state reset, so there's nothing to resume — clear the notification and stop.
        if (intent == null || intent.action == null) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            stopNow()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_CANCEL -> {
                cancelled = true
                javaJob?.cancelled = true
                runCatching { handle?.destroy() }
                Log.i(TAG, "Cancel requested")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val format = runCatching { PackFormat.valueOf(intent.getStringExtra(EXTRA_FORMAT) ?: "") }.getOrNull()
                val out = intent.getStringExtra(EXTRA_OUT)?.let { File(it) }
                val inputs = intent.getStringArrayListExtra(EXTRA_INPUTS)?.map { File(it) } ?: emptyList()
                val level = runCatching { PackLevel.valueOf(intent.getStringExtra(EXTRA_LEVEL) ?: "") }.getOrDefault(PackLevel.NORMAL)
                val password = intent.getStringExtra(EXTRA_PASSWORD)
                val mmt = intent.getIntExtra(EXTRA_MMT, 1)
                val profileJson = intent.getStringExtra(EXTRA_PROFILE_JSON)
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, 0L)
                if (format == null || out == null || inputs.isEmpty()) { PackManager.set(PackState()); stopNow(); return START_NOT_STICKY }
                // One at a time. `start` pre-published this job's SCANNING state, so "running" here
                // means a DIFFERENT job (compare ids) — refuse it and leave the running one alone.
                val cur = PackManager.current
                if (cur.isRunning && cur.jobId != jobId) {
                    Log.w(TAG, "Pack already running; ignoring start")
                    return START_NOT_STICKY
                }
                startForegroundCompat(buildNotification(PackManager.current))
                runPack(format, out, inputs, level, password, mmt, profileJson, jobId)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun runPack(
        format: PackFormat, out: File, inputs: List<File>, level: PackLevel, password: String?,
        mmt: Int, profileJson: String?, jobId: Long,
    ) {
        cancelled = false
        handle = null
        javaJob = null
        val ctx = applicationContext
        Thread {
            acquireWakeLock()
            try {
                val startMs = SystemClock.elapsedRealtime()
                PackManager.set(
                    PackState(
                        phase = PackPhase.SCANNING, jobId = jobId, archivePath = out.absolutePath,
                        archiveName = out.name, format = format,
                    )
                )
                refresh()

                // Pre-scan: total bytes + file count are the progress denominator (7zz only reports
                // a percent, so speed/ETA are derived from percent × total; the Java engine reports
                // bytes directly).
                val planned = TarZstPack.planInputs(inputs, format.isWcp)
                val (totalBytes, totalFiles) = TarZstPack.measure(planned)
                PackManager.update { it.copy(phase = PackPhase.PACKING, totalBytes = totalBytes, totalFiles = totalFiles) }
                refresh()

                var lastTick = SystemClock.elapsedRealtime()
                var lastBytes = 0L
                var emaBps = 0L
                var lastUi = 0L
                var files = 0
                val size = totalBytes.coerceAtLeast(1L)

                fun push(bytes: Long, currentFile: String?, force: Boolean = false) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTick >= 500) {
                        val dt = (now - lastTick).coerceAtLeast(1)
                        val inst = ((bytes - lastBytes) * 1000 / dt).coerceAtLeast(0)
                        emaBps = if (emaBps == 0L) inst else (emaBps * 2 + inst) / 3
                        lastTick = now
                        lastBytes = bytes
                    }
                    // The Java engine calls per 256 KB chunk — throttle the StateFlow/notification.
                    if (!force && now - lastUi < 200) return
                    lastUi = now
                    val eta = if (emaBps > 0) (size - bytes) / emaBps else -1L
                    val pct = (bytes * 100 / size).toInt().coerceIn(0, 100)
                    PackManager.update {
                        it.copy(
                            phase = PackPhase.PACKING, percent = pct,
                            currentFile = currentFile ?: it.currentFile,
                            bytesProcessed = bytes.coerceIn(0, size), speedBps = emaBps, etaSeconds = eta,
                            elapsedMs = now - startMs, filesAdded = files,
                        )
                    }
                    refresh()
                }

                val result: SevenZip.Result = runCatching {
                    if (format.engine == PackEngine.JAVA_TAR_ZSTD) {
                        val job = TarZstPack.Job().also { javaJob = it }
                        if (cancelled) job.cancelled = true
                        val err = TarZstPack.create(
                            out, planned, level.zstd, mmt, profileJson,
                            object : TarZstPack.Listener {
                                override fun onProgress(bytesDone: Long, currentEntry: String?) = push(bytesDone, currentEntry)
                                override fun onFile(name: String) { files++ }
                            },
                            job,
                        )
                        when (err) {
                            null -> SevenZip.Result(0, "")
                            "cancelled" -> SevenZip.Result(255, "")
                            else -> SevenZip.Result(2, err)
                        }
                    } else {
                        SevenZipPack.create(
                            ctx, format, out, inputs, level, password, mmt, ReadBuffer.MB1.bytes,
                            object : SevenZip.Listener {
                                override fun onProgress(percent: Int, currentFile: String?) =
                                    push(size * percent / 100, currentFile, force = true)
                                override fun onFile(name: String) {
                                    files++
                                    PackManager.update { it.copy(currentFile = name, filesAdded = files) }
                                }
                            },
                            onHandle = { h -> handle = h; if (cancelled) h.destroy() },
                        )
                    }
                }.getOrElse { SevenZip.Result(-1, it.message ?: "engine failed") }

                handle = null
                javaJob = null
                val elapsed = SystemClock.elapsedRealtime() - startMs
                val ok = !cancelled && result.exitCode in 0..1 && out.isFile
                if (!ok) out.delete()   // never leave a truncated archive behind
                val terminal = when {
                    cancelled -> PackManager.current.copy(phase = PackPhase.CANCELLED, elapsedMs = elapsed, speedBps = 0, etaSeconds = -1)
                    ok -> PackManager.current.copy(
                        phase = PackPhase.DONE, percent = 100, elapsedMs = elapsed, filesAdded = files,
                        bytesProcessed = totalBytes, speedBps = 0, etaSeconds = 0, currentFile = null,
                        archiveSize = out.length(),
                    )
                    else -> PackManager.current.copy(
                        phase = PackPhase.ERROR, elapsedMs = elapsed, filesAdded = files,
                        errorTail = result.stderrTail.takeIf { it.isNotBlank() } ?: "7-Zip exit code ${result.exitCode}",
                    )
                }
                PackManager.set(terminal)
                postTerminalNotification(terminal)
                stopForeground(Service.STOP_FOREGROUND_DETACH)
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }.also { it.name = "pack-worker"; it.start() }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    // ── Notification ──

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Compressing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows archive creation progress and keeps it running in the background"
                setShowBadge(false)
            }
        )
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(s: PackState): Notification {
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, PackService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = when (s.phase) {
            PackPhase.SCANNING -> "Scanning files…"
            else -> buildString {
                append("${s.percent}%")
                if (s.speedBps > 0) append("  •  ${StringUtils.formatBytes(s.speedBps)}/s")
                if (s.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(s.etaSeconds * 1000)}")
            }
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Compressing ${s.archiveName}")
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, s.percent, s.phase == PackPhase.SCANNING)
            .setContentIntent(tapIntent())
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    private fun refresh() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(PackManager.current))
    }

    private fun postTerminalNotification(s: PackState) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val (title, text) = when (s.phase) {
            PackPhase.DONE -> "Created ${s.archiveName}" to
                "${s.filesAdded} files • ${StringUtils.formatBytes(s.archiveSize)} in ${StringUtils.humanDuration(s.elapsedMs)}"
            PackPhase.CANCELLED -> "Compression cancelled" to s.archiveName
            else -> "Compression failed" to (s.errorTail?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: s.archiveName)
        }
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tapIntent())
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun stopNow() {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
