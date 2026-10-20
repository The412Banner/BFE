package com.the412banner.bfe.ui.screens.home

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.screens.PaneState
import java.io.File

/** The app's top-level screens. The file manager is always composed underneath the others. */
enum class AppRoute { HOME, FILES, CATEGORY, APPS, CLOUD, REMOTE, SERVER }

/**
 * Where the Home dashboard (or "Show in folder", or a start-up "Last folder") asks the file manager to
 * go. It lands in the ACTIVE pane. Not a data class on purpose: every request is a new object, so the
 * file manager's LaunchedEffect fires again even when the same folder is asked for twice.
 */
class FmLocationRequest private constructor(
    private val dir: File?,
    private val floor: File?,
    // A root / SAF / remote location: its top first, then any folders below it down to the target.
    private val alts: List<Loc>,
    private val altLabel: String,
) {
    /** Move [pane] there: a File folder (with [floor] as the Back/up floor), or a root/SAF/remote location. */
    fun applyTo(pane: PaneState) {
        pane.showFavorites = false
        pane.showSearch = false
        pane.searchQuery = ""
        if (alts.isNotEmpty()) {
            pane.openAltRoot(alts.first(), altLabel)
            if (alts.size > 1) pane.altStack = alts
            return
        }
        val target = dir ?: return
        pane.exitAlt()
        pane.rootPath = (floor ?: target).absolutePath
        pane.path = target.absolutePath
    }

    companion object {
        /** A File folder; Back climbs to the volume it lives on, then returns Home. */
        fun folder(dir: File, floor: File = volumeRootOf(dir)): FmLocationRequest = FmLocationRequest(dir, floor, emptyList(), "")

        /** A root (or SAF) location, browsed through its backend, e.g. "/" or /data/data/<pkg>. */
        fun alt(root: Loc, label: String): FmLocationRequest = FmLocationRequest(null, null, listOf(root), label)

        /**
         * A folder deep inside a root / SAF / remote location: [stack] is its top, then each folder down to
         * it, so the breadcrumb shows the whole way and Back climbs up through it.
         */
        fun altStack(stack: List<Loc>, label: String): FmLocationRequest = FmLocationRequest(null, null, stack, label)
    }
}

/**
 * The storage volume root a path lives on: /storage/emulated/N for internal storage, /storage/<id> for
 * an SD card or USB drive, else "/". Used as the file manager's Back floor for Home jumps.
 */
fun volumeRootOf(file: File): File {
    val p = file.absolutePath
    Regex("^/storage/emulated/\\d+").find(p)?.let { return File(it.value) }
    Regex("^/storage/([^/]+)").find(p)?.let { m ->
        val name = m.groupValues[1]
        if (name != "emulated" && name != "self") return File(m.value)
    }
    Regex("^/mnt/media_rw/[^/]+").find(p)?.let { return File(it.value) }
    return File("/")
}

/** The two Home-related preferences: which screen BFE opens on, and the folder to reopen. */
object HomePrefs {
    private const val KEY_START_ON = "homeStartOn"
    private const val KEY_LAST_FOLDER = "fmLastFolder"
    const val START_HOME = "home"
    const val START_LAST_FOLDER = "last"

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun startOn(ctx: Context): String = prefs(ctx).getString(KEY_START_ON, START_HOME) ?: START_HOME
    fun setStartOn(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_START_ON, value).apply()

    /** The active pane's last File folder, if it still exists. */
    fun lastFolder(ctx: Context): File? =
        prefs(ctx).getString(KEY_LAST_FOLDER, null)?.let(::File)?.takeIf { it.isDirectory }

    fun setLastFolder(ctx: Context, path: String) {
        if (prefs(ctx).getString(KEY_LAST_FOLDER, null) != path) prefs(ctx).edit().putString(KEY_LAST_FOLDER, path).apply()
    }
}

/**
 * Hosts [content] with system Back routed to it only while [active]. The file manager stays composed
 * under Home (so both panes keep their folder, selection and clipboard), but its own Back handlers must
 * not fire while it is hidden — so while inactive its subtree gets a detached dispatcher that never
 * receives Back. The provider is always present, so toggling never recreates [content]; BackHandler
 * re-registers with the real dispatcher the moment it becomes active again.
 */
@Composable
fun BackScope(active: Boolean, content: @Composable () -> Unit) {
    val real = LocalOnBackPressedDispatcherOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detached = remember(lifecycleOwner) {
        object : OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
            override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
        }
    }
    val owner = if (active && real != null) real else detached
    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner, content = content)
}
