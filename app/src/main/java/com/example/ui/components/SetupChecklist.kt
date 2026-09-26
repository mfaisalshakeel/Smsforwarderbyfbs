package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Spacing
import com.example.ui.theme.appColors

/** One requirement the user has to satisfy before forwarding works properly. */
data class SetupStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isComplete: Boolean,
    val isRequired: Boolean,
    val actionLabel: String,
    val onAction: () -> Unit
)

/**
 * The setup checklist. Every prerequisite lives in one place with its own action button, so a
 * new user is never left guessing which of four unrelated screens still needs attention.
 */
@Composable
fun SetupChecklistCard(
    steps: List<SetupStep>,
    modifier: Modifier = Modifier
) {
    val completed = steps.count { it.isComplete }
    val total = steps.size
    val allDone = completed == total

    AppCard(
        modifier = modifier,
        tone = if (allDone) Tone.Success else Tone.Warning,
        filled = !allDone
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (allDone) "Everything is set up" else "Finish setting up",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$completed of $total steps complete",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "$completed/$total",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.size(Spacing.md))

        LinearProgressIndicator(
            progress = { if (total == 0) 1f else completed.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .semantics { contentDescription = "$completed of $total setup steps complete" }
        )

        Spacer(modifier = Modifier.size(Spacing.md))

        steps.forEach { step ->
            SetupStepRow(step)
        }
    }
}

@Composable
private fun SetupStepRow(step: SetupStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .then(if (step.isComplete) Modifier else Modifier.clickable { step.onAction() })
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (step.isComplete) {
                        MaterialTheme.appColors.success.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (step.isComplete) Icons.Default.Check else step.icon,
                contentDescription = if (step.isComplete) "Done" else "Not done yet",
                tint = if (step.isComplete) {
                    MaterialTheme.appColors.success
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.size(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = step.title, style = MaterialTheme.typography.bodyLarge)
                if (!step.isRequired) {
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    StatusPill(text = "Optional", tone = Tone.Neutral)
                }
            }
            if (!step.isComplete) {
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!step.isComplete) {
            TextButton(onClick = step.onAction) { Text(step.actionLabel) }
        }
    }
}

/** Icons shared by the checklist and the onboarding flow. */
object SetupIcons {
    val Sms = Icons.Default.Sms
    val Notifications = Icons.Default.NotificationsActive
    val Battery = Icons.Default.BatteryAlert
    val Phone = Icons.Default.PhoneAndroid
}
