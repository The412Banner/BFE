package com.the412banner.bfe.cloud

import android.content.Context
import android.util.Log
import com.the412banner.bfe.security.SecretVault
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Keeps rclone.conf — every cloud sign-in token and key, and passwords rclone only "obscures" — encrypted
 * at rest with rclone's own config encryption. The password is 32 random bytes made once and kept ONLY
 * in [SecretVault]; rclone is handed it through RCLONE_CONFIG_PASS in its environment, never on the
 * command line, so the file on disk is always RCLONE_ENCRYPT_V0 ciphertext.
 *
 * [prepare] runs before every rcd start, under RcloneDaemon's start lock with no rclone running:
 *  - a save rclone was interrupted in (old file moved aside, new one not yet in place) is finished;
 *  - a plain config (BFE 1.1.0 and older) is encrypted in place by rclone itself — `rclone config
 *    encryption set`, which takes the new password from --password-command, here a shell echoing the
 *    environment variable — or by [RcloneConfigCrypt] when the engine isn't installed, then checked;
 *  - an encrypted config the vault's password can't open (the vault key was lost) is cleared and the
 *    user told once ([SecretVault.markLost]); one that opens is left as it is;
 *  - leftover copies (rclone's temp and backup files, BFE's own temp) are deleted, so no plain copy
 *    survives the migration.
 * Every step is safe to repeat: whatever an interruption left half done is finished at the next start.
 */
object RcloneSecureConfig {
    private const val TAG = "BFE_RCLONE"
    const val VAULT_ENTRY = "rclone.config_password"
    private const val CONF = "rclone.conf"
    private const val TMP = "rclone.conf.bfe-tmp"
    // rclone's own save files: os.CreateTemp(dir, "rclone.conf") and (…, "rclone.conf.old").
    private val RCLONE_SAVE_FILE = Regex("""^rclone\.conf(\.old)?\d+$""")

    /** The password rcd is started with, or null before there is one. */
    fun password(ctx: Context): String? = SecretVault.get(ctx, VAULT_ENTRY)

    /**
     * True while a plain config (or a leftover copy) from before the vault is still on disk, or an
     * encrypted one is left whose password the vault no longer has.
     */
    fun needsSecuring(ctx: Context): Boolean {
        val conf = RcloneDaemon.configFile(ctx)
        val data = conf.takeIf { it.isFile }?.readBytes()
        val plain = data != null && !RcloneConfigCrypt.isEncrypted(data)
        val orphaned = data != null && !plain && password(ctx) == null
        return plain || orphaned || leftovers(conf.parentFile).isNotEmpty()
    }

    /**
     * Gets rclone.conf ready for rcd (see the class comment) and returns its password. [engine] is the
     * installed rclone binary, or null when there isn't one (rclone.conf is then sealed by BFE). Blocking.
     */
    fun prepare(ctx: Context, engine: File?): String {
        val conf = RcloneDaemon.configFile(ctx)
        val dir = conf.parentFile ?: throw IOException("no config folder")
        dir.mkdirs()
        finishInterruptedSave(dir, conf)
        var pass = SecretVault.get(ctx, VAULT_ENTRY)
        val existing = conf.takeIf { it.isFile }?.readBytes()
        if (existing != null && RcloneConfigCrypt.isEncrypted(existing)) {
            val p = pass
            val unreadable = when {
                p == null -> true
                RcloneConfigCrypt.opens(existing, p) -> false
                // rclone gets the last word before a config is thrown away.
                engine != null -> !engineAccepts(engine, conf, p)
                else -> false // decided at the next start, with the engine there to ask
            }
            if (unreadable) {
                Log.w(TAG, "rclone.conf is encrypted with a password BFE no longer has; clearing it")
                deleteAll(dir)
                // A password the vault itself lost is already on its list for the user.
                if (p != null) SecretVault.markLost(ctx, VAULT_ENTRY)
            }
        }
        if (pass == null) {
            pass = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            // Committed before anything is encrypted with it.
            SecretVault.put(ctx, VAULT_ENTRY, pass)
        }
        if (conf.isFile && !RcloneConfigCrypt.isEncrypted(conf.readBytes())) encryptInPlace(conf, pass, engine)
        leftovers(dir).forEach { it.delete() }
        return pass
    }

    /** Writes [bytes] to a temp file, syncs it and renames it over [file]: the old content stays whole until the new one is. */
    internal fun writeReplacing(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, TMP)
        FileOutputStream(tmp).use {
            it.write(bytes)
            it.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Couldn't write the rclone config")
        }
    }

    // ── internals ──

    private fun encryptInPlace(conf: File, pass: String, engine: File?) {
        val plain = conf.readBytes()
        if (engine != null) {
            runCatching { runEngine(engine, conf, pass, "config", "encryption", "set") }
                .onFailure { Log.w(TAG, "rclone couldn't encrypt its config (${it.message}); BFE seals it instead") }
            conf.parentFile?.let { finishInterruptedSave(it, conf) }
            val now = conf.takeIf { it.isFile }?.readBytes()
            if (now != null && RcloneConfigCrypt.isEncrypted(now) && (RcloneConfigCrypt.opens(now, pass) || engineAccepts(engine, conf, pass))) {
                Log.i(TAG, "rclone.conf encrypted by rclone (${RcloneConfigCrypt.sectionNames(plain).size} remotes)")
                return
            }
        }
        // rclone's format, sealed here; proven to open before it replaces the only copy.
        val sealed = RcloneConfigCrypt.encrypt(plain, pass)
        if (!RcloneConfigCrypt.decrypt(sealed, pass).contentEquals(plain)) throw IOException("Couldn't encrypt the rclone config")
        writeReplacing(conf, sealed)
        Log.i(TAG, "rclone.conf encrypted (${RcloneConfigCrypt.sectionNames(plain).size} remotes)")
    }

    /** `rclone config encryption check`: exit 0 = the file is encrypted and [pass] opens it. */
    private fun engineAccepts(engine: File, conf: File, pass: String): Boolean =
        runCatching { runEngine(engine, conf, pass, "config", "encryption", "check") }.isSuccess

    /** Runs one short `rclone config …` command on [conf], the password only in its environment. */
    private fun runEngine(engine: File, conf: File, pass: String, vararg args: String) {
        val pb = ProcessBuilder(listOf(engine.absolutePath) + args + listOf("--config", conf.absolutePath, "--ask-password=false"))
        pb.redirectErrorStream(true)
        pb.directory(conf.parentFile)
        pb.environment().apply {
            put("RCLONE_CONFIG_PASS", pass)
            // `encryption set` reads the new password with --password-command (set here through the
            // environment, so it isn't in argv either); the shell only echoes the variable above.
            put("RCLONE_PASSWORD_COMMAND", "/system/bin/sh -c \"echo \$RCLONE_CONFIG_PASS\"")
            put("HOME", conf.parentFile?.absolutePath ?: "/")
        }
        val p = pb.start()
        p.outputStream.close() // nothing to answer: never wait for a prompt
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            throw IOException("rclone ${args.last()} timed out")
        }
        val out = p.inputStream.bufferedReader().readText()
        if (p.exitValue() != 0) {
            throw IOException("rclone ${args.last()} failed (${p.exitValue()}): " + out.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().take(200))
        }
    }

    /**
     * rclone saves by writing the new config to a temp file, moving the old one aside to
     * rclone.conf.old…, then moving the new one in. Stopped between those moves, rclone.conf is
     * missing: put back the newest complete copy (the temp is fully written before the first move).
     */
    private fun finishInterruptedSave(dir: File, conf: File) {
        if (conf.exists()) return
        val newest = dir.listFiles()?.filter { it.isFile && it.length() > 0 && RCLONE_SAVE_FILE.matches(it.name) }
            ?.maxByOrNull { it.lastModified() } ?: return
        if (newest.renameTo(conf)) Log.i(TAG, "Finished an interrupted rclone config save")
    }

    /** Every copy next to rclone.conf: rclone's temp/backup files, BFE's temp, the old rename temp. */
    private fun leftovers(dir: File?): List<File> =
        dir?.listFiles()?.filter { it.isFile && it.name != CONF && it.name.startsWith(CONF) }.orEmpty()

    private fun deleteAll(dir: File) {
        File(dir, CONF).delete()
        leftovers(dir).forEach { it.delete() }
    }
}
