package com.the412banner.bfe.apk.bundle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.the412banner.bfe.MainActivity
import com.the412banner.bfe.storage.RootAccess
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

enum class BundleStage(val label: String) {
    IDLE(""),
    OBB("Copying game data"),
    WRITING("Sending to Android"),
    CONFIRM("Waiting for you to confirm"),
    INSTALLING("Installing"),
    DONE("Installed"),
    ERROR("Failed"),
    CANCELLED("Cancelled");

    val isRunning: Boolean get() = this == OBB || this == WRITING || this == CONFIRM || this == INSTALLING
}

data class BundleInstallState(
    val stage: BundleStage = BundleStage.IDLE,
    val jobId: Long = 0L,
    val title: String = "",
    val done: Long = 0L,
    val total: Long = 0L,
    val message: String? = null,
) {
    val isRunning: Boolean get() = stage.isRunning
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

object BundleInstallManager {
    private val _state = MutableStateFlow(BundleInstallState())
    val state: StateFlow<BundleInstallState> = _state.asStateFlow()
    val current: BundleInstallState get() = _state.value
    fun set(s: BundleInstallState) { _state.value = s }
    fun update(block: (BundleInstallState) -> BundleInstallState) { _state.value = block(_state.value) }
    fun clearIfTerminal() { if (!_state.value.isRunning) _state.value = BundleInstallState() }
}

/**
 * Installs a split-APK bundle with one PackageInstaller session (the same API the Play Store uses):
 * every chosen part is streamed straight from the bundle into the session, then Android asks the user
 * to confirm. XAPK game data (OBB) is copied first into Android/obb/<package>; Android lets an app
 * that may install packages write there, and Android sets the folder's owner when the app installs.
 * If that write is refused and BFE has root, the file is written as root with the folder's owner.
 * Foreground service + wake lock, like the other BFE jobs; the session result arrives in
 * [BundleInstallReceiver].
 */
class BundleInstallService : Service() {

    companion object {
        private const val TAG = "BundleInstall"
        const val CHANNEL_ID = "apk_job_channel"
        const val NOTIFICATION_ID = 9006
        private const val ACTION_START = "com.the412banner.bfe.bundle.START"
        private const val ACTION_CANCEL = "com.the412banner.bfe.bundle.CANCEL"
        private const val EXTRA_JOB = "job"

        @Volatile private var cancelled = false

        fun start(ctx: Context, source: File, title: String, packageName: String?, parts: List<Bundles.Part>, obbs: List<Bundles.Obb>) {
            val app = ctx.applicationContext
            val jobId = System.currentTimeMillis()
            val total = parts.sumOf { it.size } + obbs.sumOf { it.size }
            BundleInstallManager.set(BundleInstallState(BundleStage.WRITING, jobId, title, 0L, total))
            val job = JSONObject().apply {
                put("id", jobId); put("source", source.absolutePath); put("title", title)
                packageName?.let { put("pkg", it) }
                put("parts", JSONArray().apply { parts.forEach { p -> put(JSONObject().put("e", p.entry).put("s", p.size)) } })
                put("obbs", JSONArray().apply { obbs.forEach { o -> put(JSONObject().put("e", o.entry).put("s", o.size).put("t", o.target)) } })
            }
            val i = Intent(app, BundleInstallService::class.java).setAction(ACTION_START).putExtra(EXTRA_JOB, job.toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
        }

        fun cancel(ctx: Context) {
            cancelled = true
            val app = ctx.applicationContext
            runCatching { app.startService(Intent(app, BundleInstallService::class.java).setAction(ACTION_CANCEL)) }
        }

        internal fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "APK tools", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            }
        }

        internal fun tapIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        internal fun notifyText(ctx: Context, title: String, text: String, ongoing: Boolean, progress: Int?, content: PendingIntent? = null) {
            ensureChannel(ctx)
            val b = Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
                .setContentTitle(title).setContentText(text)
                .setOngoing(ongoing).setOnlyAlertOnce(true).setAutoCancel(!ongoing)
                .setContentIntent(content ?: tapIntent(ctx))
            if (progress != null) b.setProgress(100, progress, false)
            if (ongoing) {
                val cancel = PendingIntent.getService(
                    ctx, 2, Intent(ctx, BundleInstallService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
                )
                b.addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            }
            (ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, b.build())
        }
    }

    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> { cancelled = true; return START_NOT_STICKY }
            ACTION_START -> {
                val job = runCatching { JSONObject(intent.getStringExtra(EXTRA_JOB) ?: "") }.getOrNull()
                if (job == null) { stopSelf(); return START_NOT_STICKY }
                ensureChannel(this)
                val n = Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Installing ${job.optString("title")}").setContentText("Starting…")
                    .setOngoing(true).setContentIntent(tapIntent(this)).build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else startForeground(NOTIFICATION_ID, n)
                run(job)
                return START_NOT_STICKY
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun run(job: JSONObject) {
        cancelled = false
        Thread {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:bundle").apply { setReferenceCounted(false); acquire() }
            val jobId = job.getLong("id")
            val title = job.getString("title")
            val source = File(job.getString("source"))
            val pkg = job.optString("pkg").ifEmpty { null }
            val parts = job.getJSONArray("parts").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            val obbs = job.getJSONArray("obbs").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            val zip = if (source.isDirectory) null else runCatching { ZipFile(source) }.getOrNull()
            var session: PackageInstaller.Session? = null
            var sessionId = -1
            val writtenObbs = ArrayList<String>()
            var lastPct = -1
            fun progress(stage: BundleStage, done: Long) {
                BundleInstallManager.update { it.copy(stage = stage, done = done) }
                val total = BundleInstallManager.current.total
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (pct != lastPct) { lastPct = pct; notifyText(this, "Installing $title", "${stage.label}… $pct%", true, pct) }
            }
            fun open(entry: String): InputStream =
                if (zip == null) File(source, entry).inputStream()
                else zip.getInputStream(zip.getEntry(entry) ?: throw IOException("$entry is missing from the bundle"))
            try {
                if (zip == null && !source.isDirectory) throw IOException("Couldn't open ${source.name}")
                var done = 0L
                // 1. Game data first: Android fixes the folder's owner when the app installs.
                for (o in obbs) {
                    if (cancelled) throw InterruptedException()
                    val target = o.getString("t")
                    progress(BundleStage.OBB, done)
                    val base = done
                    val viaRoot = writeObb(target) { out ->
                        open(o.getString("e")).use { input -> copy(input, out) { n -> progress(BundleStage.OBB, base + n) } }
                    }
                    writtenObbs += (if (viaRoot) "R" else "D") + target
                    done += o.getLong("s")
                }
                // 2. One install session with every chosen part.
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    pkg?.let { setAppPackageName(it) }
                    setSize(parts.sumOf { it.getLong("s") })
                    if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE)
                }
                sessionId = installer.createSession(params)
                val s = installer.openSession(sessionId)
                session = s
                parts.forEachIndexed { i, p ->
                    if (cancelled) throw InterruptedException()
                    val entry = p.getString("e")
                    val size = p.getLong("s")
                    // Session file names must be unique; keep the part's own name for readable errors.
                    val name = "${i}_" + entry.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val base = done
                    s.openWrite(name, 0, size).use { out ->
                        open(entry).use { input -> copy(input, out) { n -> progress(BundleStage.WRITING, base + n) } }
                        s.fsync(out)
                    }
                    done += size
                    s.setStagingProgress(done.toFloat() / BundleInstallManager.current.total.coerceAtLeast(1))
                }
                if (cancelled) throw InterruptedException()
                BundleInstallManager.update { it.copy(stage = BundleStage.INSTALLING, done = it.total) }
                notifyText(this, "Installing $title", "Installing…", false, null)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(
                    this, sessionId, Intent(this, BundleInstallReceiver::class.java).putExtra(BundleInstallReceiver.EXTRA_JOB, jobId)
                        .putExtra(BundleInstallReceiver.EXTRA_TITLE, title)
                        .putExtra(BundleInstallReceiver.EXTRA_OBBS, writtenObbs.toTypedArray()),
                    flags,
                )
                s.commit(status.intentSender)
                s.close()
                session = null
            } catch (t: Throwable) {
                val wasCancel = cancelled || t is InterruptedException
                if (!wasCancel) Log.e(TAG, "bundle install failed", t)
                runCatching { session?.abandon() }
                if (session == null && sessionId >= 0) runCatching { packageManager.packageInstaller.abandonSession(sessionId) }
                // Game data staged for an install that never happened is removed again.
                ObbStaging.discard(writtenObbs)
                val msg = if (wasCancel) "Cancelled" else (t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
                BundleInstallManager.update { it.copy(stage = if (wasCancel) BundleStage.CANCELLED else BundleStage.ERROR, message = msg) }
                notifyText(this, if (wasCancel) "Install cancelled" else "Couldn't install $title", msg, false, null)
            } finally {
                runCatching { zip?.close() }
                wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
                wakeLock = null
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }.also { it.name = "bundle-install"; it.start() }
    }

    private fun copy(input: InputStream, out: OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(1 shl 20)
        var total = 0L
        while (true) {
            if (cancelled) throw InterruptedException()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            onBytes(total)
        }
    }

    /**
     * Writes one OBB as [ObbStaging.tempOf] next to <shared storage>/[target]; it replaces the real
     * name only once Android reports the install ([ObbStaging.commit]), so a failed or cancelled
     * install never touches game data that was already there. Direct first (an installer app may write
     * Android/obb); if Android refuses and root is available, as root into the same folder on /data/media
     * with the folder's owner and group. Returns true when root was used.
     */
    private fun writeObb(target: String, body: (OutputStream) -> Unit): Boolean {
        val dest = File(Environment.getExternalStorageDirectory(), target)
        val tmp = File(Environment.getExternalStorageDirectory(), ObbStaging.tempOf(target))
        val direct = runCatching {
            dest.parentFile?.mkdirs()
            tmp.outputStream().use(body)
        }
        if (direct.isSuccess) return false
        runCatching { tmp.delete() }
        val cause = direct.exceptionOrNull()
        if (cause is InterruptedException) throw cause
        Log.w(TAG, "direct OBB write refused: ${cause?.message}")
        if (!RootAccess.ensure()) {
            throw IOException("Android didn't let BFE write the game data into ${File(target).parent} (${cause?.message}). Untick it to install the app alone.")
        }
        val raw = "/data/media/0/" + ObbStaging.tempOf(target)
        val dir = File(raw).parent!!
        val q = { s: String -> "'" + s.replace("'", "'\\''") + "'" }
        // A new folder gets Android/obb's own layout (media_rw:ext_obb_rw, setgid); Android hands it to
        // the app's uid at install. An existing folder keeps its owner, and the file gets the same.
        val prep = Shell.cmd(
            "[ -d ${q(dir)} ] || { mkdir -p ${q(dir)} && chown 1023:1079 ${q(dir)} && chmod 2770 ${q(dir)}; }",
        ).exec()
        if (!prep.isSuccess) throw IOException("Couldn't create ${File(target).parent} as root")
        SuFileOutputStream.open(SuFile(raw)).use(body)
        val fix = Shell.cmd("chown \$(stat -c %u:%g ${q(dir)}) ${q(raw)} && chmod 0660 ${q(raw)} && restorecon ${q(raw)} 2>/dev/null; true").exec()
        if (!fix.isSuccess) throw IOException("Couldn't set the owner of ${dest.name}")
        return true
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        super.onDestroy()
    }
}

/** PackageInstaller session results for [BundleInstallService]. */
class BundleInstallReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_JOB = "job"
        const val EXTRA_TITLE = "title"
        const val EXTRA_OBBS = "obbs"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(EXTRA_JOB, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "app"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        fun mine(block: (BundleInstallState) -> BundleInstallState) =
            BundleInstallManager.update { if (it.jobId == jobId) block(it) else it }
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mine { it.copy(stage = BundleStage.CONFIRM) }
                // Started directly while BFE is on screen; the notification is the way back otherwise.
                val pi = PendingIntent.getActivity(context, jobId.toInt(), confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                BundleInstallService.notifyText(context, "Install $title", "Tap to confirm", false, null, pi)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val obbs = intent.getStringArrayExtra(EXTRA_OBBS)?.toList().orEmpty()
                val pending = goAsync()
                Thread {
                    try {
                        val err = ObbStaging.commit(obbs)
                        mine { it.copy(stage = BundleStage.DONE, done = it.total, message = err) }
                        BundleInstallService.notifyText(context, "Installed $title", err ?: "Done", false, null)
                    } finally { pending.finish() }
                }.start()
            }
            else -> {
                val aborted = status == PackageInstaller.STATUS_FAILURE_ABORTED
                val msg = friendly(status, raw)
                val obbs = intent.getStringArrayExtra(EXTRA_OBBS)?.toList().orEmpty()
                mine { it.copy(stage = if (aborted) BundleStage.CANCELLED else BundleStage.ERROR, message = msg) }
                BundleInstallService.notifyText(context, if (aborted) "Install cancelled" else "Couldn't install $title", msg, false, null)
                if (obbs.isNotEmpty()) {
                    val pending = goAsync()
                    Thread { try { ObbStaging.discard(obbs) } finally { pending.finish() } }.start()
                }
            }
        }
    }

    private fun friendly(status: Int, raw: String?): String {
        val what = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Blocked by the device"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicts with the installed app (different signature, or a newer version is installed). Uninstall it first."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Not compatible with this device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "The bundle is broken or a needed part is missing"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
            else -> "Install failed"
        }
        return if (raw.isNullOrBlank() || status == PackageInstaller.STATUS_FAILURE_ABORTED) what else "$what ($raw)"
    }
}

/**
 * Staged OBB files: "D<target>" was written directly, "R<target>" as root. The staged copy sits next
 * to the real name as ".<name>.bfe-part" until the install succeeds.
 */
internal object ObbStaging {
    fun tempOf(target: String): String {
        val f = File(target)
        return (f.parent?.let { "$it/" } ?: "") + ".${f.name}.bfe-part"
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Moves every staged file onto its real name; returns an error line, or null when all moved. */
    fun commit(items: List<String>): String? {
        val failed = ArrayList<String>()
        for (it in items) {
            val root = it.startsWith("R")
            val target = it.substring(1)
            val ok = if (!root) {
                val sd = Environment.getExternalStorageDirectory()
                val dest = File(sd, target)
                runCatching { if (dest.exists()) dest.delete(); File(sd, tempOf(target)).renameTo(dest) }.getOrDefault(false)
            } else {
                RootAccess.ensure() && Shell.cmd("mv -f ${q("/data/media/0/" + tempOf(target))} ${q("/data/media/0/$target")}").exec().isSuccess
            }
            if (!ok) failed += File(target).name
        }
        return if (failed.isEmpty()) null else "Installed, but the game data couldn't be put in place: ${failed.joinToString()}"
    }

    fun discard(items: List<String>) {
        for (it in items) {
            val target = it.substring(1)
            if (it.startsWith("R")) {
                if (RootAccess.ensure()) runCatching { Shell.cmd("rm -f ${q("/data/media/0/" + tempOf(target))}").exec() }
            } else runCatching { File(Environment.getExternalStorageDirectory(), tempOf(target)).delete() }
        }
    }
}
