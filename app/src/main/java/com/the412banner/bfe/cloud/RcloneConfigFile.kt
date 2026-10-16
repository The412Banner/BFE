package com.the412banner.bfe.cloud

import java.io.File
import java.io.IOException

/**
 * Direct edits to rclone.conf for the one thing the rc API can't do: renaming a remote. rclone keeps
 * no rename call, and re-creating a remote under a new name would re-run its setup (and sign-in).
 * Renaming the INI section header keeps every setting and token as-is; rcd stats the file before each
 * config access and reloads it when it changed, so the new name is live immediately.
 */
object RcloneConfigFile {
    private val lock = Any()

    fun renameSection(file: File, old: String, new: String) = synchronized(lock) {
        if (!file.isFile) throw IOException("No rclone config yet")
        val lines = file.readLines()
        if (lines.any { it.trim().equals("[$new]", ignoreCase = true) }) throw IOException("\"$new\" already exists")
        var found = false
        val out = lines.map { line ->
            if (!found && line.trim() == "[$old]") { found = true; "[$new]" } else line
        }
        if (!found) throw IOException("\"$old\" isn't in the config")
        val tmp = File(file.parentFile, file.name + ".rename")
        tmp.writeText(out.joinToString("\n", postfix = "\n"))
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Couldn't write the rclone config")
        }
    }
}
