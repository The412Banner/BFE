package com.the412banner.bfe.server

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/** A folder picked for sharing. [label] is the name clients see (volume roots get "Internal", "SD card"...). */
data class SharedFolder(val path: String, val readOnly: Boolean, val label: String)

/** Everything the "Access from network" screen configures. Persisted by [ServerPrefs]. */
data class ServerSettings(
    val httpPort: Int = DEFAULT_HTTP_PORT,
    val ftpPort: Int = DEFAULT_FTP_PORT,
    /** FTP passive data ports, "first-last". */
    val passivePorts: String = DEFAULT_PASSIVE_PORTS,
    /** True: share the primary storage root at "/". False: share only [folders]. */
    val wholeStorage: Boolean = true,
    val wholeStorageReadOnly: Boolean = false,
    val folders: List<SharedFolder> = emptyList(),
    val anonymous: Boolean = false,
    val user: String = DEFAULT_USER,
    val password: String = "",
    /** Hold a partial wake lock so transfers continue with the screen off. */
    val keepAwake: Boolean = true,
    val startHttpOnLaunch: Boolean = false,
    val startFtpOnLaunch: Boolean = false,
) {
    fun credentials() = Credentials(anonymous, user.trim(), password)

    /** The roots to mount, or empty when "chosen folders" has none. */
    fun shareRoots(): List<ShareRoot> =
        if (wholeStorage) listOf(ShareRoot(STORAGE_LABEL, storageRoot(), wholeStorageReadOnly))
        else folders.map { ShareRoot(it.label, File(it.path), it.readOnly) }

    /** True when some client could change files (shown next to the anonymous-access warning). */
    val anyWritable: Boolean get() = if (wholeStorage) !wholeStorageReadOnly else folders.any { !it.readOnly }

    companion object {
        const val DEFAULT_HTTP_PORT = 8080
        const val DEFAULT_FTP_PORT = 2121
        const val DEFAULT_PASSIVE_PORTS = "50000-50050"
        const val DEFAULT_USER = "bfe"
        const val STORAGE_LABEL = "Internal storage"

        @Suppress("DEPRECATION")
        fun storageRoot(): File = Environment.getExternalStorageDirectory()

        /** Null when [port] is usable for a listener, else why not. */
        fun portProblem(port: Int?): String? = when {
            port == null -> "Enter a port number"
            port < 1024 -> "Use a port of 1024 or higher (lower ones need root)"
            port > 65535 -> "Ports go up to 65535"
            else -> null
        }

        /** "a-b" -> a..b, or null when malformed / out of range / too wide. */
        fun parsePassive(range: String): IntRange? {
            val m = Regex("""^\s*(\d{4,5})\s*-\s*(\d{4,5})\s*$""").find(range) ?: return null
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            if (a < 1024 || b > 65535 || b < a || b - a > 1000) return null
            return a..b
        }
    }
}

/** SharedPreferences storage for [ServerSettings], plus which protocols the user has switched on. */
object ServerPrefs {
    private const val PREFS = "bfe_server"
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** An 8-character password that's easy to type on a TV remote or another phone (no 0/O, 1/l). */
    fun generatePassword(): String {
        val rnd = SecureRandom()
        return String(CharArray(8) { ALPHABET[rnd.nextInt(ALPHABET.length)] })
    }

    fun load(ctx: Context): ServerSettings {
        val p = prefs(ctx)
        var password = p.getString("password", null)
        if (password.isNullOrEmpty()) {
            // First use: a random password, shown on screen and editable.
            password = generatePassword()
            p.edit().putString("password", password).apply()
        }
        val folders = runCatching {
            val arr = JSONArray(p.getString("folders", "[]"))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SharedFolder(o.getString("path"), o.optBoolean("ro", false), o.optString("label", File(o.getString("path")).name))
            }
        }.getOrDefault(emptyList())
        val d = ServerSettings()
        return ServerSettings(
            httpPort = p.getInt("httpPort", d.httpPort),
            ftpPort = p.getInt("ftpPort", d.ftpPort),
            passivePorts = p.getString("passivePorts", d.passivePorts) ?: d.passivePorts,
            wholeStorage = p.getBoolean("wholeStorage", d.wholeStorage),
            wholeStorageReadOnly = p.getBoolean("wholeStorageReadOnly", d.wholeStorageReadOnly),
            folders = folders,
            anonymous = p.getBoolean("anonymous", d.anonymous),
            user = p.getString("user", d.user) ?: d.user,
            password = password,
            keepAwake = p.getBoolean("keepAwake", d.keepAwake),
            startHttpOnLaunch = p.getBoolean("startHttpOnLaunch", d.startHttpOnLaunch),
            startFtpOnLaunch = p.getBoolean("startFtpOnLaunch", d.startFtpOnLaunch),
        )
    }

    fun save(ctx: Context, s: ServerSettings) {
        val arr = JSONArray()
        s.folders.forEach { arr.put(JSONObject().put("path", it.path).put("ro", it.readOnly).put("label", it.label)) }
        prefs(ctx).edit()
            .putInt("httpPort", s.httpPort)
            .putInt("ftpPort", s.ftpPort)
            .putString("passivePorts", s.passivePorts)
            .putBoolean("wholeStorage", s.wholeStorage)
            .putBoolean("wholeStorageReadOnly", s.wholeStorageReadOnly)
            .putString("folders", arr.toString())
            .putBoolean("anonymous", s.anonymous)
            .putString("user", s.user)
            .putString("password", s.password)
            .putBoolean("keepAwake", s.keepAwake)
            .putBoolean("startHttpOnLaunch", s.startHttpOnLaunch)
            .putBoolean("startFtpOnLaunch", s.startFtpOnLaunch)
            .apply()
    }

    // Which servers the user has switched on. Persisted so a restarted service (after the system
    // reclaimed the process) brings back exactly what was running.
    fun wantHttp(ctx: Context) = prefs(ctx).getBoolean("runHttp", false)
    fun wantFtp(ctx: Context) = prefs(ctx).getBoolean("runFtp", false)

    fun setWanted(ctx: Context, http: Boolean?, ftp: Boolean?) {
        val e = prefs(ctx).edit()
        http?.let { e.putBoolean("runHttp", it) }
        ftp?.let { e.putBoolean("runFtp", it) }
        e.commit()
    }
}
