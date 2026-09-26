package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.DestinationType
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.ui.components.AppCard
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ContentContainer
import com.example.ui.components.EmptyState
import com.example.ui.components.InfoBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.Tone
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.rulesState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()

    var editingRule by remember { mutableStateOf<ForwardingRuleEntity?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var rulePendingDeletion by remember { mutableStateOf<ForwardingRuleEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        ContentContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(top = Spacing.sm, bottom = 96.dp)
            ) {
                if (!settings.isSenderAccountConfigured &&
                    rules.any { it.destinationType == DestinationType.EMAIL }
                ) {
                    item {
                        InfoBanner(
                            title = "No email account connected",
                            message = "Your email rules cannot send until you link a sender account.",
                            tone = Tone.Warning,
                            action = {
                                TextButton(onClick = onNavigateToSettings) { Text("Open setup") }
                            }
                        )
                    }
                }

                if (rules.isEmpty()) {
                    item {
                        AppCard {
                            EmptyState(
                                icon = Icons.Default.FilterList,
                                title = "No rules yet",
                                message = "A rule decides which messages get forwarded and where " +
                                    "they go. Create one to get started."
                            )
                        }
                    }
                } else {
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { enabled -> viewModel.toggleRule(rule.id, enabled) },
                            onEdit = { editingRule = rule },
                            onDelete = { rulePendingDeletion = rule },
                            onTest = { viewModel.testRule(rule) }
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { isCreating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
                .testTag("add_rule_fab"),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New rule") }
        )
    }

    if (isCreating || editingRule != null) {
        RuleEditorDialog(
            initialRule = editingRule,
            availableSimCount = availableSims.size,
            onDismiss = {
                isCreating = false
                editingRule = null
            },
            onSave = { rule ->
                viewModel.saveRule(rule)
                isCreating = false
                editingRule = null
            }
        )
    }

    rulePendingDeletion?.let { rule ->
        ConfirmDialog(
            title = "Delete this rule?",
            message = "\"${rule.name}\" will stop forwarding immediately. " +
                "Messages already in your history are not affected.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteRule(rule.id)
                rulePendingDeletion = null
            },
            onDismiss = { rulePendingDeletion = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(
    rule: ForwardingRuleEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = destinationLabel(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = rule.isEnabled, onCheckedChange = onToggle)
        }

        Spacer(modifier = Modifier.size(Spacing.md))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (rule.forwardSms) StatusPill(text = "SMS", tone = Tone.Info)
            if (rule.forwardMms) StatusPill(text = "MMS", tone = Tone.Info)
            if (rule.forwardMissedCalls) StatusPill(text = "Missed calls", tone = Tone.Info)
            if (rule.forwardNotifications) {
                val appCount = rule.selectedPackages.size
                StatusPill(
                    text = if (appCount == 0) "All apps" else "$appCount app(s)",
                    tone = Tone.Info
                )
            }
            if (rule.simSlot > 0) StatusPill(text = "SIM ${rule.simSlot}", tone = Tone.Neutral)
            if (rule.senderFilterType != "ANY") {
                StatusPill(text = "Sender filter", tone = Tone.Neutral)
            }
            if (rule.contentFilterType != "ANY") {
                StatusPill(text = "Keyword filter", tone = Tone.Neutral)
            }
            if (rule.scheduleEnabled) {
                StatusPill(text = "Scheduled", tone = Tone.Neutral, icon = Icons.Default.Schedule)
            }
            if (rule.digestEnabled) {
                StatusPill(text = "Digest", tone = Tone.Neutral, icon = Icons.Default.Inbox)
            }
            if (!rule.isConfigured) {
                StatusPill(text = "Incomplete", tone = Tone.Danger)
            }
        }

        Spacer(modifier = Modifier.size(Spacing.sm))

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onTest) {
                Icon(Icons.Default.Send, contentDescription = "Send a test message")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit this rule")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete this rule",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun destinationLabel(rule: ForwardingRuleEntity): String {
    val prefix = when (rule.destinationType) {
        DestinationType.SMS -> "SMS to"
        DestinationType.WEBHOOK -> "Webhook to"
        DestinationType.TELEGRAM -> "Telegram to"
        else -> "Email to"
    }
    return "$prefix ${rule.destinationSummary}"
}
