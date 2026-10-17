package com.the412banner.bfe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.the412banner.bfe.cloud.RcloneSecureConfig
import com.the412banner.bfe.security.SecretVault
import com.the412banner.bfe.server.ServerPrefs
import com.the412banner.bfe.ui.screens.OutlinedAlertDialog

/**
 * Tells the user — once — that saved credentials had to be cleared because the vault key that
 * protected them is gone (keystore reset, BFE's data moved to another device). Shown over any screen
 * as soon as the vault notices; OK forgets the notice.
 */
@Composable
fun SecurityNoticeHost() {
    val context = LocalContext.current
    val lost by remember { SecretVault.lost(context) }.collectAsState()
    if (lost.isEmpty()) return

    val lines = buildList {
        if (RcloneSecureConfig.VAULT_ENTRY in lost) add("Cloud and Remote: add your accounts again.")
        if (ServerPrefs.PASSWORD_ENTRY in lost) add("Access from network: BFE made a new password; it's shown on that screen.")
        val keys = lost.count { it.startsWith("apk.key/") }
        if (keys > 0) add("Signing keys: the passwords of $keys ${if (keys == 1) "key were" else "keys were"} cleared. Remove ${if (keys == 1) "it" else "them"} and import again.")
        if (isEmpty()) add("Re-enter the passwords BFE asks for.")
    }
    OutlinedAlertDialog(
        onDismissRequest = { SecretVault.acknowledgeLost(context) },
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text("Saved sign-ins were cleared") },
        text = {
            Column {
                Text(
                    "BFE's credential key on this device is gone (for example after the device's keystore was reset, " +
                        "or BFE's data was moved to another device), so what it protected can't be unlocked any more.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                lines.forEach {
                    Spacer(Modifier.height(8.dp))
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { SecretVault.acknowledgeLost(context) }) { Text("OK") } },
    )
}
