package com.the412banner.bfe.ui.internals

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** One row of a package level: a sub-package (with its class count) or a class. */
internal sealed interface PackageItem {
    val label: String
    class Pkg(override val label: String, val path: String, val count: Int) : PackageItem
    class Cls(override val label: String, val name: String) : PackageItem
}

/**
 * The rows directly under [pkg] ("" = the top, else "com/example/") of [names] — class names in slash
 * form ("com/example/Foo$Bar"), sorted. Packages first, then classes, each in name order.
 */
internal fun packageLevel(names: List<String>, pkg: String): List<PackageItem> {
    val pkgs = LinkedHashMap<String, Int>()
    val classes = ArrayList<PackageItem.Cls>()
    for (n in names) {
        if (!n.startsWith(pkg)) continue
        val rest = n.substring(pkg.length)
        val slash = rest.indexOf('/')
        if (slash < 0) classes += PackageItem.Cls(rest, n)
        else rest.substring(0, slash).let { pkgs[it] = (pkgs[it] ?: 0) + 1 }
    }
    return pkgs.entries.sortedBy { it.key.lowercase() }.map { PackageItem.Pkg(it.key, "$pkg${it.key}/", it.value) } +
        classes.sortedBy { it.label.lowercase() }
}

/**
 * A package tree one level at a time, like folders: tap a package to go in, a class to open it.
 * The caller owns [pkg] (so Back can climb out) and the class list.
 */
@Composable
internal fun PackageBrowser(names: List<String>, pkg: String, onPackage: (String) -> Unit, onClass: (String) -> Unit, modifier: Modifier = Modifier) {
    val rows = remember(names, pkg) { packageLevel(names, pkg) }
    LazyColumn(modifier.fillMaxSize()) {
        items(rows, key = { (if (it is PackageItem.Pkg) "p:" else "c:") + it.label }) { r ->
            when (r) {
                is PackageItem.Pkg -> InternalsRow(Icons.Filled.Folder, r.label, null, { onPackage(r.path) }, end = "%,d".format(r.count), code = true)
                is PackageItem.Cls -> InternalsRow(Icons.Filled.Code, r.label, null, { onClass(r.name) }, code = true)
            }
        }
    }
}
