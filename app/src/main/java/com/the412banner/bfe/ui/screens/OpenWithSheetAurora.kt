package com.the412banner.bfe.ui.screens

import android.content.ComponentName
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.storage.Loc
import com.the412banner.bfe.ui.components.GlassChip
import com.the412banner.bfe.ui.components.SectionLabel
import com.the412banner.bfe.ui.components.pressScale
import com.the412banner.bfe.ui.components.staggeredEnter
import com.the412banner.bfe.ui.icons.FileTypeIcon
import com.the412banner.bfe.ui.theme.BfeMotion
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.WindowBlurBehind
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.motionTween
import com.the412banner.bfe.ui.theme.rememberWindowBlurAvailable

/**
 * BFE's Open with sheet in Aurora Glass. Same content and behaviour as Classic's (OpenWithSheet.kt owns
 * the state, the app query and the launch): a frosted sheet (the screen behind is blurred by the system
 * on Android 12+) with the file up top, BFE's own actions as equal-height glass tiles, the "Open in BFE
 * as" tool tiles, "Open as" chips,
 * the "Always use" switch, then the viewer apps as tiles (the remembered default first and marked).
 * BFE's own viewer / player ("View" / "Play") can be the remembered default too, marked the same way.
 * A tap on a file always lands here; nothing is ever auto-launched.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AuroraOpenWithSheet(
    file: Loc,
    detectedMime: String,
    actions: List<SheetAction>,
    /** "Open in BFE as" tiles: BFE's tools for any file (OpenAsTools.kt). */
    tools: List<SheetAction>,
    chips: List<OpenAs>,
    selected: OpenAs,
    onSelect: (OpenAs) -> Unit,
    apps: List<ViewerApp>?,
    savedComponent: ComponentName?,
    ext: String,
    always: Boolean,
    onAlways: (Boolean) -> Unit,
    onLaunch: (ViewerApp) -> Unit,
    /** Runs a BFE action (built-in viewers follow the "Always use" rules, like apps). */
    onAction: (SheetAction) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    val blur = rememberWindowBlurAvailable()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Glass.Sheet,
        containerColor = if (blur) g.dialogOverBlur else g.sheet,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (blur) 0.22f else 0.42f),
        dragHandle = { SheetGrip() },
    ) {
        WindowBlurBehind(enabled = blur, radius = 24.dp, dimAmount = 0f)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 84.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            // ── Header: icon · name · size · type ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)) {
                    FileTypeIcon(file.name, isDir = false, size = 46.dp, mime = detectedMime)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(StringUtils.formatBytes(file.size), detectedMime.takeIf { it != "*/*" }).filterNotNull().joinToString("  ·  "),
                            color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // ── BFE's own actions for this file ──
            if (actions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Actions", Modifier.padding(top = 0.dp)) }
                items(actions.size) { i ->
                    val a = actions[i]
                    // One of BFE's own viewers, remembered with "Always use": marked like a default app.
                    val isDefault = a.component != null && a.component == savedComponent
                    SheetTile(
                        label = a.label,
                        onClick = { onAction(a) },
                        highlighted = isDefault,
                        caption = if (isDefault) "Default" else null,
                        modifier = Modifier.staggeredEnter(i, Unit),
                    ) { Icon(a.icon, null, tint = cs.primary, modifier = Modifier.size(24.dp)) }
                }
            }
            // ── Open in BFE as: every built-in tool, whatever the extension ──
            if (tools.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Open in BFE as") }
                items(tools.size) { i ->
                    val t = tools[i]
                    SheetTile(label = t.label, onClick = { onAction(t) }) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(t.icon, null, tint = cs.primary, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
            // ── Open as ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel("Open as")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { c -> GlassChip(c.label, onClick = { onSelect(c) }, selected = c == selected) }
                    }
                    if (ext.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .glass(Glass.Chip, GlassLevel.Panel)
                                .clickable { onAlways(!always) }
                                .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        ) {
                            Text("Always use for .$ext", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = always, onCheckedChange = onAlways)
                        }
                    }
                    SectionLabel("Apps")
                }
            }
            // ── App list ──
            when {
                apps == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = cs.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    }
                }
                apps.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                        Icon(Icons.Filled.SearchOff, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No installed app opens this as ${selected.label.lowercase()}.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                        if (selected.mime != "*/*") {
                            Spacer(Modifier.height(12.dp))
                            GlassChip("Open as Any", onClick = { onSelect(chips.first { it.mime == "*/*" }) }, selected = true)
                        }
                    }
                }
                else -> items(apps, key = { it.component.flattenToString() }) { app ->
                    val isDefault = app.component == savedComponent
                    SheetTile(
                        label = app.label,
                        onClick = { onLaunch(app) },
                        highlighted = isDefault,
                        caption = if (isDefault) "Default" else null,
                    ) {
                        if (app.icon != null) Image(app.icon, null, modifier = Modifier.size(42.dp))
                        else Icon(Icons.Filled.Android, null, modifier = Modifier.size(42.dp))
                    }
                }
            }
        }
    }
}

/** The sheet's drag handle: a short frosted grip. */
@Composable
private fun SheetGrip() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
    }
}

/**
 * A tile in the sheet: picture over a label that is always two lines tall, so every tile in a row is
 * the same height. [highlighted] = the remembered default app.
 */
@Composable
private fun SheetTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    caption: String? = null,
    picture: @Composable () -> Unit,
) {
    val g = LocalGlass.current
    val cs = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (highlighted) g.selection else g.panel, motionTween(reduced, BfeMotion.SHORT), label = "sheetTile")
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressScale(interaction, 0.95f)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (highlighted) cs.primary.copy(alpha = 0.45f) else g.border, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { picture() }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = cs.onSurface,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (caption != null) Text(caption, fontSize = 10.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
    }
}
