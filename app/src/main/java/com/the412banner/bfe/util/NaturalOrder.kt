package com.the412banner.bfe.util

/**
 * "Natural" name order, ignoring case: runs of digits compare as numbers, so "Disc 2" sorts before
 * "Disc 10" and "file9" before "file10". Shared by the file lists and the media viewer/player's
 * folder playlists so both agree on what "next" means.
 */
fun naturalCompare(x: String, y: String): Int {
    var i = 0
    var j = 0
    while (i < x.length && j < y.length) {
        val cx = x[i]
        val cy = y[j]
        if (cx.isDigit() && cy.isDigit()) {
            val si = i
            val sj = j
            while (i < x.length && x[i].isDigit()) i++
            while (j < y.length && y[j].isDigit()) j++
            val nx = x.substring(si, i).trimStart('0')
            val ny = y.substring(sj, j).trimStart('0')
            if (nx.length != ny.length) return nx.length - ny.length
            val c = nx.compareTo(ny)
            if (c != 0) return c
        } else {
            val c = cx.lowercaseChar().compareTo(cy.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (x.length - i) - (y.length - j)
}
