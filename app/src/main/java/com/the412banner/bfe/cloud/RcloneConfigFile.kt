package com.the412banner.bfe.cloud

import android.content.Context
import java.io.IOException

/**
 * Direct edits to rclone.conf for the one thing the rc API can't do: renaming a remote. rclone keeps
 * no rename call, and re-creating a remote under a new name would re-run its setup (and sign-in).
 * Renaming the INI section header keeps every setting and token as-is; rcd stats the file before each
 * config access and reloads it when it changed, so the new name is live immediately.
 *
 * The file is encrypted ([RcloneSecureConfig]): it is opened with the vault's password, edited in
 * memory and sealed again in rclone's format ([RcloneConfigCrypt]) — the plain text never touches
 * the disk. The running rcd must then list the new name, or the previous file is put back.
 */
object RcloneConfigFile {
    private val lock = Any()

    fun renameSection(ctx: Context, api: RcloneApi, old: String, new: String) = synchronized(lock) {
        val file = RcloneDaemon.configFile(ctx)
        if (!file.isFile) throw IOException("No rclone config yet")
        val pass = RcloneSecureConfig.password(ctx) ?: throw IOException("The rclone config is locked")
        val before = file.readBytes()
        val plain = if (RcloneConfigCrypt.isEncrypted(before)) RcloneConfigCrypt.decrypt(before, pass) else before
        val lines = String(plain, Charsets.UTF_8).reader().readLines()
        if (lines.any { it.trim().equals("[$new]", ignoreCase = true) }) throw IOException("\"$new\" already exists")
        var found = false
        val out = lines.map { line ->
            if (!found && line.trim() == "[$old]") { found = true; "[$new]" } else line
        }
        if (!found) throw IOException("\"$old\" isn't in the config")
        val text = out.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
        RcloneSecureConfig.writeReplacing(file, RcloneConfigCrypt.encrypt(text, pass))
        val names = runCatching { api.remotes().map { it.name } }.getOrDefault(emptyList())
        if (new !in names || old in names) {
            RcloneSecureConfig.writeReplacing(file, before)
            throw IOException("rclone didn't pick up the renamed config")
        }
    }
}
