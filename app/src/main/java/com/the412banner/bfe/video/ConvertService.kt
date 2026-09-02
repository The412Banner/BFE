package com.the412banner.bfe.video

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
import java.io.File

/**
 * Foreground service running one "Convert to MP4" job — a batch of inputs transcoded one after
 * another by the bundled ffmpeg ([Ffmpeg.convert]). Same shape as PackService: worker thread, partial
 * wake lock, ongoing notification with Cancel (destroys the ffmpeg process; the partial .mp4 is
 * deleted), progress into [ConvertManager] for the app-wide pill, one job at a time.
 */
class ConvertService : Service() {

    companion object {
        private const val TAG = "ConvertService"
        private const val CHANNEL_ID = "convert_channel"
        private const val NOTIFICATION_ID = 9006
        const val ACTION_START = "com.the412banner.bfe.video.START"
        const val ACTION_CANCEL = "com.the412banner.bfe.video.CANCEL"
        const val EXTRA_INPUTS = "inputs"
        const val EXTRA_OUTPUT_NAMES = "outputNames"
        const val EXTRA_OUTPUT_DIR = "outputDir"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_JOB_ID = "jobId"

        @Volatile private var proc: Process? = null
        @Volatile private var cancelled = false

        fun busyReason(): String? = if (ConvertManager.current.isRunning) "Another conversion is already running" else null

        fun start(ctx: Context, req: ConvertRequest): Long {
            val app = ctx.applicationContext
            val jobId = System.currentTimeMillis()
            ConvertManager.set(
                ConvertState(
                    phase = ConvertPhase.PROBING, jobId = jobId, outputDir = req.outputDir,
                    batchTotal = req.inputs.size, currentName = File(req.inputs.first()).name,
                )
            )
            val i = Intent(app, ConvertService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_INPUTS, ArrayList(req.inputs))
                putStringArrayListExtra(EXTRA_OUTPUT_NAMES, ArrayList(req.outputNames))
                putExtra(EXTRA_OUTPUT_DIR, req.outputDir)
                putExtra(EXTRA_QUALITY, req.quality.name)
                putExtra(EXTRA_RESOLUTION, req.resolution.name)
                putExtra(EXTRA_AUDIO, req.keepAudio)
                putExtra(EXTRA_JOB_ID, jobId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
            return jobId
        }

        fun cancel(ctx: Context) {
            val app = ctx.applicationContext
            app.startService(Intent(app, ConvertService::class.java).apply { action = ACTION_CANCEL })
        }
    }

    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Converting video", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows video conversion progress and keeps it running in the background"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            stopNow(); return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_CANCEL -> {
                cancelled = true
                runCatching { proc?.destroy() }
                Log.i(TAG, "Cancel requested")
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val inputs = intent.getStringArrayListExtra(EXTRA_INPUTS)?.map { File(it) } ?: emptyList()
                val names = intent.getStringArrayListExtra(EXTRA_OUTPUT_NAMES) ?: arrayListOf()
                val outDir = intent.getStringExtra(EXTRA_OUTPUT_DIR)?.let { File(it) }
                val quality = runCatching { ConvertQuality.valueOf(intent.getStringExtra(EXTRA_QUALITY) ?: "") }.getOrDefault(ConvertQuality.BALANCED)
                val resolution = runCatching { ConvertResolution.valueOf(intent.getStringExtra(EXTRA_RESOLUTION) ?: "") }.getOrDefault(ConvertResolution.KEEP)
                val audio = intent.getBooleanExtra(EXTRA_AUDIO, true)
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, 0L)
                if (inputs.isEmpty() || outDir == null || names.size != inputs.size) { ConvertManager.set(ConvertState()); stopNow(); return START_NOT_STICKY }
                val cur = ConvertManager.current
                if (cur.isRunning && cur.jobId != jobId) { Log.w(TAG, "Conversion already running; ignoring"); return START_NOT_STICKY }
                startForegroundCompat(buildNotification(ConvertManager.current))
                run(inputs, names, outDir, quality, resolution, audio)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun run(inputs: List<File>, names: List<String>, outDir: File, quality: ConvertQuality, resolution: ConvertResolution, audio: Boolean) {
        cancelled = false
        proc = null
        val ctx = applicationContext
        Thread {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:convert").apply { setReferenceCounted(false); acquire() }
            val startMs = SystemClock.elapsedRealtime()
            val total = inputs.size
            var done = 0
            var failed = 0
            var lastErr = ""
            val outputs = ArrayList<String>()
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            try {
                for ((idx, input) in inputs.withIndex()) {
                    if (cancelled) break
                    val output = File(outDir, names[idx])
                    ConvertManager.update {
                        it.copy(
                            phase = ConvertPhase.PROBING, batchIndex = idx + 1, currentName = input.name, percent = 0,
                            outTimeMs = 0, outputBytes = 0, speed = 0f, etaSeconds = -1, durationMs = 0,
                            overallPercent = idx * 100 / total, elapsedMs = SystemClock.elapsedRealtime() - startMs,
                        )
                    }
                    refresh()
                    val duration = Ffmpeg.probeDurationMs(ctx, input)
                    if (cancelled) break
                    ConvertManager.update { it.copy(phase = ConvertPhase.CONVERTING, durationMs = duration) }
                    refresh()
                    var lastUi = 0L
                    val r = runCatching {
                        Ffmpeg.convert(
                            ctx, input, output, quality, resolution, audio, threads,
                            object : Ffmpeg.Listener {
                                override fun onProgress(outTimeMs: Long, outputBytes: Long, speed: Float) {
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastUi < 400) return
                                    lastUi = now
                                    val pct = if (duration > 0) (outTimeMs * 100 / duration).toInt().coerceIn(0, 99) else 0
                                    val remainingMs = if (duration > 0 && speed > 0f) ((duration - outTimeMs) / speed).toLong() else -1L
                                    ConvertManager.update {
                                        it.copy(
                                            percent = pct, outTimeMs = outTimeMs, outputBytes = outputBytes, speed = speed,
                                            etaSeconds = if (remainingMs >= 0) remainingMs / 1000 else -1L,
                                            overallPercent = (idx * 100 + pct) / total,
                                            elapsedMs = now - startMs,
                                        )
                                    }
                                    refresh()
                                }
                            },
                            onProcess = { p -> proc = p; if (cancelled) p.destroy() },
                        )
                    }.getOrElse { Ffmpeg.Result(-1, it.message ?: "exec failed") }
                    proc = null
                    if (cancelled) { output.delete(); break }
                    if (r.exitCode == 0 && output.isFile && output.length() > 0) {
                        done++
                        outputs += output.absolutePath
                    } else {
                        failed++
                        output.delete()
                        lastErr = r.stderrTail.lineSequence().lastOrNull { it.isNotBlank() } ?: "ffmpeg exit ${r.exitCode}"
                        Log.w(TAG, "convert failed for ${input.name}: $lastErr")
                    }
                    ConvertManager.update { it.copy(done = done, failed = failed, outputs = outputs.toList(), errorTail = lastErr.takeIf { s -> s.isNotBlank() }) }
                }
                val elapsed = SystemClock.elapsedRealtime() - startMs
                val terminal = ConvertManager.current.copy(
                    phase = when {
                        cancelled -> ConvertPhase.CANCELLED
                        done == 0 -> ConvertPhase.ERROR
                        else -> ConvertPhase.DONE
                    },
                    percent = 100, overallPercent = 100, elapsedMs = elapsed, done = done, failed = failed,
                    outputs = outputs.toList(), speed = 0f, etaSeconds = -1,
                    errorTail = if (done == 0 && !cancelled) (lastErr.ifBlank { "ffmpeg failed" }) else lastErr.takeIf { it.isNotBlank() },
                )
                ConvertManager.set(terminal)
                postTerminal(terminal)
            } finally {
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
                wakeLock = null
                stopSelf()
            }
        }.also { it.name = "convert-worker"; it.start() }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        super.onDestroy()
    }

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(s: ConvertState): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ConvertService::class.java).apply { action = ACTION_CANCEL }, PendingIntent.FLAG_IMMUTABLE,
        )
        val title = (if (s.batchTotal > 1) "Converting ${s.batchIndex}/${s.batchTotal} — " else "Converting ") + s.currentName
        val body = when (s.phase) {
            ConvertPhase.PROBING -> "Reading…"
            else -> buildString {
                append("${s.percent}%")
                if (s.speed > 0f) append("  •  ${"%.1f".format(s.speed)}×")
                if (s.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(s.etaSeconds * 1000)}")
            }
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, s.overallPercent, s.phase == ConvertPhase.PROBING)
            .setContentIntent(tapIntent())
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    private fun refresh() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(ConvertManager.current))
    }

    private fun postTerminal(s: ConvertState) {
        val (title, text) = when (s.phase) {
            ConvertPhase.DONE -> "Converted ${s.done} video${if (s.done == 1) "" else "s"}" to
                ("${StringUtils.humanDuration(s.elapsedMs)}" + if (s.failed > 0) " • ${s.failed} failed" else "")
            ConvertPhase.CANCELLED -> "Conversion cancelled" to (if (s.done > 0) "${s.done} finished before cancel" else s.currentName)
            else -> "Conversion failed" to (s.errorTail ?: s.currentName)
        }
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(tapIntent())
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, n)
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun stopNow() { stopForeground(Service.STOP_FOREGROUND_REMOVE); stopSelf() }
}
