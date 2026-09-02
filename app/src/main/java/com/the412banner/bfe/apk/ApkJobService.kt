package com.the412banner.bfe.apk

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
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Everything one APK job needs, intent-serialisable. */
data class ApkJobRequest(
    val kind: ApkJobKind,
    val input: String,
    val splits: List<String>,
    val output: String,
    val edits: ApkEdits?,
    val key: KeyRef,
    val schemes: SignSchemes,
    val installAfter: Boolean,
) {
    fun toJson(): String = JSONObject().apply {
        put("kind", kind.name); put("input", input); put("output", output); put("installAfter", installAfter)
        put("splits", org.json.JSONArray(splits))
        edits?.let { e ->
            put("edits", JSONObject().apply {
                e.packageName?.let { put("packageName", it) }
                e.label?.let { put("label", it) }
                e.versionCode?.let { put("versionCode", it) }
                e.versionName?.let { put("versionName", it) }
                e.minSdk?.let { put("minSdk", it) }
                e.targetSdk?.let { put("targetSdk", it) }
                e.debuggable?.let { put("debuggable", it) }
                e.allowBackup?.let { put("allowBackup", it) }
                e.extractNativeLibs?.let { put("extractNativeLibs", it) }
                e.iconImagePath?.let { put("icon", it) }
            })
        }
        put("key", JSONObject().apply {
            put("storePath", key.storePath); put("storeType", key.storeType); put("alias", key.alias)
            put("storePassword", key.storePassword); put("keyPassword", key.keyPassword); put("displayName", key.displayName)
        })
        put("v1", schemes.v1); put("v2", schemes.v2); put("v3", schemes.v3)
    }.toString()

    companion object {
        fun fromJson(s: String): ApkJobRequest {
            val o = JSONObject(s)
            val sp = o.optJSONArray("splits")
            val e = o.optJSONObject("edits")
            val k = o.getJSONObject("key")
            fun JSONObject.optIntOrNull(n: String): Int? = if (has(n)) getInt(n) else null
            fun JSONObject.optBoolOrNull(n: String): Boolean? = if (has(n)) getBoolean(n) else null
            fun JSONObject.optStrOrNull(n: String): String? = if (has(n)) getString(n) else null
            return ApkJobRequest(
                kind = ApkJobKind.valueOf(o.getString("kind")),
                input = o.getString("input"),
                splits = if (sp == null) emptyList() else (0 until sp.length()).map { sp.getString(it) },
                output = o.getString("output"),
                edits = e?.let {
                    ApkEdits(
                        it.optStrOrNull("packageName"), it.optStrOrNull("label"), it.optIntOrNull("versionCode"),
                        it.optStrOrNull("versionName"), it.optIntOrNull("minSdk"), it.optIntOrNull("targetSdk"),
                        it.optBoolOrNull("debuggable"), it.optBoolOrNull("allowBackup"), it.optBoolOrNull("extractNativeLibs"),
                        it.optStrOrNull("icon"),
                    )
                },
                key = KeyRef(
                    k.getString("storePath"), k.getString("storeType"), k.getString("alias"),
                    k.getString("storePassword"), k.getString("keyPassword"), k.getString("displayName"),
                ),
                schemes = SignSchemes(o.getBoolean("v1"), o.getBoolean("v2"), o.getBoolean("v3")),
                installAfter = o.getBoolean("installAfter"),
            )
        }
    }
}

/**
 * Foreground service for the APK clone / edit / sign pipeline — the third sibling of
 * UnpackService / PackService: worker thread, wake lock, ongoing notification with Cancel, one job
 * at a time, progress by stage into [ApkJobManager] (the File Manager's pill + result dialog read
 * it). Stages: parse → (merge splits) → rewrite → (icon) → write+align → sign → verify.
 * Cancel is polled between stages; a cancelled or failed job deletes its partial output.
 */
class ApkJobService : Service() {

    companion object {
        private const val TAG = "ApkJobService"
        private const val CHANNEL_ID = "apk_job_channel"
        private const val NOTIFICATION_ID = 9005
        const val ACTION_START = "com.the412banner.bfe.apk.START"
        const val ACTION_CANCEL = "com.the412banner.bfe.apk.CANCEL"
        const val EXTRA_REQUEST = "request"
        const val EXTRA_JOB_ID = "jobId"

        @Volatile private var cancelled = false

        fun busyReason(): String? = if (ApkJobManager.current.isRunning) "Another APK job is already running" else null

        fun start(ctx: Context, req: ApkJobRequest): Long {
            val app = ctx.applicationContext
            val jobId = System.currentTimeMillis()
            ApkJobManager.set(
                ApkJobState(
                    stage = ApkJobStage.PARSING, jobId = jobId, kind = req.kind,
                    sourceName = File(req.input).name, outputPath = req.output, installAfter = req.installAfter,
                )
            )
            val i = Intent(app, ApkJobService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REQUEST, req.toJson())
                putExtra(EXTRA_JOB_ID, jobId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
            return jobId
        }

        fun cancel(ctx: Context) {
            val app = ctx.applicationContext
            app.startService(Intent(app, ApkJobService::class.java).apply { action = ACTION_CANCEL })
        }
    }

    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "APK tools", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows APK clone / sign progress and keeps it running in the background"
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
            ACTION_CANCEL -> { cancelled = true; Log.i(TAG, "Cancel requested"); return START_NOT_STICKY }
            ACTION_START -> {
                val req = runCatching { ApkJobRequest.fromJson(intent.getStringExtra(EXTRA_REQUEST) ?: "") }.getOrNull()
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, 0L)
                if (req == null) { ApkJobManager.set(ApkJobState()); stopNow(); return START_NOT_STICKY }
                val cur = ApkJobManager.current
                if (cur.isRunning && cur.jobId != jobId) { Log.w(TAG, "APK job already running; ignoring"); return START_NOT_STICKY }
                startForegroundCompat(buildNotification(ApkJobManager.current))
                run(req, jobId)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun run(req: ApkJobRequest, jobId: Long) {
        cancelled = false
        Thread {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:apk").apply { setReferenceCounted(false); acquire() }
            val startMs = SystemClock.elapsedRealtime()
            val out = File(req.output)
            val unsigned = File(cacheDir, "apkjob-$jobId/unsigned.apk")
            fun stage(s: ApkJobStage) {
                ApkJobManager.update { it.copy(stage = s, elapsedMs = SystemClock.elapsedRealtime() - startMs) }
                refresh()
            }
            try {
                val fixups = ArrayList<String>()
                val warnings = ArrayList<String>()
                val extractNative: Boolean?
                val toSign: File
                if (req.kind == ApkJobKind.SIGN) {
                    stage(ApkJobStage.PARSING)
                    extractNative = runCatching { ApkRewriter.inspect(File(req.input)).extractNativeLibs }.getOrNull()
                    toSign = File(req.input)
                } else {
                    val r = ApkRewriter.rewrite(
                        File(req.input), req.splits.map { File(it) }, req.edits ?: ApkEdits(null, null, null, null, null, null, null, null, null, null),
                        unsigned, ::stage, { cancelled },
                    )
                    fixups += r.fixups; warnings += r.warnings
                    extractNative = r.extractNativeLibs
                    toSign = unsigned
                }
                if (cancelled) throw ApkRewriter.Cancelled()
                val pageAlign = if (extractNative == false) 16384 else 4096
                stage(ApkJobStage.ALIGNING)   // apksig re-aligns while it copies (see ApkSigning)
                stage(ApkJobStage.SIGNING)
                ApkSigning.sign(toSign, out, req.key, req.schemes, pageAlign)
                if (cancelled) throw ApkRewriter.Cancelled()
                stage(ApkJobStage.VERIFYING)
                val v = ApkSigning.verify(out)
                if (!v.verified) throw IOException("Signature didn't verify: ${v.errors.firstOrNull() ?: "unknown"}")
                val misaligned = runCatching { ApkSigning.misalignedEntries(out, pageAlign) }.getOrDefault(emptyList())
                if (misaligned.isNotEmpty()) warnings += "Alignment audit: ${misaligned.size} stored entries off-boundary (${misaligned.first()})"
                else fixups += "zip-aligned: stored entries on 4 bytes, native libs on ${pageAlign / 1024} KB pages"
                fixups += "Signed with ${req.key.displayName} (" + listOfNotNull(
                    "v1".takeIf { v.v1 }, "v2".takeIf { v.v2 }, "v3".takeIf { v.v3 },
                ).joinToString("+") + ")"
                val meta = runCatching { ApkRewriter.inspect(out) }.getOrNull()
                val report = ApkJobReport(
                    outputPath = out.absolutePath, fixups = fixups, warnings = warnings,
                    verified = v.verified, v1 = v.v1, v2 = v.v2, v3 = v.v3, signerDn = v.signerDn,
                    packageName = meta?.packageName, label = meta?.label, versionCode = meta?.versionCode, versionName = meta?.versionName,
                )
                ApkJobManager.update { it.copy(stage = ApkJobStage.DONE, elapsedMs = SystemClock.elapsedRealtime() - startMs, report = report) }
                postTerminal(ApkJobManager.current)
            } catch (c: ApkRewriter.Cancelled) {
                out.delete()
                ApkJobManager.update { it.copy(stage = ApkJobStage.CANCELLED, elapsedMs = SystemClock.elapsedRealtime() - startMs) }
                postTerminal(ApkJobManager.current)
            } catch (t: Throwable) {
                Log.e(TAG, "APK job failed", t)
                out.delete()
                val msg = when {
                    cancelled -> "cancelled"
                    else -> t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                }
                ApkJobManager.update {
                    it.copy(stage = if (cancelled) ApkJobStage.CANCELLED else ApkJobStage.ERROR, error = msg, elapsedMs = SystemClock.elapsedRealtime() - startMs)
                }
                postTerminal(ApkJobManager.current)
            } finally {
                unsigned.parentFile?.deleteRecursively()
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
                wakeLock = null
                stopSelf()
            }
        }.also { it.name = "apk-job"; it.start() }
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

    private fun buildNotification(s: ApkJobState): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ApkJobService::class.java).apply { action = ACTION_CANCEL }, PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (s.kind) { ApkJobKind.CLONE -> "Cloning"; ApkJobKind.EDIT -> "Editing"; ApkJobKind.SIGN -> "Signing" } + " ${s.sourceName}"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(s.stage.label + "…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (s.fraction * 100).toInt(), false)
            .setContentIntent(tapIntent())
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    private fun refresh() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(ApkJobManager.current))
    }

    private fun postTerminal(s: ApkJobState) {
        val (title, text) = when (s.stage) {
            ApkJobStage.DONE -> "APK ready" to "${File(s.outputPath).name}" + (s.report?.let { r -> " — ${r.packageName ?: ""} ${r.versionName ?: ""}" } ?: "")
            ApkJobStage.CANCELLED -> "APK job cancelled" to s.sourceName
            else -> "APK job failed" to (s.error ?: s.sourceName)
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
