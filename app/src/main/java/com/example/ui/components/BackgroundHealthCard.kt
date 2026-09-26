package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.util.BackgroundHealth
import com.example.util.BackgroundHealthChecker
import com.example.util.HealthAction
import com.example.util.HealthIssue
import com.example.util.HealthStatus
import com.example.util.IssueSeverity

/**
 * The most important thing on the screen: an honest answer to "is this actually running?".
 *
 * A switch being on proves nothing — Android kills background apps silently. This reports what
 * the engine has really been doing, names every reason it might not be, and gives each one a
 * button that fixes it.
 */
@Composable
fun BackgroundHealthCard(
    health: BackgroundHealth,
    now: Long,
    onAction: (HealthAction) -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = when (health.status) {
        HealthStatus.WORKING -> Tone.Success
        HealthStatus.AT_RISK -> Tone.Warning
        HealthStatus.NOT_WORKING -> Tone.Danger
    }
    val icon = when (health.status) {
        HealthStatus.WORKING -> Icons.Default.CheckCircle
        HealthStatus.AT_RISK -> Icons.Default.WarningAmber
        HealthStatus.NOT_WORKING -> Icons.Default.ErrorOutline
    }

    AppCard(
        modifier = modifier
            .testTag("background_health_card")
            .semantics { contentDescription = health.headline },
        tone = tone,
        filled = true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(toneAccent(tone).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = toneAccent(tone),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.size(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = health.headline,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = statusDetail(health, now),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (health.issues.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Spacing.md))
            HorizontalDivider(color = toneAccent(tone).copy(alpha = 0.25f))

            health.issues.forEach { issue ->
                HealthIssueRow(issue = issue, onAction = onAction)
            }
        }

        Spacer(modifier = Modifier.size(Spacing.sm))
        TextButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier.testTag("open_diagnostics_button")
        ) {
            Text("See background details")
        }
    }
}

/** The line under the headline: what the engine last actually did. */
private fun statusDetail(health: BackgroundHealth, now: Long): String = when {
    health.status == HealthStatus.NOT_WORKING ->
        "${health.blockingIssues.size} thing(s) must be fixed before messages can be forwarded."

    health.lastDeliveryAt > 0L ->
        "Last message forwarded " + BackgroundHealthChecker.describeAge(now - health.lastDeliveryAt) + " ago."

    health.lastEventAt > 0L ->
        "Last checked a message " + BackgroundHealthChecker.describeAge(now - health.lastEventAt) + " ago."

    health.lastHeartbeatAt > 0L ->
        "Running and waiting for the first message."

    else -> "Waiting for the first message."
}

@Composable
private fun HealthIssueRow(
    issue: HealthIssue,
    onAction: (HealthAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (issue.severity == IssueSeverity.BLOCKING) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
        )
        Spacer(modifier = Modifier.size(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = issue.title, style = MaterialTheme.typography.titleSmall)
            Text(text = issue.detail, style = MaterialTheme.typography.bodySmall)
        }
        val action = issue.action
        val label = issue.actionLabel
        if (action != null && label != null) {
            Spacer(modifier = Modifier.size(Spacing.sm))
            Button(
                onClick = { onAction(action) },
                shape = Shapes.button,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.xs
                )
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Expanded view: the raw facts, so a sceptical user can verify for themselves. */
@Composable
fun BackgroundDiagnosticsCard(
    health: BackgroundHealth,
    now: Long,
    serviceRunning: Boolean,
    lastStopReason: String,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Text(text = "Background details", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.size(Spacing.sm))

        DiagnosticRow("Service running", if (serviceRunning) "Yes" else "No")
        DiagnosticRow("Last check-in", relative(health.lastHeartbeatAt, now))
        DiagnosticRow("Last message seen", relative(health.lastEventAt, now))
        DiagnosticRow("Last message forwarded", relative(health.lastDeliveryAt, now))
        DiagnosticRow("Last restart after reboot", relative(health.lastBootRestartAt, now))
        if (lastStopReason.isNotBlank()) {
            DiagnosticRow("Last stop reason", lastStopReason)
        }

        Spacer(modifier = Modifier.size(Spacing.md))
        Text(
            text = "The service checks in every 10 minutes. If the last check-in is much older " +
                "than that, Android has killed the app and messages are being missed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun relative(timestamp: Long, now: Long): String =
    if (timestamp <= 0L) "Never" else BackgroundHealthChecker.describeAge(now - timestamp) + " ago"

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(Spacing.md))
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
