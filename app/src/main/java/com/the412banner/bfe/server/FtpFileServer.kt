package com.the412banner.bfe.server

import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authentication
import org.apache.ftpserver.ftplet.AuthenticationFailedException
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.message.MessageResource
import org.apache.ftpserver.message.MessageResourceFactory
import org.apache.ftpserver.usermanager.AnonymousAuthentication
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.WritePermission

/**
 * The FTP side of "Access from network", on Apache FtpServer. Pure JVM (no android.*).
 *
 * One listener per LAN address; passive data sockets open on the same address the client reached
 * (FtpServer's default), from the configured port range. The control channel is UTF-8 and FEAT
 * advertises UTF8, so OPTS UTF8 ON succeeds. Files come from [BfeFileSystemFactory], which enforces
 * read-only shares itself, so the user here needs no per-path permissions.
 */
class FtpFileServer(
    private val host: ServerHost,
    private val addresses: List<String>,
    private val port: Int,
    private val passivePorts: String,
) {
    private var server: FtpServer? = null

    fun start() {
        val factory = FtpServerFactory()
        // Plain locals on purpose: inside the factories' apply blocks, `port` / `passivePorts` would
        // resolve to the factory's own getters (port 21, any passive port), not these settings.
        val controlPort = port
        val dataPorts = passivePorts
        addresses.forEachIndexed { i, address ->
            val dataFactory = DataConnectionConfigurationFactory()
            dataFactory.passivePorts = dataPorts
            dataFactory.idleTime = 120
            val listenerFactory = ListenerFactory()
            listenerFactory.serverAddress = address
            listenerFactory.port = controlPort
            listenerFactory.idleTimeout = 600
            listenerFactory.dataConnectionConfiguration = dataFactory.createDataConnectionConfiguration()
            val listener = listenerFactory.createListener()
            // FtpServerFactory starts with a "default" listener on port 21; the first one replaces it.
            factory.addListener(if (i == 0) "default" else "lan$i", listener)
        }
        factory.connectionConfig = ConnectionConfigFactory().apply {
            // Anonymous logins are let through to the user manager, which decides per current settings.
            isAnonymousLoginEnabled = true
            maxLogins = 64
            maxAnonymousLogins = 64
            maxLoginFailures = 5
            loginFailureDelay = 1000
        }.createConnectionConfig()
        factory.userManager = BfeUserManager(host)
        factory.fileSystem = BfeFileSystemFactory(host)
        factory.ftplets = mapOf("bfe" to ActivityFtplet(host))
        factory.messageResource = PlainFeatMessages(MessageResourceFactory().createMessageResource())
        server = factory.createServer().also { it.start() }
    }

    fun stop() {
        server?.stop()
        server = null
    }
}

/**
 * FtpServer's stock FEAT reply lists AUTH SSL / AUTH TLS even with no TLS configured; clients that see
 * it may try TLS first. This server is plain FTP on the LAN, so FEAT says exactly what works.
 */
private class PlainFeatMessages(private val base: MessageResource) : MessageResource by base {
    override fun getMessage(code: Int, subId: String?, language: String?): String? =
        if (code == 211 && subId == "FEAT") FEAT else base.getMessage(code, subId, language)

    companion object {
        private const val FEAT = "Extensions supported\n SIZE\n MDTM\n REST STREAM\n MLST Size;Modify;Type;Perm\n" +
            " MODE Z\n UTF8\n TVFS\n MD5\n MMD5\n MFMT\nEnd"
    }
}

/** Accepts the current [ServerHost.credentials]; with anonymous access on, accepts everyone. */
private class BfeUserManager(private val host: ServerHost) : UserManager {

    private fun user(name: String): User = BaseUser().apply {
        setName(name)
        setHomeDirectory("/")
        setEnabled(true)
        setMaxIdleTime(600)
        // FtpServer refuses a login outright unless some authority answers ConcurrentLoginRequest
        // (0 = no limit). Write access is decided per share by the file system, not here.
        setAuthorities(listOf(ConcurrentLoginPermission(0, 0), WritePermission()))
    }

    override fun authenticate(authentication: Authentication?): User {
        val creds = host.credentials
        when (authentication) {
            is AnonymousAuthentication ->
                if (creds.anonymous) return user(UserManager.ANONYMOUS)
            is UsernamePasswordAuthentication ->
                if (creds.anonymous || creds.matches(authentication.username, authentication.password ?: "")) {
                    return user(authentication.username ?: UserManager.ANONYMOUS)
                }
        }
        throw AuthenticationFailedException("Authentication failed")
    }

    override fun getUserByName(username: String?): User? = username?.let { user(it) }
    override fun getAllUserNames(): Array<String> = arrayOf(host.credentials.user)
    override fun delete(username: String?) {}
    override fun save(user: User?) {}
    override fun doesExist(username: String?): Boolean = username != null
    override fun getAdminName(): String = host.credentials.user
    override fun isAdmin(username: String?): Boolean = false
}

/** Feeds the status screen: connections, logins, transfers and what was changed. */
private class ActivityFtplet(private val host: ServerHost) : DefaultFtplet() {

    private fun ip(session: FtpSession): String = session.clientAddress?.address?.hostAddress ?: "?"

    private fun path(session: FtpSession, request: FtpRequest): String =
        runCatching { session.fileSystemView.getFile(request.argument).absolutePath }.getOrNull() ?: (request.argument ?: "")

    override fun onConnect(session: FtpSession): FtpletResult {
        host.activity.ftpConnected(ip(session))
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        // A transfer cut off by the disconnect never reaches afterCommand.
        if (session.getAttribute(TX) != null) {
            session.removeAttribute(TX)
            host.activity.transferEnded()
        }
        host.activity.ftpDisconnected(ip(session))
        return FtpletResult.DEFAULT
    }

    override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult {
        if (request.command.uppercase() in TRANSFERS && session.getAttribute(TX) == null) {
            session.setAttribute(TX, true)
            host.activity.transferStarted()
        }
        return FtpletResult.DEFAULT
    }

    // Handled here rather than in the on*End callbacks, which also fire for refused commands.
    override fun afterCommand(session: FtpSession, request: FtpRequest, reply: FtpReply?): FtpletResult {
        val cmd = request.command.uppercase()
        if (cmd in TRANSFERS && session.getAttribute(TX) != null) {
            session.removeAttribute(TX)
            host.activity.transferEnded()
        }
        val ok = (reply?.code ?: 0) in 200..299
        val what = when (cmd) {
            "PASS" -> if (session.isLoggedIn) "Signed in as ${session.user?.name ?: "?"}" else null
            "RETR" -> if (ok) "Downloaded ${path(session, request)}" else null
            "STOR", "STOU" -> if (ok) "Uploaded ${path(session, request)}" else null
            "APPE" -> if (ok) "Appended to ${path(session, request)}" else null
            "DELE" -> if (ok) "Deleted ${path(session, request)}" else null
            "MKD" -> if (ok) "Created folder ${path(session, request)}" else null
            "RMD" -> if (ok) "Removed folder ${request.argument ?: ""}" else null
            "RNTO" -> if (ok) "Renamed to ${path(session, request)}" else null
            else -> null
        }
        if (what != null) host.activity.event(ip(session), "FTP", what)
        return FtpletResult.DEFAULT
    }

    companion object {
        private const val TX = "bfe.transfer"
        private val TRANSFERS = setOf("RETR", "STOR", "STOU", "APPE")
    }
}
