package com.the412banner.bfe.unpack

import android.content.Context
import com.the412banner.bfe.cloud.CloudTransfers
import com.the412banner.bfe.cloud.RcloneApi
import com.the412banner.bfe.cloud.TransferItem
import com.the412banner.bfe.cloud.TransferKind
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.storage.LocCodec
import com.the412banner.bfe.storage.RootAccess
import com.the412banner.bfe.storage.StorageTransfer
import java.io.File

/**
 * Extracting to a place the engines can't write to directly: another app's storage (SAF), a root
 * folder, or a remote (SMB, SFTP, FTP, WebDAV, cloud account). The engines need a real folder, so the
 * job extracts into a staging folder of BFE's own first, then [deliver] puts the result in place:
 * app storage and root folders are copied by the unpack service itself (part of the job's progress);
 * a remote gets one background rclone move in the transfer queue (notification, retries, Retry
 * button), which also empties the staging folder as the files arrive.
 *
 * Staging lives in Android/data/<BFE>/files/extract, not the cache, so Android never clears it while
 * an upload waits or after one failed (the files stay there for Retry).
 */
object ExtractHandoff {

    /** Where the extracted folder finally goes, and how to name that place to the user. */
    class Target(val loc: Loc, val label: String) {
        fun encode(): String? = LocCodec.encode(loc)?.let { "$label\n$it" }

        companion object {
            fun decode(s: String?): Target? {
                if (s.isNullOrBlank()) return null
                val label = s.substringBefore('\n')
                val loc = LocCodec.decode(s.substringAfter('\n', "")) ?: return null
                return Target(loc, label)
            }
        }
    }

    /** Whether extracting into [loc] needs a staging folder first (anything but a plain device folder). */
    fun needed(loc: Loc): Boolean = loc !is Loc.FileLoc && loc !is Loc.ArchiveLoc

    fun stagingRoot(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "extract")

    fun isStaging(ctx: Context, path: String): Boolean = path.startsWith(stagingRoot(ctx).absolutePath + "/")

    /**
     * A new, empty staging folder for one job: the job extracts into `<it>/<folder name>`. Folders left
     * empty by earlier jobs (a remote move empties them) are removed first.
     */
    fun newStagingDir(ctx: Context): File {
        val root = stagingRoot(ctx)
        root.listFiles()?.forEach { if (it.isDirectory && isEmptyTree(it)) it.deleteRecursively() }
        return File(root, System.currentTimeMillis().toString()).also { it.mkdirs() }
    }

    /**
     * The staging folder for a space-saving unpack ([SpaceSaver]) of [archive]: always the same one,
     * because the engine keeps its resume journal there and the download is already partly deleted
     * when a run stops. `<it>/<folder name>` is what the engine writes; [noteTarget] records where it
     * goes, so a reopened Unpack screen can continue it.
     */
    fun spaceSaverDir(ctx: Context, archive: File): File {
        val id = java.security.MessageDigest.getInstance("SHA-256").digest(archive.absolutePath.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        return File(stagingRoot(ctx), "keep-$id")
    }

    private const val TARGET_FILE = ".bfe-target"

    /** Remembers [target] next to the extracted folder [extracted] (in its staging folder, never uploaded). */
    fun noteTarget(extracted: File, target: Target) {
        val dir = extracted.parentFile ?: return
        runCatching { dir.mkdirs(); File(dir, TARGET_FILE).writeText(target.encode().orEmpty()) }
    }

    /** Where an unfinished space-saving unpack of [archive] into staging was going, or null. */
    fun unfinishedTarget(ctx: Context, archive: File, name: String): Target? {
        val dir = spaceSaverDir(ctx, archive)
        if (!File(dir, "$name/.bfe-inplace").isDirectory) return null
        return runCatching { Target.decode(File(dir, TARGET_FILE).readText()) }.getOrNull()
    }

    private fun isEmptyTree(dir: File): Boolean =
        dir.listFiles()?.all { it.isDirectory && isEmptyTree(it) } ?: true

    sealed class Outcome {
        /** Copied into [Target.loc]; the staging folder is gone. */
        object Copied : Outcome()
        /** Handed to the transfer queue (remote): it uploads in the background. */
        object Queued : Outcome()
        object Cancelled : Outcome()
        /** Couldn't be put in place; the extracted files are still at [keptAt]. */
        class Failed(val keptAt: File) : Outcome()
    }

    /**
     * Puts the extracted folder [extracted] (inside a staging folder) into [target]. Blocking; runs on
     * the unpack worker. [onProgress] gets a percent (or -1) while a copy runs.
     */
    fun deliver(
        ctx: Context,
        extracted: File,
        target: Target,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit,
    ): Outcome {
        val stagingJob = extracted.parentFile
        val loc = target.loc
        if (loc is Loc.RemoteLoc) {
            val item = TransferItem(
                id = CloudTransfers.newId(),
                kind = TransferKind.COPY,
                remote = loc.remote,
                name = extracted.name,
                isDir = extracted.isDirectory,
                size = if (extracted.isDirectory) -1L else extracted.length(),
                srcFs = RcloneApi.LOCAL_FS, srcPath = RcloneApi.localPath(extracted.absolutePath),
                dstFs = RcloneApi.fsOf(loc.remote), dstPath = RcloneApi.join(loc.path, extracted.name),
                move = true,
                fromLabel = "Unpacked",
                toLabel = target.label,
                dstDirId = loc.id,
            )
            CloudTransfers.enqueue(ctx, listOf(item))
            stagingJob?.let { File(it, TARGET_FILE).delete() }
            return Outcome.Queued
        }
        if (loc is Loc.RootLoc && !RootAccess.ensure()) return Outcome.Failed(extracted)
        val total = sizeOf(extracted).coerceAtLeast(1L)
        var lastPct = -1
        val ok = StorageTransfer.copyInto(
            ctx, Loc.FileLoc(extracted), loc, extracted.name,
            onBytes = { b ->
                val pct = ((b * 100) / total).toInt().coerceIn(0, 100)
                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
            },
            isCancelled = isCancelled,
        )
        return when {
            isCancelled() -> { stagingJob?.deleteRecursively(); Outcome.Cancelled }
            ok -> { stagingJob?.deleteRecursively(); Outcome.Copied }
            else -> Outcome.Failed(extracted)
        }
    }

    private fun sizeOf(f: File): Long =
        if (f.isDirectory) f.listFiles()?.sumOf { sizeOf(it) } ?: 0L else f.length()
}
