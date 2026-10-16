package com.the412banner.bfe.server

/**
 * Cheap connection/activity bookkeeping for the status screen: which devices were active recently,
 * how many transfers are running, and the last few things clients did. Thread-safe; both servers
 * call into it from their own threads. Pure JVM.
 */
class ServerActivity {

    data class Event(val time: Long, val ip: String, val protocol: String, val text: String)

    data class Snapshot(
        /** Devices seen in the last [RECENT_MS] or holding an FTP session open. */
        val devices: List<String>,
        val transfers: Int,
        /** Newest first. */
        val recent: List<Event>,
        /** Bumped on every change, so a poller can skip identical snapshots. */
        val version: Long,
    ) {
        companion object {
            val EMPTY = Snapshot(emptyList(), 0, emptyList(), 0L)
        }
    }

    private val lastSeen = HashMap<String, Long>()
    private val ftpSessions = HashMap<String, Int>()
    private val events = ArrayDeque<Event>()
    private var transfers = 0
    private var version = 0L

    @Synchronized
    fun seen(ip: String) {
        val now = System.currentTimeMillis()
        val prev = lastSeen.put(ip, now)
        if (prev == null || now - prev > RECENT_MS) version++
    }

    @Synchronized
    fun event(ip: String, protocol: String, text: String) {
        lastSeen[ip] = System.currentTimeMillis()
        events.addFirst(Event(System.currentTimeMillis(), ip, protocol, text))
        while (events.size > MAX_EVENTS) events.removeLast()
        version++
    }

    @Synchronized
    fun transferStarted() {
        transfers++
        version++
    }

    @Synchronized
    fun transferEnded() {
        transfers = (transfers - 1).coerceAtLeast(0)
        version++
    }

    @Synchronized
    fun ftpConnected(ip: String) {
        ftpSessions[ip] = (ftpSessions[ip] ?: 0) + 1
        lastSeen[ip] = System.currentTimeMillis()
        version++
    }

    @Synchronized
    fun ftpDisconnected(ip: String) {
        val n = (ftpSessions[ip] ?: 1) - 1
        if (n <= 0) ftpSessions.remove(ip) else ftpSessions[ip] = n
        lastSeen[ip] = System.currentTimeMillis()
        version++
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val cutoff = System.currentTimeMillis() - RECENT_MS
        lastSeen.entries.removeAll { it.value < cutoff && it.key !in ftpSessions }
        val devices = (lastSeen.keys + ftpSessions.keys).toSortedSet().toList()
        return Snapshot(devices, transfers, events.toList(), version)
    }

    @Synchronized
    fun reset() {
        lastSeen.clear()
        ftpSessions.clear()
        events.clear()
        transfers = 0
        version++
    }

    companion object {
        const val RECENT_MS = 2 * 60 * 1000L
        private const val MAX_EVENTS = 30
    }
}
