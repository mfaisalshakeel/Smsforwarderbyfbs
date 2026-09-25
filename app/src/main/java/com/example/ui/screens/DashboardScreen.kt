package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.SmsLogEntity
import com.example.ui.components.AppCard
import com.example.ui.components.ContentContainer
import com.example.ui.components.EmptyState
import com.example.ui.components.LogDetailDialog
import com.example.ui.components.LogItemCard
import com.example.ui.components.PenduCoderFooter
import com.example.ui.components.SectionHeader
import com.example.ui.components.SetupChecklistCard
import com.example.ui.components.SetupStep
import com.example.ui.components.StatTile
import com.example.ui.components.StatusDot
import com.example.ui.components.Tone
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    setupSteps: List<SetupStep>,
    onNavigateToRules: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedLog by remember { mutableStateOf<SmsLogEntity?>(null) }

    val setupIncomplete = setupSteps.any { it.isRequired && !it.isComplete } ||
        !settings.isSenderAccountConfigured

    ContentContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = Spacing.sm,
                bottom = Spacing.xxl
            )
        ) {
            item {
                EngineCard(
                    isEnabled = settings.isForwarderEnabled,
                    isConfigured = settings.isSenderAccountConfigured,
                    activeRules = stats.activeRulesCount,
                    onToggle = viewModel::toggleMasterSwitch
                )
            }

            if (setupIncomplete) {
                item {
                    SetupChecklistCard(steps = setupSteps)
                }
            }

            item {
                SectionHeader(title = "At a glance")
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(
                        value = stats.totalCount.toString(),
                        label = "Received",
                        tone = Tone.Neutral,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToLogs
                    )
                    StatTile(
                        value = stats.successCount.toString(),
                        label = "Forwarded",
                        tone = Tone.Success,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToLogs
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(
                        value = stats.pendingCount.toString(),
                        label = "Queued for retry",
                        tone = if (stats.pendingCount > 0) Tone.Warning else Tone.Neutral,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToLogs
                    )
                    StatTile(
                        value = stats.failedCount.toString(),
                        label = "Failed",
                        tone = if (stats.failedCount > 0) Tone.Danger else Tone.Neutral,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToLogs
                    )
                }
            }

            item {
                QuickActions(
                    onNavigateToRules = onNavigateToRules,
                    onNavigateToSimulator = onNavigateToSimulator,
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            item {
                SectionHeader(
                    title = "Recent activity",
                    trailing = {
                        if (recentLogs.isNotEmpty()) {
                            TextButton(onClick = onNavigateToLogs) { Text("See all") }
                        }
                    }
                )
            }

            if (recentLogs.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Default.Inbox,
                            title = "Nothing forwarded yet",
                            message = if (stats.activeRulesCount == 0) {
                                "Create your first rule and messages will start appearing here."
                            } else {
                                "When a message matches one of your rules it will show up here."
                            },
                            action = if (stats.activeRulesCount == 0) {
                                {
                                    Button(onClick = onNavigateToRules, shape = Shapes.button) {
                                        Text("Create a rule")
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            } else {
                items(recentLogs.take(5), key = { it.id }) { log ->
                    LogItemCard(log = log, onClick = { selectedLog = log })
                }
            }

            item {
                Spacer(modifier = Modifier.size(Spacing.sm))
                PenduCoderFooter()
            }
        }
    }

    selectedLog?.let { log ->
        LogDetailDialog(
            log = log,
            isRetrying = isBusy,
            onDismiss = { selectedLog = null },
            onRetry = { viewModel.retryLog(log.id) { selectedLog = null } },
            onDelete = {
                viewModel.deleteLog(log.id)
                selectedLog = null
            }
        )
    }
}

@Composable
private fun EngineCard(
    isEnabled: Boolean,
    isConfigured: Boolean,
    activeRules: Int,
    onToggle: (Boolean) -> Unit
) {
    val tone = when {
        !isEnabled -> Tone.Neutral
        !isConfigured -> Tone.Warning
        activeRules == 0 -> Tone.Warning
        else -> Tone.Success
    }
    val statusText = when {
        !isEnabled -> "Paused"
        !isConfigured -> "Waiting for an email account"
        activeRules == 0 -> "No active rules yet"
        else -> "Watching for messages"
    }

    AppCard(tone = tone, filled = tone != Tone.Neutral) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(tone = tone, description = statusText)
            Spacer(modifier = Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) "Forwarding is on" else "Forwarding is off",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("master_switch")
            )
        }
    }
}

@Composable
private fun QuickActions(
    onNavigateToRules: () -> Unit,
    onNavigateToSimulator: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column {
        SectionHeader(title = "Quick actions")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            QuickActionButton(
                label = "Rules",
                icon = Icons.Default.FilterList,
                onClick = onNavigateToRules,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = "Test",
                icon = Icons.Default.Science,
                onClick = onNavigateToSimulator,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                label = "Setup",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = Shapes.button,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.sm,
            vertical = Spacing.md
        ),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(Spacing.xs))
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
