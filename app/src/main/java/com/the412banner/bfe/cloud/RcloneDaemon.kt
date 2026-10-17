package com.the412banner.bfe.cloud

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Owns BFE's single long-lived `rclone rcd` ([RcdProcess]): started on first use, shared by every
 * screen and the transfer service, and stopped once nothing has needed it for [IDLE_STOP_MS] (an idle
 * rcd costs no CPU, but there's no reason to keep it around). Holders [retain] it while visible or busy
 * and [release] it after.
 *
 * Everything rclone keeps lives in app-private storage: the config (accounts, tokens, obscured
 * passwords) in `filesDir/rclone/rclone.conf`, encrypted by rclone with a password only the credential
 * vault holds ([RcloneSecureConfig]), and caches and temp files under `cacheDir`. BFE disables Android
 * backups, so the config never leaves the device.
 */
object RcloneDaemon {
    private const val TAG = "BFE_RCLONE"
    private const val IDLE_STOP_MS = 90_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Mutex()
    private val holders = mutableSetOf<String>()
    private var idleStop: Job? = null
    @Volatile private var rcd: RcdProcess? = null

    fun configFile(ctx: Context): File = File(ctx.filesDir, "rclone/rclone.conf")

    fun retain(tag: String) = synchronized(holders) {
        holders += tag
        idleStop?.cancel()
        idleStop = null
    }

    fun release(tag: String) = synchronized(holders) {
        holders -= tag
        if (holders.isEmpty()) {
            idleStop?.cancel()
            idleStop = scope.launch {
                delay(IDLE_STOP_MS)
                val idle = synchronized(holders) { holders.isEmpty() }
                if (idle) startLock.withLock { stopLocked() }
            }
        }
    }

    /** The daemon's API, starting rclone first when it isn't running. Throws if it can't start. */
    suspend fun api(ctx: Context): RcloneApi = startLock.withLock {
        val running = rcd?.takeIf { it.isAlive }
        if (running != null) return@withLock RcloneApi(running.client)
        val app = ctx.applicationContext
        if (!RcloneManager.isInstalled(app)) throw IOException("rclone isn't installed")
        val started = withContext(Dispatchers.IO) {
            val bin = RcloneManager.binary(app)
            // Encrypt / check rclone.conf first and get its password from the vault.
            val configPassword = RcloneSecureConfig.prepare(app, bin)
            val tmp = File(app.cacheDir, "rclone-tmp").apply { deleteRecursively(); mkdirs() }
            RcdProcess.start(
                binary = bin,
                config = configFile(app),
                configPassword = configPassword,
                cacheDir = File(app.cacheDir, "rclone"),
                tempDir = tmp,
                extraEnv = certEnv(),
                onLog = { Log.i(TAG, it) },
            )
        }
        rcd = started
        Log.i(TAG, "rcd running on ${started.client.baseUrl}")
        RcloneApi(started.client)
    }

    /**
     * At launch: a plain rclone.conf left by an older BFE (or a stray copy) is encrypted now, not at the
     * next Cloud / Remote visit. Works without the engine installed. No-op when there's nothing plain.
     */
    fun secureAtRest(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            startLock.withLock {
                if (rcd?.isAlive == true) return@withLock
                runCatching {
                    if (RcloneSecureConfig.needsSecuring(app)) {
                        RcloneSecureConfig.prepare(app, RcloneManager.binary(app).takeIf { RcloneManager.isInstalled(app) })
                    }
                }.onFailure { Log.w(TAG, "Couldn't secure the rclone config yet: ${it.message}") }
            }
        }
    }

    /** rclone's last log lines (for error details). */
    fun logTail(): String = rcd?.logTail() ?: ""

    fun stopNow() {
        scope.launch { startLock.withLock { stopLocked() } }
    }

    private fun stopLocked() {
        val r = rcd ?: return
        rcd = null
        runCatching { r.stop() }
        Log.i(TAG, "rcd stopped")
    }

    /** Point Go's TLS at Android's CA stores (the updatable APEX one first, then the system one). */
    private fun certEnv(): Map<String, String> {
        val dirs = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts").filter { File(it).isDirectory }
        return if (dirs.isEmpty()) emptyMap() else mapOf("SSL_CERT_DIR" to dirs.joinToString(":"))
    }
}
