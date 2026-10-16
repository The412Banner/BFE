@file:OptIn(ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.cloud

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.the412banner.bfe.cloud.CloudSection
import com.the412banner.bfe.cloud.CloudService
import com.the412banner.bfe.cloud.CloudServices
import com.the412banner.bfe.cloud.CloudSetup
import com.the412banner.bfe.cloud.FieldKind
import com.the412banner.bfe.cloud.RcOption
import com.the412banner.bfe.cloud.RcProvider
import com.the412banner.bfe.cloud.RcloneDaemon
import com.the412banner.bfe.cloud.ServiceField
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens [url] in the user's browser (never a WebView: Google refuses embedded sign-in). */
internal fun openInBrowser(ctx: android.content.Context, url: String): Boolean = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/**
 * Add an account / connection: pick a service (or any rclone backend under More services), fill its
 * form, and [CloudSetup] does the rest (browser sign-in, rclone's follow-up questions, a test listing).
 * [prefill] reopens the form of a session whose connection test failed ("Edit").
 */
@Composable
internal fun AddAccountPage(section: CloudSection, prefill: CloudSetup.Session?, onBack: () -> Unit) {
    val context = LocalContext.current
    var picked by rememberSaveable { mutableStateOf(prefill?.service?.id ?: prefill?.let { "more:${it.type}" }) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var taken by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        taken = runCatching { withContext(Dispatchers.IO) { RcloneDaemon.api(context).remotes().map { it.name } } }.getOrDefault(emptyList())
            .filter { it != prefill?.name }
    }
    fun goBack() {
        if (picked != null && prefill == null) picked = null
        else if (showMore) showMore = false
        else onBack()
    }
    BackHandler { goBack() }

    val p = picked
    val service = p?.let { id -> CloudServices.all.firstOrNull { it.id == id } }
    when {
        p != null && p.startsWith("more:") -> ProviderForm(p.removePrefix("more:"), taken, prefill, onBack = { goBack() })
        service != null -> ServiceForm(service, taken, prefill, onBack = { goBack() })
        showMore -> MoreServicesList(onPick = { picked = "more:$it" }, onBack = { goBack() })
        else -> ServicePicker(section, onPick = { picked = it.id }, onMore = { showMore = true }, onBack = { goBack() })
    }
}

@Composable
private fun PageScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
            )
        },
        content = content,
    )
}

@Composable
private fun ServicePicker(section: CloudSection, onPick: (CloudService) -> Unit, onMore: () -> Unit, onBack: () -> Unit) {
    PageScaffold(if (section == CloudSection.CLOUD) "Add account" else "Add connection", onBack) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            items(CloudServices.forSection(section), key = { it.id }) { s ->
                ServiceTile(s.label, s.blurb, { ServiceAvatar(s.icon, s.color) }) { onPick(s) }
            }
            item(key = "more") {
                ServiceTile("More services…", "Every backend rclone has", {
                    ServiceAvatar(Icons.Filled.Widgets, MaterialTheme.colorScheme.onSurfaceVariant)
                }, onMore)
            }
        }
    }
}

@Composable
private fun ServiceTile(label: String, blurb: String, avatar: @Composable () -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            avatar()
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MoreServicesList(onPick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var providers by remember { mutableStateOf(CloudCache.providers) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (providers == null) {
            runCatching { CloudCache.providers(context) }
                .onSuccess { providers = it }
                .onFailure { error = CloudSetup.friendly(it.message) }
        }
    }
    PageScaffold("More services", onBack) { pad ->
        val list = providers?.filter { !it.hidden && it.name != "local" }?.sortedBy { it.description.lowercase() }
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                list == null && error == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list == null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "Every storage system this rclone build includes, set up with rclone's own options. " +
                                "The services on the previous page are the same backends with simpler forms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(list, key = { it.name }) { p ->
                        val (icon, color) = CloudServices.all.firstOrNull { it.type == p.name }?.let { it.icon to it.color }
                            ?: CloudServices.lookForType(p.name)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p.name) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            ServiceAvatar(icon, color, size = 36)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.description, style = MaterialTheme.typography.bodyLarge)
                                Text(p.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ── Curated service form ─────────────────────────────────────────────────

@Composable
private fun ServiceForm(service: CloudService, taken: List<String>, prefill: CloudSetup.Session?, onBack: () -> Unit) {
    val context = LocalContext.current
    val values = remember(service.id) {
        mutableStateMapOf<String, String>().apply {
            service.fields.forEach { put(it.key, prefill?.form?.get(it.key) ?: it.default) }
            put("client_id", prefill?.form?.get("client_id").orEmpty())
            put("client_secret", prefill?.form?.get("client_secret").orEmpty())
        }
    }
    var name by remember(service.id) { mutableStateOf(prefill?.name ?: uniqueName(service.label, taken)) }
    var ownId by remember(service.id) { mutableStateOf(!prefill?.form?.get("client_id").isNullOrBlank()) }
    val nameError = if (prefill != null && name.trim() == prefill.name) null else nameProblem(name.trim(), taken)
    val missing = service.fields.any { it.required && values[it.key].isNullOrBlank() }

    PageScaffold(service.label, onBack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceAvatar(service.icon, service.color, size = 48)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(service.label, style = MaterialTheme.typography.titleLarge)
                    Text(service.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            service.note?.let { note ->
                NoteCard(note, service.noteLink?.let { link -> "How to make one" to link })
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name in BFE") },
                singleLine = true,
                isError = nameError != null,
                supportingText = if (nameError != null) { { Text(nameError) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            service.fields.forEach { f ->
                FieldInput(f, values[f.key].orEmpty()) { values[f.key] = it }
            }
            if (service.oauth) {
                Text(
                    "Signing in opens ${service.label} in your browser. When it's done, tap “Return to BFE”. " +
                        "BFE keeps the sign-in only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { ownId = !ownId }.padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Use my own client ID", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (service.ownClientIdRecommended) "Recommended for ${service.label}" else "Optional; rclone's built-in one works",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ownId, onCheckedChange = { ownId = it })
                }
                if (ownId) {
                    FieldInput(ServiceField("client_id", "Client ID", required = true), values["client_id"].orEmpty()) { values["client_id"] = it }
                    FieldInput(ServiceField("client_secret", "Client secret", FieldKind.PASSWORD), values["client_secret"].orEmpty()) { values["client_secret"] = it }
                    Text(
                        "Set the app's redirect URL to http://127.0.0.1:53682/ (http://localhost:53682/ for Dropbox).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                enabled = nameError == null && !missing && (!ownId || !values["client_id"].isNullOrBlank()),
                onClick = {
                    val form = HashMap<String, String>()
                    service.fields.forEach { f ->
                        val v = values[f.key].orEmpty().trim()
                        when {
                            v.isNotEmpty() -> form[f.key] = v
                            f.blankValue != null -> form[f.key] = f.blankValue
                        }
                    }
                    if (service.oauth && ownId) {
                        values["client_id"]?.trim()?.takeIf { it.isNotEmpty() }?.let { form["client_id"] = it }
                        values["client_secret"]?.trim()?.takeIf { it.isNotEmpty() }?.let { form["client_secret"] = it }
                    }
                    CloudSetup.begin(context, name.trim(), service.type, service, form)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (service.oauth) "Sign in with ${service.label}" else "Connect") }
        }
    }
}

@Composable
private fun NoteCard(text: String, link: Pair<String, String>?) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (link != null) {
                    TextButton(onClick = { openInBrowser(context, link.second) }, contentPadding = PaddingValues(0.dp)) {
                        Text(link.first)
                    }
                }
            }
        }
    }
}

/** One form input: a switch for yes/no, a text field (with suggestions) for the rest. */
@Composable
internal fun FieldInput(field: ServiceField, value: String, onChange: (String) -> Unit) {
    if (field.kind == FieldKind.BOOL) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onChange((value != "true").toString()) }.padding(vertical = 2.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(field.label, style = MaterialTheme.typography.bodyLarge)
                field.help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Switch(checked = value == "true", onCheckedChange = { onChange(it.toString()) })
        }
        return
    }
    var reveal by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val password = field.kind == FieldKind.PASSWORD
    val help = field.help
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(if (field.required || field.default.isNotEmpty() || field.blankValue != null) field.label else "${field.label} (optional)") },
            placeholder = if (field.hint.isNotEmpty()) { { Text(field.hint, maxLines = 1, overflow = TextOverflow.Ellipsis) } } else null,
            supportingText = if (help != null) { { Text(help) } } else null,
            singleLine = true,
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.kind) {
                    FieldKind.PASSWORD -> KeyboardType.Password
                    FieldKind.NUMBER -> KeyboardType.Number
                    FieldKind.URL -> KeyboardType.Uri
                    FieldKind.EMAIL -> KeyboardType.Email
                    else -> KeyboardType.Text
                },
                autoCorrect = false,
            ),
            trailingIcon = when {
                password -> { { IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (reveal) "Hide" else "Show") } } }
                field.choices.isNotEmpty() -> { { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, "Choose") } } }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (field.choices.isNotEmpty()) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                field.choices.forEach { (v, label) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(label)
                                Text(v, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = { onChange(v); menu = false },
                    )
                }
            }
        }
    }
}

// ── More services: a form generated from rclone's own option list ────────

@Composable
private fun ProviderForm(type: String, taken: List<String>, prefill: CloudSetup.Session?, onBack: () -> Unit) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(CloudCache.providers?.firstOrNull { it.name == type }) }
    LaunchedEffect(type) {
        if (provider == null) provider = runCatching { CloudCache.providers(context) }.getOrNull()?.firstOrNull { it.name == type }
    }
    val p = provider
    PageScaffold(p?.description ?: type, onBack) { pad ->
        if (p == null) {
            Box(Modifier.padding(pad).fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        } else {
            ProviderFormBody(p, taken, prefill, Modifier.padding(pad))
        }
    }
}

@Composable
private fun ProviderFormBody(p: RcProvider, taken: List<String>, prefill: CloudSetup.Session?, modifier: Modifier) {
    val context = LocalContext.current
    val values = remember(p.name) {
        mutableStateMapOf<String, String>().apply {
            p.options.forEach { o -> put(o.name, prefill?.form?.get(o.name) ?: o.default) }
        }
    }
    var name by remember(p.name) { mutableStateOf(prefill?.name ?: uniqueName(p.description.substringBefore('(').trim(), taken)) }
    var advanced by remember { mutableStateOf(false) }
    val selectedProvider = values["provider"]?.takeIf { p.options.any { o -> o.name == "provider" } }
    val visible = p.options.filter { o ->
        !o.hidden && o.name != CloudServices.START_PATH_KEY && (advanced || !o.advanced) && o.appliesTo(selectedProvider)
    }
    val nameError = if (prefill != null && name.trim() == prefill.name) null else nameProblem(name.trim(), taken)
    val missing = visible.any { it.required && it.default.isEmpty() && values[it.name].isNullOrBlank() }
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name in BFE") },
            singleLine = true,
            isError = nameError != null,
            supportingText = if (nameError != null) { { Text(nameError) } } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        visible.forEach { o ->
            OptionInput(o, values[o.name].orEmpty()) { values[o.name] = it }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 4.dp),
        ) {
            Text("Show advanced options", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = advanced, onCheckedChange = { advanced = it })
        }
        Button(
            enabled = nameError == null && !missing,
            onClick = {
                // Only what differs from rclone's defaults (and isn't hidden) is sent.
                val form = HashMap<String, String>()
                p.options.forEach { o ->
                    val v = values[o.name].orEmpty().trim()
                    if (!o.hidden && v.isNotEmpty() && v != o.default) form[o.name] = v
                }
                CloudSetup.begin(context, name.trim(), p.name, null, form)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Connect") }
    }
}

/** An input for one rclone option (setup forms and the generic question dialog share the look). */
@Composable
private fun OptionInput(o: RcOption, value: String, onChange: (String) -> Unit) {
    val kind = when {
        o.isBool -> FieldKind.BOOL
        o.isPassword -> FieldKind.PASSWORD
        o.type == "int" -> FieldKind.NUMBER
        else -> FieldKind.TEXT
    }
    val help = o.help.lineSequence().drop(1).joinToString(" ") { it.trim() }.trim().take(300).ifEmpty { null }
    FieldInput(
        ServiceField(
            key = o.name,
            label = o.title.take(60),
            kind = kind,
            required = o.required && o.default.isEmpty(),
            default = o.default,
            help = help,
            choices = o.examples.map { it.value to it.help.lineSequence().first().take(80) }.filter { it.first.isNotEmpty() },
        ),
        value,
        onChange,
    )
}

// ── Setup dialogs (driven by CloudSetup) ─────────────────────────────────

@Composable
internal fun SetupDialogs(session: CloudSetup.Session, onEdit: () -> Unit) {
    val context = LocalContext.current
    val noDismiss = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    val label = session.service?.label ?: session.name
    when (val ph = session.phase) {
        is CloudSetup.Phase.Working -> OutlinedAlertDialog(
            onDismissRequest = {},
            properties = noDismiss,
            title = { Text(session.name) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(ph.message)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { CloudSetup.cancel(context) }) { Text("Cancel") } },
        )

        is CloudSetup.Phase.Browser -> {
            LaunchedEffect(ph.url) {
                if (!ph.opened) {
                    openInBrowser(context, ph.url)
                    CloudSetup.markBrowserOpened()
                }
            }
            OutlinedAlertDialog(
                onDismissRequest = {},
                properties = noDismiss,
                icon = { Icon(Icons.Filled.OpenInBrowser, null) },
                title = { Text("Sign in to $label") },
                text = {
                    Column {
                        Text("Finish signing in in your browser, then tap “Return to BFE” there (or just come back here).")
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Waiting for the sign-in…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { openInBrowser(context, ph.url) }) { Text("Open again") } },
                dismissButton = { TextButton(onClick = { CloudSetup.cancel(context) }) { Text("Cancel") } },
            )
        }

        is CloudSetup.Phase.Code -> {
            var code by remember(ph) { mutableStateOf("") }
            OutlinedAlertDialog(
                onDismissRequest = {},
                properties = noDismiss,
                title = { Text("Sign in to $label") },
                text = {
                    Column {
                        ph.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
                        Text("Open the sign-in page, approve access, then paste the code it shows.")
                        val url = ph.url
                        if (url != null) {
                            TextButton(onClick = { openInBrowser(context, url) }, contentPadding = PaddingValues(0.dp)) { Text("Open sign-in page") }
                        }
                        OutlinedTextField(value = code, onValueChange = { code = it }, singleLine = true, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(enabled = code.isNotBlank(), onClick = { CloudSetup.answer(code.trim()) }) { Text("Continue") } },
                dismissButton = { TextButton(onClick = { CloudSetup.cancel(context) }) { Text("Cancel") } },
            )
        }

        is CloudSetup.Phase.Question -> QuestionDialog(ph.option, ph.error, onAnswer = { CloudSetup.answer(it) }, onCancel = { CloudSetup.cancel(context) })

        is CloudSetup.Phase.TestFailed -> OutlinedAlertDialog(
            onDismissRequest = {},
            properties = noDismiss,
            title = { Text("${session.name} didn't open") },
            text = {
                Text(
                    "It was saved, but listing its files failed:\n\n${ph.error}\n\n" +
                        "Edit the details, keep it anyway (e.g. if the server is offline right now), or remove it.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { CloudSetup.discard(context) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    if (session.isNew) TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = { CloudSetup.dismiss() }) { Text("Keep") }
                }
            },
        )

        is CloudSetup.Phase.Failed -> OutlinedAlertDialog(
            onDismissRequest = { CloudSetup.cancel(context) },
            title = { Text(if (session.isNew) "Couldn't add ${session.name}" else "Couldn't sign in") },
            text = { Text(ph.error) },
            confirmButton = { TextButton(onClick = { CloudSetup.cancel(context) }) { Text("OK") } },
        )

        is CloudSetup.Phase.Done -> Unit
    }
}

/** One of rclone's own setup questions: a choice list, yes/no, or a value. */
@Composable
private fun QuestionDialog(option: RcOption, error: String?, onAnswer: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember(option) { mutableStateOf(option.default) }
    var reveal by remember(option) { mutableStateOf(false) }
    val body = option.help.lineSequence().drop(1).joinToString("\n").trim()
    val choices = option.examples
    OutlinedAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(option.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                if (body.isNotEmpty()) {
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                if (choices.isNotEmpty()) {
                    choices.forEach { ex ->
                        val label = ex.help.lineSequence().first().ifBlank { ex.value }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = value == ex.value, role = Role.RadioButton, onClick = { value = ex.value })
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = value == ex.value, onClick = { value = ex.value })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                if (ex.value != label && ex.value != "true" && ex.value != "false") {
                                    Text(ex.value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (!option.exclusive) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
                if (choices.isEmpty() || !option.exclusive) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        label = { Text(if (choices.isEmpty()) option.name else "Or enter a value") },
                        visualTransformation = if (option.isPassword && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = if (option.isPassword) {
                            { IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !option.required || value.isNotEmpty(), onClick = { onAnswer(value) }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
