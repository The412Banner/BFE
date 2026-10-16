package com.the412banner.bfe.cloud

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * One running `rclone rcd`: the remote-control daemon every Cloud/Remote operation goes through.
 * It listens on 127.0.0.1 only, on a free port picked at start, behind a random user/password that
 * only this process knows (passed through the environment, not the command line). `--rc-serve` also
 * lets [RcClient.openRead] stream remote files.
 *
 * Plain JVM (ProcessBuilder), so the same code starts a daemon in a desktop test. Android specifics
 * (paths, the idle timer, logging) live in [RcloneDaemon].
 */
class RcdProcess private constructor(private val process: Process, val client: RcClient) {
    private val tail = ArrayDeque<String>()

    val isAlive: Boolean get() = process.isAlive

    /** The last lines rclone logged, for error messages. */
    fun logTail(lines: Int = 20): String = synchronized(tail) { tail.toList().takeLast(lines).joinToString("\n") }

    private fun addLine(line: String) = synchronized(tail) {
        tail.addLast(line)
        while (tail.size > 300) tail.removeFirst()
    }

    /** Asks rclone to quit (it finishes cleanly), then makes sure the process is gone. */
    fun stop() {
        runCatching { client.call("core/quit", readTimeoutMs = 3_000) }
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }

    companion object {
        /**
         * Starts rcd with [binary], keeping its config in [config] and scratch files in [cacheDir] /
         * [tempDir]; returns once the API answers (or throws with rclone's last log lines).
         */
        fun start(
            binary: File,
            config: File,
            cacheDir: File,
            tempDir: File,
            extraEnv: Map<String, String> = emptyMap(),
            onLog: (String) -> Unit = {},
            timeoutMs: Long = 20_000,
        ): RcdProcess {
            config.parentFile?.mkdirs()
            cacheDir.mkdirs()
            tempDir.mkdirs()
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val pass = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val user = "bfe"
            val pb = ProcessBuilder(
                binary.absolutePath, "rcd",
                "--rc-addr", "127.0.0.1:$port",
                "--rc-serve",
                // Keep finished jobs around long enough for a slow poller to read their result.
                "--rc-job-expire-duration", "1h",
                "--config", config.absolutePath,
                "--cache-dir", cacheDir.absolutePath,
                "--temp-dir", tempDir.absolutePath,
                "--log-level", "INFO",
            )
            pb.redirectErrorStream(true)
            // A "local" remote's relative paths resolve against the working directory: make that "/"
            // so they mean the same as LOCAL_FS paths.
            pb.directory(File("/"))
            pb.environment().apply {
                put("RCLONE_RC_USER", user)
                put("RCLONE_RC_PASS", pass)
                put("HOME", config.parentFile?.absolutePath ?: tempDir.absolutePath)
                put("TMPDIR", tempDir.absolutePath)
                putAll(extraEnv)
            }
            val process = pb.start()
            val rcd = RcdProcess(process, RcClient(port, user, pass))
            Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        rcd.addLine(line)
                        onLog(line)
                    }
                }
            }.apply { name = "rclone-log"; isDaemon = true; start() }

            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError: Exception? = null
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive) {
                    Thread.sleep(150) // let the log reader catch the last lines
                    throw IOException("rclone stopped (exit ${process.exitValue()}): ${rcd.logTail(6)}".trim())
                }
                try {
                    rcd.client.call("rc/noop", readTimeoutMs = 2_000)
                    return rcd
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(120)
                }
            }
            process.destroy()
            throw IOException("rclone didn't start in time: ${lastError?.message ?: ""} ${rcd.logTail(6)}".trim())
        }
    }
}
