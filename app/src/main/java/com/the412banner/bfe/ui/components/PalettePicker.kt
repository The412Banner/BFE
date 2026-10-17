package com.the412banner.bfe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.ui.theme.BfeMotion
import com.the412banner.bfe.ui.theme.BfePalette
import com.the412banner.bfe.ui.theme.BfeTheme
import com.the412banner.bfe.ui.theme.LocalReducedMotion
import com.the412banner.bfe.ui.theme.UiStyle
import com.the412banner.bfe.ui.theme.motionSpring
import com.the412banner.bfe.ui.theme.motionTween
import com.the412banner.bfe.ui.theme.paletteColors

/**
 * Aurora's palette chooser (Settings → Appearance): a scrolling row of round swatches, each a tiny
 * painting of that palette's backdrop with its accent as a dot. The chosen one is ringed in its accent
 * and springs up slightly.
 */
@Composable
fun PalettePicker(selected: BfePalette, dark: Boolean, onPick: (BfePalette) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        BfePalette.values().forEach { p -> PaletteSwatch(p, dark, p == selected) { onPick(p) } }
    }
}

@Composable
private fun PaletteSwatch(palette: BfePalette, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val colors = remember(palette, dark) { paletteColors(palette, dark) }
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, motionSpring(reduced), label = "swatchScale")
    val ring by animateColorAsState(
        if (selected) colors.accent else MaterialTheme.colorScheme.outlineVariant,
        motionTween(reduced, BfeMotion.SHORT),
        label = "swatchRing",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = "${palette.label} palette" }
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(if (selected) 2.dp else 1.dp, ring, CircleShape)
                .padding(3.dp)
                .clip(CircleShape),
        ) {
            Canvas(Modifier.size(46.dp)) {
                drawRect(colors.base)
                val r = size.minDimension
                listOf(Offset(0.2f, 0.25f), Offset(0.85f, 0.55f), Offset(0.35f, 0.95f)).forEachIndexed { i, o ->
                    val c = Offset(size.width * o.x, size.height * o.y)
                    drawCircle(
                        Brush.radialGradient(
                            0f to colors.blobs[i].copy(alpha = (colors.blobAlpha + 0.25f).coerceAtMost(1f)),
                            1f to Color.Transparent,
                            center = c,
                            radius = r * 0.7f,
                        ),
                        radius = r * 0.7f,
                        center = c,
                    )
                }
                drawCircle(colors.accent, radius = r * 0.13f, center = Offset(size.width * 0.68f, size.height * 0.68f))
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check, null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(18.dp).clip(CircleShape).graphicsLayer { alpha = 0.95f },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Preview(name = "Palette picker", widthDp = 420)
@Composable
private fun PalettePickerPreview() {
    BfeTheme(style = UiStyle.AURORA) { PalettePicker(BfePalette.NEUTRAL, dark = true, onPick = {}) }
}
