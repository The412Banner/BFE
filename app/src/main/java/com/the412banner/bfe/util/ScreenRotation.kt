package com.the412banner.bfe.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.the412banner.bfe.VideoPlayerActivity
import java.lang.ref.WeakReference

/**
 * App-wide screen rotation lock (Settings > Appearance > Screen rotation). Auto follows the
 * device's own rotation setting; Portrait and Landscape lock every BFE screen to that shape (either
 * way up). Applied to each activity as it is created and to every open one when changed. The video
 * player keeps its own rotation control, so it is left alone.
 */
object ScreenRotation {
    enum class Mode(val label: String) { AUTO("Auto"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

    private const val PREF = "screen_rotation"

    var mode by mutableStateOf(Mode.AUTO)
        private set

    private val open = mutableListOf<WeakReference<Activity>>()

    fun install(app: Application) {
        val p = PreferenceManager.getDefaultSharedPreferences(app)
        mode = runCatching { Mode.valueOf(p.getString(PREF, Mode.AUTO.name)!!) }.getOrDefault(Mode.AUTO)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: Bundle?) {
                if (!managed(a)) return
                open += WeakReference(a)
                apply(a)
            }
            override fun onActivityDestroyed(a: Activity) { open.removeAll { it.get() == null || it.get() === a } }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, out: Bundle) {}
        })
    }

    fun set(ctx: Context, m: Mode) {
        mode = m
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(PREF, m.name).apply()
        open.mapNotNull { it.get() }.forEach(::apply)
    }

    private fun managed(a: Activity) = a !is VideoPlayerActivity

    // Android 8.0 refuses a fixed orientation on translucent activities (the photo viewer), so a
    // refusal just leaves that screen following the device.
    private fun apply(a: Activity) {
        runCatching {
            a.requestedOrientation = when (mode) {
                Mode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                Mode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                Mode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }
}
