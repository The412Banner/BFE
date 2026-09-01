package com.the412banner.bfe.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/** A pinned SAF "app storage" location: a persisted tree Uri plus a friendly label + owning app. */
data class PinnedLocation(
    val label: String,
    val treeUri: Uri,
    val packageName: String?,
)

/**
 * Persists the user's pinned SAF locations (label + tree Uri + owning package) in SharedPreferences,
 * and owns the persistable-Uri-permission lifecycle (take on pin, release on unpin).
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
                val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PinnedLocation(
                    label = o.optString("label").ifBlank { "App storage" },
                    treeUri = Uri.parse(uri),
                    packageName = o.optString("pkg").takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "list parse failed", e)
            emptyList()
        }
    }

    fun isPinned(context: Context, treeUri: Uri): Boolean =
        list(context).any { it.treeUri == treeUri }

    /** Take a persistable read+write grant on [treeUri] and pin it. Idempotent on the same Uri. */
    fun add(context: Context, label: String, treeUri: Uri, packageName: String?) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.e(TAG, "takePersistableUriPermission failed", it) }
        val current = list(context).filterNot { it.treeUri == treeUri }
        save(context, current + PinnedLocation(label, treeUri, packageName))
    }

    /** Unpin [treeUri] and release its persistable grant. */
    fun remove(context: Context, treeUri: Uri) {
        save(context, list(context).filterNot { it.treeUri == treeUri })
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
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
                    .put("uri", it.treeUri.toString())
                    .put("pkg", it.packageName ?: ""),
            )
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, arr.toString()).apply()
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
