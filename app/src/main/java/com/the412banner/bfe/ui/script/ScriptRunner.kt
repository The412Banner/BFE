package com.the412banner.bfe.ui.script

import com.the412banner.bfe.storage.RootAccess
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

/**
 * What a script's first line asks to be run with: `#!/system/bin/sh`, `#!/usr/bin/env python3`, …
 * [program] is a path, or a bare name to look up when [viaEnv]; [arg] is the optional one argument.
 */
internal data class Shebang(val program: String, val arg: String?, val viaEnv: Boolean) {
    /** How the confirm dialog names it. */
    val label: String get() = program.substringAfterLast('/')

    companion object {
        /** The interpreter on [firstLine], or null when there is no #! line (or it names a plain sh). */
        fun parse(firstLine: String?): Shebang? {
            val l = firstLine?.trim()?.takeIf { it.startsWith("#!") }?.removePrefix("#!")?.trim() ?: return null
            val parts = l.split(Regex("\\s+"), limit = 2)
            val prog = parts[0].takeIf { it.isNotEmpty() } ?: return null
            val rest = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            val sb = if (prog.endsWith("/env") && rest != null) {
                val envParts = rest.split(Regex("\\s+"), limit = 2)
                Shebang(envParts[0], envParts.getOrNull(1), viaEnv = true)
            } else {
                Shebang(prog, rest, viaEnv = false)
            }
            // Any "sh" (/bin/sh doesn't exist on Android) is just the device's sh.
            return sb.takeUnless { it.label == "sh" && it.arg == null }
        }
    }
}

/**
 * Runs one script: `sh` (or its [shebang] interpreter when the device has it — else sh, with a note)
 * in the script's folder, stdout and stderr merged into one live stream of lines. Without root it is
 * an ordinary child process of BFE (BFE's own permissions); [asRoot] runs it in a fresh libsu root
 * shell (BFE's root path). [stop] ends the script and everything it started.
 */
internal class ScriptRunner(private val script: File, private val shebang: Shebang?, private val asRoot: Boolean) {
    @Volatile private var pid = -1
    @Volatile private var process: Process? = null
    @Volatile private var shell: Shell? = null
    @Volatile var stopped = false
        private set

    /** Blocking (off the main thread). Hands each output line to [onLine]; returns the exit code. */
    fun run(onLine: (String) -> Unit): Int {
        val cmd = command()
        val take: (String) -> Unit = { line ->
            if (line.startsWith(MARK)) pid = line.removePrefix(MARK).trim().toIntOrNull() ?: -1 else onLine(line)
        }
        return if (asRoot) runRoot(cmd, take) else runPlain(cmd, take)
    }

    private fun runPlain(cmd: String, take: (String) -> Unit): Int {
        val p = ProcessBuilder("/system/bin/sh", "-c", cmd)
            .directory(script.parentFile)
            .redirectErrorStream(true)
            .start()
        process = p
        try {
            p.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    take(line)
                }
            }
        } catch (e: IOException) {
            if (!stopped) throw e
        }
        return p.waitFor()
    }

    private fun runRoot(cmd: String, take: (String) -> Unit): Int {
        if (!RootAccess.ensure()) throw IOException("Root access wasn't granted to BFE.")
        // Its own shell, so a long script never holds up the shell BFE's root file browsing uses.
        val sh = Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build()
        shell = sh
        try {
            if (!sh.isRoot) throw IOException("Root access wasn't granted to BFE.")
            val out = object : CallbackList<String>(Executor { it.run() }) {
                override fun onAddElement(e: String) = take(e)
            }
            return sh.newJob().add(cmd).to(out).exec().code
        } finally {
            runCatching { sh.close() }
        }
    }

    /**
     * Ends the script: TERM to it and every process under it, KILL a moment later for whatever is
     * left, then lets go of the shell / process so the output stops.
     */
    fun stop() {
        stopped = true
        val p = pid
        if (p > 0) {
            killTree(p, "TERM")
            Thread.sleep(1200)
            killTree(p, "KILL")
        }
        process?.destroy()
        if (asRoot && p <= 0) runCatching { shell?.close() }
    }

    private fun killTree(p: Int, signal: String) {
        // Children first (found through pgrep -P), then the process itself.
        val k = "k() { for c in \$(pgrep -P \$1 2>/dev/null); do k \$c; done; kill -$signal \$1 2>/dev/null; }; k $p"
        runCatching {
            if (asRoot) Shell.cmd(k).exec()
            else ProcessBuilder("/system/bin/sh", "-c", k).redirectErrorStream(true).start().waitFor()
        }
    }

    // The script runs in the background of a small sh wrapper that first reports its PID (for Stop),
    // then waits for it and exits with its exit code.
    private fun command(): String {
        val dir = script.parentFile?.absolutePath ?: "/"
        return "cd ${q(dir)} 2>&1 || exit 1\n" +
            "{ ${launcher()} ; } </dev/null 2>&1 &\n" +
            "p=\$!\n" +
            "echo \"$MARK\$p\"\n" +
            "wait \$p\n"
    }

    private fun launcher(): String {
        val s = q(script.absolutePath)
        val sb = shebang ?: return "sh $s"
        val fallback = "echo ${q("[BFE] ${sb.label} isn't available here, running with sh")}; sh $s"
        val arg = sb.arg?.let { " " + q(it) } ?: ""
        return if (sb.viaEnv) {
            "if command -v ${q(sb.program)} >/dev/null 2>&1; then ${q(sb.program)}$arg $s; else $fallback; fi"
        } else {
            "if [ -x ${q(sb.program)} ]; then ${q(sb.program)}$arg $s; else $fallback; fi"
        }
    }

    companion object {
        private const val MARK = "\u0001BFE-PID "

        /** Single-quotes [s] for sh. */
        fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"
    }
}
