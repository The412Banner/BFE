package com.the412banner.bfe.storage

import android.content.Context
import android.provider.DocumentsContract
import android.util.LruCache
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Number of direct children of a folder, for the "N items" line under folder rows.
 *
 * Counted lazily per visible row, off the main thread, one level only (never recursive). Results are
 * cached by the folder's id + lastModified (a directory's mtime changes when an entry is added or
 * removed), so scrolling back over a row or re-listing the folder costs nothing. Unreadable folders
 * answer -1 and the row shows just the date.
 *
 * Per backend: File = `File.list()` length; SAF = one children query asking only for the document
 * id column (the provider still enumerates, but nothing else is materialised); Root = one
 * `ls -A | wc -l` through the already-open libsu shell, and only once root has been granted; Archive =
 * the opened archive's index.
 */
object ChildCount {
    const val UNREADABLE = -1

    private val cache = LruCache<String, Int>(2048)
    // A handful at a time: a fling over a big folder must not queue hundreds of SAF/root round trips
    // ahead of the rows that are actually on screen (cancelled rows release their slot immediately).
    private val gate = Semaphore(4)

    private fun key(loc: Loc) = "${loc.id}@${loc.lastModified}"

    /** The cached answer, or null when it has not been counted yet. Cheap; safe on the main thread. */
    fun cached(loc: Loc): Int? = cache.get(key(loc))

    suspend fun count(context: Context, loc: Loc): Int {
        cached(loc)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                val n = runCatching { countBlocking(context, loc) }.getOrDefault(UNREADABLE)
                cache.put(key(loc), n)
                n
            }
        }
    }

    private fun countBlocking(context: Context, loc: Loc): Int = when (loc) {
        is Loc.FileLoc -> loc.file.list()?.size ?: UNREADABLE
        is Loc.SafLoc -> {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(loc.treeUri, loc.documentId)
            context.contentResolver.query(
                uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null,
            )?.use { it.count } ?: UNREADABLE
        }
        is Loc.RootLoc -> {
            if (Shell.isAppGrantedRoot() != true) UNREADABLE
            else {
                val q = "'" + loc.path.replace("'", "'\\''") + "'"
                val r = Shell.cmd("[ -d $q ] && ls -A $q | wc -l").exec()
                if (r.isSuccess) r.out.firstOrNull()?.trim()?.toIntOrNull() ?: UNREADABLE else UNREADABLE
            }
        }
        // Archive folders: straight from the already-built index (never starts a 7zz listing).
        is Loc.ArchiveLoc -> ArchiveIndexCache.peek(loc.archive)?.tree?.children(loc.inner)?.size ?: UNREADABLE
    }
}
