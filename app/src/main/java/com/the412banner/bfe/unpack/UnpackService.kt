package com.the412banner.bfe.unpack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.the412banner.bfe.MainActivity
import com.the412banner.bfe.UnpackArchiveActivity
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.util.ErrorLog
import com.the412banner.bfe.util.IncomingFile
import java.io.File

/**
 * Foreground service that runs one archive extraction to completion, surviving the app going to
 * background for the hour-or-two a big repack takes. It drives [SevenZip.extract] on a worker
 * thread, mirrors progress into [UnpackManager] (which the Compose screen collects) and into an
 * ongoing notification with a Cancel action, and kills the 7zz process on cancel.
 *
 * Modelled on the download foreground service; kept separate because a
 * download and an unpack can legitimately run at once and want distinct notifications.
 */
class UnpackService : Service() {

    companion object {
        private const val TAG = "UnpackService"
        private const val CHANNEL_ID = "unpack_channel"
        private const val NOTIFICATION_ID = 9003
        private const val COPYING = "Copying into BFE…"

        const val ACTION_START = "com.the412banner.bfe.unpack.START"
        const val ACTION_CANCEL = "com.the412banner.bfe.unpack.CANCEL"
        // Background "Extract to ./<Archive name>/" requests (see QuickExtractActivity) + live controls.
        const val ACTION_QUICK = "com.the412banner.bfe.unpack.QUICK"
        const val ACTION_PAUSE = "com.the412banner.bfe.unpack.PAUSE"
        const val ACTION_RESUME = "com.the412banner.bfe.unpack.RESUME"
        const val ACTION_CANCEL_ALL = "com.the412banner.bfe.unpack.CANCEL_ALL"
        const val EXTRA_ARCHIVE = "archive"
        // GOG DLC batch: several InnoSetup installers (base + DLC setups) extracted back-to-back into
        // one destination. When present it supersedes EXTRA_ARCHIVE; element 0 is the primary/base.
        const val EXTRA_ARCHIVES = "archives"
        const val EXTRA_DEST = "dest"
        const val EXTRA_MMT = "mmt"
        const val EXTRA_BUFFER = "buffer"
        const val EXTRA_IS_INNO = "isInno"
        const val EXTRA_TOTAL_SIZE = "totalSize"
        const val EXTRA_ENGINE = "engine"   // "7z" | "inno" | "unarc" | "extra" (ExtraFormats)
        // OTA payload.bin: the partitions to write out (engine "extra"); absent = every extractable one.
        const val EXTRA_PARTS = "parts"

        // Process-static so the notification's Cancel action can reach the running process even if
        // onStartCommand hasn't re-published `instance` yet.
        @Volatile private var proc: Process? = null
        @Volatile private var cancelled = false

        // One job runs at a time; background requests that arrive meanwhile wait here (in order) and
        // are planned only when their turn comes. [busy] is owned by the running job: set when one
        // starts, cleared only by [onJobFinished] when nothing is left — always under [lock].
        private class Queued(val name: String, val path: String?, val uri: Uri?)
        private val queue = ArrayDeque<Queued>()
        private val lock = Any()
        @Volatile private var busy = false
        @Volatile private var paused = false
        // Each finished / failed / refused job keeps its own notification; the ongoing one moves on.
        private val terminalIds = java.util.concurrent.atomic.AtomicInteger(9100)

        // Pause = SIGSTOP the engine process (7zz / innoextract / unarc / the ExtraFormats tools),
        // resume = SIGCONT. The in-app EGG / OTA decoders wait on [paused] instead.
        private const val SIGCONT = 18
        private const val SIGSTOP = 19
        private val ENGINE_BINARIES = listOf("lib7zz.so", "libinnoextract.so", "libunarc.so") + ExtraFormats.TOOL_BINARIES

        fun isBusy(): Boolean = busy

        /**
         * Queues [uri] (a file another app handed over) for a background extraction with default
         * settings; progress, controls and the result live in notifications only. The read grant on
         * [uri] is passed on to the service with the intent.
         */
        fun enqueue(ctx: Context, uri: Uri) {
            val i = Intent(ctx, UnpackService::class.java).apply {
                action = ACTION_QUICK
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun start(
            ctx: Context, archive: String, dest: String, mmt: Int, bufferBytes: Int, isInno: Boolean, totalSize: Long,
            engine: String = "7z", parts: List<String>? = null,
        ) {
            val app = ctx.applicationContext
            val i = Intent(app, UnpackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ARCHIVE, archive)
                putExtra(EXTRA_DEST, dest)
                putExtra(EXTRA_MMT, mmt)
                putExtra(EXTRA_BUFFER, bufferBytes)
                putExtra(EXTRA_IS_INNO, isInno)
                putExtra(EXTRA_TOTAL_SIZE, totalSize)
                putExtra(EXTRA_ENGINE, engine)
                parts?.let { putStringArrayListExtra(EXTRA_PARTS, ArrayList(it)) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
        }

        /**
         * Extract several InnoSetup installers ([archives], base game first, then DLC setups) one after
         * another into the SAME [dest] — the GOG DLC auto-batch. [totalSize] is the combined `.bin`
         * payload of the whole folder so the one progress bar climbs across the entire batch.
         */
        fun startBatch(ctx: Context, archives: List<String>, dest: String, totalSize: Long) {
            val app = ctx.applicationContext
            val i = Intent(app, UnpackService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_ARCHIVES, ArrayList(archives))
                putExtra(EXTRA_ARCHIVE, archives.firstOrNull() ?: "")
                putExtra(EXTRA_DEST, dest)
                putExtra(EXTRA_MMT, 1)
                putExtra(EXTRA_BUFFER, ReadBuffer.MB1.bytes)
                putExtra(EXTRA_IS_INNO, true)
                putExtra(EXTRA_TOTAL_SIZE, totalSize)
                putExtra(EXTRA_ENGINE, "inno")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
        }

        fun cancel(ctx: Context) {
            val app = ctx.applicationContext
            app.startService(Intent(app, UnpackService::class.java).apply { action = ACTION_CANCEL })
        }
    }

    // Held for the whole extraction so the CPU keeps running with the screen off — screen-off CPU
    // suspend is the main reason a long background extraction appears to "pause". Balanced by a
    // finally in the worker thread AND onDestroy, so it can never leak.
    @Volatile private var wakeLock: PowerManager.WakeLock? = null
    // stopSelf(id) only stops when no newer start arrived meanwhile (a request queued at the last moment).
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:unpack").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    /** (file count, total bytes) of everything under [dir]; (0, 0) if it can't be walked. */
    private fun measureDir(dir: File): Pair<Int, Long> = runCatching {
        var c = 0; var b = 0L
        dir.walkTopDown().forEach { if (it.isFile) { c++; b += it.length() } }
        c to b
    }.getOrDefault(0 to 0L)

    /**
     * A running destination-size poller plus the baseline it subtracts. [baseCount]/[baseBytes] are
     * what was ALREADY in the destination when the job started, so progress reflects only what this
     * job adds — see [startDestPoller].
     */
    private class DestPoll(
        val polling: java.util.concurrent.atomic.AtomicBoolean,
        val thread: Thread,
        val baseCount: Int,
        val baseBytes: Long,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // START_STICKY restart re-delivers a null intent. The worker thread and the 7zz process died
        // with the old process (7-Zip extraction isn't resumable), and our process-static state reset
        // to IDLE, so there is nothing to resume — clear any stale notification and stop cleanly. The
        // wake lock + battery-optimisation exemption are what prevent this kill in the first place.
        if (intent == null || intent.action == null) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            stopNow()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_CANCEL, ACTION_CANCEL_ALL -> {
                if (intent.action == ACTION_CANCEL_ALL) synchronized(lock) { queue.clear() }
                cancelled = true
                runCatching { proc?.destroy() }
                // A stopped process only acts on the kill once it runs again.
                if (paused) { signalEngines(SIGCONT); paused = false }
                Log.i(TAG, "Cancel requested (${intent.action})")
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                if (busy && !paused) {
                    paused = true
                    signalEngines(SIGSTOP)
                    releaseWakeLock()
                    refresh()
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                if (paused) {
                    acquireWakeLock()
                    signalEngines(SIGCONT)
                    paused = false
                    refresh()
                }
                return START_NOT_STICKY
            }
            ACTION_QUICK -> {
                val uri = intent.data
                if (uri == null) {
                    if (!busy) stopNow()
                    return START_NOT_STICKY
                }
                val path = if (uri.scheme == "file") uri.path else null
                val name = path?.let { File(it).name } ?: IncomingFile.meta(this, uri).name
                synchronized(lock) {
                    // Foreground within seconds of startForegroundService, whether it runs now or waits.
                    startForegroundCompat(buildNotification(
                        if (busy) UnpackManager.current else UnpackState(phase = UnpackPhase.LISTING, archiveName = name),
                    ))
                    queue.addLast(Queued(name, path, uri))
                    if (busy) refresh() else runNextQueued()
                }
                return START_STICKY
            }
            ACTION_START -> {
                val archive = intent.getStringExtra(EXTRA_ARCHIVE)
                val dest = intent.getStringExtra(EXTRA_DEST)
                val mmt = intent.getIntExtra(EXTRA_MMT, 1)
                val buffer = intent.getIntExtra(EXTRA_BUFFER, ReadBuffer.MB1.bytes)
                val isInno = intent.getBooleanExtra(EXTRA_IS_INNO, false)
                val totalSize = intent.getLongExtra(EXTRA_TOTAL_SIZE, 0L)
                val engine = intent.getStringExtra(EXTRA_ENGINE) ?: "7z"
                val parts = intent.getStringArrayListExtra(EXTRA_PARTS)
                // A GOG DLC batch arrives as EXTRA_ARCHIVES (base first); a normal job as EXTRA_ARCHIVE.
                val archives = intent.getStringArrayListExtra(EXTRA_ARCHIVES)
                    ?.map { File(it) }?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(archive?.let { File(it) })
                if (archives.isEmpty() || dest == null) { stopNow(); return START_NOT_STICKY }
                // Refuse a second concurrent job — one at a time, like the DownloadCoordinator.
                synchronized(lock) {
                    if (busy || UnpackManager.current.isRunning) {
                        Log.w(TAG, "Unpack already running; ignoring start")
                        return START_NOT_STICKY
                    }
                    busy = true
                }
                startForegroundCompat(buildNotification(UnpackManager.current.copy(
                    phase = UnpackPhase.LISTING, archiveName = archives.first().name,
                )))
                runExtraction(archives, File(dest), mmt, buffer, isInno, totalSize, engine, parts)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun runExtraction(
        archives: List<File>, destDir: File, mmt: Int, buffer: Int, isInno: Boolean, totalSize: Long, engine: String,
        parts: List<String>? = null,
    ) {
        cancelled = false
        paused = false
        val ctx = applicationContext
        // The primary (base-game) installer drives the state's name/path; the rest are DLC siblings
        // extracted into the same destDir after it (inno batch only).
        val archive = archives.first()
        val batchTotal = if (isInno) archives.size else 1
        // Speed/ETA and the reported size track the DATA the engine reads. For an InnoSetup installer
        // that is the Setup-*.bin payload total (passed in), not the small Setup.exe.
        val dataSize = if (totalSize > 0) totalSize else archive.length()
        Thread {
            acquireWakeLock()
            try {
            val startMs = SystemClock.elapsedRealtime()

            UnpackManager.set(
                UnpackState(
                    phase = UnpackPhase.LISTING,
                    archivePath = archive.absolutePath,
                    archiveName = archive.name,
                    destPath = destDir.absolutePath,
                    archiveSize = dataSize,
                    isInno = isInno,
                    engine = engine,
                    batchTotal = batchTotal,
                )
            )
            refresh()

            // engine: "unarc" = FreeArc native, "inno" = innoextract (standard-Inno/GOG), "extra" = the
            // ExtraFormats engines (lzip/lzop/LZ4/ECM/ALZ/EGG/ACE/LZX/OTA payload), else 7-Zip.
            val isExtra = engine == ExtraFormats.ENGINE
            val extraProbe = if (isExtra) ExtraFormats.probe(ctx, archive) else null
            val info = if (isInno || isExtra) null else SevenZip.list(ctx, archive)
            UnpackManager.update {
                it.copy(
                    phase = UnpackPhase.EXTRACTING,
                    archiveType = when {
                        engine == "unarc" -> "FreeArc repack"
                        isInno -> "InnoSetup installer"
                        isExtra -> extraProbe?.typeLabel
                        else -> info?.type
                    },
                )
            }
            refresh()

            // Speed/ETA: the engine reports percent, not bytes, so processed-bytes = percent/100 *
            // source size — genuine read throughput of the source, smoothed with a light EMA.
            var lastTick = SystemClock.elapsedRealtime()
            var lastBytes = 0L
            var emaBps = 0L
            var files = 0
            val size = dataSize.coerceAtLeast(1L)

            fun pushProgress(percent: Int, currentFile: String?) {
                val now = SystemClock.elapsedRealtime()
                val bytes = (size * percent / 100).coerceIn(0, size)
                if (now - lastTick >= 500) {
                    val dt = (now - lastTick).coerceAtLeast(1)
                    val inst = ((bytes - lastBytes) * 1000 / dt).coerceAtLeast(0)
                    emaBps = if (emaBps == 0L) inst else (emaBps * 2 + inst) / 3
                    lastTick = now
                    lastBytes = bytes
                }
                val eta = if (emaBps > 0) (size - bytes) / emaBps else -1L
                UnpackManager.update {
                    it.copy(
                        phase = UnpackPhase.EXTRACTING, percent = percent,
                        currentFile = currentFile ?: it.currentFile,
                        bytesProcessed = bytes, speedBps = emaBps, etaSeconds = eta,
                        elapsedMs = now - startMs, filesExtracted = files,
                    )
                }
                refresh()
            }

            // The ExtraFormats engines report bytes against their own total (input bytes for the
            // single-stream tools, output bytes for EGG / the destination pollers, payload bytes for an
            // OTA); they can fire per chunk and from a poller thread, so repaint only when the percent
            // moves or every 500 ms.
            val byteLock = Any()
            var lastPaint = 0L
            var lastPct = -1
            fun pushBytes(done: Long, total: Long) = synchronized(byteLock) {
                val now = SystemClock.elapsedRealtime()
                val tot = total.coerceAtLeast(1L)
                val bytes = done.coerceIn(0L, tot)
                val pct = (bytes * 100 / tot).toInt()
                if (pct == lastPct && now - lastPaint < 500) return@synchronized
                if (now - lastTick >= 500) {
                    val inst = ((bytes - lastBytes) * 1000 / (now - lastTick).coerceAtLeast(1)).coerceAtLeast(0)
                    emaBps = if (emaBps == 0L) inst else (emaBps * 2 + inst) / 3
                    lastTick = now
                    lastBytes = bytes
                }
                lastPct = pct
                lastPaint = now
                val eta = if (emaBps > 0) (tot - bytes) / emaBps else -1L
                UnpackManager.update {
                    it.copy(
                        phase = UnpackPhase.EXTRACTING, percent = pct, bytesProcessed = bytes,
                        speedBps = emaBps, etaSeconds = eta, elapsedMs = now - startMs,
                    )
                }
                refresh()
            }

            // Destination-size polling shared by the FreeArc and innoextract paths (their engines don't
            // report a usable overall percent). It measures what THIS job adds to destDir: a baseline of
            // whatever was already there is subtracted — a previous or partial extract of the same game,
            // or the installer itself sitting inside the target folder. Without the baseline the very
            // first tick already sees gigabytes, so the bar reads 100% / ETA 0s from the start and never
            // moves while the file count keeps climbing (user-reported on a Fallout NV re-extract).
            fun startDestPoller(total: Long, threadName: String): DestPoll {
                val (baseCount, baseBytes) = measureDir(destDir)
                val polling = java.util.concurrent.atomic.AtomicBoolean(true)
                val tot = total.coerceAtLeast(1L)
                val t = Thread {
                    var pLastB = 0L; var pLastT = SystemClock.elapsedRealtime(); var pEma = 0L
                    while (polling.get()) {
                        runCatching { Thread.sleep(2500) }
                        val (count, bytes) = measureDir(destDir)
                        val written = (bytes - baseBytes).coerceAtLeast(0L)
                        val added = (count - baseCount).coerceAtLeast(0)
                        val now = SystemClock.elapsedRealtime()
                        val dt = (now - pLastT).coerceAtLeast(1)
                        val inst = ((written - pLastB) * 1000 / dt).coerceAtLeast(0)
                        pEma = if (pEma == 0L) inst else (pEma * 2 + inst) / 3
                        pLastB = written; pLastT = now
                        val pct = (written * 100 / tot).toInt().coerceIn(0, 100)
                        val eta = if (pEma > 0) (tot - written).coerceAtLeast(0) / pEma else -1L
                        files = added
                        UnpackManager.update {
                            it.copy(
                                phase = UnpackPhase.EXTRACTING, percent = pct,
                                bytesProcessed = written, speedBps = pEma, etaSeconds = eta,
                                elapsedMs = now - startMs, filesExtracted = added,
                            )
                        }
                        refresh()
                    }
                }.also { it.name = threadName; it.start() }
                return DestPoll(polling, t, baseCount, baseBytes)
            }
            /** Files this job added to destDir (final tally after the engine exits). */
            fun addedFiles(poll: DestPoll): Int = (measureDir(destDir).first - poll.baseCount).coerceAtLeast(0)

            val result = runCatching {
                if (isExtra) {
                    val r = ExtraFormats.extract(
                        ctx, archive, destDir, parts?.toSet(), extraProbe?.totalBytes ?: 0L,
                        object : ExtractSink {
                            override fun onBytes(done: Long, total: Long) = pushBytes(done, total)
                            override fun onFile(name: String) { UnpackManager.update { it.copy(currentFile = name) } }
                        },
                        onProcess = { proc = it },
                        // The in-app decoders poll this between chunks: it holds them while paused.
                        cancelled = {
                            while (paused && !cancelled) Thread.sleep(200)
                            cancelled
                        },
                    )
                    files = r.files
                    SevenZip.Result(if (r.exitCode == 0) 0 else 2, r.tail)
                } else if (engine == "unarc") {
                    // FreeArc: unarc block-buffers stdout, so drive progress by POLLING the destination
                    // size against the total from `unarc l` (140 GB-class). Speed/ETA from the byte delta.
                    val unarcTotal = Unarc.list(ctx, archive)?.totalBytes?.takeIf { it > 0 } ?: dataSize
                    UnpackManager.update { it.copy(archiveSize = unarcTotal) }
                    val poll = startDestPoller(unarcTotal, "unarc-poller")
                    val r = Unarc.extract(
                        ctx, archive, destDir,
                        listener = { name -> UnpackManager.update { it.copy(currentFile = name) } },
                        onProcess = { proc = it },
                    )
                    poll.polling.set(false)
                    runCatching { poll.thread.join(3000) }
                    if (r.exitCode == 0) files = addedFiles(poll)
                    SevenZip.Result(if (r.exitCode == 0) 0 else 2, r.stderrTail)
                } else if (isInno) {
                    // innoextract's --progress percent is unreliable for GOG multi-part installers: it
                    // sawtooths ~0-9 (per-chunk, not overall) instead of climbing 0-100, which made the
                    // bar look deceiving. Drive progress the same way as the FreeArc path — POLL the
                    // destination size against the payload total. innoextract still does the extraction;
                    // we just ignore its percent for the bar.
                    val poll = startDestPoller(dataSize, "inno-poller")
                    // Run each installer — base game first, then any auto-detected DLC setups — into the
                    // SAME destDir so the DLC files overlay the base install. The single poller above
                    // spans the whole batch because it measures cumulative dest size, not per-installer.
                    var primaryExit = 0
                    var stderrTail = ""
                    var dlcFailed = 0
                    for ((idx, installer) in archives.withIndex()) {
                        if (cancelled) break
                        UnpackManager.update {
                            it.copy(
                                batchIndex = idx + 1,
                                currentFile = if (batchTotal > 1) "Installer ${idx + 1}/$batchTotal — ${installer.name}" else it.currentFile,
                            )
                        }
                        refresh()
                        val r = Innoextract.extract(
                            ctx, installer, destDir,
                            listener = { /* innoextract percent ignored — poller above drives the bar */ },
                            onProcess = { proc = it },
                        )
                        if (r.exitCode != 0) {
                            stderrTail = r.stderrTail
                            // The base game (idx 0) failing is fatal; a DLC failing is soft — the game is
                            // still usable, so record it and carry on to the next sibling.
                            if (idx == 0) { primaryExit = r.exitCode; break }
                            dlcFailed++
                            UnpackManager.update { it.copy(batchFailed = dlcFailed) }
                            refresh()
                        }
                    }
                    poll.polling.set(false)
                    runCatching { poll.thread.join(3000) }
                    // innoextract has no per-file callback; count the files this job added for the summary.
                    if (primaryExit == 0) files = addedFiles(poll)
                    // innoextract uses 0=ok; map a non-zero BASE exit to an error code (2) for the terminal
                    // logic. Failed DLC siblings don't fail the job — they surface as batchFailed instead.
                    SevenZip.Result(if (primaryExit == 0) 0 else 2, stderrTail)
                } else {
                    SevenZip.extract(
                        ctx, archive, destDir, mmt, buffer,
                        object : SevenZip.Listener {
                            override fun onProgress(percent: Int, currentFile: String?) = pushProgress(percent, currentFile)
                            override fun onFile(name: String) {
                                files++
                                UnpackManager.update { it.copy(currentFile = name, filesExtracted = files) }
                            }
                        },
                        onProcess = { proc = it },
                    )
                }
            }.getOrElse { SevenZip.Result(-1, it.message ?: "exec failed") }

            // Unwrap a single inner .tar (a .wcp/.tzst/.tar.gz decompresses to just its .tar) so one
            // action lands the real files. 7-Zip path only; best-effort; only when the first pass won.
            if (!isInno && !cancelled && result.exitCode <= 1) {
                UnpackManager.update { it.copy(currentFile = "Unpacking inner archive…", percent = 0) }
                refresh()
                runCatching {
                    SevenZip.unwrapSingleTar(
                        ctx, destDir, mmt, buffer,
                        object : SevenZip.Listener {
                            override fun onProgress(percent: Int, currentFile: String?) {
                                UnpackManager.update { it.copy(phase = UnpackPhase.EXTRACTING, percent = percent) }
                                refresh()
                            }
                            override fun onFile(name: String) {
                                files++
                                UnpackManager.update { it.copy(currentFile = name, filesExtracted = files) }
                            }
                        },
                        onProcess = { proc = it },
                    )
                }
            }

            proc = null
            val elapsed = SystemClock.elapsedRealtime() - startMs
            val terminal = when {
                cancelled -> UnpackState(
                    phase = UnpackPhase.CANCELLED, archivePath = archive.absolutePath,
                    archiveName = archive.name, destPath = destDir.absolutePath, elapsedMs = elapsed,
                    filesExtracted = files, archiveSize = dataSize, isInno = isInno, engine = engine,
                )
                result.exitCode <= 1 -> UnpackManager.current.copy(
                    phase = UnpackPhase.DONE, percent = 100, elapsedMs = elapsed,
                    filesExtracted = files, speedBps = 0, etaSeconds = 0, currentFile = null,
                )
                else -> UnpackManager.current.copy(
                    phase = UnpackPhase.ERROR, elapsedMs = elapsed, filesExtracted = files,
                    errorTail = result.stderrTail.takeIf { it.isNotBlank() } ?: "7-Zip exit code ${result.exitCode}",
                )
            }
            UnpackManager.set(terminal)
            postTerminalNotification(terminal)
            if (terminal.phase == UnpackPhase.ERROR) {
                ErrorLog.record(
                    ctx, "Extraction", terminal.errorTail,
                    details = listOf(
                        ErrorLog.archiveFacts(archive.name, dataSize) + (if (archives.size > 1) " (${archives.size} parts)" else ""),
                        "Engine: $engine${if (isInno) " (Inno Setup)" else ""}, exit code ${result.exitCode}, files extracted $files, " +
                            "elapsed ${elapsed / 1000} s" + (if (terminal.batchFailed > 0) ", ${terminal.batchFailed} DLC failed" else ""),
                    ),
                )
            }
            // A file another app streamed in (no real path to it) was copied to app storage just for this
            // job; once it's extracted the copy is dead weight. Kept after an error/cancel so a retry works.
            if (terminal.phase == UnpackPhase.DONE &&
                archive.absolutePath.startsWith(IncomingFile.incomingDir(this).absolutePath + "/")
            ) archive.delete()
            } finally {
                // Balanced release on EVERY exit path (success, error, cancel, process death), then the
                // next queued request, or stop.
                onJobFinished()
            }
        }.also { it.name = "unpack-worker"; it.start() }
    }

    // ── Background queue ──

    /** Starts the next queued request on a worker thread, or marks the service idle. Caller holds [lock]. */
    private fun runNextQueued() {
        val item = queue.removeFirstOrNull()
        if (item == null) { busy = false; return }
        busy = true
        Thread({ prepareAndRun(item) }, "unpack-prepare").start()
    }

    /** After every job (or a request that never got to run): release the CPU, then the next request or stop. */
    private fun onJobFinished() {
        releaseWakeLock()
        paused = false
        synchronized(lock) {
            runNextQueued()
            if (!busy) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf(lastStartId)
            }
        }
    }

    /**
     * Turns a queued request into a job: find the real file (or copy a streamed one into app storage,
     * extracting to Download/), plan it like the Unpack screen would, then run it. A request that can't
     * run leaves a notification saying why (tap opens the full Unpack screen for it).
     */
    private fun prepareAndRun(item: Queued) {
        cancelled = false
        paused = false
        acquireWakeLock()
        UnpackManager.set(UnpackState(phase = UnpackPhase.LISTING, archivePath = item.path ?: "", archiveName = item.name))
        refresh()
        var file: File? = item.path?.let { File(it) }?.takeIf { it.isFile }
        val plan = try {
            var destBase: File? = null
            if (file == null && item.uri != null) file = IncomingFile.resolvePath(this, item.uri)?.let { File(it) }
            if (file == null && item.uri != null) {
                val meta = IncomingFile.meta(this, item.uri)
                IncomingFile.clean(this, null)
                val free = IncomingFile.freeBytes(IncomingFile.incomingDir(this))
                if (meta.size > 0 && meta.size > free) {
                    throw IllegalStateException(
                        "${meta.name} is ${StringUtils.formatBytes(meta.size)} but only ${StringUtils.formatBytes(free)} is free.",
                    )
                }
                file = IncomingFile.copyInBlocking(this, item.uri, meta, isCancelled = {
                    while (paused && !cancelled) Thread.sleep(200)
                    cancelled
                }) { copied ->
                    val pct = if (meta.size > 0) (copied * 100 / meta.size).toInt() else 0
                    UnpackManager.update { it.copy(percent = pct, bytesProcessed = copied, currentFile = COPYING) }
                    refresh()
                }
                destBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            val f = file ?: throw IllegalStateException("${item.name} couldn't be read.")
            QuickExtract.plan(this, f, destBase)
        } catch (c: java.util.concurrent.CancellationException) {
            null
        } catch (t: Throwable) {
            QuickExtract.Plan.Refuse(t.message ?: t.javaClass.simpleName)
        }
        // Cancel pressed while it was still being planned (listing/classifying runs no `proc` to kill).
        when (val decided = if (cancelled) null else plan) {
            is QuickExtract.Plan.Run -> {
                val job = decided.job
                runExtraction(job.archives.map { File(it) }, File(job.dest), job.mmt, job.bufferBytes, job.isInno, job.totalSize, job.engine)
                return   // the extraction's own finally calls onJobFinished
            }
            is QuickExtract.Plan.Refuse -> {
                ErrorLog.record(this, "Extraction (couldn't start)", decided.reason,
                    details = listOf(ErrorLog.archiveFacts(item.name, file?.length() ?: -1L) + if (item.path == null) " (shared from another app)" else ""))
                postNotice("Can't extract ${item.name}", decided.reason, file?.absolutePath)
            }
            null -> postNotice("Extraction cancelled", item.name, null)
        }
        onJobFinished()
    }

    /** Running engine processes of this app (the extractors are exec'd children of our process). */
    private fun enginePids(): List<Int> {
        val me = android.os.Process.myPid()
        return File("/proc").list()?.mapNotNull { it.toIntOrNull() }?.filter { pid ->
            runCatching {
                val ppid = File("/proc/$pid/stat").readText().substringAfterLast(')').trim().split(' ')[1].toInt()
                ppid == me && File("/proc/$pid/cmdline").readText().let { cmd -> ENGINE_BINARIES.any { cmd.contains(it) } }
            }.getOrDefault(false)
        }.orEmpty()
    }

    private fun signalEngines(signal: Int) = enginePids().forEach { runCatching { android.os.Process.sendSignal(it, signal) } }

    override fun onDestroy() {
        releaseWakeLock()   // backstop — the worker's finally already releases on normal exits
        super.onDestroy()
    }

    // ── Notification ──

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Unpacking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows archive extraction progress and keeps it running in the background"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(s: UnpackState): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            UnpackArchiveActivity.intent(this, s.archivePath.ifEmpty { s.archiveName }),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(requestCode: Int, label: String, what: String) = Notification.Action.Builder(
            null, label,
            PendingIntent.getService(
                this, requestCode,
                Intent(this, UnpackService::class.java).apply { action = what },
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
        val waiting = synchronized(lock) { queue.map { it.name } }
        val body = when {
            s.phase == UnpackPhase.LISTING && s.currentFile == COPYING -> "$COPYING ${s.percent}%"
            s.phase == UnpackPhase.LISTING -> "Reading ${s.archiveName}…"
            else -> buildString {
                append("${s.percent}%")
                if (!paused && s.speedBps > 0) append("  •  ${StringUtils.formatBytes(s.speedBps)}/s")
                if (!paused && s.etaSeconds >= 0) append("  •  ETA ${formatDuration(s.etaSeconds * 1000)}")
            }
        }
        val big = if (waiting.isEmpty()) body else body + "\nNext: " + waiting.joinToString(", ")
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (paused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_download)
            .setContentTitle(if (paused) "Paused · ${s.archiveName}" else "Extracting ${s.archiveName}")
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(big))
            .apply { if (waiting.isNotEmpty()) setSubText("${waiting.size} queued") }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, s.percent, s.phase == UnpackPhase.LISTING && s.currentFile != COPYING)
            .setContentIntent(tap)
            .addAction(if (paused) action(2, "Resume", ACTION_RESUME) else action(2, "Pause", ACTION_PAUSE))
            .addAction(action(1, "Cancel", ACTION_CANCEL))
            .apply { if (waiting.isNotEmpty()) addAction(action(3, "Cancel all", ACTION_CANCEL_ALL)) }
            .build()
    }

    private fun refresh() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(UnpackManager.current))
    }

    /** Replace the ongoing notification with a dismissible terminal one (done / error / cancelled). */
    private fun postTerminalNotification(s: UnpackState) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val id = terminalIds.getAndIncrement()
        val tap = PendingIntent.getActivity(
            this, id,
            UnpackArchiveActivity.intent(this, s.archivePath.ifEmpty { s.archiveName }),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (s.phase) {
            UnpackPhase.DONE -> {
                // For a GOG DLC batch, say how many installers landed (and flag any DLC that failed).
                val dlcCount = (s.batchTotal - 1).coerceAtLeast(0)
                val doneTitle = if (dlcCount > 0) "Unpacked ${s.archiveName} + $dlcCount DLC" else "Unpacked ${s.archiveName}"
                val doneText = buildString {
                    append("${s.filesExtracted} files • ${StringUtils.formatBytes(s.archiveSize)} in ${formatDuration(s.elapsedMs)}")
                    if (s.batchFailed > 0) append(" • ${s.batchFailed} DLC skipped (couldn't be read)")
                }
                doneTitle to doneText
            }
            UnpackPhase.CANCELLED -> "Unpack cancelled" to s.archiveName
            else -> "Unpack failed" to (s.errorTail?.lineSequence()?.lastOrNull { it.isNotBlank() } ?: s.archiveName)
        }
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .apply { if (s.phase == UnpackPhase.DONE && s.destPath.isNotEmpty()) addAction(openFolderAction(id, s.destPath)) }
            .build()
        nm.notify(id, n)
    }

    /** "Open folder": BFE's file manager on the extracted folder (SINGLE_TOP keeps a running BFE's panes). */
    private fun openFolderAction(id: Int, folder: String): Notification.Action {
        val open = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PATH, folder)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, id or 0x10000, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, "Open folder", pi).build()
    }

    /** A dismissible one-off notice (a request that couldn't run); tapping opens the Unpack screen for [path]. */
    private fun postNotice(title: String, text: String, path: String?) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val id = terminalIds.getAndIncrement()
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .apply {
                if (path != null) setContentIntent(PendingIntent.getActivity(
                    this@UnpackService, id, UnpackArchiveActivity.intent(this@UnpackService, path),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
            }
            .build()
        nm.notify(id, n)
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

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
