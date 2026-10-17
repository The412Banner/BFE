package com.the412banner.bfe.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.appicon.AppIcon
import com.the412banner.bfe.appicon.AppIcons
import com.the412banner.bfe.ui.components.BRAND_NAME
import com.the412banner.bfe.util.AppSnackbar
import androidx.compose.material3.BottomSheetDefaults
import com.the412banner.bfe.ui.components.AccentButton
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.WindowBlurBehind
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.ui.theme.rememberWindowBlurAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Settings → Appearance → App icon: the launcher icon in use, opening into every choice grouped as
 * Mixes / Originals / Classic. Tapping one opens a sheet with a big preview (and how it looks as an
 * Android 13+ themed icon) and Apply; [AppIcons] does the switch. Self-contained, so SettingsScreen
 * only needs the one call.
 */
@Composable
fun AppIconSetting() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "appIconChevron")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Aurora Glass: the section is a glass card like the other settings.
            .then(if (isAurora) Modifier.padding(horizontal = 12.dp, vertical = 3.dp).glass(Glass.Chip, GlassLevel.Panel) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // The live icon stands in for the row glyph; 28 + 12 keeps the text on the other rows' line.
            Crossfade(targetState = current, label = "appIconCurrent") { AppIconArt(it, 28.dp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("App icon", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (current.isDefault) "${current.name} (default)" else current.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.ExpandMore, if (expanded) "Hide icons" else "Show icons", modifier = Modifier.rotate(chevron))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                AppIcon.Group.values().forEach { group ->
                    Text(
                        group.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                    IconGrid(AppIcons.ALL.filter { it.group == group }, current) { previewId = it.id }
                }
            }
        }
    }

    AppIcons.ALL.firstOrNull { it.id == previewId }?.let { icon ->
        AppIconSheet(
            icon = icon,
            inUse = icon == current,
            onDismiss = { previewId = null },
            onApplied = { current = it; previewId = null },
        )
    }
}

/** Equal-width tiles, as many per row as fit (at least 3); short rows keep the same tile width. */
@Composable
private fun IconGrid(icons: List<AppIcon>, current: AppIcon, onPick: (AppIcon) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 88.dp).toInt().coerceIn(3, 8)
        Column {
            icons.chunked(columns).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { icon ->
                        IconTile(icon, selected = icon == current, onClick = { onPick(icon) }, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun IconTile(icon: AppIcon, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tilePress")
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tileRing")
    // The check badge pops in on the newly chosen tile and shrinks away on the old one.
    val badge by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tileBadge")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, interactionSource = interaction, indication = LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Box(Modifier.size(62.dp).border(2.dp, ring, SquircleShape))
            AppIconArt(icon, 52.dp)
            if (badge > 0.01f) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { scaleX = badge; scaleY = badge; alpha = badge.coerceIn(0f, 1f) }
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                ) {
                    Icon(Icons.Filled.Check, "In use", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Two reserved label lines keep every tile in a row the same height.
        Text(
            icon.name,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Big preview + themed preview + Apply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppIconSheet(icon: AppIcon, inUse: Boolean, onDismiss: () -> Unit, onApplied: (AppIcon) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var applying by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }
    val pop = remember(icon.id) { Animatable(0.82f) }
    LaunchedEffect(icon.id) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }

    // Aurora Glass: a frosted sheet (the system blurs the screen behind it on Android 12+).
    val aurora = isAurora
    val g = LocalGlass.current
    val blur = rememberWindowBlurAvailable()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = if (aurora) Glass.Sheet else BottomSheetDefaults.ExpandedShape,
        containerColor = if (aurora) (if (blur) g.dialogOverBlur else g.sheet) else BottomSheetDefaults.ContainerColor,
        tonalElevation = if (aurora) 0.dp else BottomSheetDefaults.Elevation,
        scrimColor = if (aurora) Color.Black.copy(alpha = if (blur) 0.22f else 0.42f) else BottomSheetDefaults.ScrimColor,
    ) {
        if (aurora) WindowBlurBehind(enabled = blur, radius = 24.dp, dimAmount = 0f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            Text(icon.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                when (icon.group) {
                    AppIcon.Group.MIXES -> "Mix ${icon.code}" + if (icon.isDefault) " · the default icon" else ""
                    AppIcon.Group.ORIGINALS -> "Option ${icon.code}"
                    AppIcon.Group.CLASSIC -> "$BRAND_NAME's first icon"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(132.dp)) {
                    AppIconArt(icon, 112.dp, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, "${icon.name} icon")
                    Spacer(Modifier.height(8.dp))
                    // As a launcher labels it.
                    Text(BRAND_NAME, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ThemedIconPreview(icon, 64.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Themed icon" else "Themed (Android 13+)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Your launcher can take a few seconds to show the new icon, and some only update once you " +
                    "leave $BRAND_NAME. If it disappears from your home screen, add it back from the app drawer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = close, modifier = Modifier.weight(1f)) { Text(if (inUse) "Close" else "Cancel") }
                val apply: () -> Unit = {
                    applying = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { AppIcons.apply(context, icon) }
                        applying = false
                        if (ok) {
                            AppSnackbar.show(context, "App icon changed to ${icon.name}")
                            sheetState.hide()
                            onApplied(icon)
                        } else {
                            AppSnackbar.show(context, "Couldn't change the app icon")
                        }
                    }
                }
                if (aurora) AccentButton(
                    if (inUse) "In use" else "Use this icon",
                    onClick = apply,
                    enabled = !inUse && !applying,
                    modifier = Modifier.weight(1f).height(40.dp),
                ) else Button(
                    enabled = !inUse && !applying,
                    modifier = Modifier.weight(1f),
                    onClick = apply,
                ) { Text(if (inUse) "In use" else "Use this icon") }
            }
        }
    }
}

/**
 * An icon's squircle artwork filling [size]. The art drawable is the whole 108 adaptive canvas with the
 * squircle in its middle, so it is drawn larger and centred; the margin around the squircle is clear.
 */
@Composable
private fun AppIconArt(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Image(
            painter = painterResource(icon.art),
            contentDescription = contentDescription,
            modifier = Modifier.requiredSize(size / AppIcons.ART_SQUIRCLE_FRACTION),
        )
    }
}

/** Roughly how an Android 13+ launcher draws the themed icon: the silhouette, tinted, on a tinted disc. */
@Composable
private fun ThemedIconPreview(icon: AppIcon, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        // The disc is the 72 dp viewport of the 108 canvas.
        Image(
            painter = painterResource(icon.mono),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** The icons' own superellipse (|x|^4 + |y|^4 = 1), for the selection ring. */
private object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val rx = size.width / 2
        val ry = size.height / 2
        val steps = 96
        for (i in 0 until steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val r = 1.0 / (abs(c).pow(4) + abs(s).pow(4)).pow(0.25)
            val x = (rx + rx * r * c).toFloat()
            val y = (ry + ry * r * s).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
