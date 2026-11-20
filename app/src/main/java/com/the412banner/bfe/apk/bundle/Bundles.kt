package com.the412banner.bfe.apk.bundle

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Split-APK bundles: XAPK (APKPure), APKM (APKMirror), APKS (SAI export or bundletool output) and a
 * plain folder of split APKs (what `pm path` lists for an installed app). A bundle is read from its
 * zip directory (or the folder listing) without extracting anything; the metadata file each format
 * ships (manifest.json / info.json / meta.sai_v2.json / meta.sai_v1.json) fills in name and version
 * when present.
 *
 * Every APK is classified by its split name, taken from the XAPK manifest when it lists one, else from
 * the file name ("split_config.arm64_v8a.apk", "config.xxhdpi.apk", bundletool's
 * "splits/base-en.apk"). Config splits are processor (ABI), screen density or language parts;
 * [autoSelect] keeps the ones that fit this device, like the Play Store does.
 */
object Bundles {

    val EXTENSIONS = setOf("xapk", "apks", "apkm")

    fun isBundleName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    enum class Kind { BASE, FEATURE, ABI, DENSITY, LANGUAGE, OTHER }

    /** One APK inside the bundle. [entry] is the zip entry name, or the file name for a folder. */
    data class Part(
        val entry: String,
        val size: Long,
        val kind: Kind,
        /** The module the part belongs to: "base" or a feature module's name. */
        val module: String,
        /** ABI / density / language token for config parts ("arm64_v8a", "xxhdpi", "en"). */
        val config: String?,
    ) {
        val fileName: String get() = entry.substringAfterLast('/')
    }

    /** XAPK game data: [entry] in the zip, [target] relative to the shared storage root. */
    data class Obb(val entry: String, val size: Long, val target: String)

    data class Info(
        val source: File,
        val isFolder: Boolean,
        val parts: List<Part>,
        val obbs: List<Obb>,
        val packageName: String?,
        val label: String?,
        val versionName: String?,
        val versionCode: Long?,
        val minSdk: Int?,
        /** Zip entry of the icon the format ships (icon.png), if any. */
        val iconEntry: String?,
    ) {
        val base: Part? get() = parts.firstOrNull { it.kind == Kind.BASE }
    }

    class NotABundle(msg: String) : Exception(msg)

    private val ABIS = setOf("armeabi", "armeabi_v7a", "arm64_v8a", "x86", "x86_64", "mips", "mips64", "riscv64")
    private val DENSITIES = linkedMapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
    )
    private val LANGUAGE = Regex("^[a-z]{2,3}([_-][A-Za-z0-9+]+)*$")

    /** Reads a bundle file (zip) or a folder of APKs. Throws [NotABundle] when there is no base APK. */
    fun read(source: File): Info = if (source.isDirectory) readFolder(source) else readZip(source)

    private fun readZip(file: File): Info = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
        fun text(name: String): String? = zip.getEntry(name)?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }
        val meta = parseMeta(text("manifest.json"), text("info.json"), text("meta.sai_v2.json") ?: text("meta.sai_v1.json"))
        // bundletool's own .apks: splits/… is the real install set; standalones/ and universal.apk are
        // alternatives for older devices and must not be installed together with it.
        val hasBundletoolSplits = entries.any { it.name.startsWith("splits/") && it.name.endsWith(".apk") }
        val apks = entries.filter { e ->
            e.name.endsWith(".apk", ignoreCase = true) &&
                (!hasBundletoolSplits || e.name.startsWith("splits/"))
        }
        val parts = classifyAll(apks.map { it.name to it.size }, meta.splitIds)
        val obbs = entries.filter { it.name.endsWith(".obb", ignoreCase = true) }.map { e ->
            Obb(e.name, e.size, meta.obbTargets[e.name] ?: defaultObbTarget(e.name, meta.packageName))
        }
        val icon = listOf("icon.png", "icon.webp", "icon.jpg").firstOrNull { zip.getEntry(it) != null }
        finish(file, false, parts, obbs, meta, icon)
    }

    private fun readFolder(dir: File): Info {
        val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }.orEmpty().sortedBy { it.name }
        val parts = classifyAll(apks.map { it.name to it.length() }, emptyMap())
        val obbs = dir.listFiles { f -> f.isFile && f.name.endsWith(".obb", ignoreCase = true) }.orEmpty().map { f ->
            Obb(f.name, f.length(), defaultObbTarget(f.name, null))
        }
        return finish(dir, true, parts, obbs, Meta(), null)
    }

    private fun finish(source: File, folder: Boolean, parts: List<Part>, obbs: List<Obb>, meta: Meta, icon: String?): Info {
        if (parts.none { it.kind == Kind.BASE }) {
            throw NotABundle(if (parts.isEmpty()) "No APKs inside" else "No base APK inside (only split parts)")
        }
        // An OBB with no package in its path can only be placed once the package is known.
        val pkg = meta.packageName
        val fixedObbs = obbs.map { o ->
            if (o.target.startsWith("Android/obb//") && pkg != null) o.copy(target = "Android/obb/$pkg/" + o.target.substringAfterLast('/')) else o
        }
        return Info(source, folder, parts, fixedObbs, pkg, meta.label, meta.versionName, meta.versionCode, meta.minSdk, icon)
    }

    /**
     * Re-reads every part's own AndroidManifest (streamed, nothing extracted) so the split names come
     * from the APKs rather than their file names, and fills in package / version from the base when
     * the format shipped no metadata. Parts whose manifest can't be read keep the file-name guess.
     */
    fun refineWithManifests(info: Info): Info {
        val zip = if (info.isFolder) null else ZipFile(info.source)
        try {
            val ids = HashMap<String, String>()
            var baseFacts: AndroidManifestBlock? = null
            for (p in info.parts) {
                val m = runCatching { open(info, p.entry, zip).use { manifestOf(it) } }.getOrNull() ?: continue
                val split = m.split
                ids[p.entry] = if (split.isNullOrEmpty()) "base" else split
                if (split.isNullOrEmpty()) baseFacts = m
            }
            if (ids.isEmpty() || ids.values.count { it == "base" } != 1) return info
            val parts = info.parts.map { p ->
                val id = ids[p.entry] ?: return@map p
                // bundletool names are already exact; everything else is re-classified by the real name.
                if (p.entry.startsWith("splits/")) p else classify(p.entry, p.size, id)
            }
            val b = baseFacts
            val pkg = info.packageName ?: b?.packageName
            return info.copy(
                parts = parts,
                packageName = pkg,
                versionName = info.versionName ?: b?.versionName,
                versionCode = info.versionCode ?: b?.versionCode?.toLong(),
                minSdk = info.minSdk ?: b?.minSdkVersion,
                label = info.label ?: b?.applicationLabelString?.takeIf { it.isNotBlank() && !it.startsWith("@") },
                obbs = info.obbs.map { o ->
                    if (o.target.startsWith("Android/obb//") && pkg != null) o.copy(target = "Android/obb/$pkg/" + o.target.substringAfterLast('/')) else o
                },
            )
        } finally {
            runCatching { zip?.close() }
        }
    }

    /** The binary AndroidManifest.xml of the APK in [apk] (a stream), read without the rest of it. */
    private fun manifestOf(apk: InputStream): AndroidManifestBlock? {
        val z = ZipInputStream(apk.buffered())
        while (true) {
            val e = z.nextEntry ?: return null
            if (e.name == "AndroidManifest.xml") return AndroidManifestBlock.load(z)
        }
    }

    // ── Metadata ──

    internal data class Meta(
        val packageName: String? = null,
        val label: String? = null,
        val versionName: String? = null,
        val versionCode: Long? = null,
        val minSdk: Int? = null,
        /** XAPK: zip entry → split id ("base", "config.arm64_v8a"). */
        val splitIds: Map<String, String> = emptyMap(),
        /** XAPK: OBB zip entry → install path. */
        val obbTargets: Map<String, String> = emptyMap(),
    )

    /** XAPK manifest.json, APKM info.json, SAI meta json — whichever is present, in that order. */
    internal fun parseMeta(xapk: String?, apkm: String?, sai: String?): Meta {
        xapk?.let { s ->
            runCatching {
                val o = JSONObject(s)
                val ids = HashMap<String, String>()
                o.optJSONArray("split_apks")?.let { a ->
                    for (i in 0 until a.length()) {
                        val e = a.optJSONObject(i) ?: continue
                        val f = e.optString("file"); val id = e.optString("id")
                        if (f.isNotEmpty() && id.isNotEmpty()) ids[f] = id
                    }
                }
                val obb = HashMap<String, String>()
                o.optJSONArray("expansions")?.let { a ->
                    for (i in 0 until a.length()) {
                        val e = a.optJSONObject(i) ?: continue
                        val f = e.optString("file"); val p = e.optString("install_path")
                        if (f.isNotEmpty()) obb[f] = p.ifEmpty { f }
                    }
                }
                return Meta(
                    packageName = o.optString("package_name").ifEmpty { null },
                    label = o.optString("name").ifEmpty { null },
                    versionName = o.optString("version_name").ifEmpty { null },
                    versionCode = o.optString("version_code").toLongOrNull(),
                    minSdk = o.optString("min_sdk_version").toIntOrNull(),
                    splitIds = ids, obbTargets = obb,
                )
            }
        }
        apkm?.let { s ->
            runCatching {
                val o = JSONObject(s)
                return Meta(
                    packageName = o.optString("pname").ifEmpty { null },
                    label = o.optString("app_name").ifEmpty { null },
                    versionName = o.optString("release_version").ifEmpty { null },
                    versionCode = o.optString("versioncode").toLongOrNull(),
                    minSdk = o.optString("min_api").toIntOrNull(),
                )
            }
        }
        sai?.let { s ->
            runCatching {
                val o = JSONObject(s)
                return Meta(
                    packageName = o.optString("package").ifEmpty { null },
                    label = o.optString("label").ifEmpty { null },
                    versionName = o.optString("version_name").ifEmpty { null },
                    versionCode = o.optString("version_code").toLongOrNull(),
                    minSdk = o.optString("min_sdk").toIntOrNull(),
                )
            }
        }
        return Meta()
    }

    private fun defaultObbTarget(entry: String, pkg: String?): String {
        // XAPKs keep the full path ("Android/obb/<pkg>/main.12.<pkg>.obb"); a bare name gets the
        // package from "main.<version>.<package>.obb".
        val norm = entry.replace('\\', '/')
        if (norm.startsWith("Android/obb/")) return norm
        val name = norm.substringAfterLast('/')
        val fromName = Regex("^(main|patch)\\.\\d+\\.(.+)\\.obb$", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(2)
        return "Android/obb/${pkg ?: fromName ?: ""}/$name"
    }

    // ── Classification ──

    /** Classifies APK entries; with exactly one non-config, non-feature APK, that one is the base. */
    internal fun classifyAll(apks: List<Pair<String, Long>>, splitIds: Map<String, String>): List<Part> {
        val raw = apks.map { (name, size) -> classify(name, size, splitIds[name]) }
        if (raw.any { it.kind == Kind.BASE }) return raw
        // No file is called base: an XAPK without ids stores the base as "<package>.apk", SAI/APKM may
        // use the app name. The one APK that isn't a config or "split_" part is the base.
        val candidates = raw.filter { it.kind == Kind.OTHER || it.kind == Kind.FEATURE }
            .filter { !it.fileName.startsWith("split_", true) && !it.fileName.startsWith("config.", true) }
        val only = candidates.singleOrNull() ?: if (raw.size == 1) raw.single() else null
        return if (only == null) raw else raw.map { if (it === only) it.copy(kind = Kind.BASE, module = "base", config = null) else it }
    }

    internal fun classify(entry: String, size: Long, splitId: String?): Part {
        val file = entry.substringAfterLast('/')
        val stem = file.removeSuffix(".apk").removeSuffix(".APK")
        // bundletool: splits/<module>-master.apk, splits/<module>-<config>.apk
        if (entry.startsWith("splits/")) {
            val module = stem.substringBefore('-')
            val rest = stem.substringAfter('-', "master")
            if (rest == "master") return Part(entry, size, if (module == "base") Kind.BASE else Kind.FEATURE, module, null)
            return configPart(entry, size, module, rest)
        }
        val id = splitId ?: stem.removePrefix("split_")
        if (splitId == "base" || id == "base") return Part(entry, size, Kind.BASE, "base", null)
        // "config.arm64_v8a" (base module) or "<feature>.config.xxhdpi" (a feature module's config).
        val cfgAt = id.indexOf("config.")
        if (cfgAt >= 0 && (cfgAt == 0 || id[cfgAt - 1] == '.')) {
            val module = if (cfgAt == 0) "base" else id.substring(0, cfgAt - 1)
            return configPart(entry, size, module, id.substring(cfgAt + "config.".length))
        }
        // Any other "split_x" is a feature module; anything else is unknown (kept, installed as-is).
        return if (splitId != null || file.startsWith("split_", true)) Part(entry, size, Kind.FEATURE, id, null)
        else Part(entry, size, Kind.OTHER, "base", null)
    }

    private fun configPart(entry: String, size: Long, module: String, token: String): Part {
        val t = token.lowercase()
        val kind = when {
            t in ABIS -> Kind.ABI
            t in DENSITIES -> Kind.DENSITY
            LANGUAGE.matches(token) -> Kind.LANGUAGE
            else -> Kind.OTHER
        }
        return Part(entry, size, kind, module, t)
    }

    // ── Device fit ──

    /**
     * The parts to install on a device with [abis] (Build.SUPPORTED_ABIS order), screen [densityDpi]
     * and [languages] (ISO codes, first = preferred): every base / feature / unknown part, and per
     * module the best processor part, the closest density at or above the screen's (else the largest),
     * and the parts for the device languages (English when none of them is there).
     */
    fun autoSelect(parts: List<Part>, abis: List<String>, densityDpi: Int, languages: List<String>): Set<String> {
        val out = LinkedHashSet<String>()
        parts.filter { it.kind == Kind.BASE || it.kind == Kind.FEATURE || it.kind == Kind.OTHER }.forEach { out += it.entry }
        val deviceAbis = abis.map { it.lowercase().replace('-', '_') }
        val langs = languages.map { it.lowercase().substringBefore('-').substringBefore('_') }.filter { it.isNotEmpty() }
        parts.groupBy { it.module }.forEach { (_, mod) ->
            val abiParts = mod.filter { it.kind == Kind.ABI }
            deviceAbis.firstNotNullOfOrNull { a -> abiParts.firstOrNull { it.config == a } }?.let { out += it.entry }
            val dens = mod.filter { it.kind == Kind.DENSITY }.mapNotNull { p -> DENSITIES[p.config]?.let { p to it } }
            if (dens.isNotEmpty()) {
                val pick = dens.filter { it.second >= densityDpi }.minByOrNull { it.second } ?: dens.maxBy { it.second }
                out += pick.first.entry
            }
            val langParts = mod.filter { it.kind == Kind.LANGUAGE }
            val match = langParts.filter { p -> p.config!!.substringBefore('_').substringBefore('-') in langs }
            (match.ifEmpty { langParts.filter { it.config == "en" } }).forEach { out += it.entry }
        }
        return out
    }

    /** A missing processor part means the app's native code isn't there for this device. */
    fun missingAbi(parts: List<Part>, selected: Set<String>): Boolean {
        val withAbi = parts.filter { it.kind == Kind.ABI }.map { it.module }.toSet()
        return withAbi.any { m -> parts.none { it.module == m && it.kind == Kind.ABI && it.entry in selected } }
    }

    /** Short, readable name of a part for the checklist. */
    fun describe(p: Part): String = when (p.kind) {
        Kind.BASE -> "Main app"
        Kind.FEATURE -> "Feature: ${p.module}"
        Kind.ABI -> "Processor: ${p.config!!.replace('_', '-')}" + moduleSuffix(p)
        Kind.DENSITY -> "Screen: ${p.config}" + moduleSuffix(p)
        Kind.LANGUAGE -> "Language: ${p.config}" + moduleSuffix(p)
        Kind.OTHER -> p.fileName
    }

    private fun moduleSuffix(p: Part) = if (p.module == "base") "" else " (${p.module})"

    /** Opens one part (or OBB) for reading: a zip entry, or the file in a folder bundle. */
    fun open(info: Info, entry: String, zip: ZipFile?): InputStream =
        if (info.isFolder) File(info.source, entry).inputStream()
        else zip!!.getInputStream(zip.getEntry(entry) ?: throw java.io.FileNotFoundException(entry))
}
