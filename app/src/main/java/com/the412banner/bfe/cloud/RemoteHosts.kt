package com.the412banner.bfe.cloud

import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Home address for server remotes (SFTP, FTP, SMB): a second address saved with the remote under
 * [KEY]. Before rclone touches such a remote, BFE checks once per network whether the home address
 * answers. If it does, the call's fs string gets rclone connection-string overrides
 * (`name,host="…",port=N:path`) so rclone connects there; if not, the remote's own Host is used.
 * The saved config is never rewritten, so Host keeps the away address.
 *
 * Plain JVM like [RcClient]; the app reports network changes through [networkChanged].
 */
object RemoteHosts {
    /** The rclone config key BFE stores the home address under (rclone keeps unknown keys as they are). */
    const val KEY = "bfe_home_host"

    private const val PROBE_MS = 800
    private const val MAX_AGE_MS = 10 * 60_000L

    /** Port used when neither the home address nor the remote names one. */
    private val DEFAULT_PORTS = mapOf("sftp" to 22, "ftp" to 21, "smb" to 445)

    /** What a server of this type sends first, to tell it apart from some other open port. */
    private val GREETINGS = mapOf("sftp" to "SSH-", "ftp" to "220")

    data class Home(val type: String, val host: String, val port: Int)

    private data class Probe(val at: Long, val generation: Int, val reachable: Boolean)

    @Volatile private var homes: Map<String, Home>? = null
    @Volatile private var generation = 0
    private val probes = ConcurrentHashMap<String, Probe>()
    private val locks = ConcurrentHashMap<String, Any>()

    /** The device joined or left a network: check home addresses again on next use. */
    fun networkChanged() {
        generation++
        probes.clear()
    }

    /** A remote was added, changed or removed. */
    fun configChanged() {
        homes = null
        probes.clear()
    }

    /** The router [RcClient] runs every fs string through. */
    fun routerFor(client: RcClient): RcClient.Router = object : RcClient.Router {
        override fun route(fs: String): String = route(client, fs)
        override fun failed(fs: String) { remoteOf(fs)?.let { probes.remove(it) } }
        override fun configChanged() = this@RemoteHosts.configChanged()
    }

    /** True when the last check found [remote]'s home address answering (for display). */
    fun usingHome(remote: String): Boolean = probes[remote]?.reachable == true

    internal fun route(client: RcClient, fs: String): String {
        val name = remoteOf(fs) ?: return fs
        val all = homes ?: load(client).also { homes = it }
        val home = all[name] ?: return fs
        if (!reachable(name, home)) return fs
        return override(fs, name, home)
    }

    /** The remote name an fs string starts with ("nas:a/b" → "nas"); null for local paths or overrides already present. */
    internal fun remoteOf(fs: String): String? {
        val i = fs.indexOf(':')
        if (i <= 0) return null
        val name = fs.substring(0, i)
        return name.takeUnless { it.contains(',') || it.contains('/') }
    }

    /** "nas:a/b" → `nas,host="192.168.1.20",port=2222:a/b`. */
    internal fun override(fs: String, name: String, home: Home): String =
        name + ",host=" + quote(home.host) + ",port=" + home.port + fs.substring(name.length)

    private fun quote(v: String) = "\"" + v.replace("\"", "\"\"") + "\""

    /** The home address of every remote that has one, from rclone's config. */
    private fun load(client: RcClient): Map<String, Home> {
        val dump = runCatching { client.call("config/dump", readTimeoutMs = 30_000) }.getOrNull() ?: return emptyMap()
        return parseDump(dump)
    }

    internal fun parseDump(dump: JSONObject): Map<String, Home> {
        val out = HashMap<String, Home>()
        for (name in dump.keys()) {
            val o = dump.optJSONObject(name) ?: continue
            val type = o.optString("type", "")
            parseHome(o.optString(KEY, ""), type, o.optString("port", ""))?.let { out[name] = it }
        }
        return out
    }

    /**
     * "192.168.1.20" or "192.168.1.20:22" (a hostname works too). A bare address uses the remote's own
     * port, else the type's default. Null when empty or the type has no host setting.
     */
    internal fun parseHome(raw: String, type: String, port: String): Home? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        val fallbackPort = port.trim().toIntOrNull() ?: DEFAULT_PORTS[type] ?: return null
        val i = v.lastIndexOf(':')
        if (i > 0 && v.indexOf(':') == i) {
            val p = v.substring(i + 1).toIntOrNull()
            if (p != null && p in 1..65535) return Home(type, v.substring(0, i), p)
        }
        return Home(type, v, fallbackPort)
    }

    private fun reachable(name: String, home: Home): Boolean {
        fresh(name)?.let { return it.reachable }
        val lock = locks.getOrPut(name) { Any() }
        synchronized(lock) {
            fresh(name)?.let { return it.reachable }
            val gen = generation
            val ok = answers(home)
            probes[name] = Probe(System.currentTimeMillis(), gen, ok)
            return ok
        }
    }

    private fun fresh(name: String): Probe? =
        probes[name]?.takeIf { it.generation == generation && System.currentTimeMillis() - it.at < MAX_AGE_MS }

    /** Connects to the home address and, for SFTP and FTP, checks the server's greeting. */
    internal fun answers(home: Home): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(home.host, home.port), PROBE_MS)
            val greeting = GREETINGS[home.type] ?: return@use true
            s.soTimeout = PROBE_MS
            readStart(s.getInputStream(), greeting.length) == greeting
        }
    }.getOrDefault(false)

    private fun readStart(input: InputStream, n: Int): String {
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(buf, got, n - got)
            if (r < 0) break
            got += r
        }
        return String(buf, 0, got, Charsets.ISO_8859_1)
    }
}
