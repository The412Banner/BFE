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

enum class TransferKind { DOWNLOAD, UPLOAD }

enum class TransferStatus { QUEUED, PREPARING, RUNNING, DONE, FAILED, CANCELLED }

/**
 * One queued copy between a remote and this device. A download copies [remotePath] (file or folder)
 * into the local folder [localDir]; an upload copies [localPath] (file or folder) into the remote
 * folder [remoteDir]. [sourceUri] is an upload picked through the system file picker that has no
 * real path: it is copied into BFE's cache first ([TransferStatus.PREPARING]).
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
) {
    val isActive: Boolean get() = status == TransferStatus.QUEUED || status == TransferStatus.PREPARING || status == TransferStatus.RUNNING
    val percent: Int get() = if (totalBytes > 0) ((bytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
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

    internal fun update(id: Long, block: (TransferItem) -> TransferItem) = synchronized(this) {
        _items.value = _items.value.map { if (it.id == id) block(it) else it }
    }

    internal fun get(id: Long): TransferItem? = _items.value.firstOrNull { it.id == id }

    internal fun nextQueued(): TransferItem? = _items.value.firstOrNull { it.status == TransferStatus.QUEUED }

    internal fun emitFinished(item: TransferItem) {
        _finished.tryEmit(item)
    }
}
