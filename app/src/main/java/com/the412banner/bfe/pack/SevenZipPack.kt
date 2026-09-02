package com.the412banner.bfe.pack

import android.content.Context
import com.the412banner.bfe.unpack.SevenZip
import java.io.File

/**
 * Archive CREATION via the bundled `7zz` — the mirror image of [SevenZip.extract], same exec pattern
 * (nativeLibraryDir + LD_LIBRARY_PATH) and the same live in-place-percent progress parser.
 *
 * - zip / 7z / tar: one process, `7zz a -t<type> … -- <out> <inputs…>`.
 * - tar.gz / tar.xz: 7-Zip has no one-shot "tar then compress", and staging a full .tar on disk would
 *   double the footprint of a game folder — so two processes are chained through a pipe:
 *   `7zz a -ttar -so` (progress on stderr via -bsp2) → `7zz a -t<gzip|xz> -si<name>.tar <out>`.
 *   Progress comes from the tar leg (it knows the input size); the compressor leg is silenced.
 *
 * Inputs are passed as absolute paths; 7-Zip stores each one by its LAST path component (verified:
 * `a out.zip /x/y/big` → entries `big/…`), so a folder becomes a top-level folder in the archive
 * and the selection's parent path never leaks in. `--` ends switch parsing so a name beginning with
 * `-` or `@` is a file, not a switch/listfile.
 */
object SevenZipPack {

    /** Both processes of a pipe (or the single one) so the service can destroy them on cancel. */
    class Handle(val processes: List<Process>) {
        fun destroy() = processes.forEach { runCatching { it.destroy() } }
    }

    fun create(
        context: Context,
        format: PackFormat,
        out: File,
        inputs: List<File>,
        level: PackLevel,
        password: String?,
        mmt: Int,
        bufferBytes: Int,
        listener: SevenZip.Listener,
        onHandle: (Handle) -> Unit,
    ): SevenZip.Result {
        out.parentFile?.mkdirs()
        if (out.exists()) out.delete()   // 7zz would otherwise UPDATE an existing archive
        val inputArgs = inputs.map { it.absolutePath }
        return when (format.engine) {
            PackEngine.SEVENZIP -> single(context, format, out, inputArgs, level, password, mmt, bufferBytes, listener, onHandle)
            PackEngine.SEVENZIP_PIPE -> piped(context, format, out, inputArgs, level, mmt, bufferBytes, listener, onHandle)
            PackEngine.JAVA_TAR_ZSTD -> throw IllegalArgumentException("${format.name} is built by TarZstPack, not 7zz")
        }
    }

    private fun commonArgs(format: PackFormat, level: PackLevel, mmt: Int): List<String> = buildList {
        add("a")
        add("-t${format.sevenZipType}")
        if (format.supportsLevel) add("-mx=${level.mx}")
        add("-mmt=$mmt")
        add("-y")
        // tar keeps symlinks AS links (what tar means); zip/7z follow them so the archive is portable.
        if (format == PackFormat.TAR || format.engine == PackEngine.SEVENZIP_PIPE) add("-snl")
    }

    private fun single(
        context: Context, format: PackFormat, out: File, inputs: List<String>, level: PackLevel,
        password: String?, mmt: Int, bufferBytes: Int, listener: SevenZip.Listener, onHandle: (Handle) -> Unit,
    ): SevenZip.Result {
        val args = ArrayList(commonArgs(format, level, mmt))
        args += listOf("-bsp1", "-bb1")   // progress → stdout, "+ path" per added file
        if (!password.isNullOrEmpty() && format.supportsPassword) {
            args += "-p$password"
            when (format) {
                PackFormat.SEVEN_Z -> args += "-mhe=on"      // encrypt the header (file names) too
                PackFormat.ZIP -> args += "-mem=AES256"      // not the weak legacy ZipCrypto
                else -> Unit
            }
        }
        args += "--"
        args += out.absolutePath
        args += inputs
        val proc = SevenZip.newProcess(context, *args.toTypedArray()).start()
        onHandle(Handle(listOf(proc)))

        val stderr = StringBuilder()
        val errThread = SevenZip.collectStderr(proc.errorStream, stderr)
        SevenZip.pumpProgress(proc.inputStream, bufferBytes, listener)
        val exit = proc.waitFor()
        runCatching { errThread.join(500) }
        if (exit <= 1) listener.onProgress(100, null)
        return SevenZip.Result(exit, synchronized(stderr) { stderr.toString().trim() })
    }

    private fun piped(
        context: Context, format: PackFormat, out: File, inputs: List<String>, level: PackLevel,
        mmt: Int, bufferBytes: Int, listener: SevenZip.Listener, onHandle: (Handle) -> Unit,
    ): SevenZip.Result {
        // Leg 1: tar to stdout. -bsp2 routes the live percent to STDERR (stdout is the tar stream);
        // -bso0 keeps the banner/summary off stdout too. The archive-name argument is required by
        // the parser but unused with -so.
        val tarArgs = ArrayList(commonArgs(PackFormat.TAR, level, mmt))
        tarArgs += listOf("-so", "-bso0", "-bsp2", "-bb1", "--", "stdout.tar")
        tarArgs += inputs
        // Leg 2: compress stdin into the final file. The inner member is named after the archive
        // ("game.tar.gz" wraps "game.tar"), matching what gzip/xz tools produce.
        val inner = format.stripExt(out.name) + ".tar"
        val compArgs = buildList {
            add("a"); add("-t${format.sevenZipType}"); add("-mx=${level.mx}"); add("-mmt=$mmt"); add("-y")
            add("-si$inner"); add("-bso0"); add("-bsp0"); add("--"); add(out.absolutePath)
        }
        val tarProc = SevenZip.newProcess(context, *tarArgs.toTypedArray()).start()
        val compProc = SevenZip.newProcess(context, *compArgs.toTypedArray()).start()
        onHandle(Handle(listOf(tarProc, compProc)))

        // Pump tar's stdout into the compressor's stdin. Closing the compressor's stdin at EOF is what
        // lets it finish the archive.
        val pumpError = arrayOfNulls<Throwable>(1)
        val pump = Thread {
            try {
                tarProc.inputStream.use { i -> compProc.outputStream.use { o -> i.copyTo(o, bufferBytes.coerceAtLeast(256 * 1024)) } }
            } catch (t: Throwable) {
                pumpError[0] = t
                runCatching { compProc.outputStream.close() }
            }
        }.also { it.name = "pack-pipe"; it.start() }

        val compErr = StringBuilder()
        val compErrThread = SevenZip.collectStderr(compProc.errorStream, compErr)
        // The tar leg's stderr carries BOTH its progress and any error text; the same parser reads it
        // (non-progress lines simply don't match).
        SevenZip.pumpProgress(tarProc.errorStream, bufferBytes, listener)

        val tarExit = tarProc.waitFor()
        runCatching { pump.join() }
        val compExit = compProc.waitFor()
        runCatching { compErrThread.join(500) }
        val exit = when {
            tarExit > 1 -> tarExit
            compExit > 1 -> compExit
            pumpError[0] != null -> 2
            else -> maxOf(tarExit, compExit)
        }
        if (exit <= 1) listener.onProgress(100, null)
        val tail = buildString {
            val c = synchronized(compErr) { compErr.toString().trim() }
            if (c.isNotEmpty()) append(c)
            pumpError[0]?.let { if (isNotEmpty()) append('\n'); append("pipe: ${it.message}") }
            if (isEmpty() && exit > 1) append("7-Zip exit code $exit (tar=$tarExit, ${format.sevenZipType}=$compExit)")
        }
        return SevenZip.Result(exit, tail)
    }
}
