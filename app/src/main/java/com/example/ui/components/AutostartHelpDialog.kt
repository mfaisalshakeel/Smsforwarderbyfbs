package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.util.PowerHelper

/**
 * Xiaomi, Oppo, Vivo and their relatives freeze background apps regardless of the platform's
 * own battery exemption, and each buries the switch somewhere different. Telling the user the
 * exact path for their phone is the difference between the app working and not.
 */
@Composable
fun AutostartHelpDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.dialog,
        modifier = Modifier.testTag("autostart_help_dialog"),
        title = { Text("Keep this app running") },
        text = {
            Column {
                Text(
                    text = "Your phone closes background apps more aggressively than Android " +
                        "itself does. To make sure messages are never missed:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.size(Spacing.md))
                Text(
                    text = PowerHelper.autostartInstructions(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(Spacing.md))
                Text(
                    text = "You only need to do this once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}
