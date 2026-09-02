package com.the412banner.bfe.apk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.apk.ApkSplitInfoCleaner
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ByteInputSource
import com.reandroid.archive.writer.ZipAligner
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.arsc.value.ValueType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.function.Predicate

/**
 * The clone/edit engine, on ARSCLib (the same binary-manifest + resources.arsc editor APKEditor is
 * built on). Nothing here touches dex: the code keeps living under the ORIGINAL package, so a rename
 * is a manifest/arsc rewrite plus the fixups that keep that code resolvable and installable beside
 * the original app:
 *
 *  1. every relative component class name (`.Foo`, or a bare `Foo` with no dots) on application /
 *     activity / activity-alias / service / receiver / provider (and `targetActivity`) is made
 *     ABSOLUTE with the OLD package — done BEFORE the rename, so the classes still resolve;
 *  2. every `<provider android:authorities>` is moved under the new package (prefix replace when it
 *     embeds the old package, otherwise `newPkg.<authority>`) — same authority twice on one device
 *     is INSTALL_FAILED_CONFLICTING_PROVIDER;
 *  3. package-scoped permissions — `<permission>`/`<permission-group>`/`<permission-tree>`
 *     declarations, `<uses-permission>` and `android:permission`/`readPermission`/`writePermission`
 *     references whose name starts with `oldPkg.` — are re-prefixed (C2D_MESSAGE,
 *     DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION, …);
 *  4. `android:sharedUserId` (+ label/maxSdk) is dropped — a clone can never join the original's uid;
 *  5. `android:taskAffinity` equal to (or under) the old package follows the rename;
 *  6. finally the manifest `package` and the resources.arsc package are renamed together
 *     ([ApkModule.setPackageName]) so `@0x7f…` resources keep resolving.
 *
 * Everything applied is reported back as a fixup line for the result dialog.
 */
object ApkRewriter {
    private const val TAG = "ApkRewriter"

    class Cancelled : IOException("cancelled")

    // android.R.attr ids ARSCLib's AndroidManifest interface doesn't list.
    private const val ID_sharedUserId = 0x0101000b
    private const val ID_permission = 0x01010006
    private const val ID_readPermission = 0x01010007
    private const val ID_writePermission = 0x01010008
    private const val ID_permissionGroup = 0x0101000a
    private const val ID_taskAffinity = 0x01010012
    private const val ID_allowBackup = 0x01010280
    private const val ID_sharedUserLabel = 0x01010261
    private const val ID_sharedUserMaxSdkVersion = 0x01010620
    private const val ID_drawable = 0x01010199

    private val COMPONENT_TAGS = setOf(
        AndroidManifest.TAG_application, AndroidManifest.TAG_activity, AndroidManifest.TAG_activity_alias,
        AndroidManifest.TAG_service, AndroidManifest.TAG_receiver, AndroidManifest.TAG_provider,
    )
    private val PERMISSION_DECL_TAGS = setOf(
        AndroidManifest.TAG_permission, "permission-group", "permission-tree",
        AndroidManifest.TAG_uses_permission, "uses-permission-sdk-23",
    )

    // ── Inspect ──

    /** Reads the editor's prefill from [apk] (a base APK; [splits] only feed the split list). */
    fun inspect(apk: File, splits: List<File> = emptyList()): ApkMeta {
        val module = ApkModule.loadApkFile(apk)
        try {
            val m = module.androidManifest ?: throw IOException("No AndroidManifest.xml in ${apk.name}")
            val app = m.applicationElement
            val manifestEl = m.manifestElement
            var label = ""
            var labelIsRef = false
            app?.searchAttributeByResourceId(AndroidManifest.ID_label)?.let { a ->
                when (a.valueType) {
                    ValueType.STRING -> label = a.valueAsString ?: ""
                    ValueType.REFERENCE -> {
                        labelIsRef = true
                        label = runCatching {
                            module.tableBlock?.getResource(a.data)?.get()?.valueAsString
                        }.getOrNull() ?: ""
                    }
                    else -> Unit
                }
            }
            fun boolAttr(el: ResXmlElement?, id: Int): Boolean? =
                el?.searchAttributeByResourceId(id)?.takeIf { it.valueType == ValueType.BOOLEAN }?.valueAsBoolean
            val authorities = ArrayList<String>()
            m.listApplicationElementsByTag(AndroidManifest.TAG_provider).forEach { p ->
                p.searchAttributeByResourceId(AndroidManifest.ID_authorities)?.valueAsString?.let { authorities += it }
            }
            val perms = ArrayList<String>()
            manifestEl?.listElements(AndroidManifest.TAG_permission)?.forEach { p ->
                p.searchAttributeByResourceId(AndroidManifest.ID_name)?.valueAsString?.let { perms += it }
            }
            return ApkMeta(
                packageName = m.packageName ?: "",
                label = label,
                labelIsReference = labelIsRef,
                versionCode = m.versionCode ?: 0,
                versionName = m.versionName ?: "",
                minSdk = m.minSdkVersion,
                targetSdk = m.targetSdkVersion,
                debuggable = boolAttr(app, AndroidManifest.ID_debuggable),
                allowBackup = boolAttr(app, ID_allowBackup),
                extractNativeLibs = m.isExtractNativeLibs,
                sharedUserId = manifestEl?.searchAttributeByResourceId(ID_sharedUserId)?.valueAsString,
                providerAuthorities = authorities,
                declaredPermissions = perms,
                splitNames = splits.map { it.name },
                sourcePath = apk.absolutePath,
            )
        } finally {
            runCatching { module.close() }
        }
    }

    // ── Rewrite ──

    class Result(val fixups: List<String>, val warnings: List<String>, val extractNativeLibs: Boolean?)

    /**
     * Loads [base] (+ merges [splits] when given), applies [edits], and writes the unsigned,
     * zip-aligned result to [out]. [onStage] reports progress; [isCancelled] is polled between steps.
     */
    fun rewrite(
        base: File,
        splits: List<File>,
        edits: ApkEdits,
        out: File,
        onStage: (ApkJobStage) -> Unit,
        isCancelled: () -> Boolean,
    ): Result {
        val fixups = ArrayList<String>()
        val warnings = ArrayList<String>()
        val logger = object : APKLogger {
            override fun logMessage(msg: String?) { Log.i(TAG, msg ?: "") }
            override fun logError(msg: String?, tr: Throwable?) { Log.e(TAG, msg ?: "", tr) }
            override fun logVerbose(msg: String?) {}
        }
        fun check() { if (isCancelled()) throw Cancelled() }

        onStage(ApkJobStage.PARSING)
        var bundle: ApkBundle? = null
        val module: ApkModule = if (splits.isEmpty()) {
            ApkModule.loadApkFile(base).also { it.setAPKLogger(logger) }
        } else {
            onStage(ApkJobStage.MERGING)
            val b = ApkBundle().also { it.setAPKLogger(logger) }
            bundle = b
            b.addModule(ApkModule.loadApkFile(base, "base"))
            splits.forEach { s -> b.addModule(ApkModule.loadApkFile(s, s.nameWithoutExtension)) }
            check()
            val merged = try {
                b.mergeModules()
            } catch (e: Exception) {
                throw IOException("Couldn't merge the split APKs: ${e.message ?: e.javaClass.simpleName}", e)
            }
            ApkSplitInfoCleaner.cleanSplitInfo(merged)
            merged.setAPKLogger(logger)
            fixups += "Merged ${splits.size} split APK${if (splits.size == 1) "" else "s"} into the base and removed the split markers"
            merged
        }
        try {
            check()
            val manifest = module.androidManifest ?: throw IOException("No AndroidManifest.xml")
            val oldPkg = manifest.packageName ?: throw IOException("Manifest has no package")
            onStage(ApkJobStage.REWRITING)

            val newPkg = edits.packageName?.trim()?.takeIf { it.isNotEmpty() && it != oldPkg }
            if (newPkg != null) {
                PackageNames.problem(newPkg)?.let { throw IOException("Invalid package name: $it") }
                renamePackage(module, manifest, oldPkg, newPkg, fixups)
            }

            edits.label?.let { label ->
                manifest.setApplicationLabel(label)
                // A launcher shows the MAIN activity's own label when it has one — keep them in step.
                manifest.mainActivity?.searchAttributeByResourceId(AndroidManifest.ID_label)?.let { a ->
                    a.setValueAsString(label)
                    fixups += "Main activity label set to \"$label\" as well"
                }
                fixups += "Label → \"$label\""
            }
            edits.versionCode?.let { manifest.setVersionCode(it); fixups += "versionCode → $it" }
            edits.versionName?.let { manifest.setVersionName(it); fixups += "versionName → \"$it\"" }
            edits.minSdk?.let { manifest.setMinSdkVersion(it); fixups += "minSdkVersion → $it" }
            edits.targetSdk?.let { manifest.setTargetSdkVersion(it); fixups += "targetSdkVersion → $it" }
            edits.debuggable?.let { manifest.setDebuggable(it); fixups += "debuggable → $it" }
            edits.allowBackup?.let { v ->
                manifest.orCreateApplicationElement.getOrCreateAndroidAttribute("allowBackup", ID_allowBackup).setValueAsBoolean(v)
                fixups += "allowBackup → $v"
            }
            edits.extractNativeLibs?.let { v ->
                module.setExtractNativeLibs(v)
                fixups += "extractNativeLibs → $v" + if (!v) " (native libs stored uncompressed)" else ""
            }
            check()

            edits.iconImagePath?.let { path ->
                onStage(ApkJobStage.ICON)
                replaceIcon(module, manifest, File(path), fixups, warnings)
            }
            check()

            onStage(ApkJobStage.WRITING)
            val extractNative = manifest.isExtractNativeLibs
            // Drop the source's signing block — apksig produces a fresh one; leaving the stale one
            // in would only be discarded anyway.
            module.setApkSignatureBlock(null)
            manifest.refreshFull()
            if (module.hasTableBlock()) module.tableBlock.refresh()
            out.parentFile?.mkdirs()
            if (out.exists()) out.delete()
            val writer = module.createApkFileWriter(out)
            try {
                // zipalign, in Java: 4 bytes for every STORED entry; native libs on a page boundary
                // (16 KB when they're mapped straight out of the APK, i.e. extractNativeLibs=false).
                writer.setZipAligner(apkAligner(extractNative == false))
                writer.setApkSignatureBlock(null)
                writer.write()
            } finally {
                runCatching { writer.close() }
            }
            return Result(fixups, warnings, extractNative)
        } finally {
            runCatching { module.close() }
            runCatching { bundle?.close() }
        }
    }

    /** ARSCLib's writer-side aligner: 4-byte default, page alignment for `lib/**\/*.so`. */
    fun apkAligner(pageAlignLibs16k: Boolean): ZipAligner = ZipAligner().apply {
        setDefaultAlignment(4)
        setFileAlignment(Predicate<String> { n -> n.startsWith("lib/") && n.endsWith(".so") }, if (pageAlignLibs16k) 16384 else 4096)
    }

    // ── Rename fixups ──

    private fun renamePackage(module: ApkModule, manifest: AndroidManifestBlock, oldPkg: String, newPkg: String, fixups: MutableList<String>) {
        val app = manifest.applicationElement
        val manifestEl = manifest.manifestElement ?: throw IOException("Manifest has no <manifest> element")

        // 1. Absolute component class names (before the rename, against the OLD package).
        if (app != null) {
            var absolutized = 0
            val elements = ArrayList<ResXmlElement>().apply { add(app); app.recursiveElements().forEach { add(it) } }
            for (el in elements) {
                if (el.name !in COMPONENT_TAGS) continue
                for (id in intArrayOf(AndroidManifest.ID_name, AndroidManifest.ID_targetActivity)) {
                    val a = el.searchAttributeByResourceId(id) ?: continue
                    if (a.valueType != ValueType.STRING) continue
                    val v = a.valueAsString ?: continue
                    val abs = absoluteClassName(v, oldPkg) ?: continue
                    a.setValueAsString(abs)
                    absolutized++
                }
            }
            if (absolutized > 0) fixups += "Made $absolutized relative component class name${if (absolutized == 1) "" else "s"} absolute under $oldPkg"

            // 2. Provider authorities.
            for (p in manifest.listApplicationElementsByTag(AndroidManifest.TAG_provider)) {
                val a = p.searchAttributeByResourceId(AndroidManifest.ID_authorities) ?: continue
                if (a.valueType != ValueType.STRING) continue
                val old = a.valueAsString ?: continue
                val renamed = old.split(';').joinToString(";") { auth ->
                    val t = auth.trim()
                    when {
                        t.isEmpty() -> t
                        t == oldPkg -> newPkg
                        t.startsWith("$oldPkg.") -> newPkg + t.substring(oldPkg.length)
                        // Doesn't embed the package but would still collide with the original install.
                        else -> "$newPkg.$t"
                    }
                }
                if (renamed != old) {
                    a.setValueAsString(renamed)
                    fixups += "Provider authorities \"$old\" → \"$renamed\""
                }
            }

            // 3b. Permission REFERENCES on components (android:permission / read / write).
            var permRefs = 0
            for (el in elements) {
                for (id in intArrayOf(ID_permission, ID_readPermission, ID_writePermission)) {
                    val a = el.searchAttributeByResourceId(id) ?: continue
                    if (a.valueType != ValueType.STRING) continue
                    val v = a.valueAsString ?: continue
                    if (v.startsWith("$oldPkg.")) { a.setValueAsString(newPkg + v.substring(oldPkg.length)); permRefs++ }
                }
            }
            if (permRefs > 0) fixups += "Re-prefixed $permRefs component permission reference${if (permRefs == 1) "" else "s"}"

            // 5. taskAffinity.
            var affinities = 0
            for (el in elements) {
                val a = el.searchAttributeByResourceId(ID_taskAffinity) ?: continue
                if (a.valueType != ValueType.STRING) continue
                val v = a.valueAsString ?: continue
                val nv = when {
                    v == oldPkg -> newPkg
                    v.startsWith("$oldPkg.") -> newPkg + v.substring(oldPkg.length)
                    else -> null
                } ?: continue
                a.setValueAsString(nv); affinities++
            }
            if (affinities > 0) fixups += "Rewrote $affinities taskAffinity value${if (affinities == 1) "" else "s"} to $newPkg"
        }

        // 3a. Permission declarations + uses-permission at the manifest level.
        var permDecls = 0
        for (el in manifestEl.recursiveElements().asSequence().toList()) {
            if (el.name !in PERMISSION_DECL_TAGS) continue
            for (id in intArrayOf(AndroidManifest.ID_name, ID_permissionGroup)) {
                val a = el.searchAttributeByResourceId(id) ?: continue
                if (a.valueType != ValueType.STRING) continue
                val v = a.valueAsString ?: continue
                if (v.startsWith("$oldPkg.")) {
                    a.setValueAsString(newPkg + v.substring(oldPkg.length)); permDecls++
                }
            }
        }
        if (permDecls > 0) fixups += "Renamed $permDecls package-scoped permission name${if (permDecls == 1) "" else "s"} ($oldPkg.* → $newPkg.*)"

        // 4. sharedUserId.
        val shared = manifestEl.searchAttributeByResourceId(ID_sharedUserId)?.valueAsString
        if (shared != null) {
            manifestEl.removeAttributesWithId(ID_sharedUserId)
            manifestEl.removeAttributesWithId(ID_sharedUserLabel)
            manifestEl.removeAttributesWithId(ID_sharedUserMaxSdkVersion)
            fixups += "Dropped android:sharedUserId=\"$shared\" (a clone can't share the original's uid)"
        }

        // 6. The package itself: manifest + resources.arsc package block.
        module.setPackageName(newPkg)
        fixups += "Package $oldPkg → $newPkg (manifest + resources.arsc)"
    }

    /** `.Foo` → `pkg.Foo`; bare `Foo` → `pkg.Foo`; already-qualified → null (unchanged). */
    private fun absoluteClassName(name: String, pkg: String): String? = when {
        name.isEmpty() -> null
        name.startsWith(".") -> pkg + name
        !name.contains('.') -> "$pkg.$name"
        else -> null
    }

    // ── Icon ──

    private val BITMAP_EXT = setOf("png", "webp", "jpg", "jpeg")

    private fun replaceIcon(module: ApkModule, manifest: AndroidManifestBlock, image: File, fixups: MutableList<String>, warnings: MutableList<String>) {
        val src = BitmapFactory.decodeFile(image.absolutePath) ?: run {
            warnings += "Couldn't decode ${image.name} — icon left unchanged"; return
        }
        val table = module.tableBlock ?: run { warnings += "No resources.arsc — icon left unchanged"; return }
        val ids = listOf(manifest.iconResourceId, manifest.roundIconResourceId).filter { it != 0 }.distinct()
        if (ids.isEmpty()) { warnings += "Manifest declares no android:icon — nothing to replace"; return }
        var replaced = 0
        for (id in ids) {
            val re = table.getResource(id) ?: continue
            replaced += replaceResourceImages(module, re, src, foreground = false, depth = 0)
        }
        if (replaced == 0) warnings += "Icon resources weren't bitmaps or adaptive icons BFE could rewrite — icon left unchanged"
        else fixups += "Launcher icon replaced ($replaced image${if (replaced == 1) "" else "s"} across densities)"
    }

    /**
     * For every density entry of [re]: a bitmap file is overwritten with [src] scaled for that
     * density; an adaptive-icon XML has its `<foreground>` drawable rewritten (image inset to the
     * 66/108 safe zone) — or, when that foreground isn't a bitmap (a vector), the adaptive entry is
     * repointed at a new PNG; a reference entry is followed. Returns how many images were written.
     */
    private fun replaceResourceImages(module: ApkModule, re: ResourceEntry, src: Bitmap, foreground: Boolean, depth: Int): Int {
        if (depth > 4) return 0
        var count = 0
        val table = module.tableBlock
        val it = re.iterator(true)
        while (it.hasNext()) {
            val entry = it.next()
            val rv = entry.resValue ?: continue
            when (rv.valueType) {
                ValueType.STRING -> {
                    val path = rv.valueAsString ?: continue
                    val ext = path.substringAfterLast('.', "").lowercase()
                    val dpi = entry.resConfig?.density?.flag ?: 0
                    if (ext in BITMAP_EXT) {
                        writeIconFile(module, path, src, sizeFor(dpi, foreground), foreground)
                        count++
                    } else if (ext == "xml") {
                        val fgId = adaptiveForegroundRef(module, path)
                        val fgRes = fgId?.let { table.getResource(it) }
                        val n = if (fgRes != null) replaceResourceImages(module, fgRes, src, foreground = true, depth = depth + 1) else 0
                        if (n > 0) count += n
                        else {
                            // Vector/unknown foreground: repoint this adaptive entry at a plain PNG.
                            val newPath = path.substringBeforeLast('.') + "_bfe.png"
                            writeIconFile(module, newPath, src, sizeFor(dpi, false), false)
                            entry.setValueAsString(newPath)
                            count++
                        }
                    }
                }
                ValueType.REFERENCE -> {
                    val target = table.getResource(rv.data) ?: continue
                    count += replaceResourceImages(module, target, src, foreground, depth + 1)
                }
                else -> Unit
            }
        }
        return count
    }

    /** The `android:drawable` reference of an adaptive icon's `<foreground>`, or null. */
    private fun adaptiveForegroundRef(module: ApkModule, xmlPath: String): Int? = runCatching {
        val doc = module.loadResXmlDocument(xmlPath) ?: return null
        val root = doc.documentElement ?: return null
        if (root.name != "adaptive-icon") return null
        val fg = root.getElement("foreground") ?: return null
        val a = fg.searchAttributeByResourceId(ID_drawable) ?: return null
        if (a.valueType == ValueType.REFERENCE) a.data else null
    }.getOrNull()

    /** Launcher icon = 48dp, adaptive foreground = 108dp; unknown/any density gets xxhdpi. */
    private fun sizeFor(dpi: Int, foreground: Boolean): Int {
        val d = if (dpi in 100..700) dpi else 480
        return (if (foreground) 108 else 48) * d / 160
    }

    private fun writeIconFile(module: ApkModule, path: String, src: Bitmap, size: Int, foreground: Boolean) {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // Adaptive foregrounds are masked to the inner 66/108 — inset so nothing important is cut.
        val inset = if (foreground) (size * 21 / 108) else 0
        canvas.drawBitmap(src, null, Rect(inset, inset, size - inset, size - inset), paint)
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bmp.recycle()
        module.add(ByteInputSource(bytes, path).also { it.setUncompressed(true) })
    }
}
