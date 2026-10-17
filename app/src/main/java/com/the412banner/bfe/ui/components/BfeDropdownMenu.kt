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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

private val MenuShape = RoundedCornerShape(12.dp)

/**
 * BFE's drop-down menu: Material 3's [DropdownMenu] with rounder corners and a thin outline, so a menu
 * reads as its own panel over lists and cards of the same colour. Same parameters as [DropdownMenu];
 * every menu in the app uses this one.
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
