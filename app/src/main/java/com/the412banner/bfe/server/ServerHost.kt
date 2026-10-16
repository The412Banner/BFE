package com.the412banner.bfe.server

import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Owns the running HTTP and FTP servers and the state they share: the namespace ([fs]), the
 * credentials, and the [activity] log. Pure JVM — the Android foreground service drives it, and the
 * JVM test harness drives it the same way.
 *
 * [fs] and [credentials] are read per request / per login, so changing what's shared or the password
 * takes effect immediately without restarting anything. Ports and bind addresses need a restart.
 */
class ServerHost(
    @Volatile var fs: VirtualFs,
    @Volatile var credentials: Credentials,
) {
    val activity = ServerActivity()

    private val http = ArrayList<HttpFileServer>()
    private var ftp: FtpFileServer? = null

    @get:Synchronized var httpPort = 0; private set
    @get:Synchronized var ftpPort = 0; private set
    @get:Synchronized var httpAddresses: List<String> = emptyList(); private set
    @get:Synchronized var ftpAddresses: List<String> = emptyList(); private set

    val httpRunning: Boolean @Synchronized get() = http.isNotEmpty()
    val ftpRunning: Boolean @Synchronized get() = ftp != null

    init {
        // NanoHTTPD logs every client that hangs up mid-response at SEVERE with a stack trace — and
        // browsers abort range requests constantly while seeking a video. Errors we care about are
        // caught and reported by our own handlers.
        Logger.getLogger("fi.iki.elonen.NanoHTTPD").level = Level.OFF
    }

    /** Starts one HTTP listener per address. All-or-nothing: on failure nothing is left listening. */
    @Synchronized
    @Throws(IOException::class)
    fun startHttp(addresses: List<String>, port: Int) {
        stopHttp()
        try {
            for (a in addresses) http += HttpFileServer(a, port, this).also { it.startServer() }
        } catch (e: IOException) {
            stopHttp()
            throw IOException(bindMessage(e, port), e)
        }
        httpPort = port
        httpAddresses = addresses
    }

    @Synchronized
    fun stopHttp() {
        http.forEach { runCatching { it.stop() } }
        http.clear()
        httpAddresses = emptyList()
    }

    /** Starts FTP with one listener per address; passive data ports come from [passivePorts] ("a-b"). */
    @Synchronized
    @Throws(IOException::class)
    fun startFtp(addresses: List<String>, port: Int, passivePorts: String) {
        stopFtp()
        val server = FtpFileServer(this, addresses, port, passivePorts)
        try {
            server.start()
        } catch (e: Exception) {
            runCatching { server.stop() }
            throw IOException(bindMessage(e, port), e)
        }
        ftp = server
        ftpPort = port
        ftpAddresses = addresses
    }

    @Synchronized
    fun stopFtp() {
        ftp?.let { runCatching { it.stop() } }
        ftp = null
        ftpAddresses = emptyList()
    }

    @Synchronized
    fun stopAll() {
        stopHttp()
        stopFtp()
    }

    private fun bindMessage(e: Throwable, port: Int): String {
        val msg = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            msg.contains("EADDRINUSE", true) || msg.contains("in use", true) -> "Port $port is already in use by another app"
            msg.contains("EACCES", true) || msg.contains("Permission denied", true) -> "Port $port can't be used (pick 1024 or higher)"
            else -> "Couldn't start on port $port: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
