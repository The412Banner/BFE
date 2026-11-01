package com.the412banner.bfe.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a Windows installer to Bannerlator, which runs it with Wine. Used when BFE can't unpack an
 * installer's data itself: the installer's own Setup.exe carries every decoder it needs.
 *
 * Bannerlator 3.1.7+ has `com.winlator.star.OpenExeActivity` for VIEW intents on .exe files (any of its
 * package flavours). It turns BFE's FileProvider Uri (`…/external/<path>` or `…/root/<path>`) back into
 * the real path and runs the file from where it is, so nothing is copied.
 */
object Bannerlator {
    private const val OPEN_EXE = "com.winlator.star.OpenExeActivity"
    private const val MIME = "application/x-msdownload"

    data class Target(val component: ComponentName, val label: String)

    private fun intentFor(context: Context, exe: File): Intent? {
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exe) }.getOrNull()
            ?: return null
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The installed Bannerlator that can run [exe], or null. */
    fun find(context: Context, exe: File): Target? {
        val intent = intentFor(context, exe) ?: return null
        val pm = context.packageManager
        val ri = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .filter { it.activityInfo?.name == OPEN_EXE }
            .minByOrNull { it.activityInfo.packageName }
            ?: return null
        return Target(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), ri.loadLabel(pm).toString())
    }

    /** Opens [exe] in [target]. False when Android refused to start it. */
    fun open(context: Context, exe: File, target: Target): Boolean {
        val intent = intentFor(context, exe)?.setComponent(target.component)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
