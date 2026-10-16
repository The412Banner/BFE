package com.the412banner.bfe.server

import java.io.File
import java.security.MessageDigest

// Pure JVM (no android.*): the network file server core is exercised off-device as well.

/** One folder offered to the network, shown to clients under [name]. */
data class ShareRoot(val name: String, val dir: File, val readOnly: Boolean)

/**
 * Who may connect. One set of credentials is shared by HTTP (Basic auth) and FTP. With [anonymous]
 * on, nobody is asked for anything (FTP accepts "anonymous" and any user/password pair).
 */
data class Credentials(val anonymous: Boolean, val user: String, val password: String) {

    /** Constant-time check, so response timing doesn't leak how much of a guess was right. */
    fun matches(u: String?, p: String?): Boolean {
        if (anonymous) return true
        if (u == null || p == null) return false
        val userOk = MessageDigest.isEqual(u.toByteArray(Charsets.UTF_8), user.toByteArray(Charsets.UTF_8))
        val passOk = MessageDigest.isEqual(p.toByteArray(Charsets.UTF_8), password.toByteArray(Charsets.UTF_8))
        return userOk and passOk
    }
}
