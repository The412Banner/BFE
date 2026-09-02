package com.the412banner.bfe.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.pack.PackFormat
import com.the412banner.bfe.pack.PackLevel
import com.the412banner.bfe.pack.WcpProfile

/** The Winlator content-pack fields the user fills in for a .wcp (see [WcpProfile]). */
data class WcpMeta(
    val type: String,
    val versionName: String,
    val versionCode: Int,
    val description: String,
    val binPath: String,
    val libPath: String,
    val prefixPack: String,
)

/** What the Compress dialog hands back when confirmed. */
data class CompressRequest(
    /** Archive file name WITH extension. */
    val fileName: String,
    val format: PackFormat,
    val level: PackLevel,
    /** Non-null only for zip/7z when the user typed one. */
    val password: String?,
    /** Write into the OTHER pane's folder instead of this pane's. */
    val toOtherPane: Boolean,
    /** Present only for the wcp format. */
    val wcp: WcpMeta?,
)

/**
 * "Compress…" — archive name, format, level, optional password (zip/7z), destination pane, and the
 * profile.json fields for a Winlator .wcp. [defaultBase] is the name prefilled from the selection
 * (the item's name, or the folder's when several are selected); the extension follows the format.
 * [otherPaneName] is non-null when dual-pane is on, enabling the "into other pane" destination.
 */
@Composable
internal fun CompressDialog(
    defaultBase: String,
    itemCount: Int,
    otherPaneName: String?,
    onDismiss: () -> Unit,
    onConfirm: (CompressRequest) -> Unit,
) {
    var base by remember { mutableStateOf(defaultBase) }
    var format by remember { mutableStateOf(PackFormat.ZIP) }
    var level by remember { mutableStateOf(PackLevel.NORMAL) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var toOther by remember { mutableStateOf(false) }
    // wcp profile fields
    var wcpType by remember { mutableStateOf("DXVK") }
    var typeMenu by remember { mutableStateOf(false) }
    var wcpVersionName by remember { mutableStateOf(defaultBase) }
    var wcpVersionCode by remember { mutableStateOf("1") }
    var wcpDesc by remember { mutableStateOf("") }
    var wineBin by remember { mutableStateOf("bin") }
    var wineLib by remember { mutableStateOf("lib") }
    var winePrefix by remember { mutableStateOf("prefixPack.txz") }

    val wineLike = wcpType == "Wine" || wcpType == "Proton"
    val cleanBase = base.trim().trimEnd('.')
    val fileName = cleanBase + format.ext
    val versionCodeOk = wcpVersionCode.trim().toIntOrNull() != null
    val canConfirm = cleanBase.isNotBlank() && !cleanBase.contains('/') &&
        (!format.isWcp || (wcpVersionName.isNotBlank() && versionCodeOk))

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format.isWcp) "Create Winlator pack" else "Compress") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "$itemCount item${if (itemCount == 1) "" else "s"} → $fileName",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("Archive name") },
                    suffix = { Text(format.ext, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("Format", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PackFormat.values().forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.label, fontSize = 12.sp) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (format.supportsLevel) {
                    Spacer(Modifier.height(8.dp))
                    Text("Compression", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        PackLevel.values().forEachIndexed { i, l ->
                            SegmentedButton(
                                selected = level == l,
                                onClick = { level = l },
                                shape = SegmentedButtonDefaults.itemShape(i, PackLevel.values().size),
                            ) { Text(l.label, fontSize = 11.sp) }
                        }
                    }
                }
                if (format.supportsPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show", fontSize = 11.sp)
                            }
                        },
                        supportingText = {
                            if (format == PackFormat.SEVEN_Z && password.isNotEmpty()) Text("7z: file names are encrypted too (-mhe)", fontSize = 11.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (otherPaneName != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Save to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !toOther, onClick = { toOther = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text("This folder", fontSize = 11.sp)
                        }
                        SegmentedButton(selected = toOther, onClick = { toOther = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text("Other pane: $otherPaneName", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                if (format.isWcp) {
                    Spacer(Modifier.height(10.dp))
                    Text("profile.json", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (itemCount == 1) "The selected folder's contents are packed at the archive root next to profile.json."
                        else "The selected items are packed at the archive root next to profile.json.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Type: $wcpType", fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }, modifier = Modifier.outlinedMenuCard()) {
                        WcpProfile.TYPES.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { wcpType = t; typeMenu = false })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = wcpVersionName, onValueChange = { wcpVersionName = it },
                        label = { Text("versionName") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            if (wineLike) Text("Wine/Proton: use <wine|proton>-<ver>-<x86|x86_64|arm64ec> to be launchable", fontSize = 11.sp)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = wcpVersionCode, onValueChange = { wcpVersionCode = it },
                        label = { Text("versionCode") }, singleLine = true, isError = !versionCodeOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = wcpDesc, onValueChange = { wcpDesc = it },
                        label = { Text("description") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    )
                    if (wineLike) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = wineBin, onValueChange = { wineBin = it }, label = { Text("wine.binPath") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = wineLib, onValueChange = { wineLib = it }, label = { Text("wine.libPath") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = winePrefix, onValueChange = { winePrefix = it }, label = { Text("wine.prefixPack") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "files[] is derived from the pack layout: system32/, syswow64/, lib/, bin/, share/ map to the installer's \${system32} \${syswow64} \${libdir} \${bindir} \${sharedir}.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    onConfirm(
                        CompressRequest(
                            fileName = fileName,
                            format = format,
                            level = level,
                            password = password.takeIf { it.isNotEmpty() && format.supportsPassword },
                            toOtherPane = toOther && otherPaneName != null,
                            wcp = if (format.isWcp) WcpMeta(
                                type = wcpType,
                                versionName = wcpVersionName.trim(),
                                versionCode = wcpVersionCode.trim().toIntOrNull() ?: 0,
                                description = wcpDesc.trim(),
                                binPath = wineBin.trim(), libPath = wineLib.trim(), prefixPack = winePrefix.trim(),
                            ) else null,
                        )
                    )
                },
            ) { Text(if (format.isWcp) "Create pack" else "Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
