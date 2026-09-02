package com.the412banner.bfe.apk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the editor prefills from an APK — read from the binary AndroidManifest via ARSCLib so every
 * scalar the Advanced section exposes comes from the same parser that will rewrite it.
 */
data class ApkMeta(
    val packageName: String,
    val label: String,
    /** True when the label is a `@string` reference (the editor then writes a literal in its place). */
    val labelIsReference: Boolean,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int?,
    val targetSdk: Int?,
    val debuggable: Boolean?,
    val allowBackup: Boolean?,
    val extractNativeLibs: Boolean?,
    val sharedUserId: String?,
    val providerAuthorities: List<String>,
    val declaredPermissions: List<String>,
    /** Split/bundle install: names of the split APKs that were found next to the base. */
    val splitNames: List<String> = emptyList(),
    /** Where the APK came from (a file, or an installed package's sourceDir). */
    val sourcePath: String,
)

/** The user's edits. A null field = leave as in the source. */
data class ApkEdits(
    val packageName: String?,
    val label: String?,
    val versionCode: Int?,
    val versionName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val debuggable: Boolean?,
    val allowBackup: Boolean?,
    val extractNativeLibs: Boolean?,
    /** Absolute path of a PNG/JPEG/WebP to become the launcher icon, or null to keep. */
    val iconImagePath: String?,
)

/** Which signature schemes to emit. v4 is deliberately not offered. */
data class SignSchemes(val v1: Boolean = true, val v2: Boolean = true, val v3: Boolean = true)

/** Identifies a signing key: the app-private keystore copy + alias + passwords. */
data class KeyRef(
    /** Absolute path of the keystore file (inside app-private storage). */
    val storePath: String,
    val storeType: String,
    val alias: String,
    val storePassword: String,
    val keyPassword: String,
    /** Human name shown in pickers ("BFE Test Key", or the imported file's name). */
    val displayName: String,
) {
    val isBuiltIn: Boolean get() = displayName == SigningKeys.BUILT_IN_NAME
}

/** The kind of job the APK service runs. */
enum class ApkJobKind { CLONE, EDIT, SIGN }

/** Where the job is; also the notification/pill text. */
enum class ApkJobStage(val label: String) {
    IDLE(""),
    PARSING("Parsing"),
    MERGING("Merging splits"),
    REWRITING("Rewriting manifest"),
    ICON("Replacing icon"),
    WRITING("Writing APK"),
    ALIGNING("Aligning"),
    SIGNING("Signing"),
    VERIFYING("Verifying"),
    DONE("Done"),
    ERROR("Failed"),
    CANCELLED("Cancelled");

    val isRunning: Boolean get() = this != IDLE && this != DONE && this != ERROR && this != CANCELLED
}

/** Result of an APK job, kept on the state for the result dialog. */
data class ApkJobReport(
    val outputPath: String,
    val fixups: List<String>,
    val warnings: List<String>,
    val verified: Boolean,
    val v1: Boolean,
    val v2: Boolean,
    val v3: Boolean,
    val signerDn: String?,
    val packageName: String?,
    val label: String?,
    val versionCode: Int?,
    val versionName: String?,
)

/** The single, process-wide snapshot of the running (or last) APK job — [ApkJobService] writes it. */
data class ApkJobState(
    val stage: ApkJobStage = ApkJobStage.IDLE,
    val jobId: Long = 0L,
    val kind: ApkJobKind = ApkJobKind.CLONE,
    val sourceName: String = "",
    val outputPath: String = "",
    val installAfter: Boolean = false,
    val elapsedMs: Long = 0L,
    val error: String? = null,
    val report: ApkJobReport? = null,
) {
    val isRunning: Boolean get() = stage.isRunning
    /** 0..1 progress by stage, for the bars. */
    val fraction: Float get() = when (stage) {
        ApkJobStage.IDLE -> 0f
        ApkJobStage.PARSING -> 0.1f
        ApkJobStage.MERGING -> 0.2f
        ApkJobStage.REWRITING -> 0.35f
        ApkJobStage.ICON -> 0.45f
        ApkJobStage.WRITING -> 0.6f
        ApkJobStage.ALIGNING -> 0.7f
        ApkJobStage.SIGNING -> 0.8f
        ApkJobStage.VERIFYING -> 0.95f
        else -> 1f
    }
}

object ApkJobManager {
    private val _state = MutableStateFlow(ApkJobState())
    val state: StateFlow<ApkJobState> = _state.asStateFlow()
    val current: ApkJobState get() = _state.value
    fun set(next: ApkJobState) { _state.value = next }
    fun update(block: (ApkJobState) -> ApkJobState) { _state.value = block(_state.value) }
    fun clearIfTerminal() {
        val s = _state.value.stage
        if (s == ApkJobStage.DONE || s == ApkJobStage.ERROR || s == ApkJobStage.CANCELLED) _state.value = ApkJobState()
    }
}

/** Package-name validation shared by the editor and the engine: Java-identifier segments, ≥2 of them. */
object PackageNames {
    private val SEGMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    /** null when valid, else a short reason. */
    fun problem(name: String): String? {
        if (name.isBlank()) return "Package name is empty"
        val parts = name.split('.')
        if (parts.size < 2) return "Needs at least two segments (e.g. com.example.app)"
        for (p in parts) {
            if (p.isEmpty()) return "Empty segment (double dot?)"
            if (p[0].isDigit()) return "Segment \"$p\" can't start with a digit"
            if (!SEGMENT.matches(p)) return "Segment \"$p\" has an invalid character"
        }
        return null
    }
}
