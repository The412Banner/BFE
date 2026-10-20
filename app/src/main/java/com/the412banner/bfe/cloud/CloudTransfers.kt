package com.the412banner.bfe.cloud

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * DOWNLOAD / UPLOAD come from the Cloud and Remote screens; COPY is a file-manager paste that involves a
 * remote (device → remote, remote → device, or between remotes), described by rclone fs + path pairs.
 */
enum class TransferKind { DOWNLOAD, UPLOAD, COPY }

enum class TransferStatus { QUEUED, PREPARING, RUNNING, DONE, FAILED, CANCELLED }

/**
 * One queued copy between a remote and this device. A download copies [remotePath] (file or folder)
 * into the local folder [localDir]; an upload copies [localPath] (file or folder) into the remote
 * folder [remoteDir]. [sourceUri] is an upload picked through the system file picker that has no
 * real path: it is copied into BFE's cache first ([TransferStatus.PREPARING]).
 *
 * A [TransferKind.COPY] copies (or, with [move], moves) [srcPath] inside the rclone fs [srcFs] to
 * [dstPath] inside [dstFs] ("name:" for a remote, [RcloneApi.LOCAL_FS] for this device). The target
 * name was settled by the paste (conflicts asked first), so it is used as is: a file there is
 * replaced, a folder there is merged into. [fromLabel] / [toLabel] say where in plain words;
 * [srcDirId] / [dstDirId] are the file-manager folders to refresh when it finishes.
 */
data class TransferItem(
    val id: Long,
    val kind: TransferKind,
    val remote: String,
    val name: String,
    val isDir: Boolean,
    val remotePath: String = "",
    val localDir: String = "",
    val localPath: String = "",
    val remoteDir: String = "",
    val sourceUri: String? = null,
    val size: Long = -1L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val bytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speed: Double = 0.0,
    val etaSec: Long = -1L,
    val files: Long = 0L,
    val totalFiles: Long = 0L,
    val error: String? = null,
    val cancelRequested: Boolean = false,
    /** Where the copy landed (download: local path; upload: remote path). */
    val resultPath: String? = null,
    val srcFs: String = "",
    val srcPath: String = "",
    val dstFs: String = "",
    val dstPath: String = "",
    val move: Boolean = false,
    val fromLabel: String = "",
    val toLabel: String = "",
    val srcDirId: String? = null,
    val dstDirId: String? = null,
    /** Which try this is (rclone jobs are re-run after a failure; see [CloudTransferService]). */
    val attempt: Int = 1,
    /** Files that failed inside a folder copy (rclone's error count for the job). */
    val failedFiles: Long = 0L,
) {
    val isActive: Boolean get() = status == TransferStatus.QUEUED || status == TransferStatus.PREPARING || status == TransferStatus.RUNNING
    val percent: Int get() = if (totalBytes > 0) ((bytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    /** A failed item can be queued again, except an upload whose picker grant was only good once. */
    val canRetry: Boolean get() = status == TransferStatus.FAILED && sourceUri == null

    /** "Downloading" / "Uploading" / "Copying" / "Moving", for the notification and the lists. */
    val verb: String get() = when (kind) {
        TransferKind.DOWNLOAD -> "Downloading"
        TransferKind.UPLOAD -> "Uploading"
        TransferKind.COPY -> if (move) "Moving" else "Copying"
    }

    /** Where it goes, in plain words ("From NAS", "To NAS", "NAS → Internal"). */
    val route: String get() = when (kind) {
        TransferKind.DOWNLOAD -> "From $remote"
        TransferKind.UPLOAD -> "To $remote"
        TransferKind.COPY -> "$fromLabel → $toLabel"
    }
}

/**
 * The transfer queue (app-wide): screens enqueue, [CloudTransferService] runs items one at a time and
 * writes progress back here; the transfers sheet, pill and notification all read [items].
 */
object CloudTransfers {
    private val ids = AtomicLong(System.currentTimeMillis())
    private val _items = MutableStateFlow<List<TransferItem>>(emptyList())
    val items: StateFlow<List<TransferItem>> get() = _items

    private val _finished = MutableSharedFlow<TransferItem>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Each item as it finishes (so a browser can refresh the folder it landed in). */
    val finished: SharedFlow<TransferItem> get() = _finished

    fun newId(): Long = ids.incrementAndGet()

    fun enqueue(ctx: Context, items: List<TransferItem>) {
        if (items.isEmpty()) return
        synchronized(this) { _items.value = _items.value + items }
        val app = ctx.applicationContext
        val i = Intent(app, CloudTransferService::class.java).setAction(CloudTransferService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(i) else app.startService(i)
    }

    /** Cancels one item (queued ones are dropped at once, the running one is stopped by the service). */
    fun cancel(id: Long) = update(id) {
        when (it.status) {
            TransferStatus.QUEUED -> it.copy(status = TransferStatus.CANCELLED)
            TransferStatus.PREPARING, TransferStatus.RUNNING -> it.copy(cancelRequested = true)
            else -> it
        }
    }

    fun cancelAll() = _items.value.filter { it.isActive }.forEach { cancel(it.id) }

    fun clearFinished() = synchronized(this) { _items.value = _items.value.filter { it.isActive } }

    /**
     * Queues failed item [id] again as a fresh item. A folder copy picks up where it stopped: rclone
     * skips the files that already arrived, so only the failed and missing ones are copied.
     */
    fun retry(ctx: Context, id: Long) {
        val t = get(id)?.takeIf { it.canRetry } ?: return
        synchronized(this) { _items.value = _items.value.filter { it.id != id } }
        enqueue(ctx, listOf(fresh(t)))
    }

    /** [retry] for every failed item (the summary notification's and the transfers sheet's "Retry failed"). */
    fun retryFailed(ctx: Context) {
        val failed = _items.value.filter { it.canRetry }
        if (failed.isEmpty()) return
        val ids = failed.map { it.id }.toSet()
        synchronized(this) { _items.value = _items.value.filter { it.id !in ids } }
        enqueue(ctx, failed.map { fresh(it) })
    }

    private fun fresh(t: TransferItem) = t.copy(
        id = newId(), status = TransferStatus.QUEUED, bytes = 0L, totalBytes = -1L, speed = 0.0, etaSec = -1L,
        files = 0L, totalFiles = 0L, error = null, cancelRequested = false, resultPath = null, attempt = 1, failedFiles = 0L,
    )

    internal fun update(id: Long, block: (TransferItem) -> TransferItem) = synchronized(this) {
        _items.value = _items.value.map { if (it.id == id) block(it) else it }
    }

    internal fun get(id: Long): TransferItem? = _items.value.firstOrNull { it.id == id }

    internal fun nextQueued(): TransferItem? = _items.value.firstOrNull { it.status == TransferStatus.QUEUED }

    internal fun emitFinished(item: TransferItem) {
        _finished.tryEmit(item)
    }
}
