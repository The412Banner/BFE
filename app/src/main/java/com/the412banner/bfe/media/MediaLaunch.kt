package com.the412banner.bfe.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.the412banner.bfe.PhotoViewerActivity
import com.the412banner.bfe.R
import com.the412banner.bfe.VideoPlayerActivity
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.IncomingFile
import java.io.File

/** What a viewer / player activity was asked to show: a playlist and where to start. */
data class MediaRequest(val items: List<MediaRef>, val index: Int)

/**
 * Opening BFE's own photo viewer and video player, and reading what they were opened with. Inside
 * BFE the playlist travels through [MediaHandoff]; the current file's path / Uri rides along in the
 * Intent so the activity can rebuild its list from the folder after a process restart, or when
 * another app opened it with ACTION_VIEW.
 */
object MediaLaunch {
    private const val EXTRA_TOKEN = "com.the412banner.bfe.extra.MEDIA_TOKEN"
    private const val EXTRA_INDEX = "com.the412banner.bfe.extra.MEDIA_INDEX"
    private const val EXTRA_PATH = "com.the412banner.bfe.extra.MEDIA_PATH"
    private const val EXTRA_NAME = "com.the412banner.bfe.extra.MEDIA_NAME"

    fun component(ctx: Context, type: MediaType): ComponentName = when (type) {
        MediaType.IMAGE -> ComponentName(ctx, PhotoViewerActivity::class.java)
        MediaType.VIDEO, MediaType.AUDIO -> ComponentName(ctx, VideoPlayerActivity::class.java)
    }

    fun label(ctx: Context, type: MediaType): String =
        ctx.getString(if (type == MediaType.IMAGE) R.string.viewer_label else R.string.player_label)

    fun intent(ctx: Context, type: MediaType, items: List<MediaRef>, index: Int): Intent {
        val i = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val current = items.getOrNull(i)
        return Intent(Intent.ACTION_VIEW).apply {
            component = component(ctx, type)
            putExtra(EXTRA_TOKEN, MediaHandoff.put(items))
            putExtra(EXTRA_INDEX, i)
            current?.path?.let { putExtra(EXTRA_PATH, it) }
            current?.let {
                putExtra(EXTRA_NAME, it.name)
                if (it.path == null) { data = Uri.parse(it.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
    }

    fun open(ctx: Context, type: MediaType, items: List<MediaRef>, index: Int) {
        if (items.isEmpty()) return
        runCatching { ctx.startActivity(intent(ctx, type, items, index)) }
            .onFailure { AppSnackbar.show(ctx, "Couldn't open ${items.getOrNull(index)?.name ?: "the file"}") }
    }

    /** Opens just [file] (an archive entry's or a cloud file's cache copy). */
    fun openFile(ctx: Context, type: MediaType, file: File) = open(ctx, type, listOf(MediaRef.of(file)), 0)

    /**
     * Reads [intent] back into a playlist. Blocking (folder listing, provider queries): call off the
     * main thread. [types] are the kinds this activity plays; the folder playlist keeps the current
     * file's own kind.
     */
    fun resolve(ctx: Context, intent: Intent, types: Set<MediaType>): MediaRequest? {
        val token = intent.getLongExtra(EXTRA_TOKEN, 0L)
        if (token != 0L) {
            MediaHandoff.take(token)?.takeIf { it.isNotEmpty() }?.let { list ->
                return MediaRequest(list, intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, list.size - 1))
            }
        }
        val data = intent.data
        val path = intent.getStringExtra(EXTRA_PATH)
            ?: data?.let { runCatching { IncomingFile.resolvePath(ctx, it) }.getOrNull() }
        val nameHint = intent.getStringExtra(EXTRA_NAME)
        if (path != null) {
            val f = File(path)
            val type = MediaTypes.typeOf(f.name, intent.type)?.takeIf { it in types } ?: types.first()
            if (f.isFile && f.canRead()) {
                val dir = f.parentFile
                if (dir != null && dir.canRead()) {
                    val (files, idx) = MediaSiblings.inFolder(dir, type, f)
                    if (files.isNotEmpty()) return MediaRequest(files.map { MediaRef.of(it) }, idx)
                }
                return MediaRequest(listOf(MediaRef.of(f)), 0)
            }
        }
        if (data == null) return null
        val meta = runCatching { IncomingFile.meta(ctx, data) }.getOrNull()
        val name = nameHint ?: meta?.name ?: data.lastPathSegment ?: "file"
        return MediaRequest(listOf(MediaRef(uri = data.toString(), name = name, path = null, size = meta?.size ?: -1L)), 0)
    }
}
