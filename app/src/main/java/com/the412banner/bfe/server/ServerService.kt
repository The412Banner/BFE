package com.the412banner.bfe.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.the412banner.bfe.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Live status of the network file servers, written by [ServerService] and read by the screen. */
data class ServerState(
    /** What the user switched on (set immediately, before the service has applied it). */
    val wantHttp: Boolean = false,
    val wantFtp: Boolean = false,
    val httpRunning: Boolean = false,
    val ftpRunning: Boolean = false,
    val httpPort: Int = 0,
    val ftpPort: Int = 0,
    val httpError: String? = null,
    val ftpError: String? = null,
    /** Set when nothing could be shared (e.g. "chosen folders" with none chosen). */
    val shareError: String? = null,
    /** Addresses the servers are bound to (or would be). */
    val addresses: List<LanAddresses.LanAddress> = emptyList(),
    /** Switched on, but no Wi-Fi / hotspot right now: servers paused until one appears. */
    val waitingForNetwork: Boolean = false,
    val activity: ServerActivity.Snapshot = ServerActivity.Snapshot.EMPTY,
) {
    val anyRunning: Boolean get() = httpRunning || ftpRunning
}

/** Process-wide holder so the service and Compose share one [ServerState] without binding. */
object ServerManager {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()
    internal fun update(block: (ServerState) -> ServerState) { _state.value = block(_state.value) }
}

/**
 * Foreground service that runs the HTTP and/or FTP file servers ("Access from network") while BFE is
 * in the background — same lifecycle shape as the extraction service.
 *
 * Holds a Wi-Fi lock (high-performance mode, so the radio doesn't drop into power save mid-transfer)
 * and, when "keep running with the screen off" is on, a partial wake lock. Binds only to Wi-Fi /
 * hotspot / Ethernet addresses ([LanAddresses]) and re-checks them every few seconds: a changed
 * address rebinds, and losing Wi-Fi pauses the servers (notification says so) until a network is back.
 * All start/stop work runs on one worker thread, never the main thread.
 */
class ServerService : Service() {

    companion object {
        private const val TAG = "ServerService"
        private const val CHANNEL_ID = "server_channel"
        private const val NOTIFICATION_ID = 9007
        private const val ACTION_APPLY = "com.the412banner.bfe.server.APPLY"
        private const val ACTION_STOP = "com.the412banner.bfe.server.STOP"

        @Volatile private var alive = false

        /** Switch HTTP and/or FTP on or off (null = leave as is) and bring the service in line. */
        fun setRunning(ctx: Context, http: Boolean? = null, ftp: Boolean? = null) {
            // With nothing running, "leave as is" means off: a flag left over from a process the system
            // killed without restarting must not bring the other server back along with this one.
            if (alive) ServerPrefs.setWanted(ctx, http, ftp) else ServerPrefs.setWanted(ctx, http ?: false, ftp ?: false)
            val wantHttp = ServerPrefs.wantHttp(ctx)
            val wantFtp = ServerPrefs.wantFtp(ctx)
            ServerManager.update {
                it.copy(
                    wantHttp = wantHttp, wantFtp = wantFtp,
                    httpError = if (http == true) null else it.httpError,
                    ftpError = if (ftp == true) null else it.ftpError,
                    shareError = if (http == true || ftp == true) null else it.shareError,
                )
            }
            send(ctx, ACTION_APPLY, wantHttp || wantFtp)
        }

        fun stopAll(ctx: Context) = setRunning(ctx, http = false, ftp = false)

        /** Push changed settings (what's shared, credentials, wake lock) into the running servers. */
        fun applySettings(ctx: Context) {
            if (alive) send(ctx, ACTION_APPLY, false)
        }

        /** "Start when BFE opens": called once from MainActivity. */
        fun startOnLaunch(ctx: Context) {
            val hasFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
            if (!hasFiles) return
            val s = ServerPrefs.load(ctx)
            if (s.startHttpOnLaunch || s.startFtpOnLaunch) {
                setRunning(ctx, http = if (s.startHttpOnLaunch) true else null, ftp = if (s.startFtpOnLaunch) true else null)
            }
        }

        private fun send(ctx: Context, action: String, wantsForeground: Boolean) {
            val app = ctx.applicationContext
            val i = Intent(app, ServerService::class.java).setAction(action)
            when {
                alive -> app.startService(i)   // already foreground; just deliver the command
                !wantsForeground -> {}          // nothing to start, nothing running
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> app.startForegroundService(i)
                else -> app.startService(i)
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "bfe-server-ctl") }
    private var ticker: ScheduledExecutorService? = null
    private var host: ServerHost? = null
    private var passiveInUse: String? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var lastAddresses: List<String> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start may have arrived through startForegroundService, so promote straight away;
        // the worker drops the notification again if it turns out there's nothing to run.
        startForegroundCompat(buildNotification(ServerManager.state.value))
        if (intent?.action == ACTION_STOP) {
            ServerPrefs.setWanted(this, http = false, ftp = false)
            ServerManager.update { it.copy(wantHttp = false, wantFtp = false) }
        }
        // A null intent is a sticky restart after the system reclaimed the process: the persisted
        // "wanted" flags bring back exactly what was running.
        worker.execute { reconcile() }
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        ticker?.shutdownNow()
        ticker = null
        worker.execute {
            host?.stopAll()
            host = null
        }
        worker.shutdown()
        releaseLocks()
        ServerManager.update {
            it.copy(httpRunning = false, ftpRunning = false, waitingForNetwork = false, activity = ServerActivity.Snapshot.EMPTY)
        }
        super.onDestroy()
    }

    /** Make the running servers match the settings, the wanted flags and the current network. Worker thread only. */
    private fun reconcile() {
        val s = ServerPrefs.load(this)
        val wantHttp = ServerPrefs.wantHttp(this)
        val wantFtp = ServerPrefs.wantFtp(this)
        ServerManager.update { it.copy(wantHttp = wantHttp, wantFtp = wantFtp) }
        if (!wantHttp && !wantFtp) {
            shutDown()
            return
        }

        val roots = s.shareRoots()
        val creds = s.credentials()
        val shareProblem = when {
            roots.isEmpty() -> "Choose at least one folder to share."
            !creds.anonymous && (creds.user.isEmpty() || creds.password.isEmpty()) -> "Set a user name and password, or allow access without one."
            else -> null
        }
        if (shareProblem != null) {
            ServerPrefs.setWanted(this, http = false, ftp = false)
            ServerManager.update { it.copy(wantHttp = false, wantFtp = false, shareError = shareProblem) }
            shutDown()
            return
        }
        val fs = VirtualFs.of(roots, s.wholeStorage)
        val h = host?.also { it.fs = fs; it.credentials = creds } ?: ServerHost(fs, creds).also { host = it }

        val addresses = LanAddresses.detect(this)
        val ips = addresses.map { it.ip }
        lastAddresses = ips
        if (ips.isEmpty()) {
            h.stopAll()
            releaseLocks()
            publish(addresses, waiting = true)
            startTicker()
            return
        }

        var httpError: String? = null
        var ftpError: String? = null
        if (wantHttp) {
            if (!h.httpRunning || h.httpAddresses != ips || h.httpPort != s.httpPort) {
                try {
                    h.startHttp(ips, s.httpPort)
                } catch (e: IOException) {
                    httpError = e.message
                    ServerPrefs.setWanted(this, http = false, ftp = null)
                }
            }
        } else {
            h.stopHttp()
        }
        if (wantFtp) {
            val pasv = ServerSettings.parsePassive(s.passivePorts)?.let { "${it.first}-${it.last}" }
                ?: ServerSettings.DEFAULT_PASSIVE_PORTS
            if (!h.ftpRunning || h.ftpAddresses != ips || h.ftpPort != s.ftpPort || passiveInUse != pasv) {
                try {
                    h.startFtp(ips, s.ftpPort, pasv)
                    passiveInUse = pasv
                } catch (e: IOException) {
                    ftpError = e.message
                    ServerPrefs.setWanted(this, http = null, ftp = false)
                }
            }
        } else {
            h.stopFtp()
        }
        ServerManager.update {
            it.copy(
                wantHttp = ServerPrefs.wantHttp(this), wantFtp = ServerPrefs.wantFtp(this),
                httpError = httpError ?: it.httpError.takeIf { _ -> !h.httpRunning },
                ftpError = ftpError ?: it.ftpError.takeIf { _ -> !h.ftpRunning },
                shareError = null,
            )
        }
        if (!h.httpRunning && !h.ftpRunning) {
            shutDown()
            return
        }
        acquireLocks(s.keepAwake)
        publish(addresses, waiting = false)
        startTicker()
    }

    /** Status + notification from the live servers. */
    private fun publish(addresses: List<LanAddresses.LanAddress>, waiting: Boolean) {
        val h = host
        ServerManager.update {
            it.copy(
                httpRunning = h?.httpRunning == true,
                ftpRunning = h?.ftpRunning == true,
                httpPort = h?.httpPort ?: 0,
                ftpPort = h?.ftpPort ?: 0,
                addresses = addresses,
                waitingForNetwork = waiting,
                activity = h?.activity?.snapshot() ?: ServerActivity.Snapshot.EMPTY,
            )
        }
        notifyStatus()
    }

    /**
     * Once a second: refresh the activity numbers; every third second: re-check the network, and
     * reconcile if the addresses changed (new DHCP lease, Wi-Fi lost or back, hotspot toggled).
     */
    private fun startTicker() {
        if (ticker != null) return
        var tick = 0
        var lastVersion = -1L
        var lastNotified = 0L
        ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "bfe-server-tick") }.also {
            it.scheduleWithFixedDelay({
                try {
                    tick++
                    val h = host
                    if (h != null) {
                        val snap = h.activity.snapshot()
                        val now = System.currentTimeMillis()
                        if (snap.version != lastVersion || now - lastNotified > 15_000) {
                            lastVersion = snap.version
                            lastNotified = now
                            ServerManager.update { st -> st.copy(activity = snap) }
                            notifyStatus()
                        }
                    }
                    if (tick % 3 == 0) {
                        val ips = LanAddresses.detect(this).map { a -> a.ip }
                        if (ips != lastAddresses) {
                            Log.i(TAG, "LAN addresses changed: $lastAddresses -> $ips")
                            lastAddresses = ips
                            worker.execute { reconcile() }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "tick failed", t)
                }
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun shutDown() {
        ticker?.shutdownNow()
        ticker = null
        host?.stopAll()
        releaseLocks()
        ServerManager.update {
            it.copy(httpRunning = false, ftpRunning = false, waitingForNetwork = false, activity = ServerActivity.Snapshot.EMPTY)
        }
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Locks ──

    @Suppress("DEPRECATION")
    private fun acquireLocks(keepAwake: Boolean) {
        if (wifiLock?.isHeld != true) {
            runCatching {
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bfe:server").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onFailure { Log.w(TAG, "Wi-Fi lock unavailable", it) }
        }
        if (keepAwake) {
            if (wakeLock?.isHeld != true) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bfe:server").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } else {
            wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
            wakeLock = null
        }
    }

    private fun releaseLocks() {
        wifiLock?.let { if (it.isHeld) runCatching { it.release() } }
        wifiLock = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    // ── Notification ──

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Access from network", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the address while BFE shares files over Wi-Fi"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(st: ServerState): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val ip = st.addresses.firstOrNull()?.ip
        val urls = buildList {
            if (st.httpRunning && ip != null) add("http://$ip:${st.httpPort}")
            if (st.ftpRunning && ip != null) add("ftp://$ip:${st.ftpPort}")
        }
        val (title, text) = when {
            st.waitingForNetwork -> "File sharing paused" to "Waiting for Wi-Fi or a hotspot. It resumes by itself."
            urls.isEmpty() -> "Access from network" to "Starting…"
            else -> {
                val devices = st.activity.devices.size
                "Sharing files on " + (st.addresses.firstOrNull()?.kind?.label ?: "Wi-Fi") to
                    urls.joinToString("  ·  ") + if (devices > 0) "  ·  $devices connected" else ""
            }
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (open != null) setContentIntent(open) }
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun notifyStatus() {
        if (!alive) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(ServerManager.state.value))
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }
}
