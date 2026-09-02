package com.the412banner.bfe.pack

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Which code path builds the archive. */
enum class PackEngine {
    /** One `7zz a -t<type>` process writing the archive directly. */
    SEVENZIP,
    /** Two `7zz` processes: `a -ttar -so` piped into `a -t<compressor> -si` (tar.gz / tar.xz). */
    SEVENZIP_PIPE,
    /** Java: commons-compress tar streamed through zstd-jni (the bundled 7-Zip can't ENCODE zstd). */
    JAVA_TAR_ZSTD,
}

/**
 * The archive formats "Compress…" can create. The bundled 7-Zip 24.08 lists zstd as decode-only
 * (`7zz i` shows no C flag; `7zz a -tzstd` answers E_NOTIMPL), so the two zstd formats — tzst and
 * the Winlator .wcp, which IS a tar.zst — are built in Java.
 */
enum class PackFormat(
    val label: String,
    val ext: String,
    /** 7-Zip `-t` type for the (outer) archive; null for the Java engine. */
    val sevenZipType: String?,
    val engine: PackEngine,
    val supportsPassword: Boolean,
    val supportsLevel: Boolean,
) {
    ZIP("zip", ".zip", "zip", PackEngine.SEVENZIP, true, true),
    SEVEN_Z("7z", ".7z", "7z", PackEngine.SEVENZIP, true, true),
    TAR("tar", ".tar", "tar", PackEngine.SEVENZIP, false, false),
    TAR_GZ("tar.gz", ".tar.gz", "gzip", PackEngine.SEVENZIP_PIPE, false, true),
    TAR_XZ("tar.xz", ".tar.xz", "xz", PackEngine.SEVENZIP_PIPE, false, true),
    TZST("tzst (tar.zst)", ".tzst", null, PackEngine.JAVA_TAR_ZSTD, false, true),
    WCP("Winlator .wcp", ".wcp", null, PackEngine.JAVA_TAR_ZSTD, false, true);

    val isWcp: Boolean get() = this == WCP

    /** Strips this format's extension from [name] if present (case-insensitive). */
    fun stripExt(name: String): String =
        if (name.lowercase().endsWith(ext)) name.dropLast(ext.length) else name
}

/**
 * Compression effort. [mx] is 7-Zip's `-mx=` (0 store … 9 ultra); [zstd] the zstd level handed to
 * zstd-jni (1 fastest … 19 slow; 19 is what the Winlator content catalogs ship at).
 */
enum class PackLevel(val label: String, val mx: Int, val zstd: Int) {
    STORE("Store", 0, 1),
    FAST("Fast", 1, 3),
    NORMAL("Normal", 5, 6),
    MAX("Max", 9, 19),
}

/**
 * The Winlator content-pack `profile.json` — field names and shape reproduced from Bannerlator's
 * `ContentProfile.java` (MARK_* constants) and `ContentsManager.readProfile()`, which is what has to
 * accept the file for the .wcp to install:
 *
 * ```
 * { "type": "DXVK",                     // ContentProfile.ContentType typeName, matched case-insensitively
 *   "versionName": "2.7.3",             // install dir = <type>/<versionName>-<versionCode>
 *   "versionCode": 1,
 *   "description": "…",
 *   "files": [ {"source": "system32/d3d11.dll", "target": "${system32}/d3d11.dll"}, … ],
 *   "wine": {"binPath": "bin", "libPath": "lib", "prefixPack": "prefixPack.txz"}   // Wine/Proton ONLY
 * }
 * ```
 * `files[].source` is a path inside the archive; `target` uses the `${libdir}` / `${system32}` /
 * `${syswow64}` / `${bindir}` / `${sharedir}` templates the installer maps into its imagefs. Every
 * listed source must exist in the pack or the install fails with ERROR_MISSINGFILES.
 */
data class WcpProfile(
    val type: String,
    val versionName: String,
    val versionCode: Int,
    val description: String,
    val files: List<Pair<String, String>>,
    /** Only for Wine/Proton; null otherwise (the installer only reads "wine" for those two types). */
    val wineBinPath: String? = null,
    val wineLibPath: String? = null,
    val winePrefixPack: String? = null,
) {
    val isWineLike: Boolean get() = type.equals("Wine", true) || type.equals("Proton", true)

    fun toJson(): String {
        val o = JSONObject()
        o.put("type", type)
        o.put("versionName", versionName)
        o.put("versionCode", versionCode)
        o.put("description", description)
        val arr = JSONArray()
        for ((src, dst) in files) arr.put(JSONObject().put("source", src).put("target", dst))
        o.put("files", arr)
        if (isWineLike) {
            o.put(
                "wine", JSONObject()
                    .put("binPath", wineBinPath ?: "bin")
                    .put("libPath", wineLibPath ?: "lib")
                    .put("prefixPack", winePrefixPack ?: "prefixPack.txz"),
            )
        }
        return o.toString(2)
    }

    companion object {
        /** The content types Bannerlator's `ContentProfile.ContentType` knows (its typeName strings). */
        val TYPES = listOf("Wine", "Proton", "DXVK", "D7VK", "VKD3D", "Box64", "WOWBox64", "FEXCore", "VEGAS")

        // Archive-root folder → installer path template. A pack laid out as system32/… syswow64/… lib/…
        // bin/… share/… maps 1:1 onto what the installer copies into its imagefs.
        private val ROOT_TEMPLATES = listOf(
            "system32" to "\${system32}",
            "syswow64" to "\${syswow64}",
            "lib" to "\${libdir}",
            "bin" to "\${bindir}",
            "share" to "\${sharedir}",
        )

        /**
         * Derives the `files[]` list for a pack whose root holds [relativeFiles] (archive-relative,
         * '/'-separated, files only). Entries under a known root folder become source→template pairs;
         * anything else stays in the archive but unlisted (the installer ignores unlisted files).
         * Wine/Proton packs are installed whole via binPath/libPath and list nothing.
         */
        fun deriveFiles(type: String, relativeFiles: List<String>): List<Pair<String, String>> {
            if (type.equals("Wine", true) || type.equals("Proton", true)) return emptyList()
            val out = ArrayList<Pair<String, String>>()
            for (rel in relativeFiles.sorted()) {
                val head = rel.substringBefore('/')
                val tail = rel.substringAfter('/', "")
                if (tail.isEmpty()) continue   // a root-level file has no install location
                val tpl = ROOT_TEMPLATES.firstOrNull { it.first.equals(head, true) }?.second ?: continue
                out.add(rel to "$tpl/$tail")
            }
            return out
        }

        fun fromJson(json: String): WcpProfile? = runCatching {
            val o = JSONObject(json)
            val files = ArrayList<Pair<String, String>>()
            val arr = o.optJSONArray("files")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                files.add(e.getString("source") to e.getString("target"))
            }
            val wine = o.optJSONObject("wine")
            WcpProfile(
                type = o.getString("type"),
                versionName = o.getString("versionName"),
                versionCode = o.getInt("versionCode"),
                description = o.optString("description", ""),
                files = files,
                wineBinPath = wine?.optString("binPath"),
                wineLibPath = wine?.optString("libPath"),
                winePrefixPack = wine?.optString("prefixPack"),
            )
        }.getOrNull()
    }
}

/** Where the archive job is in its lifecycle. Mirrors [com.the412banner.bfe.unpack.UnpackPhase]. */
enum class PackPhase { IDLE, SCANNING, PACKING, DONE, ERROR, CANCELLED }

/**
 * The single, process-wide snapshot of the running (or last) archive-creation job. The foreground
 * [PackService] writes it; the File Manager pill/notification read it. One pack job at a time.
 */
data class PackState(
    val phase: PackPhase = PackPhase.IDLE,
    /** Identifies the job (the service's start time in ms) so a UI waiting on a job can match it. */
    val jobId: Long = 0L,
    val archivePath: String = "",
    val archiveName: String = "",
    val format: PackFormat = PackFormat.ZIP,
    /** Total bytes of the inputs (the progress denominator), from the pre-scan. */
    val totalBytes: Long = 0L,
    val totalFiles: Int = 0,
    val percent: Int = 0,
    val currentFile: String? = null,
    val filesAdded: Int = 0,
    val bytesProcessed: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = -1L,
    val elapsedMs: Long = 0L,
    val errorTail: String? = null,
    /** Final archive size once DONE. */
    val archiveSize: Long = 0L,
) {
    val isRunning: Boolean get() = phase == PackPhase.SCANNING || phase == PackPhase.PACKING
}

/** Process-static holder so the service and Compose share one live [PackState] without binding. */
object PackManager {
    private val _state = MutableStateFlow(PackState())
    val state: StateFlow<PackState> = _state.asStateFlow()

    val current: PackState get() = _state.value

    fun set(next: PackState) { _state.value = next }
    fun update(block: (PackState) -> PackState) { _state.value = block(_state.value) }

    fun clearIfTerminal() {
        val p = _state.value.phase
        if (p == PackPhase.DONE || p == PackPhase.ERROR || p == PackPhase.CANCELLED) _state.value = PackState()
    }
}
