package com.the412banner.bfe.util

/**
 * Strips personal details from error text before it goes into an error log ([ErrorLog]): account and
 * remote names, addresses (URLs, hosts, IPs, e-mail), user names, file and folder names and paths,
 * passwords, tokens and keys. What's left is the shape of the error ("error in ListJSON: connection
 * error: EOF"), which is what a bug report needs.
 *
 * It errs on the side of removing too much: anything with a dot or a slash in it is treated as a name
 * or path. Plain JVM so it can be unit-tested.
 */
object LogScrub {
    private val rcloneObject = Regex(
        """^((?:\d{4}/\d\d/\d\d \d\d:\d\d:\d\d )?(?:DEBUG|INFO|NOTICE|WARNING|ERROR|CRITICAL|ALERT|EMERGENCY)\s*: )(.+?): """,
        RegexOption.MULTILINE,
    )
    private val secretPair = Regex(
        """(?i)\b(password|passwd|pass|pwd|token|access_token|refresh_token|secret|client_secret|client_id|api_?key|key|authorization|bearer|user|username|login|account|domain)(["']?\s*[:=]\s*)("[^"\n]*"|'[^'\n]*'|\S+)""",
    )
    private val bearer = Regex("""(?i)\b(Bearer|Basic)\s+\S+""")
    private val url = Regex("""(?i)\b([a-z][a-z0-9+.-]{1,15})://[^\s"'<>]+""")
    private val lookup = Regex("""(?i)\b(lookup|resolve|host) (?!<)\S+""")
    private val email = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val userAt = Regex("""\b[\w.-]+@(?=<)""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?\b""")
    private val ipv6 = Regex("""(?i)\[?\b(?:[0-9a-f]{1,4}:){5,7}[0-9a-f]{1,4}\b\]?|\[?(?:[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:?)*[0-9a-f]{0,4}\]?""")
    private val uncPath = Regex("""\\\\[^\s"']+""")
    private val winPath = Regex("""\b[A-Za-z]:\\[^\s"']*""")
    private val domainUser = Regex("""\b[\w.-]+\\[\w.$-]+""")
    private val unixPath = Regex("""(?<![\w<>:/])~?/[^\s"':,;)\]]*""")
    private val relPath = Regex("""\b(?=[\w.\-/]*[A-Za-z])[\w.\-]+(?:/[\w.\-]+)+/?""")
    private val dotted = Regex("""\b[\w-]*[A-Za-z_][\w-]*(?:\.[\w-]+)+\b|\b[\w-]+(?:\.[\w-]+)*\.[\w-]*[A-Za-z_][\w-]*\b""")
    private val quoted = Regex(""""[^"\n]*"|'[^'\n]{2,}'""")
    private val longToken = Regex("""\b(?=[A-Za-z_\-+/=]*\d)[A-Za-z0-9_\-+/=]{24,}\b|\b[0-9a-fA-F]{16,}\b""")

    /** [text] without personal details; every exact (case-insensitive) [names] word becomes `<remote>`. */
    fun scrub(text: String, names: Collection<String> = emptyList()): String {
        var s = text
        s = rcloneObject.replace(s) { "${it.groupValues[1]}<item>: " }
        s = bearer.replace(s) { "${it.groupValues[1]} <redacted>" }
        s = secretPair.replace(s) { "${it.groupValues[1]}${it.groupValues[2]}<redacted>" }
        s = url.replace(s) { "${it.groupValues[1]}://<address>" }
        s = email.replace(s, "<email>")
        names.filter { it.length >= 2 }.sortedByDescending { it.length }.forEach { n ->
            s = Regex("""(?i)(?<![\w.-])${Regex.escape(n)}(?![\w-])""").replace(s, "<remote>")
        }
        s = lookup.replace(s) { "${it.groupValues[1]} <host>" }
        s = uncPath.replace(s, "<path>")
        s = winPath.replace(s, "<path>")
        s = domainUser.replace(s, "<user>")
        s = ipv4.replace(s, "<ip>")
        s = ipv6.replace(s) { if (it.value.count { c -> c == ':' } >= 2) "<ip>" else it.value }
        s = userAt.replace(s, "<user>@")
        s = unixPath.replace(s) { if (it.value == "/" || it.value == "~/") it.value else "<path>" }
        s = relPath.replace(s, "<path>")
        s = quoted.replace(s) { if (it.value.startsWith("'")) "'<text>'" else "\"<text>\"" }
        s = dotted.replace(s, "<name>")
        s = longToken.replace(s, "<redacted>")
        return s
    }
}
