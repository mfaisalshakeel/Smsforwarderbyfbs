package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.LogStatus
import com.example.data.local.entity.SmsLogEntity
import com.example.forwarder.MessageSource
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Maps a log status to the tone and label used everywhere it appears. */
fun statusTone(status: String): Tone = when (status) {
    LogStatus.SUCCESS -> Tone.Success
    LogStatus.FAILED -> Tone.Danger
    LogStatus.PENDING, LogStatus.BATCHED -> Tone.Warning
    else -> Tone.Neutral
}

fun statusLabel(status: String): String = when (status) {
    LogStatus.SUCCESS -> "Delivered"
    LogStatus.FAILED -> "Failed"
    LogStatus.PENDING -> "Queued"
    LogStatus.BATCHED -> "In digest"
    LogStatus.SKIPPED -> "Skipped"
    else -> status
}

private fun sourceIcon(source: String): ImageVector = when (source) {
    MessageSource.NOTIFICATION -> Icons.Default.Notifications
    MessageSource.CALL -> Icons.Default.PhoneMissed
    MessageSource.MMS -> Icons.Default.Image
    else -> Icons.Default.Sms
}

private fun statusIcon(status: String): ImageVector = when (status) {
    LogStatus.SUCCESS -> Icons.Default.CheckCircle
    LogStatus.FAILED -> Icons.Default.Error
    LogStatus.PENDING -> Icons.Default.HourglassEmpty
    else -> Icons.Default.RemoveCircle
}

fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatFullTimestamp(timestamp: Long): String =
    SimpleDateFormat("EEEE d MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

/** One row in the history list. */
@Composable
fun LogItemCard(
    log: SmsLogEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(onClick = onClick),
        shape = Shapes.cardCompact,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = sourceIcon(log.source),
                    contentDescription = MessageSource.label(log.source),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                Text(
                    text = log.sender,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                StatusPill(
                    text = statusLabel(log.status),
                    tone = statusTone(log.status),
                    icon = statusIcon(log.status)
                )
            }

            Spacer(modifier = Modifier.size(Spacing.sm))

            Text(
                text = log.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.size(Spacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(log.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (log.destinationTarget.isNotBlank() && log.destinationTarget != "-") {
                    Text(
                        text = "  •  ${log.destinationTarget}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Full detail of a single forwarding attempt, with retry and delete. */
@Composable
fun LogDetailDialog(
    log: SmsLogEntity,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    isRetrying: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.dialog,
        title = {
            Column {
                Text(text = log.sender, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.size(Spacing.xs))
                StatusPill(text = statusLabel(log.status), tone = statusTone(log.status))
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailRow("Received", formatFullTimestamp(log.receivedAt))
                DetailRow("Source", MessageSource.label(log.source))
                log.packageName?.let { DetailRow("App package", it) }
                log.ruleName?.let { DetailRow("Rule", it) }
                DetailRow("Destination", "${log.destinationType} → ${log.destinationTarget}")
                if (log.simSlot > 0) DetailRow("SIM", "SIM ${log.simSlot}")
                if (log.retryCount > 0) DetailRow("Attempts", (log.retryCount + 1).toString())
                log.forwardedAt?.let { DetailRow("Delivered", formatFullTimestamp(it)) }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.md),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(text = "Message", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.size(Spacing.xs))
                Text(
                    text = log.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!log.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.size(Spacing.md))
                    InfoBanner(
                        title = "What went wrong",
                        message = log.errorMessage,
                        tone = Tone.Danger,
                        icon = Icons.Default.Error
                    )
                }
                if (!log.responsePayload.isNullOrBlank()) {
                    Spacer(modifier = Modifier.size(Spacing.md))
                    Text(
                        text = log.responsePayload,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            if (log.isBatched) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else if (log.isRetryable) {
                Button(onClick = onRetry, enabled = !isRetrying, shape = Shapes.button) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(Spacing.sm))
                    }
                    Text("Send again")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(Spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** Reusable destructive confirmation. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.dialog,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
