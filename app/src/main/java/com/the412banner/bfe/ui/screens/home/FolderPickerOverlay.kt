package com.the412banner.bfe.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.ui.screens.FileManagerScreen
import java.io.File

/**
 * A full-screen "choose a folder" page built on the file manager's own folder-pick mode (single
 * pane, folders only, a "Select this folder" button). Unlike the system folder picker it can choose
 * Download/ or a storage root, which Android 11+ refuses to hand out.
 *
 * [start] picks the volume it opens on (at its root, so every folder on it is reachable).
 * Back walks up inside the picker first (its pane registers its Back handler after this one, so it
 * wins while it can go up); at the top Back closes the picker.
 */
@Composable
fun FolderPickerOverlay(title: String, start: File? = null, onPick: (File) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 2.dp),
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cancel") }
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            FileManagerScreen(
                pickMode = true,
                pickDirMode = true,
                // The pane's start folder is also its Back floor, so start at the volume root.
                initialDir = volumeRootOf(start ?: File("/storage/emulated/0")),
                onPick = onPick,
            )
        }
    }
}
