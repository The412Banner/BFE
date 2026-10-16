@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Stand-ins for the Cloud, Remote and Access-from-network screens, which are being built separately.
// Each takes the same (onBack) shape as the real screen, so swapping one in is a one-line change at
// its route in MainActivity's AppRoot.

@Composable
fun CloudPlaceholder(onBack: () -> Unit) = ComingSoon(
    title = "Cloud",
    style = HomeTileStyles.Cloud,
    detail = "Browse and manage your cloud storage services from BFE.",
    onBack = onBack,
)

@Composable
fun RemotePlaceholder(onBack: () -> Unit) = ComingSoon(
    title = "Remote",
    style = HomeTileStyles.Remote,
    detail = "Connect to SFTP, SMB, WebDAV and other remote servers.",
    onBack = onBack,
)

@Composable
fun ServerPlaceholder(onBack: () -> Unit) = ComingSoon(
    title = "Access from network",
    style = HomeTileStyles.Network,
    detail = "Share this device's storage over Wi-Fi with a built-in HTTP / FTP server.",
    onBack = onBack,
)

@Composable
private fun ComingSoon(title: String, style: TileStyle, detail: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp).clip(CircleShape).background(style.color.copy(alpha = 0.16f)),
            ) {
                Icon(style.icon, null, tint = style.color, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Coming soon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
