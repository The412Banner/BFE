// SegmentedButton is still experimental in Material3 — file-level opt-in.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.video.ConvertQuality
import com.the412banner.bfe.video.ConvertResolution
import com.the412banner.bfe.video.VideoFormats

/** What "Convert to MP4…" hands back. [outputNames] is parallel to the dialog's input list. */
data class ConvertChoice(
    val outputNames: List<String>,
    val quality: ConvertQuality,
    val resolution: ConvertResolution,
    val keepAudio: Boolean,
    val toOtherPane: Boolean,
)

/**
 * "Convert to MP4…": output name(s), quality (x264 preset + CRF), resolution cap, audio keep/drop,
 * and the destination pane. [inputNames] are the convertible files; [skippedBink2] the .bk2 files
 * that were filtered out (the dialog says why instead of letting ffmpeg fail on them).
 */
@Composable
internal fun ConvertDialog(
    inputNames: List<String>,
    skippedBink2: List<String>,
    otherPaneName: String?,
    ffmpegMissing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ConvertChoice) -> Unit,
) {
    var names by remember { mutableStateOf(inputNames.map { VideoFormats.suggestedOutputName(it) }) }
    var quality by remember { mutableStateOf(ConvertQuality.BALANCED) }
    var resolution by remember { mutableStateOf(ConvertResolution.KEEP) }
    var keepAudio by remember { mutableStateOf(true) }
    var toOther by remember { mutableStateOf(false) }
    val namesOk = names.all { it.isNotBlank() && !it.contains('/') }
    val canConfirm = inputNames.isNotEmpty() && namesOk && !ffmpegMissing

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert to MP4") },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                if (ffmpegMissing) {
                    Text("The bundled ffmpeg isn't available on this install.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                if (skippedBink2.isNotEmpty()) {
                    Text(
                        "Skipped: " + skippedBink2.joinToString(", ") + "\n" + VideoFormats.BINK2_NOTE,
                        color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text("H.264 + AAC in an MP4 container (streaming-friendly).", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                if (inputNames.size == 1) {
                    OutlinedTextField(
                        value = names[0], onValueChange = { v -> names = listOf(v) },
                        label = { Text("Output name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("${inputNames.size} videos → <name>.mp4 next to each other:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inputNames.take(6).forEachIndexed { i, n -> Text("• $n → ${names[i]}", fontSize = 11.sp) }
                    if (inputNames.size > 6) Text("…and ${inputNames.size - 6} more", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text("Quality", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ConvertQuality.values().forEachIndexed { i, q ->
                        SegmentedButton(selected = quality == q, onClick = { quality = q }, shape = SegmentedButtonDefaults.itemShape(i, ConvertQuality.values().size)) {
                            Text(q.label, fontSize = 11.sp)
                        }
                    }
                }
                Text(
                    "x264 ${quality.preset}, CRF ${quality.crf} — " + when (quality) {
                        ConvertQuality.FAST -> "quickest, larger files"
                        ConvertQuality.BALANCED -> "good default"
                        ConvertQuality.HIGH -> "slowest, best quality per MB"
                    },
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("Resolution", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ConvertResolution.values().forEachIndexed { i, r ->
                        SegmentedButton(selected = resolution == r, onClick = { resolution = r }, shape = SegmentedButtonDefaults.itemShape(i, ConvertResolution.values().size)) {
                            Text(r.label, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Audio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = keepAudio, onClick = { keepAudio = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Keep (AAC 160k)", fontSize = 11.sp) }
                    SegmentedButton(selected = !keepAudio, onClick = { keepAudio = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("None", fontSize = 11.sp) }
                }
                if (otherPaneName != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Save to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !toOther, onClick = { toOther = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("This folder", fontSize = 11.sp) }
                        SegmentedButton(selected = toOther, onClick = { toOther = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Other pane: $otherPaneName", fontSize = 11.sp, maxLines = 1) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = {
                onConfirm(ConvertChoice(names.map { if (it.lowercase().endsWith(".mp4")) it else "$it.mp4" }, quality, resolution, keepAudio, toOther && otherPaneName != null))
            }) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
