@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.the412banner.bfe.ui.media

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.media.AspectMode
import com.the412banner.bfe.media.ColorPreset
import com.the412banner.bfe.media.CropMode
import com.the412banner.bfe.media.Deinterlace
import com.the412banner.bfe.media.GestureMath
import com.the412banner.bfe.media.MediaPrefs
import com.the412banner.bfe.media.PlayerSettings
import com.the412banner.bfe.media.ReplayGain
import com.the412banner.bfe.media.Strength
import com.the412banner.bfe.media.SubColor
import com.the412banner.bfe.media.SubSize
import com.the412banner.bfe.media.VideoLook
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.util.AppSnackbar
import com.the412banner.bfe.util.IncomingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

internal const val TAB_PICTURE = 0
internal const val TAB_EFFECTS = 1
internal const val TAB_AUDIO = 2
internal const val TAB_SUBTITLES = 3

/**
 * The player's effects sheet on frosted glass — a bottom sheet in portrait, a side sheet in landscape,
 * so the picture stays visible while you adjust it. Tabs: Picture (colour, aspect, crop, zoom,
 * rotation), Effects (VLC filters + sharpen / grain), Audio (equalizer, boost, delay, speed, tracks)
 * and Subtitles (tracks, external file, delay, style). Footer: Reset (back to defaults for this file)
 * and "Remember settings" (the next file starts with these).
 */
@Composable
internal fun PlayerSheet(
    state: PlayerState,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    landscape: Boolean,
    audioOnly: Boolean,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val tabs = if (audioOnly) listOf(TAB_AUDIO to "Audio")
    else listOf(TAB_PICTURE to "Picture", TAB_EFFECTS to "Effects", TAB_AUDIO to "Audio", TAB_SUBTITLES to "Subtitles")
    val shown = if (tabs.any { it.first == tab }) tab else tabs.first().first
    var keep by remember { mutableStateOf(MediaPrefs.keepSettings(context)) }
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

    val size = if (landscape) Modifier.fillMaxHeight().width(minOf(420, (config.screenWidthDp * 0.5f).toInt()).dp)
    else Modifier.fillMaxWidth().heightIn(max = (config.screenHeightDp * 0.62f).dp)

    Column(
        modifier = Modifier
            .windowInsetsPadding(insets)
            .padding(12.dp)
            .then(size)
            .glass(Glass.Sheet, strong = true)
            // Taps on the sheet's own background must not reach the gesture layer behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = 10.dp, start = 14.dp, end = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                SegmentedTabs(tabs, shown, onTab)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
        }
        AnimatedContent(
            targetState = shown,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "tab",
            modifier = Modifier.weight(1f, fill = false),
        ) { t ->
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(end = 8.dp, bottom = 4.dp)) {
                when (t) {
                    TAB_PICTURE -> PictureTab(state)
                    TAB_EFFECTS -> EffectsTab(state)
                    TAB_AUDIO -> AudioTab(state)
                    TAB_SUBTITLES -> SubtitlesTab(state)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
            TextButton(onClick = { state.resetSettings() }) {
                Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
            Spacer(Modifier.weight(1f))
            Text("Remember settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(checked = keep, onCheckedChange = { keep = it; state.setKeepSettings(it) })
        }
    }
}

@Composable
private fun SegmentedTabs(tabs: List<Pair<Int, String>>, selected: Int, onTab: (Int) -> Unit) {
    Row(modifier = Modifier.glass(Glass.Pill).padding(3.dp)) {
        tabs.forEach { (id, label) ->
            val sel = id == selected
            val bg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.Transparent,
                tween(220),
                label = "tabBg",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clip(Glass.Pill).background(bg).clickable { onTab(id) }.padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── Picture ──

@Composable
private fun PictureTab(state: PlayerState) {
    val s = state.settings
    Section("Look")
    Chips(ColorPreset.values().toList(), s.preset, { it.label }) { p -> state.update { it.copy(preset = p) } }
    Section("Adjust")
    SliderRow("Brightness", s.brightness, -1f..1f, 0f, { "${(it * 100).roundToInt()}" }) { v -> state.update { it.copy(brightness = v) } }
    SliderRow("Contrast", s.contrast, 0f..2f, 1f, { "${(it * 100).roundToInt()} %" }) { v -> state.update { it.copy(contrast = v) } }
    SliderRow("Saturation", s.saturation, 0f..2f, 1f, { "${(it * 100).roundToInt()} %" }) { v -> state.update { it.copy(saturation = v) } }
    SliderRow("Hue", s.hue, -180f..180f, 0f, { "${it.roundToInt()}°" }) { v -> state.update { it.copy(hue = v) } }
    SliderRow("Gamma", s.gamma, 0.4f..2.5f, 1f, { "%.2f".format(it) }) { v -> state.update { it.copy(gamma = v) } }
    Section("Aspect ratio")
    Chips(AspectMode.values().toList(), s.aspect, { it.label }) { a -> state.update { it.copy(aspect = a) } }
    Section("Crop")
    Chips(CropMode.values().toList(), s.crop, { it.label }) { c -> state.update { it.copy(crop = c) } }
    SliderRow("Zoom", s.zoom, 1f..3f, 1f, { GestureMath.formatRate((it * 20).roundToInt() / 20f) }) { v -> state.update { it.copy(zoom = v) } }
    Section("Rotate & flip")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 90, 180, 270).forEach { deg ->
            FilterChip(selected = s.rotation == deg, onClick = { state.update { it.copy(rotation = deg) } }, label = { Text("$deg°") })
        }
        FilterChip(selected = s.flipH, onClick = { state.update { it.copy(flipH = !it.flipH) } }, label = { Text("Mirror") })
        FilterChip(selected = s.flipV, onClick = { state.update { it.copy(flipV = !it.flipV) } }, label = { Text("Upside down") })
    }
    Note("Turning or flipping the picture turns its subtitles with it.")
}

// ── Effects ──

@Composable
private fun EffectsTab(state: PlayerState) {
    val s = state.settings
    Note("Deinterlace, denoise, banding removal, deblocking and the looks run inside VLC: changing one restarts playback at the same spot, and they use the CPU, so heavy ones can stutter on 4K. Sharpen and grain are instant.")
    Section("Deinterlace")
    DropdownRow("Mode", s.deinterlace.label, Deinterlace.values().map { it.label }, s.deinterlace.ordinal) { i ->
        state.update { it.copy(deinterlace = Deinterlace.values()[i]) }
    }
    Section("Denoise")
    Chips(Strength.values().toList(), s.denoise, { it.label }) { v -> state.update { it.copy(denoise = v) } }
    Section("Banding removal")
    Chips(Strength.values().toList(), s.debanding, { it.label }) { v -> state.update { it.copy(debanding = v) } }
    Section("Deblocking")
    Chips(Strength.values().toList(), s.deblocking, { it.label }) { v -> state.update { it.copy(deblocking = v) } }
    Section("Sharpen")
    if (sharpenSupported) {
        SliderRow("Strength", s.sharpen, 0f..1f, 0f, { "${(it * 100).roundToInt()} %" }) { v -> state.update { it.copy(sharpen = v) } }
    } else {
        Note("Sharpen needs Android 13 or later.")
    }
    Section("Film grain")
    SliderRow("Amount", s.grain, 0f..1f, 0f, { "${(it * 100).roundToInt()} %" }) { v -> state.update { it.copy(grain = v) } }
    Section("Looks")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoLook.values().forEach { look ->
            val on = look in s.looks
            FilterChip(
                selected = on,
                onClick = { state.update { it.copy(looks = if (on) it.looks - look else it.looks + look) } },
                label = { Text(look.label) },
            )
        }
    }
}

// ── Audio ──

@Composable
private fun AudioTab(state: PlayerState) {
    val s = state.settings
    val engine = state.engine
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets = remember { engine.eqPresetNames() }
    val labels = remember { engine.eqBandLabels() }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { loadSlave(context, state, uri, subtitle = false) }
    }

    Section("Tracks")
    TrackList(engine.audioTracks, engine.audioTrack, "No audio tracks yet.") { engine.selectAudio(it) }
    OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Load audio file…")
    }

    Section("Equalizer")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Equalizer", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = s.eqOn, onCheckedChange = { on -> state.update { it.copy(eqOn = on) } })
    }
    if (s.eqOn) {
        if (presets.isNotEmpty()) {
            val names = listOf("Custom") + presets
            DropdownRow("Preset", if (s.eqPreset in presets.indices) presets[s.eqPreset] else "Custom", names, s.eqPreset + 1) { i ->
                if (i == 0) state.update { it.copy(eqPreset = -1) }
                else engine.eqPreset(i - 1)?.let { (pre, bands) ->
                    state.update { it.copy(eqPreset = i - 1, eqPreamp = pre, eqBands = bands.take(PlayerSettings.EQ_BANDS).let { b -> b + List(PlayerSettings.EQ_BANDS - b.size) { 0f } }) }
                }
            }
        }
        SliderRow("Preamp", s.eqPreamp, -20f..20f, 0f, { "%+.1f dB".format(it) }) { v -> state.update { it.copy(eqPreamp = v, eqPreset = -1) } }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            s.eqBands.forEachIndexed { i, v ->
                BandSlider(v, labels.getOrElse(i) { "" }) { nv ->
                    state.update { st -> st.copy(eqPreset = -1, eqBands = st.eqBands.mapIndexed { j, b -> if (j == i) nv else b }) }
                }
            }
        }
    }

    Section("Volume")
    SliderRow("Boost", s.boost.toFloat(), 100f..200f, 100f, { "${it.roundToInt()} %" }) { v -> state.update { it.copy(boost = v.roundToInt()) } }
    Section("Speed")
    Chips(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), s.speed, { GestureMath.formatRate(it) }) { r -> state.update { it.copy(speed = r) } }
    SliderRow("Speed", s.speed, 0.25f..4f, 1f, { GestureMath.formatRate(it) }) { v -> state.update { it.copy(speed = (v * 20).roundToInt() / 20f) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Keep pitch", style = MaterialTheme.typography.bodyMedium)
            Text("Off = faster sounds higher, like tape (restarts playback)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = s.keepPitch, onCheckedChange = { on -> state.update { it.copy(keepPitch = on) } })
    }
    Section("Audio delay")
    DelayRow(s.audioDelayMs, step = 50L, range = 5_000L) { d -> state.update { it.copy(audioDelayMs = d) } }
    Section("Volume levelling (ReplayGain)")
    Chips(ReplayGain.values().toList(), s.replayGain, { it.label }) { g -> state.update { it.copy(replayGain = g) } }
    Note("Uses the loudness tags in music files; restarts playback.")
}

// ── Subtitles ──

@Composable
private fun SubtitlesTab(state: PlayerState) {
    val s = state.settings
    val engine = state.engine
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickSub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { loadSlave(context, state, uri, subtitle = true) }
    }
    Section("Tracks")
    TrackList(engine.spuTracks, engine.spuTrack, "No subtitles in this file. Files named like the video (.srt, .ass…) next to it load by themselves.") {
        engine.selectSubtitle(it)
    }
    OutlinedButton(onClick = { pickSub.launch(arrayOf("*/*")) }, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Load subtitle file…")
    }
    Section("Delay")
    DelayRow(s.subDelayMs, step = 100L, range = 30_000L) { d -> state.update { it.copy(subDelayMs = d) } }
    Section("Size")
    Chips(SubSize.values().toList(), s.subSize, { it.label }) { z -> state.update { it.copy(subSize = z) } }
    Section("Colour")
    Chips(SubColor.values().toList(), s.subColor, { it.label }) { c -> state.update { it.copy(subColor = c) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Background box", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = s.subBackground, onCheckedChange = { on -> state.update { it.copy(subBackground = on) } })
    }
    Note("Size, colour and the box restart playback at the same spot.")
}

/** A picked subtitle / audio file as a real path (BFE can read storage), else a cache copy. */
private suspend fun loadSlave(context: Context, state: PlayerState, uri: Uri, subtitle: Boolean) {
    val file = withContext(Dispatchers.IO) {
        runCatching {
            IncomingFile.resolvePath(context, uri)?.let { File(it) }?.takeIf { it.canRead() } ?: run {
                val meta = IncomingFile.meta(context, uri)
                val dir = File(context.cacheDir, "player-extra").apply { mkdirs() }
                val out = File(dir, meta.name)
                context.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } } ?: return@runCatching null
                out
            }
        }.getOrNull()
    }
    val ok = file != null && state.engine.addSlave(subtitle, file)
    AppSnackbar.show(context, if (ok) "Loaded ${file?.name}" else "Couldn't load that file")
}

// ── Building blocks ──

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

/** Label, value (tap it to reset to [default]) and a slider. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    val changed = kotlin.math.abs(value - default) > 1e-3f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                display(value),
                style = MaterialTheme.typography.labelLarge,
                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = changed) { onChange(default) }.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun DropdownRow(label: String, value: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp))
            }
            BfeDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(
                        text = { Text(o, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = { open = false; onSelect(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackList(tracks: List<VlcEngine.Track>, selected: Int, emptyText: String, onSelect: (Int) -> Unit) {
    if (tracks.isEmpty()) {
        Note(emptyText)
        return
    }
    tracks.forEach { t ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(t.id) }.padding(end = 8.dp),
        ) {
            RadioButton(selected = t.id == selected, onClick = { onSelect(t.id) })
            Text(if (t.id < 0) "Off" else t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** −/+ steps around a delay, the value in the middle (tap to reset). */
@Composable
private fun DelayRow(valueMs: Long, step: Long, range: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onChange((valueMs - step).coerceAtLeast(-range)) }) { Icon(Icons.Filled.Remove, "Earlier") }
        Text(
            GestureMath.formatDelay(valueMs),
            style = MaterialTheme.typography.titleMedium,
            color = if (valueMs != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(enabled = valueMs != 0L) { onChange(0L) }.padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onChange((valueMs + step).coerceAtMost(range)) }) { Icon(Icons.Filled.Add, "Later") }
    }
}

/** One equalizer band: drag (or tap) up and down, −20…+20 dB, 0 dB in the middle. */
@Composable
private fun BandSlider(value: Float, label: String, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
        Text("%+.0f".format(value), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(
            modifier = Modifier
                .height(132.dp)
                .width(28.dp)
                .pointerInput(Unit) {
                    fun valueAt(y: Float) = (20f - (y / size.height.toFloat()) * 40f).coerceIn(-20f, 20f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        change(valueAt(down.position.y))
                        down.consume()
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) break
                            change(valueAt(c.position.y))
                            c.consume()
                        }
                    }
                },
        ) {
            val cx = size.width / 2f
            val y = size.height * (20f - value) / 40f
            val mid = size.height / 2f
            drawLine(track, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(primary, Offset(cx, mid), Offset(cx, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(primary, radius = 8.dp.toPx(), center = Offset(cx, y))
        }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
