package com.the412banner.bfe.appicon

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes

/**
 * One launcher icon. [art] is the squircle artwork (the adaptive icon's foreground: the full 108 canvas
 * with the squircle [AppIcons.ART_SQUIRCLE_FRACTION] of it wide, centred); [mono] is its themed-icon
 * (Android 13+) silhouette on the same canvas. [alias] is the `<activity-alias>` that carries it.
 */
data class AppIcon(
    val id: String,
    val name: String,
    /** How the owner's previews number it: "M5", "09", or "Classic". */
    val code: String,
    val group: Group,
    val alias: String,
    @DrawableRes val art: Int,
    @DrawableRes val mono: Int,
    val isDefault: Boolean,
) {
    enum class Group(val label: String) { MIXES("Mixes"), ORIGINALS("Originals"), CLASSIC("Classic") }
}

/**
 * Settings → App icon. Every icon is its own `<activity-alias>` of MainActivity with the launcher filter
 * (generated into the manifest by scripts/appicons/build.py, see [APP_ICON_CATALOG]). Exactly one is
 * enabled, and that one is the entry launchers show; switching flips the enabled states.
 *
 * Android removes the task an alias launched when that alias is set to DISABLED, even with DONT_KILL_APP,
 * which would close BFE in the user's face. It only does that for an explicit DISABLED state, so:
 *  - the icons that are off in the manifest are switched off by resetting them to DEFAULT, never DISABLED;
 *  - only the default icon (on in the manifest) needs DISABLED, and that step waits until BFE has left the
 *    screen (every BFE activity stopped). It's remembered in prefs, so a restart in between still finishes it.
 * The new icon is switched on first, so there is never a moment without a launcher entry.
 */
object AppIcons {

    /** The art drawables are the 108 adaptive canvas; the squircle is this share of it (60 of 108). */
    const val ART_SQUIRCLE_FRACTION = 60f / 108f

    val ALL: List<AppIcon> get() = APP_ICON_CATALOG
    val DEFAULT: AppIcon by lazy { APP_ICON_CATALOG.first { it.isDefault } }

    private const val PREFS = "app_icon"
    private const val KEY_HIDE_DEFAULT = "hide_default_when_backgrounded"

    /** BFE activities currently started (on screen); main thread only. */
    private var started = 0

    /** From [com.the412banner.bfe.BfeApp]: finishes a deferred switch once BFE is in the background. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0 && !activity.isChangingConfigurations) finishDeferred(activity.applicationContext)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** The icon launchers show (or will, once a deferred switch finishes). */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        // A non-default icon is only ever on because it was picked; while a switch away from the default
        // is still deferred, both are on and the picked one is the answer.
        return ALL.firstOrNull { !it.isDefault && pm.getComponentEnabledSetting(component(context, it)) == ENABLED }
            ?: DEFAULT
    }

    /** Makes [icon] the launcher icon. Blocking binder calls: call off the main thread. */
    fun apply(context: Context, icon: AppIcon): Boolean = runCatching {
        val pm = context.packageManager
        // New icon first, then everything else back to its manifest state (off), except the default.
        val wanted = buildList {
            add(icon to if (icon.isDefault) DEFAULT_STATE else ENABLED)
            ALL.filter { it != icon && !it.isDefault }.forEach { add(it to DEFAULT_STATE) }
        }
        val changes = wanted
            .map { (ic, state) -> component(context, ic) to state }
            .filter { (cn, state) -> pm.getComponentEnabledSetting(cn) != state }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (changes.isNotEmpty()) {
                pm.setComponentEnabledSettings(changes.map { (cn, state) ->
                    PackageManager.ComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
                })
            }
        } else {
            changes.forEach { (cn, state) -> pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP) }
        }
        // Leaving the default: hide it once BFE is in the background. Back to the default: nothing to hide.
        prefs(context).edit().putBoolean(KEY_HIDE_DEFAULT, !icon.isDefault).apply()
        true
    }.getOrDefault(false)

    private fun finishDeferred(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_HIDE_DEFAULT, false)) return
        runCatching {
            val pm = context.packageManager
            // Never leave BFE without a launcher entry: only hide the default if a picked icon is on.
            if (ALL.any { !it.isDefault && pm.getComponentEnabledSetting(component(context, it)) == ENABLED }) {
                pm.setComponentEnabledSetting(component(context, DEFAULT), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
        prefs.edit().remove(KEY_HIDE_DEFAULT).apply()
    }

    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, icon.alias)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private const val DEFAULT_STATE = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
}
