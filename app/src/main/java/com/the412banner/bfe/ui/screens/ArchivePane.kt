package com.the412banner.bfe.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.ArchiveCache
import com.the412banner.bfe.storage.ArchiveEntry
import com.the412banner.bfe.storage.ArchiveException
import com.the412banner.bfe.storage.ArchiveExtract
import com.the412banner.bfe.storage.ArchiveIndex
import com.the412banner.bfe.storage.ArchiveIndexCache
import com.the412banner.bfe.storage.ArchivePasswords
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.unpack.SevenZip
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.DateText
import com.the412banner.bfe.util.DirPicker
import com.the412banner.bfe.util.ErrorLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Archive browsing, pane side: opening an archive (with its loading state and password prompt),
// pulling entries out for viewing / sharing / extraction, and the archive-only dialogs and menu. The
// File Manager's BrowserPane owns one ArchivePaneState and calls into it; the storage side is
// storage/ArchiveBackend.kt + ArchiveListing.kt.

/** True when an entry's NAME marks it as something BFE can browse once it's pulled out. */
internal fun browsableArchiveName(name: String): Boolean =
    name.lowercase().endsWith(".apk") || SevenZip.isSupported(File(name))

@Stable
internal class ArchivePaneState(private val context: Context) {

    /** The open password prompt, answered through [answer]. */
    class PasswordPrompt(val archiveName: String, val wrong: Boolean, val answer: CompletableDeferred<String?>)

    var passwordPrompt by mutableStateOf<PasswordPrompt?>(null)
        private set
    /** What an archive open is doing ("Reading Game.zip…", "Unpacking game.tar…"); null when idle. */
    var openingLabel by mutableStateOf<String?>(null)
        private set
    var openingPercent by mutableIntStateOf(-1)
        private set
    /** Entries the Extract to… dialog is open for (empty = closed). */
    var extractTargets by mutableStateOf<List<Loc.ArchiveLoc>>(emptyList())
    private val warned = HashSet<String>()

    /** Shows the password prompt and suspends until it's answered; null = cancelled. */
    suspend fun askPassword(archiveName: String, wrong: Boolean): String? {
        val prompt = PasswordPrompt(archiveName, wrong, CompletableDeferred())
        passwordPrompt = prompt
        return try { prompt.answer.await() } finally { if (passwordPrompt === prompt) passwordPrompt = null }
    }

    fun answerPassword(value: String?) { passwordPrompt?.answer?.complete(value) }

    sealed class Open {
        class Ready(val index: ArchiveIndex, val warning: String?) : Open()
        /** The user backed out of the password prompt. */
        object Cancelled : Open()
        class Failed(val message: String) : Open()
    }

    /**
     * Makes sure [loc]'s archive is indexed, asking for a password while its listing is encrypted.
     * Cancelling the caller (leaving the archive) kills the running 7zz.
     */
    suspend fun ensureOpen(loc: Loc.ArchiveLoc): Open {
        val archive = loc.archive
        try {
            while (true) {
                try {
                    val index = ArchiveIndexCache.load(context, archive) { label, pct -> openingLabel = label; openingPercent = pct }
                    val warning = index.warning?.takeIf { warned.add(index.key) }
                    return Open.Ready(index, warning)
                } catch (e: ArchiveException.PasswordNeeded) {
                    openingLabel = null
                    val pw = askPassword(archive.name, e.wrong) ?: return Open.Cancelled
                    ArchivePasswords.set(archive, pw)
                } catch (e: ArchiveException) {
                    ErrorLog.record(context, "Archive open (browse)", e, details = listOf(ErrorLog.archiveFacts(archive.name, archive.length())))
                    return Open.Failed(e.message ?: "Couldn't open ${archive.name}")
                }
            }
        } finally {
            openingLabel = null
            openingPercent = -1
        }
    }

    /**
     * Runs [block] with the archive's password, prompting first when [needed] and none is known, and
     * again after a wrong one. Null when the user cancels the prompt.
     */
    private suspend fun <T> withPassword(index: ArchiveIndex, needed: Boolean, block: suspend (String) -> T): T? {
        var ask = needed && ArchivePasswords.get(index.source) == null
        var wrong = false
        while (true) {
            if (ask) {
                val pw = askPassword(index.source.name, wrong) ?: return null
                ArchivePasswords.set(index.source, pw)
            }
            try {
                return block(ArchivePasswords.get(index.source) ?: "")
            } catch (e: ArchiveException.PasswordNeeded) {
                ArchivePasswords.forget(index.source)
                wrong = e.wrong
                ask = true
            }
        }
    }

    /** The index of [loc]'s archive (any loc of it will do), opening it if needed; null if it can't be. */
    private suspend fun indexOf(loc: Loc.ArchiveLoc): ArchiveIndex? =
        when (val o = ensureOpen(loc)) {
            is Open.Ready -> o.index
            is Open.Failed -> { AppSnackbar.show(context, o.message, long = true); null }
            Open.Cancelled -> null
        }

    /** An already-cached copy of the file [loc] (no 7zz run needed), or null. Cheap; any thread. */
    fun cachedCopy(loc: Loc.ArchiveLoc): File? {
        val index = ArchiveIndexCache.peek(loc.archive) ?: return null
        val e = index.entryFor(loc)?.takeIf { !it.isDir } ?: return null
        val f = File(ArchiveCache.viewDir(context, index.source), e.path)
        return f.takeIf { it.isFile && it.length() == e.size }
    }

    /**
     * Pulls the FILES among [locs] out into BFE's cache (for Open with, Share, or browsing a nested
     * archive). Null on failure (already reported) or when the password prompt was cancelled.
     */
    suspend fun extractForView(locs: List<Loc.ArchiveLoc>, onPercent: (Int) -> Unit): List<File>? {
        val first = locs.firstOrNull() ?: return emptyList()
        val index = indexOf(first) ?: return null
        val entries = locs.mapNotNull { index.entryFor(it) }.filter { !it.isDir }
        if (entries.isEmpty()) return emptyList()
        return try {
            withPassword(index, entries.any { index.tree.needsPassword(it) }) { pw ->
                ArchiveExtract.toViewCache(context, index, entries, pw, onPercent)
            }
        } catch (e: ArchiveException) {
            ErrorLog.record(context, "Archive extract (to view)", e,
                details = listOf(ErrorLog.archiveFacts(index.source.name, index.source.length()), "Entries: ${entries.size}, bytes: ${entries.sumOf { it.size }}"))
            AppSnackbar.show(context, e.message ?: "Couldn't extract ${first.name}", long = true)
            null
        }
    }

    /** One entry for [extractTo]: extract [loc] as [targetName]; [replace] = overwrite/merge an existing one. */
    class Item(val loc: Loc.ArchiveLoc, val targetName: String, val replace: Boolean)

    /**
     * Extracts [items] (same archive) into the folder [dest]. [onStage] drives the pane's progress
     * strip. Returns the message to show (success or failure); null when the password prompt was
     * cancelled.
     */
    suspend fun extractTo(items: List<Item>, dest: Loc, onStage: (String, Float?) -> Unit): String? {
        val first = items.firstOrNull() ?: return null
        val index = indexOf(first.loc) ?: return null
        val planned = items.mapNotNull { i -> index.entryFor(i.loc)?.let { ArchiveExtract.Planned(it, i.targetName, i.replace) } }
        return try {
            val failed = withPassword(index, planned.any { index.tree.needsPassword(it.entry) }) { pw ->
                ArchiveExtract.toDestination(context, index, planned, dest, pw, onStage)
            } ?: return null
            val where = dest.name.ifBlank { "/" }
            if (failed > 0) {
                ErrorLog.record(context, "Archive extract to folder (partial)", "$failed of ${planned.size} items failed",
                    details = listOf(ErrorLog.archiveFacts(index.source.name, index.source.length()), "Destination: ${dest.javaClass.simpleName}"))
            }
            when {
                failed > 0 -> "Extracted ${planned.size - failed} of ${planned.size} into $where"
                planned.size == 1 -> "Extracted ${planned[0].targetName} into $where"
                else -> "Extracted ${planned.size} items into $where"
            }
        } catch (e: ArchiveException) {
            ErrorLog.record(context, "Archive extract to folder", e,
                details = listOf(ErrorLog.archiveFacts(index.source.name, index.source.length()), "Items: ${planned.size}, destination: ${dest.javaClass.simpleName}"))
            e.message ?: "Extraction failed"
        }
    }
}

@Composable
internal fun rememberArchivePaneState(): ArchivePaneState {
    val context = LocalContext.current
    return remember { ArchivePaneState(context.applicationContext) }
}

/** The pane body while an archive is being opened: what's happening, progress, and a way out. */
@Composable
internal fun ArchiveOpeningIndicator(state: ArchivePaneState, archiveName: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pct = state.openingPercent
        if (pct >= 0) CircularProgressIndicator(progress = { pct / 100f }, color = MaterialTheme.colorScheme.primary)
        else CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            (state.openingLabel ?: "Opening $archiveName…") + if (pct >= 0) "  $pct%" else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** "read-only · zip" at the right end of the breadcrumb while a pane shows an archive. */
@Composable
internal fun ArchiveCrumbBadge(loc: Loc.ArchiveLoc) {
    val label by produceState<String?>(null, loc.archive) {
        value = withContext(Dispatchers.IO) { ArchiveIndexCache.peek(loc.archive)?.formatLabel }
    }
    Text(
        listOfNotNull("read-only", label).joinToString(" · "),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        maxLines = 1,
    )
}

/**
 * The ⋮ menu for an entry inside an archive: only what a read-only archive allows (open, browse a
 * nested archive, extract, share, properties, select, copy-to-clipboard for pasting elsewhere).
 */
@Composable
internal fun ArchiveEntryMenuItems(
    file: Loc.ArchiveLoc,
    onOpenWith: () -> Unit,
    onBrowse: () -> Unit,
    onExtractTo: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    @Composable
    fun Item(label: String, icon: ImageVector, action: () -> Unit) {
        DropdownMenuItem(
            text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); action() },
        )
    }
    if (!file.isDir) {
        Item("Open with…", Icons.Filled.OpenInNew, onOpenWith)
        if (browsableArchiveName(file.name)) Item("Open as folder", Icons.Filled.FolderOpen, onBrowse)
        MenuItemDivider()
    }
    Item("Extract to…", Icons.Filled.Unarchive, onExtractTo)
    if (!file.isDir) Item("Share", Icons.Filled.Share, onShare)
    Item("Properties", Icons.Filled.Info, onProperties)
    MenuItemDivider()
    Item("Select", Icons.Filled.Checklist, onSelect)
    Item("Copy", Icons.Filled.FileCopy, onCopy)
}

/**
 * The archive dialogs a pane can show: the password prompt and Extract to…. [hereDir] is the folder
 * the archive sits in; [otherPane] the other pane's folder in dual-pane mode (null otherwise).
 */
@Composable
internal fun ArchiveDialogs(
    state: ArchivePaneState,
    hereDir: File?,
    outerArchive: File?,
    otherPane: Loc?,
    onExtract: (List<Loc.ArchiveLoc>, Loc) -> Unit,
) {
    state.passwordPrompt?.let { p -> ArchivePasswordDialog(p, onAnswer = state::answerPassword) }
    val targets = state.extractTargets
    if (targets.isNotEmpty()) {
        ArchiveExtractDialog(
            items = targets,
            hereDir = hereDir,
            outerArchive = outerArchive,
            otherPane = otherPane,
            onDismiss = { state.extractTargets = emptyList() },
            onConfirm = { dest -> state.extractTargets = emptyList(); onExtract(targets, dest) },
        )
    }
}

@Composable
private fun ArchivePasswordDialog(prompt: ArchivePaneState.PasswordPrompt, onAnswer: (String?) -> Unit) {
    var value by remember(prompt) { mutableStateOf("") }
    var show by remember(prompt) { mutableStateOf(false) }
    OutlinedAlertDialog(
        onDismissRequest = { onAnswer(null) },
        title = { Text("Password") },
        text = {
            Column {
                Text(
                    prompt.archiveName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (prompt.wrong) {
                    Spacer(Modifier.height(4.dp))
                    Text("Wrong password — try again.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text("Archive password") },
                    isError = prompt.wrong,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Hide password" else "Show password")
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text("Remembered until BFE is closed.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(enabled = value.isNotEmpty(), onClick = { onAnswer(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text("Cancel") } },
    )
}

/** Where Extract to… puts things: the other pane, the archive's folder, a new folder there, or a picked one. */
private enum class ExtractDest { OTHER, HERE, NEW_FOLDER, PICKED }

@Composable
private fun ArchiveExtractDialog(
    items: List<Loc.ArchiveLoc>,
    hereDir: File?,
    outerArchive: File?,
    otherPane: Loc?,
    onDismiss: () -> Unit,
    onConfirm: (Loc) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val newFolderName = remember(outerArchive) { outerArchive?.let { SevenZip.suggestedTargetName(it) } ?: "Extracted" }
    var choice by remember { mutableStateOf(if (otherPane != null) ExtractDest.OTHER else ExtractDest.HERE) }
    var picked by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val path = DirPicker.resolvePath(r.data)
        val dir = path?.let(::File)?.takeIf { it.isDirectory }
        if (dir != null) { picked = dir; choice = ExtractDest.PICKED }
        else if (r.data?.data != null) AppSnackbar.show(context, "That folder can't be used — pick one on internal storage or an SD card")
    }

    @Composable
    fun Option(dest: ExtractDest, title: String, detail: String?, onClick: () -> Unit = { choice = dest }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        ) {
            RadioButton(selected = choice == dest, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp)
                if (detail != null) {
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.size == 1) "Extract ${items[0].name}" else "Extract ${items.size} items") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Folders keep everything inside them.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                if (otherPane != null) Option(ExtractDest.OTHER, "Other pane", otherPane.name.ifBlank { "/" })
                if (hereDir != null) {
                    Option(ExtractDest.HERE, "Next to the archive", hereDir.absolutePath)
                    Option(ExtractDest.NEW_FOLDER, "New folder “$newFolderName”", "Next to the archive")
                }
                Option(ExtractDest.PICKED, "Choose folder…", picked?.absolutePath) {
                    runCatching { picker.launch(DirPicker.buildDirIntent()) }
                        .onFailure { AppSnackbar.show(context, "No folder picker available") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = when (choice) {
                    ExtractDest.OTHER -> otherPane != null
                    ExtractDest.HERE, ExtractDest.NEW_FOLDER -> hereDir != null
                    ExtractDest.PICKED -> picked != null
                },
                onClick = {
                    when (choice) {
                        ExtractDest.OTHER -> otherPane?.let(onConfirm)
                        ExtractDest.HERE -> hereDir?.let { onConfirm(Loc.FileLoc(it)) }
                        ExtractDest.PICKED -> picked?.let { onConfirm(Loc.FileLoc(it)) }
                        ExtractDest.NEW_FOLDER -> hereDir?.let { here ->
                            scope.launch {
                                val dir = File(here, newFolderName)
                                val ok = withContext(Dispatchers.IO) { dir.isDirectory || dir.mkdirs() }
                                if (ok) onConfirm(Loc.FileLoc(dir)) else AppSnackbar.show(context, "Couldn't create “$newFolderName”")
                            }
                        }
                    }
                },
            ) { Text("Extract") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Properties of an archive (its root) or of one entry inside it. */
@Composable
internal fun ArchivePropertiesDialog(loc: Loc.ArchiveLoc, onDismiss: () -> Unit) {
    val index by produceState<ArchiveIndex?>(null, loc.id) {
        value = withContext(Dispatchers.IO) { ArchiveIndexCache.peek(loc.archive) }
    }
    val context = LocalContext.current
    fun date(ms: Long) = if (ms > 0) DateText.dateTime(context, ms) else "—"

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(loc.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                val i = index
                val e: ArchiveEntry? = i?.entryFor(loc)
                when {
                    i == null -> Text("Archive details aren't loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    loc.isRoot -> {
                        ArchivePropRow("Location", loc.archive.parent ?: "—")
                        ArchivePropRow("Format", i.formatLabel)
                        ArchivePropRow("Contents", "${i.tree.fileCount} files, ${i.tree.dirCount} folders")
                        ArchivePropRow("Unpacked", StringUtils.formatBytes(i.tree.totalSize))
                        ArchivePropRow("Size", StringUtils.formatBytes(loc.archive.length()))
                        i.props["Method"]?.takeIf { it.isNotBlank() && i.outerType == null }?.let { ArchivePropRow("Method", it) }
                        i.props["Solid"]?.takeIf { it == "+" }?.let { ArchivePropRow("Solid", "Yes") }
                        ArchivePropRow("Encrypted", if (i.tree.anyEncrypted) "Yes" else "No")
                        ArchivePropRow("Modified", date(loc.archive.lastModified()))
                        i.warning?.let { ArchivePropRow("Warning", it) }
                    }
                    e != null -> {
                        ArchivePropRow("In", listOf(loc.archive.name).plus(e.parent.split('/').filter { it.isNotEmpty() }).joinToString(" › "))
                        ArchivePropRow("Type", if (e.isDir) "Folder" else e.name.substringAfterLast('.', "").uppercase().let { if (it.isBlank() || !e.name.contains('.')) "File" else "$it file" })
                        if (e.isDir) {
                            val n = remember(e.path) { i.tree.descendants(e.path).count() }
                            ArchivePropRow("Contains", if (n == 1) "1 item" else "$n items")
                        }
                        ArchivePropRow("Size", StringUtils.formatBytes(remember(e.path) { i.tree.sizeOf(e) }))
                        if (!e.isDir && e.packedSize >= 0) ArchivePropRow("Packed", StringUtils.formatBytes(e.packedSize))
                        ArchivePropRow("Modified", date(e.modified))
                        e.method?.let { ArchivePropRow("Method", it) }
                        e.crc?.let { ArchivePropRow("CRC", it) }
                        if (!e.isDir) ArchivePropRow("Encrypted", if (e.encrypted) "Yes" else "No")
                        e.attributes?.let { ArchivePropRow("Attributes", it) }
                        if (e.renamed && e.rawPath != null) ArchivePropRow("Stored as", e.rawPath)
                    }
                    else -> Text("This entry is no longer in the archive.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))
                Text(ArchiveBackendMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private val ArchiveBackendMessage = com.the412banner.bfe.storage.ArchiveBackend.READ_ONLY_MESSAGE + "."

@Composable
private fun ArchivePropRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
