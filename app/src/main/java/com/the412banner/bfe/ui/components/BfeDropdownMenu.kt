package com.the412banner.bfe.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.ui.theme.rimBrush
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

private val MenuShape = RoundedCornerShape(12.dp)

/**
 * BFE's drop-down menu: Material 3's [DropdownMenu] with rounder corners and a thin outline, so a menu
 * reads as its own panel over lists and cards of the same colour. Same parameters as [DropdownMenu];
 * every menu in the app uses this one. Classic: a solid panel with an outline. Aurora Glass: a glass
 * panel (near-opaque frosted fill, top sheen, rim hairline).
 */
@Composable
fun BfeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isAurora) {
        // Aurora: a glass panel. The menu window has nothing to blur, so its fill is near-opaque glass
        // (surfaceContainer, which M3's menu surface paints) with the sheen, grain and rim hairline.
        val g = LocalGlass.current
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(surfaceContainer = g.menu),
            shapes = MaterialTheme.shapes.copy(extraSmall = MenuShape),
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                modifier = modifier
                    .drawBehind {
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = if (g.palette.dark) 0.05f else 0.35f), 0.4f to Color.Transparent))
                    }
                    .border(Glass.HairlineWidth, g.rimBrush(), MenuShape),
                offset = offset,
                scrollState = scrollState,
                properties = properties,
                content = content,
            )
        }
        return
    }
    // Material 3 1.2's menu takes its surface shape from shapes.extraSmall and has no border
    // parameter; the outline goes on the menu's content column, which fills that surface.
    MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = MenuShape)) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, MenuShape),
            offset = offset,
            scrollState = scrollState,
            properties = properties,
            content = content,
        )
    }
}
