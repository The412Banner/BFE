package com.the412banner.bfe.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONObject

/**
 * Per-extension "Always use" choices from the Open with sheet: tapping a `.txt` whose extension has a
 * default opens that exact app directly; the context menu's "Open with…" always shows the sheet so a
 * default can be changed. Viewed and cleared from Settings → Default apps.
 */
object DefaultApps {
    data class Entry(val ext: String, val pkg: String, val cls: String, val label: String, val mime: String)

    private const val PREFS = "bfe_default_apps"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Lower-case extension of [name] without the dot, or "" when it has none. */
    fun extOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 12 && it != name.lowercase() } ?: ""

    fun get(ctx: Context, ext: String): Entry? {
        if (ext.isEmpty()) return null
        val raw = prefs(ctx).getString(ext, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Entry(ext, o.getString("pkg"), o.getString("cls"), o.optString("label", o.getString("pkg")), o.optString("mime", "*/*"))
        }.getOrNull()
    }

    fun set(ctx: Context, entry: Entry) {
        if (entry.ext.isEmpty()) return
        val o = JSONObject()
            .put("pkg", entry.pkg).put("cls", entry.cls).put("label", entry.label).put("mime", entry.mime)
        prefs(ctx).edit().putString(entry.ext, o.toString()).apply()
    }

    fun remove(ctx: Context, ext: String) = prefs(ctx).edit().remove(ext).apply()

    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()

    fun all(ctx: Context): List<Entry> =
        prefs(ctx).all.keys.sorted().mapNotNull { get(ctx, it) }

    /** The VIEW intent for [uri] aimed at one exact app, with read (and optionally write) grants. */
    fun viewIntent(uri: Uri, mime: String, component: ComponentName, write: Boolean): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            this.component = component
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (write) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    /** Whether the saved app is still installed and still exported for VIEW. */
    fun isResolvable(ctx: Context, entry: Entry, uri: Uri): Boolean = runCatching {
        val intent = viewIntent(uri, entry.mime, ComponentName(entry.pkg, entry.cls), write = false)
        ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null ||
            ctx.packageManager.resolveActivity(intent, 0) != null
    }.getOrDefault(false)
}
