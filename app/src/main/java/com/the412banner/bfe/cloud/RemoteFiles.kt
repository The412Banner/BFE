package com.the412banner.bfe.cloud

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Opening a remote file in BFE: it is fetched into the cache first (viewers and other apps need a
 * real file), and the copy is reused while the remote file keeps the same size and date. Shared by
 * the Remote / Cloud browser and the file manager's remote folders.
 */
object RemoteFiles {

    /** The cached copy an "open" downloads to; keyed on path + size + date so a changed file is re-fetched. */
    fun openCacheFile(ctx: Context, remote: String, path: String, name: String, modTime: Long, size: Long): File {
        val key = Integer.toHexString("$remote:$path:$modTime:$size".hashCode())
        return File(ctx.cacheDir, "cloud-open/$key/$name")
    }

    /** True when [file] already holds the whole remote file (its size is known and matches). */
    fun isCached(file: File, size: Long): Boolean = file.isFile && size >= 0 && file.length() == size

    /**
     * Downloads [path] on [remote] to [dest] as an rclone job, feeding [onBytes] the running byte count.
     * Cancelling the coroutine stops the job. Blocking network work: call from Dispatchers.IO.
     */
    suspend fun fetch(ctx: Context, remote: String, path: String, dest: File, onBytes: (Long) -> Unit = {}) {
        val api = RcloneDaemon.api(ctx)
        dest.parentFile?.mkdirs()
        dest.delete()
        val job = api.startCopyFile(RcloneApi.fsOf(remote), path, RcloneApi.LOCAL_FS, RcloneApi.localPath(dest.absolutePath))
        try {
            api.rc.awaitJob(job, pollMs = 300) {
                runCatching { api.rc.stats("job/$job") }.getOrNull()?.let { s -> onBytes(s.optLong("bytes", 0L)) }
            }
        } finally {
            api.rc.deleteStats("job/$job")
        }
    }

    /** Opened-file copies older than a day are dropped. */
    fun cleanOpenCache(ctx: Context) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        File(ctx.cacheDir, "cloud-open").listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
    }
}

/**
 * The remotes the file manager's drive menu lists under the drives. Knowing them needs a running rclone,
 * so the Cloud and Remote screens save the names here each time they list the accounts; the menu then
 * shows them instantly, and rclone only starts when one is opened.
 */
object RemoteShortcuts {
    private const val KEY = "fmRemoteShortcuts"

    /** One remote: its name and rclone backend type ("smb", "sftp", "drive"…). */
    data class Shortcut(val name: String, val type: String, val section: CloudSection)

    fun list(ctx: Context): List<Shortcut> = runCatching {
        val arr = JSONArray(PreferenceManager.getDefaultSharedPreferences(ctx).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                Shortcut(
                    o.optString("name"), o.optString("type"),
                    if (o.optString("section") == CloudSection.CLOUD.name) CloudSection.CLOUD else CloudSection.REMOTE,
                )
            }
        }.filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Replaces the saved list with every remote rclone has now (accounts and connections alike). */
    fun save(ctx: Context, remotes: List<RcRemote>) {
        val arr = JSONArray()
        remotes.sortedBy { it.name.lowercase() }.forEach { r ->
            arr.put(
                JSONObject().put("name", r.name).put("type", r.type)
                    .put("section", CloudServices.sectionOf(r.type, r.params).name),
            )
        }
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
