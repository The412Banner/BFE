package com.the412banner.bfe.media

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager

/**
 * The viewer / player's stored choices: Settings → Media toggles (default prefs, so they sit with the
 * other settings), the effects sheet's "Remember these settings", and resume positions (own file,
 * one entry per media file, trimmed to the newest [ResumePolicy.MAX_ENTRIES]).
 */
object MediaPrefs {
    private const val PREF_RESUME = "media_remember_position"
    private const val PREF_PIP = "media_pip_on_leave"
    private const val PREF_KEEP_FX = "player_keep_settings"
    private const val PREF_FX = "player_settings"
    private const val RESUME_FILE = "bfe_resume_positions"

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)
    private fun resume(ctx: Context) = ctx.getSharedPreferences(RESUME_FILE, Context.MODE_PRIVATE)

    fun rememberPosition(ctx: Context): Boolean = prefs(ctx).getBoolean(PREF_RESUME, true)
    fun setRememberPosition(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(PREF_RESUME, on).apply()
        if (!on) resume(ctx).edit().clear().apply()
    }

    fun pipOnLeave(ctx: Context): Boolean = prefs(ctx).getBoolean(PREF_PIP, true)
    fun setPipOnLeave(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(PREF_PIP, on).apply()

    fun keepSettings(ctx: Context): Boolean = prefs(ctx).getBoolean(PREF_KEEP_FX, false)
    fun setKeepSettings(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(PREF_KEEP_FX, on).apply()

    /** The settings a newly opened file starts with: the remembered ones, or the defaults. */
    fun startSettings(ctx: Context): PlayerSettings =
        if (keepSettings(ctx)) PlayerSettings.decode(prefs(ctx).getString(PREF_FX, null)) else PlayerSettings.DEFAULT

    fun saveSettings(ctx: Context, s: PlayerSettings) {
        if (keepSettings(ctx)) prefs(ctx).edit().putString(PREF_FX, s.encode()).apply()
    }

    // ── Resume positions ──

    fun position(ctx: Context, key: String): ResumePolicy.Entry? =
        if (!rememberPosition(ctx)) null else ResumePolicy.decode(resume(ctx).getString(key, null))

    fun storePosition(ctx: Context, key: String, positionMs: Long, lengthMs: Long) {
        if (!rememberPosition(ctx)) return
        val p = resume(ctx)
        if (!ResumePolicy.shouldStore(positionMs, lengthMs)) {
            if (p.contains(key)) p.edit().remove(key).apply()
            return
        }
        val e = p.edit().putString(key, ResumePolicy.encode(positionMs, lengthMs, System.currentTimeMillis()))
        val stamps = p.all.mapNotNull { (k, v) -> ResumePolicy.decode(v as? String)?.let { k to it.stamp } }.toMap()
        ResumePolicy.victims(stamps + (key to Long.MAX_VALUE)).forEach { e.remove(it) }
        e.apply()
    }

    fun forgetPosition(ctx: Context, key: String) = resume(ctx).edit().remove(key).apply()
}

/**
 * Bumped when the viewer deletes (or rotates and saves) files, so the file manager re-lists the folder
 * it's showing when the user comes back.
 */
object MediaChanges {
    var tick by mutableIntStateOf(0)
        private set

    fun bump() { tick++ }
}
