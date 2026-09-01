package com.the412banner.bfe.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * A pinned "app storage" location — EITHER a SAF tree ([treeUri], granted via the system picker) OR a
 * root-backed path ([path], e.g. `/data/data/<pkg>`, no picker needed). [key] is the stable identity.
 */
data class PinnedLocation(
    val label: String,
    val kind: String,          // "saf" | "root"
    val treeUri: Uri?,         // SAF only
    val path: String?,         // root only
    val packageName: String?,
) {
    val isRoot: Boolean get() = kind == KIND_ROOT
    val key: String get() = if (isRoot) "root:$path" else treeUri.toString()

    companion object {
        const val KIND_SAF = "saf"
        const val KIND_ROOT = "root"
    }
}

/**
 * Persists the user's pinned locations (SAF trees and root paths) in SharedPreferences, and owns the
 * SAF persistable-Uri-permission lifecycle (take on pin, release on unpin).
 */
object PinnedStorage {
    private const val TAG = "PinnedStorage"
    private const val KEY = "pinnedSafLocations"

    fun list(context: Context): List<PinnedLocation> {
        val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val kind = o.optString("kind").ifBlank { PinnedLocation.KIND_SAF }
                val label = o.optString("label").ifBlank { "App storage" }
                val pkg = o.optString("pkg").takeIf { it.isNotBlank() }
                if (kind == PinnedLocation.KIND_ROOT) {
                    val path = o.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PinnedLocation(label, kind, null, path, pkg)
                } else {
                    val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PinnedLocation(label, kind, Uri.parse(uri), null, pkg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "list parse failed", e)
            emptyList()
        }
    }

    /** Pin a SAF tree: take a persistable read+write grant, then persist. Idempotent on the Uri. */
    fun add(context: Context, label: String, treeUri: Uri, packageName: String?) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.e(TAG, "takePersistableUriPermission failed", it) }
        val item = PinnedLocation(label, PinnedLocation.KIND_SAF, treeUri, null, packageName)
        save(context, list(context).filterNot { it.key == item.key } + item)
    }

    /** Pin a root-backed path (no consent step — root needs no SAF grant). Idempotent on the path. */
    fun addRoot(context: Context, label: String, path: String, packageName: String?) {
        val item = PinnedLocation(label, PinnedLocation.KIND_ROOT, null, path, packageName)
        save(context, list(context).filterNot { it.key == item.key } + item)
    }

    /** Unpin [item]; releases the SAF grant when it's a SAF pin. */
    fun remove(context: Context, item: PinnedLocation) {
        save(context, list(context).filterNot { it.key == item.key })
        val uri = item.treeUri ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.e(TAG, "releasePersistableUriPermission failed", it) }
    }

    private fun save(context: Context, items: List<PinnedLocation>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("label", it.label)
                    .put("kind", it.kind)
                    .put("uri", it.treeUri?.toString() ?: "")
                    .put("path", it.path ?: "")
                    .put("pkg", it.packageName ?: ""),
            )
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, arr.toString()).apply()
    }
}

/** Any installed app (for the root "All apps" picker — no DocumentsProvider needed). */
data class InstalledApp(val label: String, val packageName: String)

object InstalledApps {
    /** Every installed app (label + package), sorted by label, excluding BFE itself. */
    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val me = context.packageName
        return try {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
                .filter { it.packageName != me }
                .map { InstalledApp(runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.packageName), it.packageName) }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** An installed app that exposes SAF storage (a DocumentsProvider). */
data class DocProviderApp(
    val label: String,
    val packageName: String,
    val authority: String,
)

/**
 * Enumerates installed DocumentsProviders — apps that expose SAF storage — so the "Add app storage"
 * picker can list them (label + package + icon). Uses PackageManager for the
 * `android.content.action.DOCUMENTS_PROVIDER` action.
 */
object DocumentsProviderApps {
    private const val TAG = "DocProviderApps"
    private const val ACTION_DOCUMENTS_PROVIDER = "android.content.action.DOCUMENTS_PROVIDER"

    fun list(context: Context): List<DocProviderApp> {
        val pm = context.packageManager
        val myPkg = context.packageName
        return try {
            @Suppress("DEPRECATION")
            pm.queryIntentContentProviders(Intent(ACTION_DOCUMENTS_PROVIDER), 0)
                .mapNotNull { ri ->
                    val pi = ri.providerInfo ?: return@mapNotNull null
                    if (pi.packageName == myPkg) return@mapNotNull null   // don't list ourselves
                    val label = runCatching { pi.loadLabel(pm).toString() }.getOrNull()
                        ?: runCatching { pm.getApplicationInfo(pi.packageName, 0).loadLabel(pm).toString() }.getOrNull()
                        ?: pi.packageName
                    DocProviderApp(label = label, packageName = pi.packageName, authority = pi.authority)
                }
                .distinctBy { it.packageName + "/" + it.authority }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "list failed", e)
            emptyList()
        }
    }

    /**
     * A tree Uri to seed ACTION_OPEN_DOCUMENT_TREE's EXTRA_INITIAL_URI so the picker opens IN this
     * provider (best-effort — null just opens the picker at its default location).
     */
    fun initialTreeUri(context: Context, authority: String): Uri? = try {
        val rootsUri = DocumentsContract.buildRootsUri(authority)
        context.contentResolver.query(
            rootsUri,
            arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val docIdIdx = c.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
                val rootIdIdx = c.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID)
                val docId = if (docIdIdx >= 0 && !c.isNull(docIdIdx)) c.getString(docIdIdx) else null
                val rootId = if (rootIdIdx >= 0) c.getString(rootIdIdx) else null
                (docId ?: rootId)?.let { DocumentsContract.buildTreeDocumentUri(authority, it) }
            } else null
        }
    } catch (e: Exception) {
        Log.e(TAG, "initialTreeUri failed for $authority", e)
        null
    }
}
