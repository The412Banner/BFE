package com.the412banner.bfe.ui.screens

import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.WindowBlurBehind
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.ui.theme.rememberWindowBlurAvailable
import com.the412banner.bfe.ui.theme.rimBrush

// Single source of truth for the app's AlertDialogs: a drop-in wrapper around the Material3
// slot-based AlertDialog that adds a 1dp outline around the whole popup box — matching the
// outline that Modifier.outlinedMenuCard() (MenuStyle.kt) puts on the app's menus. Signature
// mirrors androidx.compose.material3.AlertDialog exactly (compose-bom 2024.02.00 → material3
// 1.2.0), so every call site can swap AlertDialog( → OutlinedAlertDialog( unchanged. The
// border shares the dialog's shape so its corner radius follows the rounded dialog.
// Aurora Glass: the same dialog as a frosted glass panel (24 dp, rim hairline, blur behind on 12+).
@Composable
internal fun OutlinedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    if (isAurora) {
        // Aurora Glass: a frosted panel with the rim hairline. On Android 12+ the screen behind the
        // dialog is blurred by the system, so the panel can be a little see-through; without that blur
        // it stays dense.
        val g = LocalGlass.current
        val blur = rememberWindowBlurAvailable()
        AlertDialog(
            onDismissRequest = onDismissRequest,
            // The blur-behind is set from inside the dialog's own window (this slot is composed there).
            confirmButton = { WindowBlurBehind(enabled = blur); confirmButton() },
            modifier = modifier.border(Glass.HairlineWidth, g.rimBrush(), shape),
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = if (blur) g.dialogOverBlur else g.dialogSolid,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = 0.dp,
            properties = properties
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}
