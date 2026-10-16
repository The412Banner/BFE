package com.the412banner.bfe.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The phone's addresses on local networks — the only addresses the servers bind to.
 *
 * - Wi-Fi: interfaces of networks the framework reports as TRANSPORT_WIFI.
 * - Hotspot: a Wi-Fi-type interface (wlan / swlan / ap / softap...) that ISN'T the connected Wi-Fi
 *   network — the phone's own access point. Tethering isn't a ConnectivityManager network, so this
 *   one is found by interface name, and only private (RFC 1918) addresses count.
 * - Ethernet (a dock / USB adapter): TRANSPORT_ETHERNET or an eth* interface.
 *
 * Cellular (rmnet, ccmni, clat v4-...), VPN (tun) and other virtual interfaces are never used, so the
 * file server is never reachable from the carrier side.
 */
object LanAddresses {

    enum class Kind(val label: String) { WIFI("Wi-Fi"), HOTSPOT("Hotspot"), ETHERNET("Ethernet") }

    data class LanAddress(val ip: String, val iface: String, val kind: Kind)

    private val NEVER = Regex(
        "^(rmnet|r_rmnet|rev_rmnet|ccmni|ccemni|pdp|ppp|v4-|clat|seth|wwan|usb|rndis|ncm|bt-pan|tun|ipsec|" +
            "dummy|lo|ifb|sit|ip6|gre|epdg|aware|nan|p2p)",
        RegexOption.IGNORE_CASE,
    )
    private val WIFI_LIKE = Regex("^(wlan|swlan|ap|softap|wifi|wigig)", RegexOption.IGNORE_CASE)

    fun detect(ctx: Context): List<LanAddress> {
        val wifi = HashSet<String>()
        val ethernet = HashSet<String>()
        val blocked = HashSet<String>()
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            @Suppress("DEPRECATION")
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                val name = cm.getLinkProperties(n)?.interfaceName ?: continue
                when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> blocked += name
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifi += name
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ethernet += name
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> blocked += name
                }
            }
        }

        val out = ArrayList<LanAddress>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
        for (ni in interfaces) {
            val up = runCatching { ni.isUp && !ni.isLoopback }.getOrDefault(false)
            if (!up) continue
            val name = ni.name ?: continue
            val kind = when {
                name in blocked -> null
                name in wifi -> Kind.WIFI
                name in ethernet || name.startsWith("eth") -> Kind.ETHERNET
                NEVER.containsMatchIn(name) -> null
                WIFI_LIKE.containsMatchIn(name) -> Kind.HOTSPOT
                else -> null
            } ?: continue
            for (addr in ni.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress) continue
                if (kind == Kind.HOTSPOT && !addr.isSiteLocalAddress) continue
                out += LanAddress(addr.hostAddress ?: continue, name, kind)
            }
        }
        return out.distinctBy { it.ip }.sortedWith(compareBy({ it.kind.ordinal }, { it.ip }))
    }
}
