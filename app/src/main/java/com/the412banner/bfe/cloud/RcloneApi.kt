package com.the412banner.bfe.cloud

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import kotlin.coroutines.coroutineContext

/** A configured rclone remote ("account" / "connection" in the UI): its name, backend type and settings. */
data class RcRemote(val name: String, val type: String, val params: Map<String, String>) {
    /** OAuth backends keep their sign-in here; missing means setup never finished. */
    val hasToken: Boolean get() = !params["token"].isNullOrBlank()
}

/** One listing entry. [path] is relative to the remote root; [modTime] is epoch ms (0 when unknown). */
data class RcEntry(
    val path: String,
    val name: String,
    val size: Long,
    val modTime: Long,
    val isDir: Boolean,
    val mimeType: String?,
)

/** Space on a remote, in bytes; -1 for a value the backend doesn't report. */
data class RcAbout(val total: Long, val used: Long, val free: Long)

data class RcOptionExample(val value: String, val help: String, val provider: String)

/** One backend option or setup question, as rclone describes it (`config/providers`, `config/create`). */
data class RcOption(
    val name: String,
    val help: String,
    val default: String,
    val type: String,
    val required: Boolean,
    val isPassword: Boolean,
    val advanced: Boolean,
    val exclusive: Boolean,
    val hidden: Boolean,
    val provider: String,
    val examples: List<RcOptionExample>,
) {
    /** First line of the help: rclone's short title for the option. */
    val title: String get() = help.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removeSuffix(".") ?: name
    val isBool: Boolean get() = type == "bool"

    /** rclone's provider filter: "" = always, "A,B" = only for those providers, "!A,B" = all but those. */
    fun appliesTo(selectedProvider: String?): Boolean {
        if (provider.isBlank() || selectedProvider == null) return true
        val negate = provider.startsWith("!")
        val list = provider.removePrefix("!").split(',').map { it.trim() }
        return if (negate) selectedProvider !in list else selectedProvider in list
    }

    companion object {
        fun parse(o: JSONObject): RcOption {
            val ex = o.optJSONArray("Examples")
            val examples = (0 until (ex?.length() ?: 0)).mapNotNull { i ->
                ex?.optJSONObject(i)?.let { e ->
                    RcOptionExample(e.optString("Value", ""), e.optString("Help", ""), e.optString("Provider", ""))
                }
            }
            return RcOption(
                name = o.optString("Name", ""),
                help = o.optString("Help", ""),
                default = o.optString("DefaultStr", "").let { if (it == "<nil>") "" else it },
                type = o.optString("Type", "string"),
                required = o.optBoolean("Required", false),
                isPassword = o.optBoolean("IsPassword", false),
                advanced = o.optBoolean("Advanced", false),
                exclusive = o.optBoolean("Exclusive", false),
                // OptionHideConfigurator = 2: rclone itself never asks for these in its config UI.
                hidden = (o.optInt("Hide", 0) and 2) != 0,
                provider = o.optString("Provider", ""),
                examples = examples,
            )
        }
    }
}

/** A backend rclone can talk to (`config/providers`). */
data class RcProvider(val name: String, val description: String, val options: List<RcOption>, val hidden: Boolean)

/**
 * One step of rclone's non-interactive config state machine. [option] is the next question (null when
 * there is none); the flow is finished when [state] is empty. [error] is a message to show with it.
 */
data class RcConfigStep(val state: String, val option: RcOption?, val error: String, val result: String) {
    val isDone: Boolean get() = state.isEmpty()

    companion object {
        fun parse(o: JSONObject?): RcConfigStep {
            if (o == null) return RcConfigStep("", null, "", "")
            val opt = o.optJSONObject("Option")?.let { RcOption.parse(it) }
            return RcConfigStep(o.optString("State", ""), opt, o.optString("Error", ""), o.optString("Result", ""))
        }
    }
}

/**
 * Typed operations over [RcClient]: the vocabulary BFE's Cloud and Remote screens (and later a
 * file-manager storage backend for remotes) are built on. Paths are always split into an fs string
 * ("name:" for a remote, "/" for the local filesystem) and a path inside it, so names and paths never
 * need quoting. Every call blocks; use from Dispatchers.IO.
 *
 * A future `RcloneLoc` + StorageBackend maps 1:1 onto this: listChildren → [list], createFolder →
 * [mkdir], rename → [moveFile] / [startMoveDir], delete → [deleteFile] / [purge], openInputStream →
 * [RcClient.openRead], cross-backend copies → [startCopyFile] / [startCopyDir] with local paths via
 * [LOCAL_FS] + [localPath].
 */
class RcloneApi(val rc: RcClient) {

    fun version(): String = rc.call("core/version").optString("version", "")

    /** Every configured remote with its settings (secrets stay obscured as rclone stores them). */
    fun remotes(): List<RcRemote> {
        val dump = rc.call("config/dump")
        return dump.keys().asSequence().mapNotNull { name ->
            val o = dump.optJSONObject(name) ?: return@mapNotNull null
            val params = o.keys().asSequence().associateWith { k -> o.optString(k, "") }
            RcRemote(name, params["type"].orEmpty(), params)
        }.sortedBy { it.name.lowercase() }.toList()
    }

    fun providers(): List<RcProvider> {
        val arr = rc.call("config/providers").optJSONArray("providers") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val opts = p.optJSONArray("Options")
            RcProvider(
                name = p.optString("Name", ""),
                description = p.optString("Description", "").ifBlank { p.optString("Name", "") },
                options = (0 until (opts?.length() ?: 0)).mapNotNull { j -> opts?.optJSONObject(j)?.let { RcOption.parse(it) } },
                hidden = p.optBoolean("Hide", false),
            )
        }
    }

    fun list(remote: String, path: String): List<RcEntry> = listFs(fsOf(remote), path)

    fun listFs(fs: String, path: String): List<RcEntry> {
        val out = rc.call("operations/list", JSONObject().put("fs", fs).put("remote", path))
        val arr = out.optJSONArray("list") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseEntry(it) } }
    }

    /** The entry at [path], or null if nothing is there. */
    fun stat(remote: String, path: String): RcEntry? {
        val out = rc.call("operations/stat", JSONObject().put("fs", fsOf(remote)).put("remote", path))
        return out.optJSONObject("item")?.let { parseEntry(it) }
    }

    /** Quota, or null when the backend can't report it. */
    fun about(remote: String): RcAbout? = runCatching {
        val o = rc.call("operations/about", JSONObject().put("fs", fsOf(remote)), readTimeoutMs = 30_000)
        fun num(k: String) = if (o.has(k) && !o.isNull(k)) o.optLong(k, -1L) else -1L
        RcAbout(total = num("total"), used = num("used"), free = num("free"))
    }.getOrNull()?.takeIf { it.total >= 0 || it.used >= 0 || it.free >= 0 }

    fun mkdir(remote: String, path: String) {
        rc.call("operations/mkdir", JSONObject().put("fs", fsOf(remote)).put("remote", path))
    }

    fun deleteFile(remote: String, path: String) {
        rc.call("operations/deletefile", JSONObject().put("fs", fsOf(remote)).put("remote", path))
    }

    /** Deletes a folder and everything in it. */
    fun purge(remote: String, path: String) {
        rc.call("operations/purge", JSONObject().put("fs", fsOf(remote)).put("remote", path), readTimeoutMs = 600_000)
    }

    /** Moves or renames one file (server-side when the backend can). */
    fun moveFile(srcFs: String, srcPath: String, dstFs: String, dstPath: String) {
        rc.call("operations/movefile", fileParams(srcFs, srcPath, dstFs, dstPath), readTimeoutMs = 600_000)
    }

    fun startCopyFile(srcFs: String, srcPath: String, dstFs: String, dstPath: String): Long =
        rc.startJob("operations/copyfile", fileParams(srcFs, srcPath, dstFs, dstPath))

    /** Copies a whole folder (merging into [dstFs]); fs strings carry the folder paths. */
    fun startCopyDir(srcFs: String, dstFs: String): Long =
        rc.startJob("sync/copy", JSONObject().put("srcFs", srcFs).put("dstFs", dstFs).put("createEmptySrcDirs", true))

    /** Moves a folder; rclone uses a server-side directory move when the backend has one. */
    fun startMoveDir(srcFs: String, dstFs: String): Long =
        rc.startJob(
            "sync/move",
            JSONObject().put("srcFs", srcFs).put("dstFs", dstFs).put("createEmptySrcDirs", true).put("deleteEmptySrcDirs", true),
        )

    // ── Config ───────────────────────────────────────────────────────────

    /**
     * Creates (or replaces) remote [name] and starts its setup non-interactively. Plain passwords in
     * [params] are obscured by rclone. Keys starting with "config_" are one-shot hints, not saved.
     */
    fun createRemote(name: String, type: String, params: Map<String, String>): RcConfigStep {
        val out = rc.call(
            "config/create",
            JSONObject().put("name", name).put("type", type).put("parameters", JSONObject(params))
                .put("opt", JSONObject().put("nonInteractive", true).put("obscure", true)),
            readTimeoutMs = 300_000,
        )
        return RcConfigStep.parse(out)
    }

    /** Re-runs a remote's setup (e.g. to sign in again) from the start. */
    fun reconfigure(name: String, params: Map<String, String> = emptyMap()): RcConfigStep {
        val out = rc.call(
            "config/update",
            JSONObject().put("name", name).put("parameters", JSONObject(params))
                .put("opt", JSONObject().put("nonInteractive", true)),
            readTimeoutMs = 300_000,
        )
        return RcConfigStep.parse(out)
    }

    /** Answers the current setup question ([state] from the previous step) with [result]. */
    fun continueConfig(name: String, state: String, result: String, hints: Map<String, String> = emptyMap()): RcConfigStep =
        RcConfigStep.parse(rc.call("config/update", continueParams(name, state, result, hints), readTimeoutMs = 300_000))

    /** Same as [continueConfig] but as a job: the step that runs the OAuth browser sign-in blocks until it's done. */
    fun startContinueConfig(name: String, state: String, result: String, hints: Map<String, String> = emptyMap()): Long =
        rc.startJob("config/update", continueParams(name, state, result, hints))

    /** The sign-in page while rclone's OAuth listener (127.0.0.1:53682) is waiting, else null. */
    fun oauthUrl(): String? {
        val o = rc.call("config/oauthstatus", readTimeoutMs = 15_000)
        return if (o.optString("status", "") == "running") o.optString("authUrl", "").ifBlank { null } else null
    }

    fun oauthStop() {
        runCatching { rc.call("config/oauthstop", readTimeoutMs = 15_000) }
    }

    fun deleteRemote(name: String) {
        rc.call("config/delete", JSONObject().put("name", name))
    }

    private fun continueParams(name: String, state: String, result: String, hints: Map<String, String>) =
        JSONObject().put("name", name).put("parameters", JSONObject(hints))
            .put("opt", JSONObject().put("nonInteractive", true).put("continue", true).put("state", state).put("result", result))

    private fun fileParams(srcFs: String, srcPath: String, dstFs: String, dstPath: String) =
        JSONObject().put("srcFs", srcFs).put("srcRemote", srcPath).put("dstFs", dstFs).put("dstRemote", dstPath)

    companion object {
        /** The local filesystem as an rclone fs; pair with [localPath]. */
        const val LOCAL_FS = "/"

        /** rclone's `default_time` (2000-01-01T00:00:00Z), reported when a backend has no date. */
        private const val RCLONE_DEFAULT_TIME_MS = 946_684_800_000L

        fun fsOf(remote: String) = "$remote:"

        /** An fs string for a folder inside a remote ("name:a/b"). */
        fun fsOf(remote: String, path: String) = "$remote:" + path.trim('/')

        /** An absolute local path as a path inside [LOCAL_FS]. */
        fun localPath(absolute: String) = absolute.trimStart('/')

        fun join(dir: String, name: String) = if (dir.isEmpty()) name else dir.trimEnd('/') + "/" + name

        fun parentOf(path: String) = path.substringBeforeLast('/', "")

        internal fun parseEntry(o: JSONObject): RcEntry {
            val mod = o.optString("ModTime", "")
            val ms = (if (mod.isBlank()) 0L else runCatching { OffsetDateTime.parse(mod).toInstant().toEpochMilli() }.getOrDefault(0L))
                // rclone fills unknown dates (e.g. SMB share roots) with its default_time; treat as no date.
                .let { if (it == RCLONE_DEFAULT_TIME_MS) 0L else it }
            val isDir = o.optBoolean("IsDir", false)
            return RcEntry(
                path = o.optString("Path", ""),
                name = o.optString("Name", ""),
                size = if (isDir) -1L else o.optLong("Size", -1L),
                modTime = ms,
                isDir = isDir,
                mimeType = o.optString("MimeType", "").ifBlank { null },
            )
        }
    }
}

/**
 * Polls job [id] until it finishes; returns its output on success, throws [RcException] with rclone's
 * error otherwise. Cancelling the coroutine stops the job too. [onPoll] sees every status.
 */
suspend fun RcClient.awaitJob(id: Long, pollMs: Long = 400, onPoll: (RcClient.JobStatus) -> Unit = {}): JSONObject {
    try {
        while (true) {
            coroutineContext.ensureActive()
            val st = jobStatus(id)
            onPoll(st)
            if (st.finished) {
                if (st.success) return st.output ?: JSONObject()
                throw RcException("job/$id", 500, st.error.ifBlank { "rclone job failed" })
            }
            delay(pollMs)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        stopJob(id)
        throw e
    }
}
