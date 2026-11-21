package com.the412banner.bfe.storage

import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * A folder [Loc] as a string, so it can ride in an Intent (the unpack service, the Unpack screen) and
 * come back as the same location. Archive entries are read-only and never a destination: no encoding.
 */
object LocCodec {

    fun encode(loc: Loc): String? = when (loc) {
        is Loc.FileLoc -> JSONObject().put("k", "file").put("path", loc.file.absolutePath)
        is Loc.SafLoc -> JSONObject().put("k", "saf").put("tree", loc.treeUri.toString()).put("doc", loc.documentId).put("name", loc.name)
        is Loc.RootLoc -> JSONObject().put("k", "root").put("path", loc.path).put("name", loc.name)
        is Loc.RemoteLoc -> JSONObject().put("k", "remote").put("remote", loc.remote).put("path", loc.path).put("name", loc.name)
        is Loc.ArchiveLoc -> null
    }?.toString()

    fun decode(s: String?): Loc? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(s)
            when (o.optString("k")) {
                "file" -> Loc.FileLoc(File(o.getString("path")))
                "saf" -> Loc.SafLoc(Uri.parse(o.getString("tree")), o.getString("doc"), o.optString("name"), true, 0L, 0L)
                "root" -> Loc.RootLoc(o.getString("path"), o.optString("name"), true, 0L, 0L)
                "remote" -> Loc.RemoteLoc(o.getString("remote"), o.optString("path"), o.optString("name"), true, 0L, 0L)
                else -> null
            }
        }.getOrNull()
    }
}
