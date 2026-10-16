package com.the412banner.bfe.unpack

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

/** A fully decided extraction: what to run, with which engine, into where. */
data class UnpackJob(
    /** The files the engine runs on; for a GOG DLC batch the base installer first, then the DLC setups. */
    val archives: List<String>,
    val dest: String,
    val mmt: Int,
    val bufferBytes: Int,
    val isInno: Boolean,
    val totalSize: Long,
    val engine: String,
)

/**
 * The background "Extract to ./<Archive name>/" path: makes the same choices the Unpack screen defaults
 * to (engine route, destination folder, Max power, 1 MB buffer) without showing it. Anything the screen
 * would stop on (not an archive, a plain program, a container-only repack, missing All-Files access)
 * comes back as [Plan.Refuse] with a one-line reason for the notification.
 */
object QuickExtract {
    sealed interface Plan {
        data class Run(val job: UnpackJob) : Plan
        data class Refuse(val reason: String) : Plan
    }

    /** Plans [file]. [destBase] overrides the folder the named output folder is created in. */
    fun plan(context: Context, file: File, destBase: File? = null): Plan {
        if (!file.isFile) return Plan.Refuse("${file.name} couldn't be read.")

        val installer = SevenZip.resolveInnoTarget(file)
        val plan = if (installer != null) planInno(context, installer, destBase) else planArchive(context, file, destBase)
        if (plan is Plan.Run && needsAllFilesAccess(context, plan.job.dest)) {
            return Plan.Refuse("BFE needs All files access to extract here. Tap to open BFE and allow it.")
        }
        return plan
    }

    private fun planInno(context: Context, installer: File, destBase: File?): Plan {
        val cls = SevenZip.classifyInno(context, installer)
        // As on the screen: a folder named for the repack, next to the repack folder.
        val name = installer.parentFile?.name?.takeIf { it.isNotBlank() } ?: "game"
        val base = destBase ?: installer.parentFile?.parentFile ?: installer.parentFile
        val dest = File(base, name).absolutePath
        val payload = installer.parentFile?.listFiles()
            ?.filter { it.isFile && (it.extension.equals("bin", true) || it == installer) }
            ?.sumOf { it.length() } ?: installer.length()
        return when (cls.route) {
            SevenZip.InnoRoute.INNOEXTRACT -> {
                if (!Innoextract.isAvailable(context)) return Plan.Refuse("The innoextract engine isn't available in this build.")
                // GOG DLC auto-batch, on by default like the screen's checkbox.
                val all = SevenZip.siblingInnoInstallers(installer)
                Plan.Run(UnpackJob(all.map { it.absolutePath }, dest, 1, ReadBuffer.MB1.bytes, true, payload, "inno"))
            }
            SevenZip.InnoRoute.FREEARC_NATIVE -> {
                if (!Unarc.isAvailable(context)) return Plan.Refuse("The unarc engine isn't available in this build.")
                val volume = cls.freeArcArchive ?: installer
                Plan.Run(UnpackJob(listOf(volume.absolutePath), dest, 1, ReadBuffer.MB1.bytes, true, payload, "unarc"))
            }
            SevenZip.InnoRoute.CONTAINER_ONLY ->
                Plan.Refuse("${installer.name} is a ${cls.compression ?: "FreeArc"} repack that can't be unpacked in-app.")
        }
    }

    private fun planArchive(context: Context, file: File, destBase: File?): Plan {
        if (!SevenZip.isAvailable(context)) return Plan.Refuse("The 7-Zip engine isn't available in this build.")
        val type = SevenZip.list(context, file)?.type
            ?: return Plan.Refuse("${file.name} isn't a recognized archive or disc image.")
        if (type in SevenZip.EXECUTABLE_TYPES) return Plan.Refuse("${file.name} is a program, not an installer or archive.")
        val base = destBase ?: file.parentFile
        val dest = File(base, SevenZip.suggestedTargetName(file)).absolutePath
        val mmt = UnpackManager.mmtFor(PowerMode.MAX, Runtime.getRuntime().availableProcessors())
        return Plan.Run(UnpackJob(listOf(file.absolutePath), dest, mmt, ReadBuffer.MB1.bytes, false, file.length(), "7z"))
    }

    /** Same gate as the screen: native engines write shared storage directly, which needs All-Files access. */
    private fun needsAllFilesAccess(context: Context, dest: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) return false
        return dest.startsWith("/storage/") && !dest.startsWith(context.filesDir.absolutePath)
    }
}
